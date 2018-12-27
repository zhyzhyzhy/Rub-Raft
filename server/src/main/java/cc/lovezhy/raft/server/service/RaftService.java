package cc.lovezhy.raft.server.service;

import cc.lovezhy.raft.server.service.model.*;

public interface RaftService {

    void requestConnect(ConnectRequest connectRequest);

    VoteResponse requestPreVote(VoteRequest voteRequest);

    VoteResponse requestVote(VoteRequest voteRequest);

    ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest);

    InstallSnapshotResponse requestInstallSnapShot(InstallSnapshotRequest installSnapShotRequest);
}
