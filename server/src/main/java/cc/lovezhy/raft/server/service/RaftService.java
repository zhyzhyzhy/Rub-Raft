package cc.lovezhy.raft.server.service;

import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.service.model.ReplicatedLogResponse;
import cc.lovezhy.raft.server.service.model.VoteRequest;
import cc.lovezhy.raft.server.service.model.VoteResponse;

public interface RaftService {

    VoteResponse requestPreVote(VoteRequest voteRequest);

    VoteResponse requestVote(VoteRequest voteRequest);

    ReplicatedLogResponse requestAppendLog(ReplicatedLogRequest replicatedLogRequest);
}
