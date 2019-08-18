package cc.lovezhy.raft.rpc.server.handler;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.server.netty.RpcService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.CompletableFuture;

public class RpcInboundHandler extends SimpleChannelInboundHandler<Object> {

    private RpcService rpcService;

    public RpcInboundHandler(RpcService rpcService) {
        this.rpcService = rpcService;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof RpcResponse) {
            CompletableFuture.runAsync(() -> rpcService.onResponse((RpcResponse) msg));
        } else if (msg instanceof RpcRequest) {
            rpcService.handleRequest(ctx.channel(), (RpcRequest) msg);
        } else {
            // TODO
            // ignore
            throw new IllegalStateException("unknown channel message, msg=" + msg);
        }
    }
}
