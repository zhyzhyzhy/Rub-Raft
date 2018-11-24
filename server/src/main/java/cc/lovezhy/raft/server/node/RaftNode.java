package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcServer;
import cc.lovezhy.raft.server.ClusterConfig;
import cc.lovezhy.raft.server.service.RaftService;
import cc.lovezhy.raft.server.service.RaftServiceImpl;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.service.model.ReplicatedLogResponse;
import cc.lovezhy.raft.server.service.model.VoteRequest;
import cc.lovezhy.raft.server.service.model.VoteResponse;
import cc.lovezhy.raft.server.storage.StorageService;
import cc.lovezhy.raft.server.utils.TimeCountDownUtil;
import cc.lovezhy.raft.server.web.StatusHttpService;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static cc.lovezhy.raft.server.RaftConstants.*;

public class RaftNode implements RaftService {

    private static final Logger log = LoggerFactory.getLogger(RaftNode.class);

    private NodeId nodeId;

    private volatile NodeStatus currentNodeStatus;

    private ClusterConfig clusterConfig;

    private volatile Long currentTerm = 0L;

    private volatile NodeId votedFor;

    private StorageService storageService;

    private List<PeerRaftNode> peerRaftNodes;

    private AtomicLong heartbeatTimeRecorder = new AtomicLong();

    private RpcServer rpcServer;
    private StatusHttpService httpService;

    private NodeScheduler nodeScheduler = new NodeScheduler();

    public RaftNode(NodeId nodeId, EndPoint endPoint, ClusterConfig clusterConfig, List<PeerRaftNode> peerRaftNodes) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        Preconditions.checkNotNull(clusterConfig);
        Preconditions.checkNotNull(peerRaftNodes);
        Preconditions.checkState(peerRaftNodes.size() >= 2, "raft cluster should init with at least 3 server!");
        log.info("peerRaftNodes={}", JSON.toJSONString(peerRaftNodes));

        this.nodeId = nodeId;
        this.peerRaftNodes = peerRaftNodes;
        this.clusterConfig = clusterConfig;
        storageService = new StorageService();

        rpcServer = new RpcServer();
        RaftService serverService = new RaftServiceImpl(this);
        rpcServer.registerService(serverService);
        rpcServer.start(endPoint);

        httpService = new StatusHttpService(this, endPoint.getPort() + 1);
        httpService.createHttpServer();
    }

    public void init() {
        nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
        startElectionTimeOut();
    }

    private long startElectionTimeOut() {
        long waitTimeOut = getRandomStartElectionTimeout();
        TimeCountDownUtil.addSchedulerTask(
                waitTimeOut,
                DEFAULT_TIME_UNIT,
                () -> voteForLeader(currentTerm + 1),
                () -> isLoseHeartbeat() && !nodeScheduler.isLeader());
        return waitTimeOut;
    }

//    private void preVote(Long currentTerm) {
//        startElectionTimeOut();
//        AtomicInteger preVotedGrantedCount = new AtomicInteger();
//        CountDownLatch latch = new CountDownLatch(peerRaftNodes.size());
//
//        peerRaftNodes.forEach(peerRaftNode -> {
//            CompletableFuture.runAsync(() -> {
//                try {
//                    VoteRequest voteRequest = new VoteRequest();
//                    voteRequest.setTerm(currentTerm + 1);
//                    voteRequest.setCandidateId(nodeId);
//                    voteRequest.setLastLogTerm(storageService.getLastCommitLogTerm());
//                    voteRequest.setLastLogIndex(storageService.getCommitIndex());
//                    VoteResponse voteResponse = peerRaftNode.getRaftService().requestPreVote(voteRequest);
//                    log.info("voteResponse={}", JSON.toJSONString(voteResponse));
//                    latch.countDown();
//                    if (voteResponse.getVoteGranted()) {
//                        preVotedGrantedCount.incrementAndGet();
//                    }
//                } catch (Exception e) {
//                    log.error(e.getMessage(), e);
//                }
//            });
//        });
//        try {
//            latch.wait(TimeUnit.MILLISECONDS.toMillis(500));
//        } catch (InterruptedException e) {
//            log.error(e.getMessage(), e);
//        }
//        if (preVotedGrantedCount.get() > clusterConfig.getNodeCount() / 2) {
//            log.info("start vote for leader");
//            voteForLeader();
//        }
//    }


    private void voteForLeader(Long voteTerm) {
        if (!nodeScheduler.compareAndSetTerm(voteTerm - 1, voteTerm)) {
            return;
        }
        nodeScheduler.changeNodeStatus(NodeStatus.PRE_CANDIDATE);
        long nextWaitTimeOut = startElectionTimeOut();
        AtomicInteger votedCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(peerRaftNodes.size());
        peerRaftNodes.forEach(peerRaftNode -> {
            CompletableFuture.runAsync(() -> {
                try {
                    VoteRequest voteRequest = new VoteRequest();
                    voteRequest.setTerm(currentTerm);
                    voteRequest.setCandidateId(nodeId);
                    voteRequest.setLastLogIndex(storageService.getCommitIndex());
                    VoteResponse voteResponse = peerRaftNode.getRaftService().requestVote(voteRequest);
                    latch.countDown();
                    if (voteResponse.getVoteGranted()) {
                        votedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            });
        });
        try {
            latch.wait(TimeUnit.MILLISECONDS.toMillis(nextWaitTimeOut));
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        if (votedCount.get() > clusterConfig.getNodeCount() / 2) {
            boolean beLeaderSuccess = nodeScheduler.beLeader(currentTerm);
            if (beLeaderSuccess) {
                startHeartbeat();
            }
        }
    }

    private void startHeartbeat() {
        peerRaftNodes.forEach(peerRaftNode -> {
            CompletableFuture.runAsync(() -> {
                try {
                    ReplicatedLogRequest replicatedLogRequest = new ReplicatedLogRequest();
                    replicatedLogRequest.setEntries(Collections.emptyList());
                    replicatedLogRequest.setLeaderCommit(storageService.getCommitIndex());
                    replicatedLogRequest.setLeaderId(nodeId);
                    replicatedLogRequest.setTerm(currentTerm);
                    peerRaftNode.getRaftService().requestAppendLog(replicatedLogRequest);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
        TimeCountDownUtil.addSchedulerTask(HEART_BEAT_TIME_INTERVAL, DEFAULT_TIME_UNIT, this::startHeartbeat, (Supplier<Boolean>) () -> nodeScheduler.isLeader());
    }

    private NodeId getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(NodeId nodeId) {
        this.votedFor = nodeId;
    }

    private void appendLogs() {

    }

    private void receiveHeartbeat() {
        this.heartbeatTimeRecorder.set(System.currentTimeMillis());
    }

    private boolean isLoseHeartbeat() {
        return System.currentTimeMillis() - heartbeatTimeRecorder.get() > HEART_BEAT_TIME_INTERVAL_TIMEOUT;
    }

    public void close() {
        this.rpcServer.close();
        this.httpService.close();
    }

    @Override
    public VoteResponse requestPreVote(VoteRequest voteRequest) {
//        VoteResponse voteResponse = new VoteResponse();
//        //自己已经是Pre_Candidate状态
//        if (!this.status.equals(NodeStatus.FOLLOWER)) {
//            voteResponse.setTerm(currentTerm.get());
//            voteResponse.setVoteGranted(false);
//            return voteResponse;
//        }
//        if (isLoseHeartbeat()) {
//            if (voteRequest.getTerm() > currentTerm.get()) {
//                voteResponse.setTerm(currentTerm.get());
//                voteResponse.setVoteGranted(true);
//            } else if (voteRequest.getTerm() == currentTerm.get() && voteRequest.getLastLogIndex() > storageService.getCommitIndex()) {
//                voteResponse.setTerm(currentTerm.get());
//                voteResponse.setVoteGranted(true);
//            } else {
//                voteResponse.setTerm(currentTerm.get());
//                voteResponse.setVoteGranted(false);
//            }
//        } else {
//            voteResponse.setTerm(currentTerm.get());
//            voteResponse.setVoteGranted(false);
//        }
//        log.info("voteRequest={}, voteResponse={}", JSON.toJSONString(voteRequest), JSON.toJSONString(voteResponse));
//        //开始超时
//        startElectionTimeOut();
//        return voteResponse;
    }

    @Override
    public synchronized VoteResponse requestVote(VoteRequest voteRequest) {
        Long term = currentTerm;
        if (Objects.isNull(this.getVotedFor()) && voteRequest.getTerm() > term && nodeScheduler.compareAndSetTerm(term, voteRequest.getTerm())) {
            this.setVotedFor(voteRequest.getCandidateId());
            return new VoteResponse(term, true);
        } else {
            return new VoteResponse(term, false);
        }
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        Long term = currentTerm;

        if (Objects.equals(replicatedLogRequest.getTerm(), term) && nodeScheduler.isFollower() && Objects.equals(replicatedLogRequest.getLeaderId(), this.getVotedFor())) {
            this.receiveHeartbeat();
            this.appendLogs();
            return new ReplicatedLogResponse(term, true);
        }

        if (replicatedLogRequest.getTerm() > term && nodeScheduler.compareAndSetTerm(term, replicatedLogRequest.getTerm())) {
            nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
            this.receiveHeartbeat();
            this.appendLogs();
            return new ReplicatedLogResponse(term, true);
        }

        return new ReplicatedLogResponse(term, false);
    }

    class NodeScheduler {
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
        public boolean incrementTerm(Long expectTerm) {
            Preconditions.checkNotNull(expectTerm);
            return compareAndSetTerm(expectTerm, expectTerm + 1);
        }

        private boolean compareAndSetTerm(Long expected, Long update) {
            Preconditions.checkNotNull(expected);
            Preconditions.checkNotNull(update);
            if (!Objects.equals(expected, currentTerm) || Objects.equals(currentNodeStatus, NodeStatus.LEADER)) {
                return false;
            }
            try {
                lockScheduler.lock();
                if (!Objects.equals(expected, currentTerm) || Objects.equals(currentNodeStatus, NodeStatus.LEADER)) {
                    return false;
                }
                currentTerm = update;
                return true;
            } finally {
                lockScheduler.unlock();
            }
        }

        public void changeNodeStatus(NodeStatus update) {
            Preconditions.checkNotNull(update);
            currentNodeStatus = update;
        }

        public boolean isLeader() {
            return Objects.equals(currentNodeStatus, NodeStatus.LEADER);
        }

        public boolean isFollower() {
            return Objects.equals(currentNodeStatus, NodeStatus.FOLLOWER);
        }

        /**
         * 因为开始选举之前会开始一个TimeOut
         * 如果TimeOut之间，还未成为Leader，就把任期+1，重新进行选举
         * TimeOut开始和新选举和上一个选举之间存在静态条件
         * 所以在当前选举想要成为Leader，首先得确定当前任期还是选举开始时的任期
         *
         * @param term 成为Leader的任期
         * @return
         */
        public boolean beLeader(Long term) {
            if (!Objects.equals(currentNodeStatus, term)) {
                return false;
            }
            try {
                lockScheduler.lock();
                return Objects.equals(currentNodeStatus, term);
            } finally {
                lockScheduler.unlock();
            }
        }
    }
}
