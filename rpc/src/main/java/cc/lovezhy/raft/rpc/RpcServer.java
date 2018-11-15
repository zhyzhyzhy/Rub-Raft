package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.server.netty.NettyServer;
import cc.lovezhy.raft.rpc.server.netty.RpcService;
import com.google.common.collect.Lists;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RpcServer {

    private static final Logger log = LoggerFactory.getLogger(RpcService.class);

    private  List<RpcProvider> providers = Lists.newArrayList();

    private  EndPoint DEFAULT_ENDPOINT = EndPoint.create("localhost", 5283);

    private  NettyServer nettyServer;

    private  Map<String, RpcProvider> serviceMap = new HashMap<>();

    private RpcService rpcService = new RpcServerService();

    public void start() {
        nettyServer = new NettyServer(DEFAULT_ENDPOINT, rpcService);
        nettyServer.start();
    }

    public void start(EndPoint endPoint) {
        nettyServer = new NettyServer(endPoint, rpcService);
        nettyServer.start();
    }

    public void registerService(Class<?> serviceClass) {
        RpcProvider provider = RpcProvider.create(serviceClass);
        serviceMap.put(serviceClass.getInterfaces()[0].getName(), provider);
        log.info("register service serviceClass={}, serveEndPoint={}", serviceClass);
        providers.add(provider);
    }

    class RpcServerService implements RpcService {
        @Override
        public void onResponse(RpcResponse response) {
            throw new IllegalStateException();
        }

        @Override
        public void handleRequest(Channel channel, RpcRequest request) {
            RpcResponse rpcResponse = new RpcResponse();
            rpcResponse.setRequestId(request.getRequestId());
            RpcProvider provider = serviceMap.get(request.getClazz());
            Object responseObject = provider.invoke(request.getMethod(), request.getArgs());
            rpcResponse.setResponseBody(responseObject);
            channel.writeAndFlush(rpcResponse);
        }
    }

}
