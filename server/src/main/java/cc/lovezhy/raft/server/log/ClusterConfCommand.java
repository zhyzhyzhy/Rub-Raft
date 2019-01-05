package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.server.node.NodeId;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import jdk.nashorn.internal.ir.annotations.Immutable;

@Immutable
public class ClusterConfCommand implements Command {

    private ClusterConfCommandTypeEnum commandType;
    private NodeId nodeId;
    private EndPoint endPoint;

    public static ClusterConfCommand addNode(NodeId nodeId, EndPoint endPoint) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        return new ClusterConfCommand(ClusterConfCommandTypeEnum.ADD, nodeId, endPoint);
    }

    public static ClusterConfCommand removeNode(NodeId nodeId, EndPoint endPoint) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        return new ClusterConfCommand(ClusterConfCommandTypeEnum.REMOVE, nodeId, endPoint);
    }

    private ClusterConfCommand() {
    }

    private ClusterConfCommand(ClusterConfCommandTypeEnum commandType, NodeId nodeId, EndPoint endPoint) {
        this.commandType = commandType;
        this.nodeId = nodeId;
        this.endPoint = endPoint;
    }

    public ClusterConfCommandTypeEnum getCommandType() {
        return commandType;
    }

    public void setCommandType(ClusterConfCommandTypeEnum commandType) {
        this.commandType = commandType;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public void setNodeId(NodeId nodeId) {
        this.nodeId = nodeId;
    }

    public EndPoint getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(EndPoint endPoint) {
        this.endPoint = endPoint;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("commandType", commandType)
                .add("nodeId", nodeId)
                .add("endPoint", endPoint)
                .toString();
    }
}
