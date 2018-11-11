package cc.lovezhy.raft.rpc.server.handler;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.server.service.RpcService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class RpcInboundHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(RpcInboundHandler.class);

    private RpcService rpcService;

    public RpcInboundHandler(RpcService rpcService) {
        requireNonNull(rpcService);
        this.rpcService = rpcService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof RpcRequest) {
            RpcRequest request = (RpcRequest)msg;
            rpcService.handleRequest(request);
        } else if (msg instanceof RpcResponse) {
            RpcResponse response = (RpcResponse)msg;
            rpcService.onResponse(response);
        }
    }
}
