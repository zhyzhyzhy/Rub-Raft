package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;

public interface ConsumerService {

    RpcResponse sendRequest(RpcRequest request) throws RequestTimeoutException;

    void sendRequestAsync(RpcRequest request);

    void sendOneWayRequest(RpcRequest request);
}
