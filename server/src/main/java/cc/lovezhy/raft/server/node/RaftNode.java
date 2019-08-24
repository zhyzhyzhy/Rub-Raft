package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcContext;
import cc.lovezhy.raft.rpc.RpcServer;
import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.server.ClusterConfig;
import cc.lovezhy.raft.server.DefaultStateMachine;
import cc.lovezhy.raft.server.NodeSlf4jHelper;
import cc.lovezhy.raft.server.log.*;
import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.service.RaftService;
import cc.lovezhy.raft.server.service.RaftServiceImpl;
import cc.lovezhy.raft.server.service.model.*;
import cc.lovezhy.raft.server.storage.StorageType;
import cc.lovezhy.raft.server.utils.EventRecorder;
import cc.lovezhy.raft.server.utils.TimeCountDownUtil;
import cc.lovezhy.raft.server.utils.VoteAction;
import cc.lovezhy.raft.server.web.ClientHttpService;
import com.alibaba.fastjson.JSON;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

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

    private volatile boolean stopped = false;

    public RaftNode(NodeId nodeId, EndPoint endPoint, ClusterConfig clusterConfig, List<PeerRaftNode> peerRaftNodes) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        Preconditions.checkNotNull(clusterConfig);
        Preconditions.checkNotNull(peerRaftNodes);
//        Preconditions.checkState(peerRaftNodes.size() >= 2, "raft cluster should init with at least 3 server!");

        this.nodeId = nodeId;
        this.peerRaftNodes = peerRaftNodes;
        this.clusterConfig = clusterConfig;
        this.endPoint = endPoint;
    }

    public ClusterConfig getClusterConfig() {
        return clusterConfig;
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
        httpService = new ClientHttpService(outerService, endPoint.getPort() + 2);
        NodeSlf4jHelper.changeObjectLogger(nodeId, httpService);

        httpService.createHttpServer();
        peerRaftNodes.forEach(peerRaftNode -> peerRaftNode.connect(this.nodeId));
        nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
        tickManager.tickElectionTimeOut();
        eventRecorder = new EventRecorder(log);
        logService = new LogServiceImpl(new DefaultStateMachine(), StorageType.MEMORY, eventRecorder);
        NodeSlf4jHelper.changeObjectLogger(nodeId, logService);
        stopped = false;
    }

    public void init1(LogService logService, Long currentTerm, NodeId voteFor) {
        NodeSlf4jHelper.initialize(nodeId);
        NodeSlf4jHelper.changeObjectLogger(nodeId, this);
        tickManager.init();
        this.currentTerm = currentTerm;
        heartbeatTimeRecorder.set(0L);
        //start rpc server
        rpcServer = new RpcServer();
        NodeSlf4jHelper.changeObjectLogger(nodeId, rpcServer);

        nodeScheduler.setVotedForForce(voteFor);
        RaftService serverService = new RaftServiceImpl(this);
        rpcServer.registerService(serverService);
        rpcServer.start(endPoint);
        outerService = new OuterService();
        //start http service
        httpService = new ClientHttpService(outerService, endPoint.getPort() + 2);
        NodeSlf4jHelper.changeObjectLogger(nodeId, httpService);

        httpService.createHttpServer();
        peerRaftNodes.forEach(peerRaftNode -> peerRaftNode.connect(this.nodeId));
        nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
        tickManager.tickElectionTimeOut();
        eventRecorder = new EventRecorder(log);
        this.logService = logService;
        NodeSlf4jHelper.changeObjectLogger(nodeId, logService);
        stopped = false;
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
        VoteAction voteAction = new VoteAction(clusterConfig.getNodeCount() / 2, clusterConfig.getNodeCount() / 2 + 1);
        peerRaftNodes.forEach(peerRaftNode -> {
            try {
                VoteRequest voteRequest = new VoteRequest();
                voteRequest.setTerm(voteTerm);
                voteRequest.setCandidateId(nodeId);
                voteRequest.setLastLogTerm(logService.getLastLogTerm());
                voteRequest.setLastLogIndex(logService.getLastLogIndex());
                log.info("preVote peerId={}, VoteRequest={}", peerRaftNode.getNodeId(), JSON.toJSONString(voteRequest));
                peerRaftNode.getRaftService().requestPreVote(voteRequest);
                SettableFuture<VoteResponse> voteResponseFuture = RpcContext.getContextFuture();
                Futures.addCallback(voteResponseFuture, new FutureCallback<VoteResponse>() {
                    @Override
                    public void onSuccess(@Nullable VoteResponse result) {
                        if (Objects.nonNull(result)) {
                            if (result.getVoteGranted()) {
                                eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("grant vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
                                preVotedGrantedCount.incrementAndGet();
                                voteAction.success();
                            } else if (result.getTerm() > voteTerm) {
                                eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("refused vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
                                nodeScheduler.compareAndSetTerm(voteTerm, result.getTerm());
                                voteAction.fail();
                            } else {
                                eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("voteResponse = %s, voteTerm=%d, peerId=[%d]", JSON.toJSONString(result), voteTerm, peerRaftNode.getNodeId().getPeerId()));
                                voteAction.fail();
                            }
                        } else {
                            eventRecorder.add(EventRecorder.Event.PRE_VOTE, String.format("voteResponse = null, peerId=[%d]", peerRaftNode.getNodeId().getPeerId()));
                            voteAction.fail();
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error(t.getMessage());
                        voteAction.fail();
                    }
                }, RpcExecutors.commonExecutor());
            } catch (Exception e) {
                voteAction.fail();
                log.error(e.getMessage());
            }
        });
        voteAction.await();
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
        log.info("startElection term={}", currentTerm);
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
                            } else {
                                eventRecorder.add(EventRecorder.Event.VOTE, String.format("refuse vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
                                voteAction.fail();
                            }
                        } else {
                            eventRecorder.add(EventRecorder.Event.VOTE, String.format("refuse vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
                            voteAction.fail();
                        }

                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error(t.getMessage(), t);
                        eventRecorder.add(EventRecorder.Event.VOTE, String.format("refuse vote from [%d]", peerRaftNode.getNodeId().getPeerId()));
                        voteAction.fail();
                    }
                }, RpcExecutors.commonExecutor());
            } catch (Exception e) {
                voteAction.fail();
                log.error(e.getMessage(), e);
            }
        });
        voteAction.await();
        if (voteAction.votedSuccess()) {
            eventRecorder.add(EventRecorder.Event.VOTE, String.format("vote success, term =  [%d]", voteTerm));
            log.debug("try to be leader, term={}", voteTerm);
            eventRecorder.add(EventRecorder.Event.VOTE, String.format("trying be leader, term =  [%d]", voteTerm));
            boolean beLeaderSuccess = nodeScheduler.beLeader(voteTerm);
            synchronized (outerService) {
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
            }
        } else {
            eventRecorder.add(EventRecorder.Event.VOTE, String.format("vote fail, term =  [%d]", voteTerm));
            log.debug("voted for leader fail, timeout count {} ", votedCount.get());
        }
    }

    @Override
    public void requestConnect(ConnectRequest connectRequest) {
        NodeId requestNodeId = connectRequest.getNodeId();
        log.info("receive connectRequest, nodeId=[{}]", connectRequest.getNodeId().getPeerId());
        this.peerRaftNodes.stream()
                .filter(peerRaftNode -> peerRaftNode.getNodeId().equals(requestNodeId))
                .findAny()
                .ifPresent(peerRaftNode -> peerRaftNode.connect(this.nodeId));
    }

    @Override
    public VoteResponse requestPreVote(VoteRequest voteRequest) {
        log.info("receive preVote, voteRequest={}", JSON.toJSONString(voteRequest));
        Long term = currentTerm;
        if (term >= voteRequest.getTerm()) {
            VoteResponse voteResponse = new VoteResponse(term, false);
            log.info("response preVote, voteResponse={}", voteResponse);
        }
        if (logService.isNewerThanSelf(voteRequest.getLastLogTerm(), voteRequest.getLastLogIndex())) {
            VoteResponse voteResponse = new VoteResponse(term, true);
            log.info("response preVote, voteResponse={}", voteResponse);
            return voteResponse;
        } else {
            return new VoteResponse(term, false);
        }
    }

    @Override
    public VoteResponse requestVote(VoteRequest voteRequest) {
        Long term = currentTerm;
        if (term >= voteRequest.getTerm()) {
            return new VoteResponse(term, false);
        }
        if (logService.isNewerThanSelf(voteRequest.getLastLogTerm(), voteRequest.getLastLogIndex())) {
            if (nodeScheduler.compareAndSetTerm(term, voteRequest.getTerm())) {
                nodeScheduler.setVotedForForce(voteRequest.getCandidateId());
                nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
                nodeScheduler.receiveHeartbeat();
                tickManager.tickElectionTimeOut();
                return new VoteResponse(voteRequest.getTerm(), true);
            }
        } else {
            //看动画
            currentTerm = term;
            tickManager.tickElectionTimeOut();
        }
        return new VoteResponse(term, false);
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        Long term = currentTerm;
        eventRecorder.add(LOG, replicatedLogRequest.toString());
        //感觉应该不会出现，除非是消息延迟
        if (term > replicatedLogRequest.getTerm()) {
            eventRecorder.add(LOG, new ReplicatedLogResponse(term, false, logService.getLastCommitLogIndex()).toString());
            return new ReplicatedLogResponse(term, false, logService.getLastCommitLogIndex());
        }

        tickManager.tickElectionTimeOut();

        // normal
        if (Objects.equals(replicatedLogRequest.getLeaderId(), nodeScheduler.getVotedFor())) {
            log.info("receiveHeartbeat from={}", replicatedLogRequest.getLeaderId());
            nodeScheduler.receiveHeartbeat();
            currentTerm = replicatedLogRequest.getTerm();
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
        return new ReplicatedLogResponse(term, false, logService.getLastCommitLogIndex());
    }

    private ReplicatedLogResponse appendLog(ReplicatedLogRequest replicatedLogRequest) {
        try {
            log.info("prevLogIndex={}, LogEntry={}", replicatedLogRequest.getPrevLogIndex(), replicatedLogRequest.getEntries());
            LogEntry logEntry = logService.get(replicatedLogRequest.getPrevLogIndex());
            log.info("currentNode entry={}", JSON.toJSONString(logEntry));
            if (Objects.isNull(logEntry)) {
                return new ReplicatedLogResponse(replicatedLogRequest.getTerm(), false, logService.getLastCommitLogIndex());
            }
            boolean isSameTerm;
            isSameTerm = logEntry.getTerm().equals(replicatedLogRequest.getPrevLogTerm());
            if (isSameTerm) {
                logService.appendLog(replicatedLogRequest.getPrevLogIndex() + 1, replicatedLogRequest.getEntries());
                List<LogEntry> logEntries = replicatedLogRequest.getEntries();
                for (LogEntry needAppendLogEntry : logEntries) {
                    switch (needAppendLogEntry.getCommand().type()) {
                        case DEFAULT:
                            break;
                        case CLUSTER_CONF: {
                            ClusterConfCommand clusterConfCommand = ((ClusterConfCommand) needAppendLogEntry.getCommand());
                            applyClusterConfig(clusterConfCommand);
                        }
                    }
                }
                logService.commit(replicatedLogRequest.getLeaderCommit());
            } else {
                log.info("not isSameTerm, totalLogEntry={}", JSON.toJSONString(logService.get(0, replicatedLogRequest.getPrevLogIndex())));
            }
            /**
             * 因为可能是还在seek日志在哪儿的阶段，有的日志是需要被覆盖的，而commit之后就不允许修改了，
             * 这样可能就直接把需要被覆盖的日志commit了
             */
//            logService.commit(replicatedLogRequest.getLeaderCommit());
            log.info("/appendLog, isSameTerm={}", isSameTerm);
            return new ReplicatedLogResponse(replicatedLogRequest.getTerm(), isSameTerm, logService.getLastCommitLogIndex());
        } catch (HasCompactException e) {
            return new ReplicatedLogResponse(replicatedLogRequest.getTerm(), true, logService.getLastCommitLogIndex());
        }
    }

    @Override
    public InstallSnapshotResponse requestInstallSnapShot(InstallSnapshotRequest installSnapShotRequest) {
        Long term = currentTerm;
        if (installSnapShotRequest.getTerm() < term) {
            return new InstallSnapshotResponse(term, false);
        }
        eventRecorder.add(EventRecorder.Event.SnapShot, String.format("install snapshot, term=%d, leaderId=[%d]", installSnapShotRequest.getTerm(), installSnapShotRequest.getLeaderId().getPeerId()));
        System.out.println("installSnapshot " + JSON.toJSONString(installSnapShotRequest));
        logService.installSnapshot(installSnapShotRequest.getSnapshot(), installSnapShotRequest.getLogEntry());
        return new InstallSnapshotResponse(term, true);
    }

    private void applyClusterConfig(ClusterConfCommand command) {
        ClusterConfig oldClusterConfig = RaftNode.this.clusterConfig;
        ClusterConfig newClusterConfig = command.toClusterConfig();
        List<PeerRaftNode> peerRaftNodes = RaftNode.this.peerRaftNodes;
        List<PeerRaftNode> newPeerRaftNodes = Lists.newArrayList();
        List<PeerRaftNode> toRemoveRaftNodes = Lists.newArrayList();
        for (PeerRaftNode peerRaftNode : peerRaftNodes) {
            NodeId nodeId = peerRaftNode.getNodeId();
            //如果新配置中没有此节点，则关闭连接
            if (!newClusterConfig.containsNode(nodeId)) {
                toRemoveRaftNodes.add(peerRaftNode);
            }
        }
        //如果新配置中有这个节点，而旧配置中没有这个节点，则增加
        for (NodeConfig nodeConfig : newClusterConfig.getNodeConfigs()) {
            if (!oldClusterConfig.containsNode(nodeConfig.getNodeId())) {
                newPeerRaftNodes.add(new PeerRaftNode(nodeConfig.getNodeId(), nodeConfig.getEndPoint()));
            }
        }
        toRemoveRaftNodes.forEach(PeerRaftNode::close);
        peerRaftNodes.removeAll(toRemoveRaftNodes);
        newPeerRaftNodes.forEach(peerRaftNode -> peerRaftNode.connect(nodeId));
        peerRaftNodes.addAll(newPeerRaftNodes);
        RaftNode.this.clusterConfig = newClusterConfig;
    }


    @VisibleForTesting
    public LogService getLogService() {
        return logService;
    }

    @VisibleForTesting
    public OuterService getOuterService() {
        return outerService;
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
        stopped = true;
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

        public boolean isFollower() {
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
        public NodeId getVotedFor() {
            return votedFor.get();
        }

        PeerRaftNode getLeader() {
            NodeId votedFor = getVotedFor();
            for (PeerRaftNode peerRaftNode : peerRaftNodes) {
                if (peerRaftNode.getNodeId().equals(votedFor)) {
                    return peerRaftNode;
                }
            }
            return null;
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
            Runnable appendHeartBeatTask = () -> peerNode.forEach((peerRaftNode, peerNodeStateMachine) -> peerNodeStateMachine.append(prepareAppendLog(peerRaftNode, peerNodeStateMachine, SettableFuture.create())));
            appendHeartBeatTask.run();
            TimeCountDownUtil.addSchedulerTask(HEART_BEAT_TIME_INTERVAL, DEFAULT_TIME_UNIT, this::tickHeartBeat, (Supplier<Boolean>) () -> nodeScheduler.isLeader());
        }

        List<SettableFuture<Boolean>> appendLog(int logIndex) {
            List<SettableFuture<Boolean>> settableFutureList = Lists.newArrayList();
            peerNode.forEach((peerRaftNode, peerNodeStateMachine) -> {
                SettableFuture<Boolean> settableFuture = peerNodeStateMachine.setCompleteFuture(logIndex);
                settableFutureList.add(settableFuture);
                //TODO 为啥一定是Empty才能appendTask？当时我是咋想的？？
//                if (peerNodeStateMachine.taskQueueIsEmpty()) {
//                    peerNodeStateMachine.appendFirst(prepareAppendLog(peerRaftNode, peerNodeStateMachine, settableFuture));
//                }
                peerNodeStateMachine.appendFirst(prepareAppendLog(peerRaftNode, peerNodeStateMachine, settableFuture));
            });
            return settableFutureList;
        }


        private Runnable prepareAppendLog(PeerRaftNode peerRaftNode, PeerNodeStateMachine peerNodeStateMachine, SettableFuture<Boolean> appendLogResult) {
            /**
             * BUG
             * 在发生网络分区的时候，发送log的任务已经被添加到各个PeerNode的任务队列中准备执行了，那时候currentTerm可能已经是最新的了
             * 所以这里在appendTask的时候先把currentTerm拿出来
             */
            long term = currentTerm;
            return () -> {
                try {
                    log.info("prepareAppendLog, to {}", peerRaftNode.getNodeId().getPeerId());
                    long currentLastLogIndex = logService.getLastLogIndex();
                    long currentLastCommitLogIndex = logService.getLastCommitLogIndex();
                    long preLogIndex = peerNodeStateMachine.getNextIndex() - 1;

                    ReplicatedLogRequest replicatedLogRequest = new ReplicatedLogRequest();
                    replicatedLogRequest.setTerm(term);
                    replicatedLogRequest.setLeaderId(nodeId);
                    replicatedLogRequest.setLeaderCommit(currentLastCommitLogIndex);
                    if (logService.hasInSnapshot(preLogIndex)) {
                        if (!peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT)) {
                            Runnable runnable = prepareInstallSnapshot(peerRaftNode, peerNodeStateMachine);
                            peerNodeStateMachine.appendFirst(runnable);
                        }
                        appendLogResult.set(false);
                        return;
                    }
                    replicatedLogRequest.setPrevLogIndex(preLogIndex);
                    replicatedLogRequest.setPrevLogTerm(logService.get(preLogIndex).getTerm());
                    List<LogEntry> logEntries = logService.get(peerNodeStateMachine.getNextIndex(), currentLastLogIndex);
                    replicatedLogRequest.setEntries(logEntries);
                    //同步方法
                    log.info("send to {} replicatedLogRequest={}", JSON.toJSONString(peerRaftNode), JSON.toJSONString(replicatedLogRequest));
                    ReplicatedLogResponse replicatedLogResponse = peerRaftNode.getRaftService().requestAppendLog(replicatedLogRequest);
                    log.info("receive from {} replicatedLogResponse={}", JSON.toJSONString(peerRaftNode), JSON.toJSONString(replicatedLogResponse));
                    /*
                     * 可能发生
                     * 成为Leader后直接被网络分区了
                     * 然后又好了，此时另外一个分区已经有Leader且Term比自己大
                     */
                    if (replicatedLogResponse.getTerm() > term) {
                        log.error("currentTerm={}, remoteServerTerm={}, remoteNodeId={}", term, replicatedLogResponse.getTerm(), peerRaftNode.getNodeId());
                        log.error("may have network isolate");
                        currentTerm = replicatedLogResponse.getTerm();
                        nodeScheduler.changeNodeStatus(NodeStatus.FOLLOWER);
                        tickManager.tickElectionTimeOut();
                        appendLogResult.set(false);
                        close();
                        return;
                    }

                    if (replicatedLogResponse.getSuccess()) {
                        peerNodeStateMachine.setNextIndex(currentLastLogIndex + 1);
                        peerNodeStateMachine.setMatchIndex(currentLastLogIndex);
                        if (!peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.NORMAL)) {
                            peerNodeStateMachine.setNodeStatus(PeerNodeStatus.NORMAL);
                        }
                        if (peerNodeStateMachine.needSendAppendLogImmediately() || peerNodeStateMachine.getMatchIndex() < logService.getLastLogIndex()) {
                            Runnable runnable = prepareAppendLog(peerRaftNode, peerNodeStateMachine, SettableFuture.create());
                            peerNodeStateMachine.appendFirst(runnable);
                        }
                    } else {
                        if (!peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT)) {
//                            long nextPreLogIndex = peerNodeStateMachine.getNextIndex() - 2;
                            long nextPreLogIndex = replicatedLogResponse.getLastCommitIndex();
                            //如果已经是在Snapshot中
                            if (logService.hasInSnapshot(nextPreLogIndex)) {
                                Runnable runnable = prepareInstallSnapshot(peerRaftNode, peerNodeStateMachine);
                                peerNodeStateMachine.appendFirst(runnable);
                            } else {
                                peerNodeStateMachine.setNextIndex(nextPreLogIndex + 1);
                                Runnable runnable = prepareAppendLog(peerRaftNode, peerNodeStateMachine, SettableFuture.create());
                                peerNodeStateMachine.appendFirst(runnable);
                            }
                        }
                    }
                    appendLogResult.set(replicatedLogResponse.getSuccess());
                } catch (Exception e) {
                    appendLogResult.set(Boolean.FALSE);
                    log.error("fail appendLog to {}", JSON.toJSONString(peerRaftNode));
                    log.error("isConnectAlive={}", peerRaftNode.isConnectAlive());
                    log.error(e.getMessage(), e);
                }
            };
        }

        private Runnable prepareInstallSnapshot(PeerRaftNode peerRaftNode, PeerNodeStateMachine peerNodeStateMachine) {
            long term = currentTerm;
            return () -> {
                try {
                    log.info("prepareInstallSnapshot peerNode={}",peerRaftNode.getNodeId());
                    if (peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT) || peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.PROBE)) {
                        return;
                    }
                    peerNodeStateMachine.setNodeStatus(PeerNodeStatus.INSTALLSNAPSHOT);
                    Snapshot snapShot = logService.getSnapShot();
                    InstallSnapshotRequest installSnapShotRequest = new InstallSnapshotRequest();
                    installSnapShotRequest.setLeaderId(nodeId);
                    installSnapShotRequest.setTerm(term);
                    installSnapShotRequest.setSnapshot(snapShot);
                    installSnapShotRequest.setLogEntry(logService.get(snapShot.getLastLogIndex()));
                    InstallSnapshotResponse installSnapshotResponse = peerRaftNode.getRaftService().requestInstallSnapShot(installSnapShotRequest);
                    if (installSnapshotResponse.getSuccess()) {
                        peerNodeStateMachine.setNodeStatus(PeerNodeStatus.PROBE);
                        peerNodeStateMachine.setNextIndex(snapShot.getLastLogIndex() + 1);
                        peerNodeStateMachine.setMatchIndex(snapShot.getLastLogIndex());
                    } else {
                        throw new IllegalStateException();
                    }
                } catch (Exception e) {
                    peerNodeStateMachine.setNodeStatus(PeerNodeStatus.NORMAL);
                    log.info("prepareInstallSnapshot fail, errMsg={}", e.getMessage(), e);
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
        private ExecutorService electionExecutor;

        private Future<?> electionFuture;

        private final Object object = new Object();

        private volatile long waitTimeOut = -1;

        private volatile long electionTimeOutVersion = -1;

        private Runnable runnable = () -> {
            long currentVersion;
            long currentWaitTimeOut;
            while (true) {
                synchronized (object) {
                    currentVersion = electionTimeOutVersion;
                    currentWaitTimeOut = waitTimeOut;
                }
                if (currentWaitTimeOut == -1) {
                    continue;
                }
                if (waitTimeOut > 0) {
                    synchronized (object) {
                        try {
                            object.wait(waitTimeOut);
                        } catch (InterruptedException e) {
                            //ignore
                        }
                    }
                }
                if (stopped) {
                    return;
                }
                if (waitTimeOut == currentWaitTimeOut && currentVersion == electionTimeOutVersion) {
                    if (nodeScheduler.isLoseHeartbeat(currentWaitTimeOut) && !nodeScheduler.isLeader() && clusterConfig.getNodeCount() >= 2) {
                        preVote(currentTerm + 1);
                    }
                }
            }
        };

        void init() {
            waitTimeOut = -1;
            electionTimeOutVersion = -1;
            electionExecutor = Executors.newSingleThreadExecutor();
            electionFuture = electionExecutor.submit(runnable);
        }

        void cancelAll() {
            if (Objects.nonNull(electionFuture)) {
                electionFuture.cancel(true);
            }
            electionExecutor.shutdown();
            while (!electionExecutor.isShutdown()) {
                continue;
            }
        }

        void update(long waitTimeOut) {
            synchronized (object) {
                electionTimeOutVersion++;
                this.waitTimeOut = waitTimeOut;
                object.notifyAll();
            }
        }

        long tickElectionTimeOut() {
            if (stopped) {
                cancelAll();
                return -1;
            }
            long waitTimeOut = getRandomStartElectionTimeout();
            update(waitTimeOut);
            return waitTimeOut;
        }
    }

    /**
     * 向外界提供服务的
     */
    public class OuterService {

        public synchronized JsonObject appendLog(Command command) {
            return appendLog(command, true);
        }

        /**
         * SelfAppend 一般对于Leader而言，自己append到LogService中就算成功
         * success 是指append到大多数
         * //todo,需要做一个区分
         */
        private AddNodeScheduler addNodeScheduler;

        public synchronized JsonObject appendLog(Command command, boolean needSyncLog) {
            if (nodeScheduler.isLeader()) {

                if (command instanceof ClusterConfCommand) {
                    ClusterConfCommand clusterConfCommand = (ClusterConfCommand) command;
                    if (clusterConfCommand.isAddCommand(clusterConfig) && needSyncLog) {
                        NodeId newNodeId = clusterConfCommand.extractNewNodeId(clusterConfig);
                        EndPoint newNodeEndPoint = clusterConfCommand.extractNewNodeIdEndPoint(clusterConfig);
                        addNodeScheduler = AddNodeScheduler.create(nodeId, newNodeId, newNodeEndPoint, currentTerm, logService);
                        SettableFuture<Boolean> booleanSettableFuture = addNodeScheduler.startSyncLog();
                        Futures.addCallback(booleanSettableFuture, new FutureCallback<Boolean>() {
                            @Override
                            public void onSuccess(@Nullable Boolean aBoolean) {
                                appendLog(command, false);
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                //ignore
                            }
                        }, RpcExecutors.commonExecutor());
                        JsonObject jsonObject = new JsonObject();
                        return jsonObject;
                    }
                }

                JsonObject jsonObject = new JsonObject();
                log.info("http append {}", JSON.toJSONString(command));
                LogEntry logEntry = LogEntry.of(command, currentTerm);
                int logIndex = logService.appendLog(logEntry);
                jsonObject.put("selfAppend", true);
                VoteAction voteAction = new VoteAction(clusterConfig.getNodeCount() / 2, clusterConfig.getNodeCount() / 2 + 1);
                //TODO 刚成为Leader，这里就来了一个Append
                List<SettableFuture<Boolean>> settableFutureList = RaftNode.this.peerNodeScheduler.appendLog(logIndex);
                settableFutureList.forEach(settableFuture -> {
                    Futures.addCallback(settableFuture, new FutureCallback<Boolean>() {
                        @Override
                        public void onSuccess(@Nullable Boolean result) {
                            if (result) {
                                voteAction.success();
                            } else {
                                voteAction.fail();
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            voteAction.fail();
                        }
                    }, RpcExecutors.commonExecutor());
                });
                try {
                    voteAction.await();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                if (voteAction.votedSuccess()) {
                    if (command instanceof ClusterConfCommand) {
                        boolean isAddCommand = ((ClusterConfCommand) command).isAddCommand(clusterConfig);
                        applyClusterConfig(((ClusterConfCommand) command));
                        if (isAddCommand) {
                            peerNodeScheduler.peerNode.put(addNodeScheduler.getPeerRaftNode(), addNodeScheduler.getPeerNodeStateMachine());
                        }
                    }
                    logService.commit(logIndex);
                    log.info("commit success, index=" + logIndex + " command=" + JSON.toJSONString(command));
                    jsonObject.put("success", true);
                } else {
                    log.info("commit fail, index=" + logIndex + " command=" + JSON.toJSONString(command));
                    jsonObject.put("success", false);
                }
                jsonObject.put("index", logIndex);
                if (command instanceof ClusterConfCommand && ((ClusterConfCommand) command).needRemoveNode(nodeId)) {
                    System.out.println("close self");
                    CompletableFuture.runAsync(RaftNode.this::close);
                }
                return jsonObject;
            } else {
                log.info("i am not leader command={}", JSON.toJSONString(command));
                return new JsonObject().put("success", false);
            }
        }

        public JsonObject getNodeStatus() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("NodeStatus", currentNodeStatus.get().toString());
            jsonObject.put("term", currentTerm.toString());
            jsonObject.put("Leader", RaftNode.this.nodeScheduler.getVotedFor().getPeerId());

            jsonObject.put("peerNodes", RaftNode.this.peerRaftNodes.stream().map(peerRaftNode -> peerRaftNode.getRpcClientOptions()).collect(Collectors.toList()));

            List<String> currentUrls = Lists.newArrayList();
            EndPoint endPoint = EndPoint.create(RaftNode.this.getEndPoint().getHost(), RaftNode.this.getEndPoint().getPort() + 2);
            currentUrls.add(endPoint.toUrl() + "/status");
            currentUrls.add(endPoint.toUrl() + "/data");
            currentUrls.add(endPoint.toUrl() + "/command");
            currentUrls.add(endPoint.toUrl() + "/snapshot");
            for (EventRecorder.Event event : EventRecorder.Event.values()) {
                currentUrls.add(endPoint.toUrl() + "/log/" + event.getUrlPath());
            }
            jsonObject.put("urls", currentUrls);
            jsonObject.put("keySize", logService.getLastCommitLogIndex());
            Map<NodeId, List<String>> nodeIdUrlMap = Maps.newHashMap();
            RaftNode.this.peerRaftNodes.forEach(peerRaftNode -> {
                EndPoint peerNodeHttpEndPoint = EndPoint.create(peerRaftNode.getEndPoint().getHost(), peerRaftNode.getEndPoint().getPort() + 2);
                List<String> urls = Lists.newArrayList();
                urls.add(peerNodeHttpEndPoint.toUrl() + "/status");
                urls.add(peerNodeHttpEndPoint.toUrl() + "/data");
                urls.add(peerNodeHttpEndPoint.toUrl() + "/command");
                urls.add(peerNodeHttpEndPoint.toUrl() + "/snapshot");
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

        public JsonObject getSnapShot() {
            Snapshot snapShot = logService.getSnapShot();
            DefaultStateMachine defaultStateMachine = new DefaultStateMachine();
            defaultStateMachine.fromSnapShot(snapShot.getData());
            return new JsonObject(defaultStateMachine.getMap());
        }

        public byte[] getKey(String key) {
            return logService.getStateMachine().getValue(key);
        }

        public JsonObject getEventLog(EventRecorder.Event event) {
            return new JsonObject(eventRecorder.eventRecorders(event));
        }

        public void restartNode() {
            RaftNode.this.close();
            RaftNode.this.init();
        }
    }
}
