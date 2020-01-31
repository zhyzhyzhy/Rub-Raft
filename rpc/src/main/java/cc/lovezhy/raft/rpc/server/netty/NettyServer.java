package cc.lovezhy.raft.rpc.server.netty;


import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.server.codec.KryoDecoder;
import cc.lovezhy.raft.rpc.server.codec.KryoEncoder;
import cc.lovezhy.raft.rpc.server.handler.RpcInboundHandler;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

public class NettyServer implements Closeable {

    private final Logger log = LoggerFactory.getLogger(NettyServer.class);

    private EndPoint endPoint;

    private Channel channel;

    private RpcService rpcService;

    private EventLoopGroup boss;
    private EventLoopGroup worker;

    public NettyServer(EndPoint endpoint, RpcService rpcService) {
        checkNotNull(endpoint);
        checkNotNull(rpcService);
        this.endPoint = endpoint;
        this.rpcService = rpcService;
    }

    public SettableFuture<Void> start() {
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        SettableFuture<Void> bindResultFuture = SettableFuture.create();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new KryoDecoder());
                        channel.pipeline().addLast(new KryoEncoder());
                        channel.pipeline().addLast(new RpcInboundHandler(rpcService));
                    }
                });
        ChannelFuture bindFuture = bootstrap.bind(new InetSocketAddress(endPoint.getHost(), endPoint.getPort()));
        channel = bindFuture.channel();
        bindFuture.addListener(f -> {
            if (f.isSuccess()) {
                bindResultFuture.set(null);
                log.debug("start rpc server success");
                Runtime.getRuntime().addShutdownHook(new Thread(this::closeSync));
            } else {
                bindResultFuture.setException(f.cause());
                boss.shutdownGracefully();
                worker.shutdownGracefully();
                log.error("start rpc server fail, message={}, endPoint={}", f.cause().getMessage(), endPoint, f.cause());
            }
        });
        try {
            bindFuture.sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return bindResultFuture;
    }

    public Channel getChannel() {
        return channel;
    }

    public synchronized void closeSync() {
        try {
            if (channel.isOpen()) {
                channel.close().sync();
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    @Override
    public void close() throws IOException {
        closeSync();
    }
}
