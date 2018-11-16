package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcClient;
import cc.lovezhy.raft.server.service.RaftService;
import com.google.common.base.Preconditions;

public class PeerRaftNode {

    private NodeId nodeId;

    private EndPoint endPoint;

    private Long nextIndex;

    private Long matchIndex;

    private RaftService raftService;

    public PeerRaftNode(NodeId nodeId, EndPoint endPoint) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        this.nodeId = nodeId;
        this.endPoint = endPoint;
        this.raftService = RpcClient.create(RaftService.class, endPoint);
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

    public Long getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(Long nextIndex) {
        this.nextIndex = nextIndex;
    }

    public Long getMatchIndex() {
        return matchIndex;
    }

    public void setMatchIndex(Long matchIndex) {
        this.matchIndex = matchIndex;
    }

    public RaftService getRaftService() {
        return raftService;
    }

    public void setRaftService(RaftService raftService) {
        this.raftService = raftService;
    }
}
