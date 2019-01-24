package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.node.RaftNode;

public interface Mock6824Config {

    RaftNode checkOneLeader();

    int checkTerms();

    void checkNoLeader();

    void end();
}
