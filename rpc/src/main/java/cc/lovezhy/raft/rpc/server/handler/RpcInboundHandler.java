package cc.lovezhy.raft.rpc.server.handler;

import cc.lovezhy.raft.rpc.RpcRequest;
import cc.lovezhy.raft.rpc.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.UUID;

public class RpcInboundHandler extends SimpleChannelInboundHandler<Object> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("active");
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        ctx.writeAndFlush(request);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("channel read");
        if (msg instanceof RpcRequest) {
            RpcRequest request = (RpcRequest)msg;
            System.out.println(request);
        } else if (msg instanceof RpcResponse) {
            RpcResponse response = (RpcResponse)msg;
            System.out.println(response);
        }
    }
}
