package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import com.google.common.util.concurrent.FutureCallback;

import java.util.concurrent.TimeoutException;

public interface ConsumerRpcService {

    RpcResponse sendRequest(RpcRequest request) throws RequestTimeoutException;

    void sendRequestAsync(RpcRequest request, FutureCallback futureCallback);

    void sendOneWayRequest(RpcRequest request);
}
