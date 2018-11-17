package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcProvider;
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
import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static cc.lovezhy.raft.server.RaftConstants.*;

public class RaftNode implements RaftService {

    private static final Logger log = LoggerFactory.getLogger(RaftNode.class);

    private NodeId nodeId;

    private volatile NodeStatus status;

    private ClusterConfig clusterConfig;

    private volatile AtomicLong currentTerm;

    private volatile NodeId votedFor;

    private StorageService storageService;

    private List<PeerRaftNode> peerRaftNodes;

    private AtomicLong heartbeat = new AtomicLong();

    private RaftService serverService;

    private RpcServer rpcServer;

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

        serverService = new RaftServiceImpl(this);

        rpcServer = new RpcServer();
        rpcServer.registerService(RaftServiceImpl.class);
        rpcServer.start(endPoint);
    }

    public void init() {
        status = NodeStatus.FOLLOWER;
        currentTerm = new AtomicLong();
        startElectionTimeOut();

    }

    // TODO 应该不会栈溢出
    private void startElectionTimeOut() {
        Long currentHeatBeat = heartbeat.get();
        TimeCountDownUtil.addSchedulerTask(
                getRandomStartElectionTimeout(),
                DEFAULT_TIME_UNIT,
                this::prevote,
                () -> currentHeatBeat == heartbeat.get(),
                this::startElectionTimeOut);
    }

    private void prevote() {
        status = NodeStatus.CANDIDATE;
        currentTerm.incrementAndGet();

        int[] votedCount = new int[1];
        peerRaftNodes.forEach(peerRaftNode -> {
            VoteRequest voteRequest = new VoteRequest();
            voteRequest.setTerm(currentTerm.get());
            voteRequest.setCandidateId(nodeId);
            voteRequest.setLastLogIndex(storageService.getCommitIndex());
            VoteResponse voteResponse = peerRaftNode.getRaftService().requestVode(voteRequest);
            if (voteResponse.getVoteGranted()) {
                votedCount[0]++;
            }
        });
        if (votedCount[0] > clusterConfig.getNodeCount() / 2) {
            this.status = NodeStatus.LEADER;
        }
    }

    private Long getCurrentTerm() {
        return currentTerm.get();
    }

    private NodeId getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(NodeId nodeId) {
        this.votedFor = nodeId;
    }

    private void changeState(NodeStatus nodeStatus) {
        Preconditions.checkNotNull(nodeStatus);
        this.status = nodeStatus;
    }

    private void appendLogs() {

    }

    private void receiveHeartBeat() {
        this.heartbeat.incrementAndGet();
    }

    @Override
    public synchronized VoteResponse requestVode(VoteRequest voteRequest) {
        if (Objects.isNull(this.getVotedFor()) || voteRequest.getTerm() > this.getCurrentTerm()) {
            this.setVotedFor(voteRequest.getCandidateId());
            this.receiveHeartBeat();
            return new VoteResponse(this.getCurrentTerm(), true);
        }
        return new VoteResponse(this.getCurrentTerm(), false);
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        if (replicatedLogRequest.getTerm() >= this.getCurrentTerm()) {
            this.changeState(NodeStatus.FOLLOWER);
            this.receiveHeartBeat();
            this.appendLogs();
            return new ReplicatedLogResponse(this.getCurrentTerm(), true);
        } else {
            return new ReplicatedLogResponse(this.getCurrentTerm(), false);
        }
    }
}
