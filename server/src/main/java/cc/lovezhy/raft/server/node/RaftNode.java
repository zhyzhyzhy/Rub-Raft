package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcContext;
import cc.lovezhy.raft.rpc.RpcServer;
import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.server.ClusterConfig;
import cc.lovezhy.raft.server.DefaultStateMachine;
import cc.lovezhy.raft.server.NodeSlf4jHelper;
import cc.lovezhy.raft.server.log.*;
import cc.lovezhy.raft.server.service.RaftService;
import cc.lovezhy.raft.server.service.RaftServiceImpl;
import cc.lovezhy.raft.server.service.model.*;
import cc.lovezhy.raft.server.storage.StorageType;
import cc.lovezhy.raft.server.utils.EventRecorder;
import cc.lovezhy.raft.server.utils.TimeCountDownUtil;
import cc.lovezhy.raft.server.utils.VoteAction;
import cc.lovezhy.raft.server.web.ClientHttpService;
import com.alibaba.fastjson.JSON;
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

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static cc.lovezhy.raft.server.RaftConstants.*;
import static cc.lovezhy.raft.server.utils.EventRecorder.Event.LOG;

public class RaftNode implements RaftService {

    private final Logger log = LoggerFactory.getLogger(RaftNode.class);

    private NodeId nodeId;

    /**
     * 节点的当前状态
     */
    private AtomicReference<NodeStatus> currentNodeStatus = new AtomicReference<>();

    /**
     * 集群信息
     */
    private ClusterConfig clusterConfig;

    /**
     * 当前任期
     */
    private volatile Long currentTerm = 0L;

    /**
     * 日志服务
     */
    private LogService logService;

    private List<PeerRaftNode> peerRaftNodes;

    private AtomicLong heartbeatTimeRecorder = new AtomicLong();

    /**
     * 为其他的节点提供Rpc服务
     */
    private EndPoint endPoint;
    private RpcServer rpcServer;

    /**
     * http服务，可以查看节点的状态等信息
     */
    private ClientHttpService httpService;

    /**
     * 提供一些与Node状态相关的原子操作，为选举，超时一些竞态条件服务
     */
    private NodeScheduler nodeScheduler = new NodeScheduler();

    /**
     * PeerNode的一些状态更新，比如MatchIndex等
     */
    private PeerNodeScheduler peerNodeScheduler;

    /**
     * 心跳和超时选举的定时任务的管理
     */
    private TickManager tickManager = new TickManager();

    private OuterService outerService;

    private EventRecorder eventRecorder;

    public RaftNode(NodeId nodeId, EndPoint endPoint, ClusterConfig clusterConfig, List<PeerRaftNode> peerRaftNodes) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        Preconditions.checkNotNull(clusterConfig);
        Preconditions.checkNotNull(peerRaftNodes);
        Preconditions.checkState(peerRaftNodes.size() >= 2, "raft cluster should init with at least 3 server!");

        this.nodeId = nodeId;
        this.peerRaftNodes = peerRaftNodes;
        this.clusterConfig = clusterConfig;
        this.endPoint = endPoint;
        logService = new LogServiceImpl(new DefaultStateMachine(), StorageType.MEMORY);
    }

    public NodeScheduler getNodeScheduler() {
        return nodeScheduler;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public EndPoint getEndPoint() {
        return endPoint;
    }

    public void init() {
        NodeSlf4jHelper.initialize(nodeId);
        NodeSlf4jHelper.changeObjectLogger(nodeId, this);
        tickManager.init();
        currentTerm = 0L;
        heartbeatTimeRecorder.set(0L);
        //start rpc server
        rpcServer = new RpcServer();
        NodeSlf4jHelper.changeObjectLogger(nodeId, rpcServer);

        nodeScheduler.setVotedForForce(null);
        RaftService serverService = new RaftServiceImpl(this);
        rpcServer.registerService(serverService);
        rpcServer.start(endPoint);
        outerService = new OuterService();
        //start http service
        httpService = new ClientHttpService(outerService, endPoint.getPort() + 1);
        NodeSlf4jHelper.changeObjectLogger(nodeId, httpService);

        httpService.createHttpServer();
        peerRaftNodes.forEach(peerRaftNode -> peerRaftNode.connect(this.nodeId));
        nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
        tickManager.tickElectionTimeOut();
        eventRecorder = new EventRecorder();
    }


    private void preVote(Long voteTerm) {
        log.info("start preVote, voteTerm={}", voteTerm);
        eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("start preVote, term=%d", voteTerm));
        long nextElectionTimeOut = tickManager.tickElectionTimeOut();
        if (!nodeScheduler.changeNodeStatusWhenNot(NodeStatus.LEADER, NodeStatus.PRE_CANDIDATE)) {
            eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("preVote fail, race fail, node has be leader, voteTerm=%d", voteTerm));
            return;
        }
        AtomicInteger preVotedGrantedCount = new AtomicInteger(1);
        CountDownLatch latch = new CountDownLatch(clusterConfig.getNodeCount() / 2);
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
                                eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("grant vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
                                preVotedGrantedCount.incrementAndGet();
                            } else if (result.getTerm() > voteTerm) {
                                eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("refused vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
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
            eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("preVote success, voteTerm=%d", voteTerm));
            voteForLeader(voteTerm);
        } else {
            eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("preVote fail, voteTerm=%d", voteTerm));
        }
    }


    private void voteForLeader(Long voteTerm) {
        eventRecorder.add(EventRecorder.Event.VOTE, String.format("start vote term=%d", voteTerm));
        if (!nodeScheduler.compareAndSetTerm(voteTerm - 1, voteTerm) || !nodeScheduler.compareAndSetVotedFor(null, nodeId)) {
            eventRecorder.add(EventRecorder.Event.VOTE, String.format("vote fail, may find high term node,  term=%d", voteTerm));
            log.debug("voted for leader fail");
            return;
        }
        log.debug("startElection term={}", currentTerm);
        nodeScheduler.changeNodeStatus(NodeStatus.CANDIDATE);
        long nextWaitTimeOut = tickManager.tickElectionTimeOut();
        //初始值为1，把自己加进去
        AtomicInteger votedCount = new AtomicInteger(1);
        VoteAction voteAction = new VoteAction(clusterConfig.getNodeCount() / 2, clusterConfig.getNodeCount() / 2 + 1);
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
                            log.debug("receive vote from={}, grant={}", peerRaftNode.getNodeId(), result.getVoteGranted());
                            if (result.getVoteGranted()) {
                                log.debug("receive vote from={}, term={}", peerRaftNode.getNodeId(), currentTerm);
                                eventRecorder.add(EventRecorder.Event.VOTE, String.format("grant vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
                                votedCount.incrementAndGet();
                                voteAction.success();
                            } else if (result.getTerm() > voteTerm) {
                                eventRecorder.add(EventRecorder.Event.VOTE, String.format("refuse vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
                                nodeScheduler.compareAndSetTerm(voteTerm, result.getTerm());
                                voteAction.fail();
                            }
                        }
                        voteAction.fail();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error(t.getMessage(), t);
                        voteAction.fail();
                    }
                }, RpcExecutors.commonExecutor());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        });
        voteAction.await();
        if (voteAction.votedSuccess()) {
            eventRecorder.add(EventRecorder.Event.VOTE, String.format("vote success, term =  [%d]", voteTerm));
            log.debug("try to be leader, term={}", voteTerm);
            eventRecorder.add(EventRecorder.Event.VOTE, String.format("trying be leader, term =  [%d]", voteTerm));
            boolean beLeaderSuccess = nodeScheduler.beLeader(voteTerm);
            if (beLeaderSuccess && nodeScheduler.changeNodeStatusWhenNot(NodeStatus.PRE_CANDIDATE, NodeStatus.LEADER)) {
                eventRecorder.add(EventRecorder.Event.VOTE, String.format("be leader success, term =  [%d]", voteTerm));
                log.info("be the leader success, currentTerm={}", voteTerm);
                peerNodeScheduler = new PeerNodeScheduler();
                peerNodeScheduler.tickHeartBeat();
                outerService.appendLog(LogConstants.getDummyCommand());
            } else {
                eventRecorder.add(EventRecorder.Event.VOTE, String.format("be leader fail, term =  [%d]", voteTerm));
                log.debug("be the leader fail, currentTerm={}", voteTerm);
            }
        } else {
            eventRecorder.add(EventRecorder.Event.VOTE, String.format("vote fail, term =  [%d]", voteTerm));
            log.debug("voted for leader fail, timeout count {} ", votedCount.get());
        }
    }

    @Override
    public void requestConnect(ConnectRequest connectRequest) {
        NodeId requestNodeId = connectRequest.getNodeId();
        log.info("connectRequest nodeId={}", nodeId);
        this.peerRaftNodes.stream()
                .filter(peerRaftNode -> peerRaftNode.getNodeId().equals(requestNodeId))
                .findAny()
                .ifPresent(peerRaftNode -> peerRaftNode.connect(this.nodeId));
    }

    @Override
    public VoteResponse requestPreVote(VoteRequest voteRequest) {
        Long term = currentTerm;
        if (voteRequest.getTerm() > term && logService.isNewerThanSelf(voteRequest.getLastLogTerm(), voteRequest.getLastLogIndex())) {
            log.debug("receive preVote grant true " + voteRequest.getCandidateId());
            return new VoteResponse(term, true);
        } else {
            log.debug("receive preVote grant fail " + voteRequest.getCandidateId());
            return new VoteResponse(term, false);
        }
    }

    @Override
    public VoteResponse requestVote(VoteRequest voteRequest) {
        Long term = currentTerm;
        if (term > voteRequest.getTerm()) {
            return new VoteResponse(term, false);
        }
        if (logService.isNewerThanSelf(voteRequest.getLastLogTerm(), voteRequest.getLastLogIndex())) {
            if (nodeScheduler.compareAndSetTerm(term, voteRequest.getTerm()) && (nodeScheduler.compareAndSetVotedFor(null, voteRequest.getCandidateId()) || nodeScheduler.compareAndSetVotedFor(voteRequest.getCandidateId(), voteRequest.getCandidateId()))) {
                nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
                nodeScheduler.receiveHeartbeat();
                tickManager.tickElectionTimeOut();
                return new VoteResponse(voteRequest.getTerm(), true);
            }
        }
        return new VoteResponse(term, false);
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        Long term = currentTerm;
        eventRecorder.add(LOG, replicatedLogRequest.toString());
        //感觉应该不会出现，除非是消息延迟
        if (term > replicatedLogRequest.getTerm()) {
            return new ReplicatedLogResponse(term, false);
        }

        Preconditions.checkState(!nodeScheduler.isLeader());

        tickManager.tickElectionTimeOut();

        // normal
        if (Objects.equals(replicatedLogRequest.getLeaderId(), nodeScheduler.getVotedFor())) {
            log.info("receiveHeartbeat from={}", replicatedLogRequest.getLeaderId());
            nodeScheduler.receiveHeartbeat();
            return appendLog(replicatedLogRequest);
        }

        // 落单的，发现更高Term的Leader，直接变成Follower
        // 三个人选举的情况
        if (nodeScheduler.compareAndSetTerm(term, replicatedLogRequest.getTerm())) {
            nodeScheduler.setVotedForForce(replicatedLogRequest.getLeaderId());
            nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
            nodeScheduler.receiveHeartbeat();
            log.info("receiveHeartbeat from={}", replicatedLogRequest.getLeaderId());
            return appendLog(replicatedLogRequest);
        }
        return new ReplicatedLogResponse(term, false);
    }

    private ReplicatedLogResponse appendLog(ReplicatedLogRequest replicatedLogRequest) {
        log.info("prevLogIndex={}, LogEntry={}", replicatedLogRequest.getPrevLogIndex(), replicatedLogRequest.getEntries());
        LogEntry logEntry = logService.get(replicatedLogRequest.getPrevLogIndex());
        boolean isSameTerm = false;
        if (Objects.nonNull(logEntry)) {
            isSameTerm = logEntry.getTerm().equals(replicatedLogRequest.getPrevLogTerm());
        }
        if (isSameTerm) {
            logService.appendLog(replicatedLogRequest.getPrevLogIndex() + 1, replicatedLogRequest.getEntries());
        }
        logService.commit(replicatedLogRequest.getLeaderCommit());
        log.debug("/appendLog, isSameTerm={}", isSameTerm);
        return new ReplicatedLogResponse(replicatedLogRequest.getTerm(), isSameTerm);
    }

    @Override
    public InstallSnapshotResponse requestInstallSnapShot(InstallSnapshotRequest installSnapShotRequest) {
        Long term = currentTerm;
        if (installSnapShotRequest.getTerm() < term) {
            return new InstallSnapshotResponse(term, false);
        }
        logService.installSnapshot(installSnapShotRequest.getSnapshot());
        return new InstallSnapshotResponse(term, true);
    }

    public void close() {
        this.peerRaftNodes.forEach(PeerRaftNode::close);
        this.nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
        this.rpcServer.close();
        this.httpService.close();
        this.tickManager.cancelAll();
        if (Objects.nonNull(this.peerNodeScheduler)) {
            this.peerNodeScheduler.close();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RaftNode raftNode = (RaftNode) o;
        return com.google.common.base.Objects.equal(nodeId, raftNode.nodeId);
    }

    @Override
    public int hashCode() {
        return com.google.common.base.Objects.hashCode(nodeId);
    }

    public class NodeScheduler {

        private AtomicReference<NodeId> votedFor = new AtomicReference<>();

        private ReentrantLock lockScheduler = new ReentrantLock();

        /**
         * timeOut开始选举和收到其他节点信息之间存在竞态条件
         * <p>
         * 同时BeLeader和开始下一任选举之间也存在竞态条件
         * 如果当前节点已经是Leader，那么也不允许修改任期
         *
         * @return 是否更新成功
         * @see NodeScheduler#beLeader(Long)
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
        boolean changeNodeStatusWhenNot(NodeStatus whenNotStatus, NodeStatus update) {
            Preconditions.checkNotNull(whenNotStatus);
            Preconditions.checkNotNull(update);
            try {
                lockScheduler.lock();
                NodeStatus currentStatus = currentNodeStatus.get();
                if (!currentStatus.equals(whenNotStatus)) {
                    currentNodeStatus.set(update);
                    return true;
                } else {
                    return false;
                }
            } finally {
                lockScheduler.unlock();
            }
        }

        void changeNodeStatus(NodeStatus nodeStatus) {
            Preconditions.checkNotNull(nodeStatus);
            currentNodeStatus.set(nodeStatus);
        }

        /**
         * 判断当前节点是不是Leader
         *
         * @return 是不是Leader
         */
        public boolean isLeader() {
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
                return Objects.equals(currentTerm, term) && currentNodeStatus.get().equals(NodeStatus.CANDIDATE);
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

    public class PeerNodeScheduler implements Closeable {

        private Map<PeerRaftNode, PeerNodeStateMachine> peerNode;

        PeerNodeScheduler() {
            this.peerNode = Maps.newHashMap();
            // 更新nextIndex和matchIndex
            long nextIndex = logService.getLastLogIndex() + 1;
            peerRaftNodes.forEach(peerRaftNode -> {
                PeerNodeStateMachine peerNodeStateMachine = PeerNodeStateMachine.create(nextIndex);
                this.peerNode.put(peerRaftNode, peerNodeStateMachine);
            });
        }

        void tickHeartBeat() {
            Runnable appendHeartBeatTask = () -> peerNode.forEach((peerRaftNode, peerNodeStateMachine) -> peerNodeStateMachine.append(prepareAppendLog(peerRaftNode, peerNodeStateMachine)));
            appendHeartBeatTask.run();
            TimeCountDownUtil.addSchedulerTask(HEART_BEAT_TIME_INTERVAL, DEFAULT_TIME_UNIT, this::tickHeartBeat, (Supplier<Boolean>) () -> nodeScheduler.isLeader());
        }

        List<SettableFuture<Void>> appendLog(int logIndex) {
            List<SettableFuture<Void>> settableFutureList = Lists.newArrayList();
            peerNode.forEach((peerRaftNode, peerNodeStateMachine) -> {
                SettableFuture<Void> settableFuture = peerNodeStateMachine.setCompleteFuture(logIndex);
                settableFutureList.add(settableFuture);
                if (peerNodeStateMachine.taskQueueIsEmpty()) {
                    peerNodeStateMachine.appendFirst(prepareAppendLog(peerRaftNode, peerNodeStateMachine));
                }
            });
            return settableFutureList;
        }


        private Runnable prepareAppendLog(PeerRaftNode peerRaftNode, PeerNodeStateMachine peerNodeStateMachine) {
            return () -> {
                try {
                    long term = currentTerm;
                    long currentLastLogIndex = logService.getLastLogIndex();
                    long currentLastCommitLogIndex = logService.getLastCommitLogIndex();
                    long preLogIndex = peerNodeStateMachine.getNextIndex() - 1;

                    ReplicatedLogRequest replicatedLogRequest = new ReplicatedLogRequest();
                    replicatedLogRequest.setTerm(term);
                    replicatedLogRequest.setLeaderId(nodeId);
                    replicatedLogRequest.setLeaderCommit(currentLastCommitLogIndex);
//                    if (new Random().nextBoolean()) {
//                        replicatedLogRequest.setPrevLogIndex(preLogIndex - 1);
//                        replicatedLogRequest.setPrevLogTerm(logService.get(preLogIndex - 1).getTerm());
//                        List<LogEntry> logEntries = logService.get(peerNodeStateMachine.getNextIndex() - 1, currentLastLogIndex);
//                        replicatedLogRequest.setEntries(logEntries);
//                    } else {
                    replicatedLogRequest.setPrevLogIndex(preLogIndex);
                    replicatedLogRequest.setPrevLogTerm(logService.get(preLogIndex).getTerm());
                    List<LogEntry> logEntries = logService.get(peerNodeStateMachine.getNextIndex(), currentLastLogIndex);
                    replicatedLogRequest.setEntries(logEntries);
//                    }
                    //同步方法
                    ReplicatedLogResponse replicatedLogResponse = peerRaftNode.getRaftService().requestAppendLog(replicatedLogRequest);
                    /*
                     * 可能发生
                     * 成为Leader后直接被网络分区了
                     * 然后又好了，此时另外一个分区已经有Leader且Term比自己大
                     */
                    if (replicatedLogResponse.getTerm() > term) {
                        log.error("currentTerm={}, remoteServerTerm={}", term, replicatedLogResponse.getTerm());
                        log.debug("may have network isolate");
                        //TODO
                        return;
                    }

                    if (replicatedLogResponse.getSuccess()) {
                        peerNodeStateMachine.setNextIndex(currentLastLogIndex + 1);
                        peerNodeStateMachine.setMatchIndex(currentLastLogIndex);
                        if (!peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.NORMAL)) {
                            peerNodeStateMachine.setNodeStatus(PeerNodeStatus.NORMAL);
                        }
                        if (peerNodeStateMachine.needSendAppendLogImmediately() || peerNodeStateMachine.getMatchIndex() < logService.getLastLogIndex()) {
                            Runnable runnable = prepareAppendLog(peerRaftNode, peerNodeStateMachine);
                            peerNodeStateMachine.appendFirst(runnable);
                        }
                    } else {
                        if (!peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT)) {
                            long nextPreLogIndex = peerNodeStateMachine.getNextIndex() - 1;
                            //如果已经是在Snapshot中
                            if (logService.hasInSnapshot(nextPreLogIndex)) {
                                peerNodeStateMachine.setNodeStatus(PeerNodeStatus.INSTALLSNAPSHOT);
                                Runnable runnable = prepareInstallSnapshot(peerRaftNode, peerNodeStateMachine);
                                peerNodeStateMachine.appendFirst(runnable);
                            } else {
                                peerNodeStateMachine.setNextIndex(peerNodeStateMachine.getNextIndex() - 1);
                                Runnable runnable = prepareAppendLog(peerRaftNode, peerNodeStateMachine);
                                peerNodeStateMachine.appendFirst(runnable);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            };
        }

        private Runnable prepareInstallSnapshot(PeerRaftNode peerRaftNode, PeerNodeStateMachine peerNodeStateMachine) {
            return () -> {
                try {
                    Snapshot snapShot = logService.getSnapShot();
                    InstallSnapshotRequest installSnapShotRequest = new InstallSnapshotRequest();
                    installSnapShotRequest.setLeaderId(nodeId);
                    installSnapShotRequest.setTerm(currentTerm);
                    installSnapShotRequest.setSnapshot(snapShot);
                    InstallSnapshotResponse installSnapshotResponse = peerRaftNode.getRaftService().requestInstallSnapShot(installSnapShotRequest);
                    if (installSnapshotResponse.getSuccess()) {
                        peerNodeStateMachine.setNodeStatus(PeerNodeStatus.PROBE);
                        peerNodeStateMachine.setNextIndex(snapShot.getLastLogIndex() + 1);
                        peerNodeStateMachine.setMatchIndex(snapShot.getLastLogIndex());
                    } else {
                        throw new IllegalStateException();
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            };
        }

        @Override
        public void close() {
            peerNode.values().forEach(PeerNodeStateMachine::close);
        }
    }

    public class TickManager {
        private Optional<Future> preVoteFuture = Optional.empty();

        void init() {
            preVoteFuture = Optional.empty();
        }

        void cancelAll() {
            preVoteFuture.ifPresent(future -> future.cancel(false));
        }

        long tickElectionTimeOut() {
            preVoteFuture.ifPresent(future -> future.cancel(false));
            long waitTimeOut = getRandomStartElectionTimeout();
            long voteTerm = currentTerm;
            Future future = TimeCountDownUtil.addSchedulerTask(
                    waitTimeOut,
                    DEFAULT_TIME_UNIT,
                    () -> preVote(voteTerm + 1),
                    () -> nodeScheduler.isLoseHeartbeat(waitTimeOut) && !nodeScheduler.isLeader());
            this.preVoteFuture = Optional.of(future);
            return waitTimeOut;
        }
    }

    /**
     * 向外界提供服务的
     */
    public class OuterService {
        public boolean appendLog(DefaultCommand command) {
            if (nodeScheduler.isLeader()) {
                log.info("http append {}", JSON.toJSONString(command));
                LogEntry logEntry = LogEntry.of(command, currentTerm);
                int logIndex = logService.appendLog(logEntry);
                List<SettableFuture<Void>> settableFutureList = RaftNode.this.peerNodeScheduler.appendLog(logIndex);
                CountDownLatch latch = new CountDownLatch(clusterConfig.getNodeCount() / 2);
                settableFutureList.forEach(settableFuture -> settableFuture.addListener(latch::countDown, RpcExecutors.commonExecutor()));
                try {
                    latch.await();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                logService.commit(logIndex);
                log.info("commit success, index=" + logIndex + " command=" + JSON.toJSONString(command));
                return true;
            } else {
                //redirect to leader
                //TODO
                return false;
            }
        }

        public JsonObject getNodeStatus() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("NodeStatus", currentNodeStatus.get().toString());
            jsonObject.put("term", currentTerm.toString());
            jsonObject.put("Leader", RaftNode.this.nodeScheduler.getVotedFor().getPeerId());

            List<String> currentUrls = Lists.newArrayList();
            EndPoint endPoint = EndPoint.create(RaftNode.this.getEndPoint().getHost(), RaftNode.this.getEndPoint().getPort() + 1);
            currentUrls.add(endPoint.toUrl() + "/status");
            currentUrls.add(endPoint.toUrl() + "/data");
            for (EventRecorder.Event event : EventRecorder.Event.values()) {
                currentUrls.add(endPoint.toUrl() + "/log/" + event.getUrlPath());
            }
            jsonObject.put("urls", currentUrls);
            Map<NodeId, List<String>> nodeIdUrlMap = Maps.newHashMap();
            RaftNode.this.peerRaftNodes.forEach(peerRaftNode -> {
                EndPoint peerNodeHttpEndPoint = EndPoint.create(peerRaftNode.getEndPoint().getHost(), peerRaftNode.getEndPoint().getPort() + 1);
                List<String> urls = Lists.newArrayList();
                urls.add(peerNodeHttpEndPoint.toUrl() + "/status");
                urls.add(peerNodeHttpEndPoint.toUrl() + "/data");
                for (EventRecorder.Event event : EventRecorder.Event.values()) {
                    urls.add(peerNodeHttpEndPoint.toUrl() + "/log/" + event.getUrlPath());
                }
                nodeIdUrlMap.put(peerRaftNode.getNodeId(), urls);
            });
            jsonObject.put("servers", nodeIdUrlMap);
            return jsonObject;
        }

        public JsonObject getKVData() {
            DefaultStateMachine defaultStateMachine = (DefaultStateMachine) logService.getStateMachine();
            return new JsonObject(defaultStateMachine.getMap());
        }

        public JsonObject getEventLog(EventRecorder.Event event) {
            return new JsonObject(eventRecorder.eventRecorders(event));
        }
    }
}
