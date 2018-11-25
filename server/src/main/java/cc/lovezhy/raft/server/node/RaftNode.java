package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcContext;
import cc.lovezhy.raft.rpc.RpcServer;
import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.server.ClusterConfig;
import cc.lovezhy.raft.server.service.RaftService;
import cc.lovezhy.raft.server.service.RaftServiceImpl;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.service.model.ReplicatedLogResponse;
import cc.lovezhy.raft.server.service.model.VoteRequest;
import cc.lovezhy.raft.server.service.model.VoteResponse;
import cc.lovezhy.raft.server.storage.StorageService;
import cc.lovezhy.raft.server.utils.Pair;
import cc.lovezhy.raft.server.utils.TimeCountDownUtil;
import cc.lovezhy.raft.server.web.StatusHttpService;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.vertx.core.json.JsonObject;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static cc.lovezhy.raft.server.RaftConstants.*;

public class RaftNode implements RaftService {

    private Logger log;

    private NodeId nodeId;

    private AtomicReference<NodeStatus> currentNodeStatus = new AtomicReference<>();

    private ClusterConfig clusterConfig;

    private volatile Long currentTerm = 0L;

    private StorageService storageService;

    private List<PeerRaftNode> peerRaftNodes;

    private AtomicLong heartbeatTimeRecorder = new AtomicLong();

    private RpcServer rpcServer;
    private StatusHttpService httpService;

    private NodeScheduler nodeScheduler = new NodeScheduler();
    private WebLogger webLogger = new WebLogger();

    public RaftNode(NodeId nodeId, EndPoint endPoint, ClusterConfig clusterConfig, List<PeerRaftNode> peerRaftNodes) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        Preconditions.checkNotNull(clusterConfig);
        Preconditions.checkNotNull(peerRaftNodes);
        Preconditions.checkState(peerRaftNodes.size() >= 2, "raft cluster should init with at least 3 server!");

        log = LoggerFactory.getLogger(String.format(getClass().getName() + " node[%d]", nodeId.getPeerId()));

        log.debug("peerRaftNodes={}", JSON.toJSONString(peerRaftNodes));

        this.nodeId = nodeId;
        this.peerRaftNodes = peerRaftNodes;
        this.clusterConfig = clusterConfig;
        storageService = new StorageService();

        rpcServer = new RpcServer();
        RaftService serverService = new RaftServiceImpl(this);
        rpcServer.registerService(serverService);
        rpcServer.start(endPoint);

        httpService = new StatusHttpService(new NodeMonitor(), endPoint.getPort() + 1);
        httpService.createHttpServer();
    }

    public void init() {
        nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
        startElectionTimeOut();
    }

    private long startElectionTimeOut() {
        long waitTimeOut = getRandomStartElectionTimeout();
        long voteTerm = currentTerm;
        TimeCountDownUtil.addSchedulerTask(
                waitTimeOut,
                DEFAULT_TIME_UNIT,
                () -> preVote(voteTerm + 1),
                () -> nodeScheduler.isLoseHeartbeat() && !nodeScheduler.isLeader());
        return waitTimeOut;
    }

    private void preVote(Long voteTerm) {
        long nextElectionTimeOut = startElectionTimeOut();
        nodeScheduler.changeNodeStatus(NodeStatus.PRE_CANDIDATE);
        AtomicInteger preVotedGrantedCount = new AtomicInteger(1);
        CountDownLatch latch = new CountDownLatch(peerRaftNodes.size());
        peerRaftNodes.forEach(peerRaftNode -> {
            try {
                VoteRequest voteRequest = new VoteRequest();
                voteRequest.setTerm(voteTerm);
                voteRequest.setCandidateId(nodeId);
                voteRequest.setLastLogTerm(storageService.getLastCommitLogTerm());
                voteRequest.setLastLogIndex(storageService.getCommitIndex());
                peerRaftNode.getRaftService().requestPreVote(voteRequest);
                SettableFuture<VoteResponse> voteResponseFuture = RpcContext.getContextFuture();
                Futures.addCallback(voteResponseFuture, new FutureCallback<VoteResponse>() {
                    @Override
                    public void onSuccess(@Nullable VoteResponse result) {
                        if (result.getVoteGranted()) {
                            preVotedGrantedCount.incrementAndGet();
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        latch.countDown();
                    }
                }, RpcExecutors.commonExecutor());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        });
        try {
            latch.await(nextElectionTimeOut, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        if (preVotedGrantedCount.get() > clusterConfig.getNodeCount() / 2) {
            voteForLeader(voteTerm);
        }
    }


    private void voteForLeader(Long voteTerm) {
        if (!nodeScheduler.compareAndSetTerm(voteTerm - 1, voteTerm) || !nodeScheduler.setTermVotedForIfAbsent(voteTerm, nodeId)) {
            return;
        }
        log.info("startElection term={}", currentTerm);
        nodeScheduler.changeNodeStatus(NodeStatus.CANDIDATE);
        long nextWaitTimeOut = startElectionTimeOut();
        //初始值为1，把自己加进去
        AtomicInteger votedCount = new AtomicInteger(1);
        CountDownLatch latch = new CountDownLatch(peerRaftNodes.size());
        peerRaftNodes.forEach(peerRaftNode -> {
            try {
                VoteRequest voteRequest = new VoteRequest();
                voteRequest.setTerm(currentTerm);
                voteRequest.setCandidateId(nodeId);
                voteRequest.setLastLogIndex(storageService.getCommitIndex());
                peerRaftNode.getRaftService().requestVote(voteRequest);
                SettableFuture<VoteResponse> voteResponseFuture = RpcContext.getContextFuture();
                Futures.addCallback(voteResponseFuture, new FutureCallback<VoteResponse>() {
                    @Override
                    public void onSuccess(@Nullable VoteResponse result) {
                        if (result.getVoteGranted()) {
                            log.info("nodeId={}, receive vote from={}, term={}", nodeId, peerRaftNode.getNodeId(), currentTerm);
                            votedCount.incrementAndGet();
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        latch.countDown();
                    }
                }, RpcExecutors.commonExecutor());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        });
        try {
            latch.await(nextWaitTimeOut, DEFAULT_TIME_UNIT);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        if (votedCount.get() > clusterConfig.getNodeCount() / 2) {
            boolean beLeaderSuccess = nodeScheduler.beLeader(currentTerm);
            if (beLeaderSuccess) {
                log.info("beLeaderSuccess");
                nodeScheduler.changeNodeStatus(NodeStatus.LEADER);
                startHeartbeat();
            }
        }
    }

    private void startHeartbeat() {
        peerRaftNodes.forEach(peerRaftNode -> {
            try {
                ReplicatedLogRequest replicatedLogRequest = new ReplicatedLogRequest();
                replicatedLogRequest.setEntries(Collections.emptyList());
                replicatedLogRequest.setLeaderCommit(storageService.getCommitIndex());
                replicatedLogRequest.setLeaderId(nodeId);
                replicatedLogRequest.setTerm(currentTerm);
                peerRaftNode.getRaftService().requestAppendLog(replicatedLogRequest);
                SettableFuture<ReplicatedLogResponse> responseSettableFuture = RpcContext.getContextFuture();
                Futures.addCallback(responseSettableFuture, new FutureCallback<ReplicatedLogResponse>() {
                    @Override
                    public void onSuccess(@Nullable ReplicatedLogResponse result) {
                        log.info("nodeid={},heartbeatTimeRecorder send", nodeId);
                        if (!result.getSuccess()) {
                            if (result.getTerm() > currentTerm) {
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {

                    }
                }, RpcExecutors.commonExecutor());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        TimeCountDownUtil.addSchedulerTask(HEART_BEAT_TIME_INTERVAL, DEFAULT_TIME_UNIT, this::startHeartbeat, (Supplier<Boolean>) () -> nodeScheduler.isLeader());
    }

    private void appendLogs() {

    }


    public void close() {
        this.rpcServer.close();
        this.httpService.close();
    }

    @Override
    public VoteResponse requestPreVote(VoteRequest voteRequest) {
        Long term = currentTerm;
        if (voteRequest.getTerm() > term) {
            return new VoteResponse(term, true);
        }
        else {
            return new VoteResponse(term, false);
        }
    }

    @Override
    public synchronized VoteResponse requestVote(VoteRequest voteRequest) {
        Long term = currentTerm;
        if (voteRequest.getTerm() > term && nodeScheduler.compareAndSetTerm(term, voteRequest.getTerm()) && nodeScheduler.setTermVotedForIfAbsent(voteRequest.getTerm(), voteRequest.getCandidateId())) {
            nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
            nodeScheduler.receiveHeartbeat();
            log.info("voteForNodeId={}, term={}", voteRequest.getCandidateId(), currentTerm);
            return new VoteResponse(term, true);
        } else if (Objects.equals(term, voteRequest.getTerm()) && nodeScheduler.setTermVotedForIfAbsent(voteRequest.getTerm(), voteRequest.getCandidateId())) {
            nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
            nodeScheduler.receiveHeartbeat();
            log.info("voteForNodeId={}, term={}", voteRequest.getCandidateId(), currentTerm);
            return new VoteResponse(term, true);
        } else {
            return new VoteResponse(term, false);
        }
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        Long term = currentTerm;
        if (Objects.equals(replicatedLogRequest.getTerm(), term) && nodeScheduler.isFollower() && Objects.equals(replicatedLogRequest.getLeaderId(), nodeScheduler.getVotedFor(term))) {
            nodeScheduler.receiveHeartbeat();
            log.info("nodeId={}, term={}, receiveHeartbeat", nodeId, currentTerm);
            this.appendLogs();
            startElectionTimeOut();
            return new ReplicatedLogResponse(term, true);
        }

        if (replicatedLogRequest.getTerm() >= term && nodeScheduler.compareAndSetTerm(term, replicatedLogRequest.getTerm())) {
            nodeScheduler.setTermVotedForce(replicatedLogRequest.getTerm(), replicatedLogRequest.getLeaderId());
            nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
            nodeScheduler.receiveHeartbeat();
            log.info("receiveHeartbeat");
            this.appendLogs();
            startElectionTimeOut();
            return new ReplicatedLogResponse(term, true);
        }

        return new ReplicatedLogResponse(term, false);
    }


    class NodeScheduler {

        //term -> votedFor
        private Map<Long, NodeId> termVotedForNodeMap = new ConcurrentHashMap<>();

        private ReentrantLock lockScheduler = new ReentrantLock();

        private AtomicLong lastTimeOutInterval = new AtomicLong();

        /**
         * timeOut开始选举和收到其他节点信息之间存在竞态条件
         * <p>
         * 同时BeLeader和开始下一任选举之间也存在竞态条件
         * 如果当前节点已经是Leader，那么也不允许修改任期
         *
         * @return 是否更新成功
         * @see {@link NodeScheduler#beLeader(Long)}
         */
        boolean incrementTerm(Long expectTerm) {
            Preconditions.checkNotNull(expectTerm);
            return compareAndSetTerm(expectTerm, expectTerm + 1);
        }

        boolean compareAndSetTerm(Long expected, Long update) {
            Preconditions.checkNotNull(expected);
            Preconditions.checkNotNull(update);
            if (!Objects.equals(expected, currentTerm) || Objects.equals(currentNodeStatus.get(), NodeStatus.LEADER)) {
                return false;
            }
            try {
                lockScheduler.lock();
                if (!Objects.equals(expected, currentTerm) || Objects.equals(currentNodeStatus.get(), NodeStatus.LEADER)) {
                    return false;
                }
                currentTerm = update;
                return true;
            } finally {
                lockScheduler.unlock();
            }
        }

        /**
         * 修改当前节点的NodeStatus
         *
         * @param update
         */
        void changeNodeStatus(NodeStatus update) {
            Preconditions.checkNotNull(update);
            currentNodeStatus.set(update);
            log.debug("change nodeStatus={}", update.toString());
        }

        /**
         * 判断当前节点是不是Leader
         *
         * @return 是不是Leader
         */
        boolean isLeader() {
            return Objects.equals(currentNodeStatus.get(), NodeStatus.LEADER);
        }

        boolean isFollower() {
            return Objects.equals(currentNodeStatus.get(), NodeStatus.FOLLOWER);
        }

        /**
         * 因为开始选举之前会开始一个TimeOut
         * 如果TimeOut之后，还未成为Leader，就把任期+1，重新进行选举
         * TimeOut开始和新选举和上一个选举之间存在竞态条件
         * 如果成为Leader的时候，这一轮选举已经超时了，那么还是不能成为Leader
         * 所以在当前选举想要成为Leader，首先得确定当前任期还是选举开始时的任期
         *
         * @param term 成为Leader的任期
         * @return
         */
        boolean beLeader(Long term) {
            if (!Objects.equals(currentTerm, term)) {
                return false;
            }
            try {
                lockScheduler.lock();
                return Objects.equals(currentTerm, term);
            } finally {
                lockScheduler.unlock();
            }
        }

        /**
         * 每一个超时还未结束，如果有其他的节点已经超时，那么就会把Term+1开始进行选举
         * 所以对于这个VotedFor，需要做一个Map，Key是任期
         *
         * @param term   任期
         * @param nodeId NodeId
         * @return
         */
        boolean setTermVotedForIfAbsent(Long term, NodeId nodeId) {
            Preconditions.checkNotNull(term);
            Preconditions.checkNotNull(nodeId);
            return termVotedForNodeMap.putIfAbsent(term, nodeId) == null;
        }

        void setTermVotedForce(Long term, NodeId nodeId) {
            Preconditions.checkNotNull(term);
            Preconditions.checkNotNull(nodeId);
            termVotedForNodeMap.put(term, nodeId);
        }

        /**
         * 得到指定任期的VotedFor
         *
         * @param term
         * @return {nullable} NodeId
         */
        NodeId getVotedFor(Long term) {
            Preconditions.checkNotNull(term);
            return termVotedForNodeMap.get(term);
        }


        /**
         * 接收到心跳
         */
        void receiveHeartbeat() {
            heartbeatTimeRecorder.set(System.currentTimeMillis());
        }


        /**
         * 判断是否丢失心跳
         */
        boolean isLoseHeartbeat() {
            return System.currentTimeMillis() - heartbeatTimeRecorder.get() > HEART_BEAT_TIME_INTERVAL_TIMEOUT;
        }
    }

    /**
     * for StatusHttpService
     */
    public class NodeMonitor {
        public JsonObject getNodeStatus() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("NodeStatus", currentNodeStatus.get().toString());
            jsonObject.put("term", currentTerm.toString());
            jsonObject.put("Leader", RaftNode.this.nodeScheduler.getVotedFor(currentTerm).getPeerId());
            return jsonObject;
        }
    }

    /**
     * for the web logger
     */
    public class WebLogger {
        private List<Pair<Long, String>> logEntry = Collections.synchronizedList(Lists.newArrayList());

        public void addLog(String item) {
            logEntry.add(Pair.of(System.currentTimeMillis(), item));
        }
    }
}
