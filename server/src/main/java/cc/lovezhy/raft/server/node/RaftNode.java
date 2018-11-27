package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcContext;
import cc.lovezhy.raft.rpc.RpcServer;
import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.server.ClusterConfig;
import cc.lovezhy.raft.server.DefaultStateMachine;
import cc.lovezhy.raft.server.log.LogEntry;
import cc.lovezhy.raft.server.log.LogService;
import cc.lovezhy.raft.server.log.LogServiceImpl;
import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.service.RaftService;
import cc.lovezhy.raft.server.service.RaftServiceImpl;
import cc.lovezhy.raft.server.service.model.*;
import cc.lovezhy.raft.server.utils.Pair;
import cc.lovezhy.raft.server.utils.TimeCountDownUtil;
import cc.lovezhy.raft.server.web.ClientHttpService;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.vertx.core.json.JsonObject;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private LogService logService;

    private List<PeerRaftNode> peerRaftNodes;

    private AtomicLong heartbeatTimeRecorder = new AtomicLong();

    private RpcServer rpcServer;
    private ClientHttpService httpService;

    private NodeScheduler nodeScheduler = new NodeScheduler();

    private PeerNodeScheduler peerNodeScheduler = new PeerNodeScheduler();

    public RaftNode(NodeId nodeId, EndPoint endPoint, ClusterConfig clusterConfig, List<PeerRaftNode> peerRaftNodes) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        Preconditions.checkNotNull(clusterConfig);
        Preconditions.checkNotNull(peerRaftNodes);
        Preconditions.checkState(peerRaftNodes.size() >= 2, "raft cluster should init with at least 3 server!");

        log = LoggerFactory.getLogger(String.format(getClass().getName() + " node[%d]", nodeId.getPeerId()));

        this.nodeId = nodeId;
        this.peerRaftNodes = peerRaftNodes;
        this.clusterConfig = clusterConfig;
        logService = new LogServiceImpl(new DefaultStateMachine());

        rpcServer = new RpcServer();
        RaftService serverService = new RaftServiceImpl(this);
        rpcServer.registerService(serverService);
        rpcServer.start(endPoint);

        httpService = new ClientHttpService(new NodeMonitor(), endPoint.getPort() + 1);
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
                () -> nodeScheduler.isLoseHeartbeat(waitTimeOut) && !nodeScheduler.isLeader());
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
                voteRequest.setLastLogTerm(logService.getLastLogTerm());
                voteRequest.setLastLogIndex(logService.getLastLogIndex());
                peerRaftNode.getRaftService().requestPreVote(voteRequest);
                SettableFuture<VoteResponse> voteResponseFuture = RpcContext.getContextFuture();
                Futures.addCallback(voteResponseFuture, new FutureCallback<VoteResponse>() {
                    @Override
                    public void onSuccess(@Nullable VoteResponse result) {
                        if (Objects.nonNull(result)) {
                            if (result.getVoteGranted()) {
                                preVotedGrantedCount.incrementAndGet();
                            } else if (result.getTerm() > voteTerm) {
                                nodeScheduler.compareAndSetTerm(voteTerm, result.getTerm());
                            }
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error(t.getMessage(), t);
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
        if (!nodeScheduler.compareAndSetTerm(voteTerm - 1, voteTerm) || !nodeScheduler.compareAndSetVotedFor(null, nodeId)) {
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
                voteRequest.setLastLogIndex(logService.getLastLogIndex());
                voteRequest.setLastLogTerm(logService.getLastLogTerm());
                peerRaftNode.getRaftService().requestVote(voteRequest);
                SettableFuture<VoteResponse> voteResponseFuture = RpcContext.getContextFuture();
                Futures.addCallback(voteResponseFuture, new FutureCallback<VoteResponse>() {
                    @Override
                    public void onSuccess(@Nullable VoteResponse result) {
                        if (Objects.nonNull(result)) {
                            if (result.getVoteGranted()) {
                                log.info("receive vote from={}, term={}", peerRaftNode.getNodeId(), currentTerm);
                                votedCount.incrementAndGet();
                            } else if (result.getTerm() > voteTerm) {
                                nodeScheduler.compareAndSetTerm(voteTerm, result.getTerm());
                            }
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
                nodeScheduler.changeNodeStatus(NodeStatus.LEADER);
                peerNodeScheduler.updatePeerLogIndex();
                startHeartbeat();
            }
        }
    }

    private void startHeartbeat() {
        peerRaftNodes.forEach(peerRaftNode -> {
            try {
                ReplicatedLogRequest replicatedLogRequest = new ReplicatedLogRequest();
                replicatedLogRequest.setTerm(currentTerm);
                replicatedLogRequest.setLeaderId(nodeId);
                replicatedLogRequest.setEntries(Collections.emptyList());
                replicatedLogRequest.setLeaderCommit(logService.getLastCommitLogIndex());
                long preLogIndex = peerRaftNode.getNextIndex() - 1;
                replicatedLogRequest.setPrevLogIndex(preLogIndex);
                replicatedLogRequest.setPrevLogTerm(logService.get(preLogIndex - 1).getTerm());
                replicatedLogRequest.setEntries(logService.get(peerRaftNode.getNextIndex(), logService.getLastLogIndex()));
                peerRaftNode.getRaftService().requestAppendLog(replicatedLogRequest);
                SettableFuture<ReplicatedLogResponse> responseSettableFuture = RpcContext.getContextFuture();
                Futures.addCallback(responseSettableFuture, new FutureCallback<ReplicatedLogResponse>() {
                    @Override
                    public void onSuccess(@Nullable ReplicatedLogResponse result) {
                        //not happen
                        if (result.getTerm() > currentTerm) {
                            log.error("currentTerm={}, remoteServerTerm={}", currentTerm, result.getTerm());
                            throw new IllegalStateException("BUG！！！！");
                        }
                        if (!result.getSuccess()) {
                            if (!peerRaftNode.getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT)) {
                                long nextPreLogIndex = peerRaftNode.getNextIndex() - 2;
                                //如果已经是在Snapshot中
                                if (logService.hasInSnapshot(nextPreLogIndex)) {
                                    peerRaftNode.setNodeStatus(PeerNodeStatus.INSTALLSNAPSHOT);
                                    //TODO
                                    peerRaftNode.getRaftService().requestInstallSnapShot(new InstallSnapShotRequest());
                                    SettableFuture<ReplicatedLogResponse> responseSettableFuture = RpcContext.getContextFuture();
                                    Futures.addCallback(responseSettableFuture, new FutureCallback<ReplicatedLogResponse>() {
                                        @Override
                                        public void onSuccess(@Nullable ReplicatedLogResponse result) {
                                            //TODO
                                            peerRaftNode.setNodeStatus(PeerNodeStatus.PROBE);
                                        }

                                        @Override
                                        public void onFailure(Throwable t) {

                                        }
                                    }, RpcExecutors.commonExecutor());
                                }
                                else {
                                    peerRaftNode.setNextIndex(peerRaftNode.getNextIndex() - 1);
                                }
                            }
                        } else {
                            peerRaftNode.setMatchIndex(peerRaftNode.getNextIndex() - 1);
                        }
                    }
                    @Override
                    public void onFailure(Throwable t) {
                        t.printStackTrace();
                    }
                }, RpcExecutors.commonExecutor());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        TimeCountDownUtil.addSchedulerTask(HEART_BEAT_TIME_INTERVAL, DEFAULT_TIME_UNIT, this::startHeartbeat, (Supplier<Boolean>) () -> nodeScheduler.isLeader());
    }

    @Override
    public VoteResponse requestPreVote(VoteRequest voteRequest) throws IOException {
        Long term = currentTerm;
        if (voteRequest.getTerm() > term && logService.isNewerThanSelf(voteRequest.getLastLogTerm(), voteRequest.getLastLogIndex())) {
            return new VoteResponse(term, true);
        } else {
            return new VoteResponse(term, false);
        }
    }

    @Override
    public VoteResponse requestVote(VoteRequest voteRequest) throws IOException {
        Long term = currentTerm;
        if (term > voteRequest.getTerm()) {
            return new VoteResponse(term, false);
        }
        if (logService.isNewerThanSelf(voteRequest.getLastLogIndex(), voteRequest.getLastLogTerm())) {
            if (nodeScheduler.compareAndSetTerm(term, voteRequest.getTerm()) && (nodeScheduler.compareAndSetVotedFor(null, voteRequest.getCandidateId()) || nodeScheduler.compareAndSetVotedFor(voteRequest.getCandidateId(), voteRequest.getCandidateId()))) {
                nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
                nodeScheduler.receiveHeartbeat();
                startElectionTimeOut();
                return new VoteResponse(voteRequest.getTerm(), true);
            }
        }
        return new VoteResponse(term, false);
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) throws IOException, HasCompactException {
        Long term = currentTerm;

        // normal
        if (Objects.equals(replicatedLogRequest.getTerm(), term) && Objects.equals(replicatedLogRequest.getLeaderId(), nodeScheduler.getVotedFor())) {
            Preconditions.checkState(!nodeScheduler.isLeader());
            nodeScheduler.receiveHeartbeat();
            LogEntry logEntry = logService.get(replicatedLogRequest.getPrevLogIndex());
            boolean isSameTerm = false;
            if (Objects.nonNull(logEntry)) {
                isSameTerm = logEntry.getTerm().equals(replicatedLogRequest.getTerm());
            }
            if (isSameTerm) {
                logService.appendLog(replicatedLogRequest);
            }
            startElectionTimeOut();
            return new ReplicatedLogResponse(term, isSameTerm);
        }

        // 落单的，发现更高Term的Leader，直接变成Follower
        if (replicatedLogRequest.getTerm() > term && nodeScheduler.compareAndSetTerm(term, replicatedLogRequest.getTerm())) {
            nodeScheduler.setVotedForForce(replicatedLogRequest.getLeaderId());
            nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
            nodeScheduler.receiveHeartbeat();
            LogEntry logEntry = logService.get(replicatedLogRequest.getPrevLogIndex());
            boolean isSameTerm = false;
            if (Objects.nonNull(logEntry)) {
                isSameTerm = logEntry.getTerm().equals(replicatedLogRequest.getTerm());
            }
            if (isSameTerm) {
                logService.appendLog(replicatedLogRequest);
            }
            startElectionTimeOut();
            return new ReplicatedLogResponse(replicatedLogRequest.getTerm(), isSameTerm);
        }
        return new ReplicatedLogResponse(term, false);
    }

    @Override
    public InstallSnapShotResponse requestInstallSnapShot(InstallSnapShotRequest installSnapShotRequest) {
        return null;
    }

    public void close() {
        this.rpcServer.close();
        this.httpService.close();
    }

    class NodeScheduler {

        private AtomicReference<NodeId> votedFor = new AtomicReference<>();

        private ReentrantLock lockScheduler = new ReentrantLock();

        /**
         * timeOut开始选举和收到其他节点信息之间存在竞态条件
         * <p>
         * 同时BeLeader和开始下一任选举之间也存在竞态条件
         * 如果当前节点已经是Leader，那么也不允许修改任期
         *
         * @return 是否更新成功
         * @see {@link NodeScheduler#beLeader(Long)}
         */
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
                if (!Objects.equals(expected, update)) {
                    votedFor.set(null);
                }
                return true;
            } finally {
                lockScheduler.unlock();
            }
        }

        boolean compareAndSetVotedFor(NodeId expect, NodeId update) {
            return votedFor.compareAndSet(expect, update);
        }

        void setVotedForForce(NodeId nodeId) {
            votedFor.set(nodeId);
        }

        /**
         * 修改当前节点的NodeStatus
         *
         * @param update 新节点状态
         */
        void changeNodeStatus(NodeStatus update) {
            Preconditions.checkNotNull(update);
            currentNodeStatus.set(update);
        }

        /**
         * 判断当前节点是不是Leader
         *
         * @return 是不是Leader
         */
        boolean isLeader() {
            return Objects.equals(currentNodeStatus.get(), NodeStatus.LEADER);
        }

        /**
         * 因为开始选举之前会开始一个TimeOut
         * 如果TimeOut之后，还未成为Leader，就把任期+1，重新进行选举
         * TimeOut开始和新选举和上一个选举之间存在竞态条件
         * 如果成为Leader的时候，这一轮选举已经超时了，那么还是不能成为Leader
         * 所以在当前选举想要成为Leader，首先得确定当前任期还是选举开始时的任期
         *
         * @param term 成为Leader的任期
         * @return 是否成功成为Leader
         */
        boolean beLeader(Long term) {
            Preconditions.checkNotNull(term);
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
         * 得到VotedFor
         *
         * @return {nullable} NodeId
         */
        NodeId getVotedFor() {
            return votedFor.get();
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
        boolean isLoseHeartbeat(long timeoutMills) {
            return System.currentTimeMillis() - heartbeatTimeRecorder.get() > timeoutMills;
        }
    }

    public class PeerNodeScheduler {
        /**
         * 成为Leader之后，更新peer节点的NextIndex和MatchIndex
         */
        public void updatePeerLogIndex() {
            peerRaftNodes.forEach(peerRaftNode -> {
                peerRaftNode.setNextIndex(logService.getLastLogIndex() + 1);
                peerRaftNode.setMatchIndex(0L);
            });
        }
    }

    /**
     * for ClientHttpService
     */
    public class NodeMonitor {
        public JsonObject getNodeStatus() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("NodeStatus", currentNodeStatus.get().toString());
            jsonObject.put("term", currentTerm.toString());
            jsonObject.put("Leader", RaftNode.this.nodeScheduler.getVotedFor().getPeerId());

            Map<NodeId, String> nodeIdUrlMap = Maps.newHashMap();
            RaftNode.this.peerRaftNodes.forEach(peerRaftNode -> {
                EndPoint endPoint = EndPoint.create(peerRaftNode.getEndPoint().getHost(), peerRaftNode.getEndPoint().getPort() + 1);
                nodeIdUrlMap.put(peerRaftNode.getNodeId(), endPoint.toUrl() + "/status");
            });
            jsonObject.put("servers", nodeIdUrlMap);
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
