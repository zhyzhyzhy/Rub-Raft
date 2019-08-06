package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.log.Command;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.utils.Pair;

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

    /**
     * check no leader in the cluster
     */
    void checkNoLeader();

    /**
     * how many servers think a log entry is committed?
     */
    Pair<Integer, Command> nCommitted(int index);

    /**
     * do a complete agreement.
     * it might choose the wrong leader initially,
     * and have to re-submit after giving up.
     * entirely gives up after about 10 seconds.
     * indirectly checks that the servers agree on the
     * same value, since nCommitted() checks this,
     * as do the threads that read from applyCh.
     * returns index.
     * if retry==true, may submit the command multiple
     * times, in case a leader fails just after Start().
     * if retry==false, calls Start() only once, in order
     * to simplify the early Lab 2B tests.
     * 我理解的就是commit一个Command，然后返回LogEntry的Index
     */
    int one(Command command, int expectedServers, boolean retry);

}
