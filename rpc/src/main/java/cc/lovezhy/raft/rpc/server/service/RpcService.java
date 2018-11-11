package cc.lovezhy.raft.rpc.server.service;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;


public interface RpcService {
    void handleRequest(RpcRequest request);

    RpcResponse sendRequest(RpcRequest request);

    void sendRequestAsync(RpcRequest request, FutureCallback futureCallback);

    void sendOneWayRequest(RpcRequest request);

    void onResponse(RpcResponse response);

    void setChannel(Channel channel);
}
