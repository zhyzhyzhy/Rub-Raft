package cc.lovezhy.raft.rpc.server.service;

import cc.lovezhy.raft.rpc.server.handler.RpcInboundHandler;
import cc.lovezhy.raft.rpc.server.codec.KryoDecoder;
import cc.lovezhy.raft.rpc.server.codec.KryoEncoder;
import cc.lovezhy.raft.rpc.server.utils.EndPoint;
import com.esotericsoftware.kryo.Kryo;
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
import java.util.Optional;

public class ServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceClient.class);
    private EndPoint endPoint;
    private Optional<Channel> channel;

    public ServiceClient(EndPoint endPoint) {
        this.endPoint = endPoint;
    }

    public SettableFuture<Void> connect() {
        EventLoopGroup worker = new NioEventLoopGroup(1);
        SettableFuture<Void> connectResultFuture = SettableFuture.create();

        Bootstrap bootstrap = new Bootstrap()
                .group(worker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new KryoDecoder(new Kryo()));
                        ch.pipeline().addLast(new KryoEncoder(new Kryo()));
                        ch.pipeline().addLast(new RpcInboundHandler());
                    }
                });
        ChannelFuture connectFuture = bootstrap.connect(new InetSocketAddress(endPoint.getHost(), endPoint.getPort()));
        this.channel = Optional.ofNullable(connectFuture.channel());
        connectFuture.addListener(f -> {
            if (f.isSuccess()) {
                log.info("rpc client connected");
                connectResultFuture.set(null);
            } else {
                connectResultFuture.setException(f.cause());
                worker.shutdownGracefully();
            }
        });
        return connectResultFuture;
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
