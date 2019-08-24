package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class NodeConfig {
    private NodeId nodeId;
    private EndPoint endPoint;

    public static NodeConfig create(NodeId nodeId, EndPoint endPoint) {
        NodeConfig config = new NodeConfig();
        config.nodeId = nodeId;
        config.endPoint = endPoint;
        return config;
    }

    private NodeConfig() {}


    public NodeId getNodeId() {
        return nodeId;
    }

    public EndPoint getEndPoint() {
        return endPoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeConfig config = (NodeConfig) o;
        return Objects.equal(nodeId, config.nodeId) &&
                Objects.equal(endPoint, config.endPoint);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(nodeId, endPoint);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("nodeId", nodeId)
                .add("endPoint", endPoint)
                .toString();
    }
}
