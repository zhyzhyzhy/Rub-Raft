package cc.lovezhy.raft.server.service;

import cc.lovezhy.raft.server.node.NodeStatus;
import cc.lovezhy.raft.server.node.RaftNode;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.service.model.ReplicatedLogResponse;
import cc.lovezhy.raft.server.service.model.VoteRequest;
import cc.lovezhy.raft.server.service.model.VoteResponse;
import com.google.common.base.Preconditions;

import java.util.Objects;

public class RaftServiceImpl implements RaftService {

    private RaftNode raftNode;

    public RaftServiceImpl(RaftNode raftNode) {
        Preconditions.checkNotNull(raftNode);
        this.raftNode = raftNode;
    }

    @Override
    public VoteResponse requestVode(VoteRequest voteRequest) {
        if (Objects.isNull(raftNode.getVotedFor()) || voteRequest.getTerm() > raftNode.getCurrentTerm()) {
            return new VoteResponse(raftNode.getCurrentTerm(), true);
        }
        return new VoteResponse(raftNode.getCurrentTerm(), false);
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        if (replicatedLogRequest.getTerm() >= raftNode.getCurrentTerm()) {
            raftNode.changeState(NodeStatus.FOLLOWER);
            raftNode.appendLogs();
            return new ReplicatedLogResponse(raftNode.getCurrentTerm(), true);
        } else {
            return new ReplicatedLogResponse(raftNode.getCurrentTerm(), false);
        }
    }
}
