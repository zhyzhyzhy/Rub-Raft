package cc.lovezhy.raft.rpc.server.netty;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.server.codec.KryoDecoder;
import cc.lovezhy.raft.rpc.server.codec.KryoEncoder;
import cc.lovezhy.raft.rpc.server.handler.RpcInboundHandler;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class NettyClient {

    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);
    private EndPoint endPoint;
    private Channel channel;
    private RpcService rpcService;
    private EventLoopGroup worker;

    public NettyClient(EndPoint endPoint, RpcService rpcService) {
        this.endPoint = endPoint;
        this.rpcService = rpcService;
    }

    public SettableFuture<Void> connect() {
        worker = new NioEventLoopGroup(1);
        SettableFuture<Void> connectResultFuture = SettableFuture.create();

        Bootstrap bootstrap = new Bootstrap()
                .group(worker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new KryoDecoder());
                        ch.pipeline().addLast(new KryoEncoder());
                        ch.pipeline().addLast(new RpcInboundHandler(rpcService));
                    }
                });
        ChannelFuture connectFuture = bootstrap.connect(new InetSocketAddress(endPoint.getHost(), endPoint.getPort()));
        this.channel = connectFuture.channel();
        connectFuture.addListener(f -> {
            if (f.isSuccess()) {
                log.info("rpc client connected endPoint={}", endPoint.toString());
                connectResultFuture.set(null);
                Runtime.getRuntime().addShutdownHook(new Thread(this::closeSync));
            } else {
                log.error("rpc connected fail! waiting for retry！", f.cause().getMessage());
                connectResultFuture.setException(f.cause());
                worker.shutdownGracefully();
            }
        });
        return connectResultFuture;
    }

    public Channel getChannel() {
        return channel;
    }

    public void closeSync() {
        try {
            if (channel.isActive()) {
                channel.close().sync();
                Preconditions.checkState(!channel.isActive());
                log.info("shutdown client");
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            worker.shutdownGracefully();
        }
    }

    public void closeAsync() {
        try {
            channel.close();
        } finally {
            worker.shutdownGracefully();
        }
    }
}
