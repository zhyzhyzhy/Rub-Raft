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
import com.google.common.base.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
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

    private AtomicLong heartbeatTimeRecorder;

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

        rpcServer = new RpcServer();
        RaftService serverService = new RaftServiceImpl(this);
        rpcServer.registerService(serverService);
        rpcServer.start(endPoint);
    }

    public void init() {
        this.status = NodeStatus.FOLLOWER;
        this.currentTerm = new AtomicLong();
        this.heartbeatTimeRecorder = new AtomicLong();
        startElectionTimeOut();
    }

    // TODO 应该不会栈溢出
    private void startElectionTimeOut() {
        TimeCountDownUtil.addSchedulerTaskWithListener(
                getRandomStartElectionTimeout(),
                DEFAULT_TIME_UNIT,
                this::preVote,
                this::isLoseHeartbeat,
                this::startElectionTimeOut);
    }

    //防止网络分区，term增大
    private void preVote() {
        int[] preVotedGrantedCount = new int[1];
        peerRaftNodes.forEach(peerRaftNode -> {
            VoteRequest voteRequest = new VoteRequest();
            voteRequest.setTerm(currentTerm.get() + 1);
            voteRequest.setCandidateId(nodeId);
            voteRequest.setLastLogTerm(storageService.getLastCommitLogTerm());
            voteRequest.setLastLogIndex(storageService.getCommitIndex());
            VoteResponse voteResponse = peerRaftNode.getRaftService().requestPreVote(voteRequest);
            if (voteResponse.getVoteGranted()) {
                preVotedGrantedCount[0]++;
            }
        });
        if (preVotedGrantedCount[0] > clusterConfig.getNodeCount() / 2) {
            voteForLeader();
        }
    }


    private void voteForLeader() {
        status = NodeStatus.CANDIDATE;
        currentTerm.incrementAndGet();
        int[] votedCount = new int[1];
        peerRaftNodes.forEach(peerRaftNode -> {
            VoteRequest voteRequest = new VoteRequest();
            voteRequest.setTerm(currentTerm.get());
            voteRequest.setCandidateId(nodeId);
            voteRequest.setLastLogIndex(storageService.getCommitIndex());
            VoteResponse voteResponse = peerRaftNode.getRaftService().requestVote(voteRequest);
            if (voteResponse.getVoteGranted()) {
                votedCount[0]++;
            }
        });
        if (votedCount[0] > clusterConfig.getNodeCount() / 2) {
            this.status = NodeStatus.LEADER;
            startHeartbeat();
        }
    }

    private void startHeartbeat() {
        peerRaftNodes.forEach(peerRaftNode -> {
            ReplicatedLogRequest replicatedLogRequest = new ReplicatedLogRequest();
            replicatedLogRequest.setEntries(Collections.emptyList());
            replicatedLogRequest.setLeaderCommit(storageService.getCommitIndex());
            replicatedLogRequest.setLeaderId(nodeId);
            replicatedLogRequest.setTerm(currentTerm.get());
            peerRaftNode.getRaftService().requestAppendLog(replicatedLogRequest);
        });
        TimeCountDownUtil.addSchedulerTask(HEART_BEAT_TIME_INTERVAL, DEFAULT_TIME_UNIT, this::startHeartbeat, (Supplier<Boolean>) () -> RaftNode.this.status == NodeStatus.LEADER);
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

    private void receiveHeartbeat() {
        this.heartbeatTimeRecorder.set(System.currentTimeMillis());
    }

    private boolean isLoseHeartbeat() {
        return System.currentTimeMillis() - heartbeatTimeRecorder.get() > HEART_BEAT_TIME_INTERVAL_TIMEOUT;
    }

    public void close() {
        this.rpcServer.close();
    }

    @Override
    public VoteResponse requestPreVote(VoteRequest voteRequest) {
        VoteResponse voteResponse = new VoteResponse();
        if (isLoseHeartbeat() && voteRequest.getTerm() > currentTerm.get()) {
            if (voteRequest.getLastLogTerm() > storageService.getLastCommitLogTerm()) {
                voteResponse.setVoteGranted(true);
                voteResponse.setTerm(currentTerm.get());
            } else if (voteRequest.getLastLogTerm().equals(storageService.getLastCommitLogTerm())
                    && voteRequest.getLastLogIndex() > storageService.getCommitIndex()) {
                voteResponse.setVoteGranted(true);
                voteResponse.setTerm(currentTerm.get());
            }
        } else {
            voteResponse.setTerm(currentTerm.get());
            voteResponse.setVoteGranted(false);
        }
        return voteResponse;
    }

    @Override
    public synchronized VoteResponse requestVote(VoteRequest voteRequest) {
        if (Objects.isNull(this.getVotedFor()) || voteRequest.getTerm() > this.getCurrentTerm()) {
            this.setVotedFor(voteRequest.getCandidateId());
            return new VoteResponse(this.getCurrentTerm(), true);
        }
        return new VoteResponse(this.getCurrentTerm(), false);
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        if (replicatedLogRequest.getTerm() >= this.getCurrentTerm()) {
            this.changeState(NodeStatus.FOLLOWER);
            this.receiveHeartbeat();
            this.appendLogs();
            return new ReplicatedLogResponse(this.getCurrentTerm(), true);
        } else {
            return new ReplicatedLogResponse(this.getCurrentTerm(), false);
        }
    }
}
