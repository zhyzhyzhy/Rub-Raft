package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import com.google.common.util.concurrent.FutureCallback;

public interface ConsumerRpcService {

    RpcResponse sendRequest(RpcRequest request);

    void sendRequestAsync(RpcRequest request, FutureCallback futureCallback);

    void sendOneWayRequest(RpcRequest request);


}
