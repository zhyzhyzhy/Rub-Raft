package cc.lovezhy.raft.server.service;

import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.service.model.ReplicatedLogResponse;
import cc.lovezhy.raft.server.service.model.VoteRequest;
import cc.lovezhy.raft.server.service.model.VoteResponse;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SettableFuture;

public class RaftServiceImpl implements RaftService {

    private RaftService raftService;

    public RaftServiceImpl(RaftService raftService) {
        Preconditions.checkNotNull(raftService);
        this.raftService = raftService;
    }

    @Override
    public VoteResponse requestPreVote(VoteRequest voteRequest) {
        return raftService.requestPreVote(voteRequest);
    }

    @Override
    public VoteResponse requestVote(VoteRequest voteRequest) {
        return raftService.requestVote(voteRequest);
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        return raftService.requestAppendLog(replicatedLogRequest);
    }
}
