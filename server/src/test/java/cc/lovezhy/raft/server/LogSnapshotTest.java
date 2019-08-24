package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.server.log.ClusterConfCommand;
import cc.lovezhy.raft.server.log.LogServiceImpl;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.utils.HttpUtils;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.server.mock6824.Utils.*;

public class LogSnapshotTest {

    private ClusterManager clusterManager;

    @After
    public void clear() {
        clusterManager.end();
    }

    @Test
    public void testSnapshotWhenReconnect() {
        int servers = 3;
        clusterManager = ClusterManager.newCluster(servers, false);

        clusterManager.one(defineNumberCommand(Integer.MAX_VALUE), servers, true);
        NodeId leaderNodeId = clusterManager.checkOneLeader();
        NodeId nodeId = clusterManager.nextNode(leaderNodeId);
        clusterManager.disconnect(nodeId);
        for (int i = 0; i < LogServiceImpl.MAX_LOG_BEFORE_TAKE_SNAPSHOT * 2; i++) {
            clusterManager.one(defineNumberCommand(i), servers - 1, true);
        }
        clusterManager.connect(nodeId);
        pause(TimeUnit.SECONDS.toMillis(5));
        clusterManager.one(randomCommand(), servers, true);

    }

    @Test
    public void testSnapshotWhenAddNewNode() {
        int servers = 3;
        clusterManager = ClusterManager.newCluster(servers, false);
        clusterManager.one(defineNumberCommand(Integer.MAX_VALUE), servers, true);

        ClusterConfCommand clusterConfCommand = clusterManager.newAddNodeCommand();
        EndPoint leaderRpcEndPoint = clusterManager.fetchLeaderEndPoint();
        HttpUtils.postConfCommand(EndPoint.create(leaderRpcEndPoint.getHost(), leaderRpcEndPoint.getPort() + 2), clusterConfCommand);

        pause(TimeUnit.SECONDS.toMillis(5));
        clusterManager.one(randomCommand(), servers + 1, true);

    }
}
