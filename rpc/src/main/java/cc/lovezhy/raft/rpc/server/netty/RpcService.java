package cc.lovezhy.raft.rpc.server.netty;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import io.netty.channel.Channel;

public interface RpcService {
    void onResponse(RpcResponse response);
    void handleRequest(Channel channel, RpcRequest request);
}
