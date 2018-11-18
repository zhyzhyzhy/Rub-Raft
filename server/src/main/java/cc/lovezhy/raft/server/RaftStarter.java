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

public class RaftStarter {

    private static final Logger log = LoggerFactory.getLogger(RaftStarter.class);

    private final List<PeerRaftNode> peerRaftNodes = Lists.newArrayList();

    private RaftNode localRaftNode;

    private ClusterConfig clusterConfig;

    private void loadPeerRaftNode(String value) {
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

    private void loadLocalRaftNode(String value) {
        Preconditions.checkNotNull(value);
        String[] localConfig = value.split(":");
        Preconditions.checkState(localConfig.length == 3);
        EndPoint endPoint = EndPoint.create(localConfig[0], localConfig[1]);
        NodeId nodeId = NodeId.create(Integer.parseInt(localConfig[2]));
        localRaftNode = new RaftNode(nodeId, endPoint, clusterConfig, peerRaftNodes);
    }

    private void check() {
        Preconditions.checkState(clusterConfig.getNodeCount() == (peerRaftNodes.size() + 1));
    }

    public void start() {
        InputStream serverPropertiesStream = RaftStarter.class.getResourceAsStream("/server.properties");
        Properties properties = new Properties();
        try {
            properties.load(serverPropertiesStream);
            start(properties);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            log.error("load config file error!");
        }
    }

    public void start(Properties properties) {
        Preconditions.checkNotNull(properties);
        loadPeerRaftNode(properties.getProperty(PEER_SERVERS_KEY));

        clusterConfig = new ClusterConfig();
        clusterConfig.setNodeCount(Integer.parseInt(properties.getProperty(CLUSTER_NODES)));

        loadLocalRaftNode(properties.getProperty(LOCAL_SERVER_KEY));

        check();

        localRaftNode.init();
    }

}
