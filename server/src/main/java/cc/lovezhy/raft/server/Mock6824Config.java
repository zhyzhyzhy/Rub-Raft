package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.node.RaftNode;

public interface Mock6824Config {

    void begin(String description);

    void end();

    /**
     * server端每接收一次请求算一次Rpc
     */
    int rpcTotal();

    RaftNode checkOneLeader();

    /**
     * check that everyone agrees on the term.
     */
    int checkTerms();

    void checkNoLeader();


}
