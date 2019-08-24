package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.server.ClusterConfig;
import cc.lovezhy.raft.server.node.NodeConfig;
import cc.lovezhy.raft.server.node.NodeId;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import jdk.nashorn.internal.ir.annotations.Immutable;

import java.util.List;

@Immutable
public class ClusterConfCommand implements Command {


    public static ClusterConfCommand create(List<NodeConfig> clusterNodeConfig) {
        ClusterConfCommand clusterConfCommand = new ClusterConfCommand();
        clusterConfCommand.clusterNodeConfig = ImmutableList.copyOf(clusterNodeConfig);
        return clusterConfCommand;
    }

    private List<NodeConfig> clusterNodeConfig;


    private ClusterConfCommand() {
    }

    public List<NodeConfig> getClusterNodeConfig() {
        return ImmutableList.copyOf(clusterNodeConfig);
    }

    public boolean isAddCommand(ClusterConfig currentConfig) {
        for (NodeConfig nodeConfig : clusterNodeConfig) {
            if (!currentConfig.getNodeConfigs().contains(nodeConfig)) {
                return true;
            }
        }
        return false;
    }

    private NodeConfig extractNewNodeConfig(ClusterConfig clusterConfig) {
        NodeConfig newNodeId = null;
        for (NodeConfig nodeConfig : clusterNodeConfig) {
            if (!clusterConfig.getNodeConfigs().contains(nodeConfig)) {
                newNodeId = nodeConfig;
                break;
            }
        }
        return newNodeId;
    }

    public NodeId extractNewNodeId(ClusterConfig clusterConfig) {
        NodeConfig nodeConfig = extractNewNodeConfig(clusterConfig);
        Preconditions.checkNotNull(nodeConfig);
        return nodeConfig.getNodeId();
    }

    public EndPoint extractNewNodeIdEndPoint(ClusterConfig clusterConfig) {
        NodeConfig nodeConfig = extractNewNodeConfig(clusterConfig);
        Preconditions.checkNotNull(nodeConfig);
        return nodeConfig.getEndPoint();
    }

    public boolean needRemoveNode(NodeId currentNodeId) {
        for (NodeConfig nodeConfig : clusterNodeConfig) {
            if (nodeConfig.getNodeId().equals(currentNodeId)) {
                return false;
            }
        }
        return true;
    }

    public ClusterConfig toClusterConfig() {
        return ClusterConfig.create(clusterNodeConfig);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("clusterNodeConfig", clusterNodeConfig)
                .toString();
    }
}
