package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.server.ClusterConfig;
import cc.lovezhy.raft.server.service.RaftService;
import cc.lovezhy.raft.server.service.RaftServiceImpl;
import cc.lovezhy.raft.server.service.model.VoteRequest;
import cc.lovezhy.raft.server.service.model.VoteResponse;
import cc.lovezhy.raft.server.storage.StorageService;
import cc.lovezhy.raft.server.utils.TimeCountDownUtil;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static cc.lovezhy.raft.server.RaftConstants.DEFAULT_TIME_UNIT;
import static cc.lovezhy.raft.server.RaftConstants.START_ELECTION_TIMEOUT;

public class RaftNode {

    private static final Logger log = LoggerFactory.getLogger(RaftNode.class);

    private NodeId nodeId;

    private volatile NodeStatus status;

    private ClusterConfig clusterConfig;

    private volatile AtomicLong currentTerm;

    private volatile NodeId votedFor;

    private StorageService storageService;

    private List<PeerRaftNode> peerRaftNodes;

    private AtomicBoolean heartbeat = new AtomicBoolean(false);

    private RaftService serverService;

    public RaftNode(NodeId nodeId, ClusterConfig clusterConfig, List<PeerRaftNode> peerRaftNodes) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(clusterConfig);
        Preconditions.checkNotNull(peerRaftNodes);
        this.nodeId = nodeId;
        this.peerRaftNodes = peerRaftNodes;
        log.info("peerRaftNodes={}", JSON.toJSONString(peerRaftNodes));
        serverService = new RaftServiceImpl(this);
    }

    public void init() {
        status = NodeStatus.FOLLOWER;
        currentTerm = new AtomicLong();

        boolean currentHeatBeat = heartbeat.get();
        TimeCountDownUtil.addSchedulerTask(
                START_ELECTION_TIMEOUT,
                DEFAULT_TIME_UNIT,
                this::prevote,
                () -> currentHeatBeat == heartbeat.get());
    }

    public void prevote() {
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

    public Long getCurrentTerm() {
        return currentTerm.get();
    }

    public NodeId getVotedFor() {
        return votedFor;
    }

    public void changeState(NodeStatus nodeStatus) {
        Preconditions.checkNotNull(nodeStatus);
        this.status = nodeStatus;
    }

    public void appendLogs() {

    }
}
