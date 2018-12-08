package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;

public interface ConsumerRpcService {

    RpcResponse sendRequest(RpcRequest request) throws RequestTimeoutException;

    RpcResponse sendRequestAsync(RpcRequest request);

    void sendOneWayRequest(RpcRequest request);
}
