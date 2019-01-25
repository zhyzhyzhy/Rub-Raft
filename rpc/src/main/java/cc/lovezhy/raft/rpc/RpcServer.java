package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.resource.RpcServerStateResource;
import cc.lovezhy.raft.rpc.server.netty.NettyServer;
import cc.lovezhy.raft.rpc.server.netty.RpcService;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RpcServer {

    private final Logger log = LoggerFactory.getLogger(RpcService.class);

    private List<RpcProvider> providers;

    private NettyServer nettyServer;

    private Map<String, RpcProvider> serviceMap;

    private RpcService rpcService;

    private RpcStatistics rpcStatistics;

    private RpcServerStateResource rpcServerStateResource;

    public RpcServer() {
        this.serviceMap = Collections.synchronizedMap(Maps.newHashMap());
        this.providers = Collections.synchronizedList(Lists.newArrayList());
        this.rpcService = new RpcServerService();
    }

    public void start() {
        start(RpcServerOptions.DEFAULT_ENDPOINT);
    }

    public void start(EndPoint endPoint) {
        Preconditions.checkNotNull(endPoint);
        RpcServerOptions rpcServerOptions = new RpcServerOptions();
        rpcServerOptions.setStartEndPoint(endPoint);
        start(rpcServerOptions);
    }

    public void start(RpcServerOptions rpcServerOptions) {
        Preconditions.checkNotNull(rpcServerOptions);
        nettyServer = new NettyServer(rpcServerOptions.getStartEndPoint(), rpcService);
        nettyServer.start();
        log.info("start rpc server endPoint={}", rpcServerOptions.getStartEndPoint());

        this.rpcStatistics = RpcStatistics.create();
        int rpcServerStateResourcePort = rpcServerOptions.getStartEndPoint().getPort() + 1;
        this.rpcServerStateResource = RpcServerStateResource.create(rpcServerStateResourcePort, rpcStatistics);
        this.rpcServerStateResource.start();
        log.info("start rpcServerStateResource at port {}", rpcServerStateResourcePort);
    }

    public void registerService(Class<?> serviceClass) {
        Preconditions.checkNotNull(serviceClass);
        Preconditions.checkState(serviceClass.getInterfaces().length == 1, "current rpcImpl class should have one interface and only one");
        RpcProvider provider = RpcProvider.create(serviceClass);
        serviceMap.put(serviceClass.getInterfaces()[0].getName(), provider);
        providers.add(provider);
        log.debug("register service serviceClass={}", serviceClass);
    }

    public <T> void registerService(T serviceBean) {
        Preconditions.checkNotNull(serviceBean);
        Preconditions.checkState(serviceBean.getClass().getInterfaces().length == 1, "current rpcImpl class should have one interface and only one");
        RpcProvider provider = RpcProvider.create(serviceBean);
        serviceMap.put(serviceBean.getClass().getInterfaces()[0].getName(), provider);
        providers.add(provider);
        log.debug("register service serviceClass={}", serviceBean.getClass().getInterfaces()[0]);
    }

    public void close() {
        nettyServer.closeSync();
        rpcServerStateResource.close();
    }

    class RpcServerService implements RpcService {
        @Override
        public void onResponse(RpcResponse response) {
            throw new IllegalStateException();
        }

        @Override
        public void handleRequest(Channel channel, RpcRequest request) {
            rpcStatistics.incrIncomingRequestAsync();
            RpcProvider provider = serviceMap.get(request.getClazz());
            Object responseObject = provider.invoke(request.getMethod(), request.getArgs());
            if (request.getRpcRequestType().equals(RpcRequestType.ONE_WAY)) {
                return;
            }
            RpcResponse rpcResponse = new RpcResponse();
            rpcResponse.setRequestId(request.getRequestId());
            rpcResponse.setResponseBody(responseObject);
            channel.writeAndFlush(rpcResponse);
        }
    }

}
