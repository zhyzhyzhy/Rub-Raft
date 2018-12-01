package cc.lovezhy.raft.server.service;

import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.service.model.*;

import java.io.IOException;

public interface RaftService {

    VoteResponse requestPreVote(VoteRequest voteRequest);

    VoteResponse requestVote(VoteRequest voteRequest);

    ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest) throws IOException, HasCompactException;

    InstallSnapshotResponse requestInstallSnapShot(InstallSnapshotRequest installSnapShotRequest);
}
