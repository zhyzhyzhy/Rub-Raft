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
import java.util.Objects;

public class PeerRaftNode implements Closeable {

    private Logger log = LoggerFactory.getLogger(PeerRaftNode.class);

    private NodeId nodeId;

    private EndPoint endPoint;

    private RaftService raftService;

    private RpcClient<RaftService> rpcClient;

    private RpcClientOptions rpcClientOptions;

    public PeerRaftNode(NodeId nodeId, EndPoint endPoint) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        this.nodeId = nodeId;
        this.endPoint = endPoint;
        RpcClientOptions rpcClientOptions = new RpcClientOptions();
        rpcClientOptions.defineMethodRequestType("requestPreVote", RpcRequestType.ASYNC);
        rpcClientOptions.defineMethodRequestType("requestVote", RpcRequestType.ASYNC);
        //appendLog和installSnapShot改为同步
//        rpcClientOptions.defineMethodRequestType("requestAppendLog", RpcRequestType.ASYNC);
//        rpcClientOptions.defineMethodRequestType("requestInstallSnapShot", RpcRequestType.ASYNC);
        this.rpcClientOptions = rpcClientOptions;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public EndPoint getEndPoint() {
        return endPoint;
    }

    public void connect() {
        if (Objects.isNull(rpcClient) || (Objects.nonNull(rpcClient.isConnectAlive()) && !rpcClient.isConnectAlive())) {
            this.rpcClient = RpcClient.create(RaftService.class, endPoint, rpcClientOptions);
            this.raftService = rpcClient.getInstance();
        } else {
            this.rpcClient.connect();
        }
    }

    public RaftService getRaftService() {
        return raftService;
    }

    @Override
    public void close() {
        if (Objects.nonNull(rpcClient)) {
            rpcClient.close();
            log.debug("close rpcClient, nodeId={}", nodeId.getPeerId());
        }
    }
}
