package cc.lovezhy.raft.server.service;

import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.service.model.ReplicatedLogResponse;
import cc.lovezhy.raft.server.service.model.VoteRequest;
import cc.lovezhy.raft.server.service.model.VoteResponse;
import com.google.common.base.Preconditions;

public class RaftServiceImpl implements RaftService {

    private RaftService raftService;

    public RaftServiceImpl(RaftService raftService) {
        Preconditions.checkNotNull(raftService);
        this.raftService = raftService;
    }

    @Override
    public VoteResponse requestVode(VoteRequest voteRequest) {
        return raftService.requestVode(voteRequest);
    }

    @Override
    public ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) {
        return raftService.requestAppendLog(replicatedLogRequest);
    }
}
