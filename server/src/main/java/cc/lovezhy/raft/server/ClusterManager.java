package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class ClusterManager implements Closeable {

    public static ClusterManager newCluster(int servers) {
        ClusterManager clusterManager = new ClusterManager();
        clusterManager.makeCluster(servers);
        return clusterManager;
    }

    private List<RaftNode> raftNodes;

    private ClusterManager() {
    }

    private void makeCluster(int servers) {
        Preconditions.checkArgument(servers > 0);
        List<RaftNode> raftNodes = Lists.newArrayList();
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", String.valueOf(servers));

        AtomicInteger nodeRpcPortAllocator = new AtomicInteger(5283);

        List<String> nodeList = Lists.newArrayList();
        for (int i = 0; i < servers; i++) {
            nodeList.add("localhost:" + nodeRpcPortAllocator.getAndAdd(2) + ":" + i);
        }
        nodeList.forEach(currentNode -> {
            properties.setProperty("local", currentNode);
            List<String> peerNode = Lists.newLinkedList(nodeList);
            peerNode.remove(currentNode);
            properties.setProperty("peer", Joiner.on(',').join(peerNode));
            raftNodes.add(new RaftStarter().start(properties));
        });
        this.raftNodes = raftNodes;
    }

    @Override
    public void close() throws IOException {
        if (Objects.nonNull(raftNodes)) {
            raftNodes.forEach(RaftNode::close);
        }
    }
}
