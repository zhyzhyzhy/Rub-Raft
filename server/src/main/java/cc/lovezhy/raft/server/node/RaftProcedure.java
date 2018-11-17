package cc.lovezhy.raft.server.node;

public interface RaftProcedure {
    void preVote();
    void voteForLeader();
}
