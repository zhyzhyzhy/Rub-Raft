package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcClient;
import cc.lovezhy.raft.rpc.RpcClientOptions;
import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.server.service.RaftService;
import cc.lovezhy.raft.server.service.model.ConnectRequest;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SettableFuture;
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
        rpcClientOptions.defineMethodRequestType("requestConnect", RpcRequestType.ONE_WAY);
        this.rpcClientOptions = rpcClientOptions;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public EndPoint getEndPoint() {
        return endPoint;
    }

    public void connect(NodeId clientNodeId) {
        if (Objects.isNull(rpcClient) || !rpcClient.isConnectAlive()) {
            this.rpcClient = RpcClient.create(RaftService.class, endPoint, rpcClientOptions);
            this.raftService = rpcClient.getInstance();
            SettableFuture<Void> settableFuture = SettableFuture.create();
            this.rpcClient.connect(settableFuture);
            settableFuture.addListener(() -> this.raftService.requestConnect(ConnectRequest.of(clientNodeId)), RpcExecutors.commonExecutor());
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
