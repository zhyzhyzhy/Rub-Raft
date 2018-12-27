package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.node.NodeId;

public class ConnectRequest {

    public static ConnectRequest of(NodeId nodeId) {
        return new ConnectRequest(nodeId);
    }

    public ConnectRequest() {
    }

    public ConnectRequest(NodeId nodeId) {
        this.nodeId = nodeId;
    }

    private NodeId nodeId;

    public NodeId getNodeId() {
        return nodeId;
    }

    public void setNodeId(NodeId nodeId) {
        this.nodeId = nodeId;
    }
}
