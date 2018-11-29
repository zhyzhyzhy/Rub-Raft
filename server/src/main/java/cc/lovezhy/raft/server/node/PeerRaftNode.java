package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcClient;
import cc.lovezhy.raft.rpc.RpcClientOptions;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.server.service.RaftService;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

public class PeerRaftNode implements Closeable {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private NodeId nodeId;

    private EndPoint endPoint;

    private Long nextIndex;

    private Long matchIndex;

    private RaftService raftService;

    private PeerNodeStatus nodeStatus;

    private RpcClient<RaftService> rpcClient;

    private RpcClientOptions rpcClientOptions;

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
        this.rpcClientOptions = rpcClientOptions;
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

    public PeerNodeStatus getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(PeerNodeStatus nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    @Override
    public void close() {
        if (Objects.nonNull(rpcClient)) {
            rpcClient.close();
            log.info("close rpcClient, nodeId={}", nodeId.getPeerId());
        }
    }

    public void connect() {
        if (Objects.isNull(rpcClient) || (Objects.nonNull(rpcClient.isConnectAlive()) && !rpcClient.isConnectAlive())) {
            this.rpcClient = RpcClient.create(RaftService.class, endPoint, rpcClientOptions);
            this.raftService = rpcClient.getInstance();
        } else {
            this.rpcClient.connect();
        }
    }
}
