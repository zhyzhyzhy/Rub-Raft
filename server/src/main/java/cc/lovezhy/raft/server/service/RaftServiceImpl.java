package cc.lovezhy.raft.server.service;

import cc.lovezhy.raft.server.service.model.*;
import com.google.common.base.Preconditions;

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

    @Override
    public InstallSnapshotResponse requestInstallSnapShot(InstallSnapshotRequest installSnapshotRequest) {
        return raftService.requestInstallSnapShot(installSnapshotRequest);
    }
}
