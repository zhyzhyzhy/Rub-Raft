package cc.lovezhy.raft.rpc.server.service;


import cc.lovezhy.raft.rpc.server.codec.KryoDecoder;
import cc.lovezhy.raft.rpc.server.codec.KryoEncoder;
import cc.lovezhy.raft.rpc.server.handler.RpcInboundHandler;
import cc.lovezhy.raft.rpc.server.utils.EndPoint;
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

import java.net.InetSocketAddress;
import java.util.Optional;

public class ServiceServer {

    private static final Logger log = LoggerFactory.getLogger(ServiceServer.class);

    private EndPoint endPoint;

    private Optional<Channel> channel = Optional.empty();

    public ServiceServer(EndPoint endpoint) {
        this.endPoint = endpoint;
    }

    public SettableFuture<Channel> start() {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        SettableFuture<Channel> bindResultFuture = SettableFuture.create();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                        channel.pipeline().addLast(new KryoDecoder());
                        channel.pipeline().addLast(new KryoEncoder());
                        channel.pipeline().addLast(new RpcInboundHandler());
                    }
                });
        ChannelFuture bindFuture = bootstrap.bind(new InetSocketAddress(endPoint.getHost(), endPoint.getPort()));
        channel = Optional.ofNullable(bindFuture.channel());
        bindFuture.addListener(f -> {
            if (f.isSuccess()) {
                bindResultFuture.set(bindFuture.channel());
                log.info("start rpc server success");
            } else {
                bindResultFuture.setException(f.cause());
                boss.shutdownGracefully();
                worker.shutdownGracefully();
                log.error("start rpc server fail, message={}", f.cause().getMessage(), f.cause());
            }
        });
        return bindResultFuture;
    }

    public void closeSync() {
        if (channel.isPresent()) {
            try {
                channel.get().closeFuture().sync();
            } catch (InterruptedException e) {
                //ignore
            }
        }
    }

    public void closeAsync() {
        channel.ifPresent(Channel::closeFuture);
    }
}
