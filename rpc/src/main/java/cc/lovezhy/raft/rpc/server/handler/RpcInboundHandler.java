package cc.lovezhy.raft.rpc.server.handler;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.server.netty.RpcService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcInboundHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(RpcInboundHandler.class);

    private RpcService rpcService;

    public RpcInboundHandler(RpcService rpcService) {
        this.rpcService = rpcService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof RpcResponse) {
            rpcService.onResponse((RpcResponse) msg);
        } else if (msg instanceof RpcRequest) {
            rpcService.handleRequest(ctx.channel(), (RpcRequest) msg);
        } else {
            // TODO
            // ignore
        }
    }
}
