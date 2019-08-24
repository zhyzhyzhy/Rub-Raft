package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.server.log.ClusterConfCommand;
import cc.lovezhy.raft.server.log.Command;
import cc.lovezhy.raft.server.node.NodeId;

public interface ClusterChangeConfig {
    ClusterConfCommand newAddNodeCommand();
    ClusterConfCommand newDelNodeCommand(NodeId nodeId);
    EndPoint fetchLeaderEndPoint();
    int one(Command command, int expectedServers, boolean retry);
    void checkClusterConfig();
    void disconnect(NodeId nodeId);
    NodeId checkOneLeader();
    void end();

}
