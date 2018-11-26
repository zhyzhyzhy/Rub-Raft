package cc.lovezhy.raft.server.service;

import cc.lovezhy.raft.server.service.model.*;

public interface RaftService {

    VoteResponse requestPreVote(VoteRequest voteRequest);

    VoteResponse requestVote(VoteRequest voteRequest);

    ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest);

    InstallSnapShotResponse requestInstallSnapShot(InstallSnapShotRequest installSnapShotRequest);
}
