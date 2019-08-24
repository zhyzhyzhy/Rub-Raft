package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.server.log.ClusterConfCommand;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.utils.HttpUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.server.mock6824.Utils.*;

public class ClusterConfChangeTest {

    private ClusterChangeConfig clusterChangeConfig;

    @Test
    public void testAddNode() {
        int servers = 3;
        clusterChangeConfig = ClusterManager.newCluster(servers, false);
        for (int i = 0; i < 10; i++) {
            clusterChangeConfig.one(defineNumberCommand(i), servers, true);
        }
        ClusterConfCommand clusterConfCommand = clusterChangeConfig.newAddNodeCommand();
        EndPoint leaderRpcEndPoint = clusterChangeConfig.fetchLeaderEndPoint();
        HttpUtils.postConfCommand(EndPoint.create(leaderRpcEndPoint.getHost(), leaderRpcEndPoint.getPort() + 2), clusterConfCommand);

        pause(TimeUnit.SECONDS.toMillis(5));
        clusterChangeConfig.one(defineNumberCommand(11), servers + 1, true);
        clusterChangeConfig.checkClusterConfig();
    }

    @Test
    public void testAddNodeElection() {
        int servers = 3;
        clusterChangeConfig = ClusterManager.newCluster(servers, false);
        clusterChangeConfig.one(defineNumberCommand(1), servers, true);
        clusterChangeConfig.checkClusterConfig();

        ClusterConfCommand clusterConfCommand = clusterChangeConfig.newAddNodeCommand();
        EndPoint leaderRpcEndPoint = clusterChangeConfig.fetchLeaderEndPoint();
        HttpUtils.postConfCommand(EndPoint.create(leaderRpcEndPoint.getHost(), leaderRpcEndPoint.getPort() + 2), clusterConfCommand);

        clusterChangeConfig.one(randomCommand(), servers + 1, true);

        NodeId nodeId = clusterChangeConfig.checkOneLeader();
        clusterChangeConfig.disconnect(nodeId);

        pause(TimeUnit.SECONDS.toMillis(2));
        NodeId nodeId1 = clusterChangeConfig.checkOneLeader();
        Assert.assertNotEquals(nodeId, nodeId1);

        clusterChangeConfig.one(randomCommand(), servers, true);
    }

    @Test
    public void testRemoveNode() {
        int servers = 3;
        clusterChangeConfig = ClusterManager.newCluster(servers, false);
        clusterChangeConfig.one(defineNumberCommand(1), servers, true);

        clusterChangeConfig.checkClusterConfig();

        NodeId nodeId = clusterChangeConfig.checkOneLeader();
        ClusterConfCommand clusterConfCommand = clusterChangeConfig.newDelNodeCommand(NodeId.create(ThreadLocalRandom.current().nextInt(0, servers)));
        EndPoint leaderRpcEndPoint = clusterChangeConfig.fetchLeaderEndPoint();
        HttpUtils.postConfCommand(EndPoint.create(leaderRpcEndPoint.getHost(), leaderRpcEndPoint.getPort() + 2), clusterConfCommand);

        pause(TimeUnit.SECONDS.toMillis(2));
        clusterChangeConfig.one(defineNumberCommand(2), servers - 1, true);
        clusterChangeConfig.one(defineNumberCommand(3), servers - 1, true);
    }

    @After
    public void end() {
        clusterChangeConfig.end();
    }

}
