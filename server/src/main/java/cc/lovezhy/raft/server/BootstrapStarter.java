package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.node.PeerRaftNode;
import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static cc.lovezhy.raft.server.PropertyConstants.*;

public class BootstrapStarter {

    private static final Logger log = LoggerFactory.getLogger(BootstrapStarter.class);

    private static final List<PeerRaftNode> peerRaftNodes = Lists.newArrayList();

    private static RaftNode localRaftNode;

    private static ClusterConfig clusterConfig;

    static {
        InputStream serverPropertiesStream = BootstrapStarter.class.getResourceAsStream("server.properties");
        Properties properties = new Properties();
        try {
            properties.load(serverPropertiesStream);

            loadPeerRaftNode(properties.getProperty(PEER_SERVERS_KEY));

            clusterConfig = new ClusterConfig();
            clusterConfig.setNodeCount(Integer.parseInt(properties.getProperty(CLUSTER_NODES)));

            loadLocalRaftNode(properties.getProperty(LOCAL_SERVER_KEY));

            check();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void loadPeerRaftNode(String value) {
        Preconditions.checkNotNull(value);
        String[] peerItems = value.split(",");
        Arrays.stream(peerItems)
                .forEach(peerItem -> {
                    String[] peerConfig = peerItem.split(":");
                    Preconditions.checkState(peerConfig.length == 3);
                    EndPoint endPoint = EndPoint.create(peerConfig[0], peerConfig[1]);
                    NodeId nodeId = NodeId.create(Integer.parseInt(peerConfig[2]));
                    PeerRaftNode peerRaftNode = new PeerRaftNode(nodeId, endPoint);
                    peerRaftNodes.add(peerRaftNode);
                });
    }

    private static void loadLocalRaftNode(String value) {
        Preconditions.checkNotNull(value);
        String[] localConfig = value.split(":");
        Preconditions.checkState(localConfig.length == 3);
        EndPoint endPoint = EndPoint.create(localConfig[0], localConfig[1]);
        NodeId nodeId = NodeId.create(Integer.parseInt(localConfig[2]));
        RaftNode raftNode = new RaftNode(nodeId, clusterConfig, peerRaftNodes);
    }

    private static void check() {
        Preconditions.checkState(clusterConfig.getNodeCount() == (peerRaftNodes.size() + 1));
    }

    public void start() {

    }
}
