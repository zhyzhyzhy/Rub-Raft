package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcClient;
import cc.lovezhy.raft.rpc.RpcClientOptions;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.server.service.RaftService;
import com.google.common.base.Preconditions;

public class PeerRaftNode {

    private NodeId nodeId;

    private EndPoint endPoint;

    private Long nextIndex;

    private Long matchIndex;

    private RaftService raftService;

    private PeerNodeStatus nodeStatus;

    public PeerRaftNode(NodeId nodeId, EndPoint endPoint) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        this.nodeId = nodeId;
        this.endPoint = endPoint;
        nodeStatus = PeerNodeStatus.NORMAL;
        RpcClientOptions rpcClientOptions = new RpcClientOptions();
        rpcClientOptions.defineMethodRequestType("requestPreVote", RpcRequestType.ASYNC);
        rpcClientOptions.defineMethodRequestType("requestVote", RpcRequestType.ASYNC);
        rpcClientOptions.defineMethodRequestType("requestAppendLog", RpcRequestType.ASYNC);
        rpcClientOptions.defineMethodRequestType("requestInstallSnapShot", RpcRequestType.ASYNC);
        this.raftService = RpcClient.create(RaftService.class, endPoint, rpcClientOptions);
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public EndPoint getEndPoint() {
        return endPoint;
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
    public PeerNodeStatus getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(PeerNodeStatus nodeStatus) {
        this.nodeStatus = nodeStatus;
    }
}
