package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.node.NodeId;

public interface Mock6824Config {

    void begin(String description);

    void end();

    /**
     * server端每接收一次请求算一次Rpc
     */
    int rpcTotal();

    void disconnect(NodeId nodeId);

    void connect(NodeId nodeId);

    NodeId checkOneLeader();

    NodeId nextNode(NodeId nodeId);

    /**
     * check that everyone agrees on the term.
     */
    int checkTerms();

    void checkNoLeader();


}
