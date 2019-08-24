package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.node.NodeConfig;
import cc.lovezhy.raft.server.node.NodeId;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import jdk.nashorn.internal.ir.annotations.Immutable;

import java.util.Comparator;
import java.util.List;

@Immutable
public class ClusterConfig {

    public static ClusterConfig create(List<NodeConfig> nodeConfigs) {
        ClusterConfig clusterConfig = new ClusterConfig();
        List<NodeConfig> configCopy = Lists.newArrayList(nodeConfigs);
        configCopy.sort(Comparator.comparingInt(o -> o.getNodeId().getPeerId()));
        clusterConfig.nodeConfigs = ImmutableList.copyOf(configCopy);
        return clusterConfig;
    }


    private List<NodeConfig> nodeConfigs;

    public List<NodeConfig> getNodeConfigs() {
        return nodeConfigs;
    }

    public int getNodeCount() {
        return nodeConfigs.size();
    }

    public boolean containsNode(NodeId nodeId) {
        for (NodeConfig nodeConfig : nodeConfigs) {
            if (nodeConfig.getNodeId().equals(nodeId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterConfig that = (ClusterConfig) o;
        return Objects.equal(nodeConfigs, that.nodeConfigs);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(nodeConfigs);
    }
}
