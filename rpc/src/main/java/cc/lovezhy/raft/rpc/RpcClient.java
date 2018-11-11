package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.proxy.ConsumerRpcService;
import cc.lovezhy.raft.rpc.proxy.ProxyFactory;
import cc.lovezhy.raft.rpc.server.netty.NettyClient;
import cc.lovezhy.raft.rpc.server.netty.RpcService;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

public class RpcClient<T> implements ConsumerRpcService, RpcService {

    public static <T> T create(Class<T> clazz, EndPoint endPoint) {
        RpcClient<T> rpcClient = new RpcClient<>(clazz, endPoint);
        return rpcClient.getInstance();
    }

    private Class<T> clazz;
    private NettyClient nettyClient;

    private Map<String, Thread> waitConditionMap = new ConcurrentHashMap<>();
    private Map<String, RpcResponse> rpcResponseMap = new ConcurrentHashMap<>();

    private RpcClient(Class<T> clazz, EndPoint endPoint) {
        this.clazz = clazz;
        nettyClient = new NettyClient(endPoint, this);
        nettyClient.connect();
    }

    private T getInstance() {
        return ProxyFactory.createRpcProxy(clazz, this);
    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        String requestId = request.getRequestId();
        nettyClient.getChannel().writeAndFlush(request);
        waitConditionMap.put(requestId, Thread.currentThread());

        if (!rpcResponseMap.containsKey(requestId)) {
            LockSupport.park();
        }

        RpcResponse rpcResponse = rpcResponseMap.get(requestId);
        waitConditionMap.remove(requestId);
        rpcResponseMap.remove(requestId);
        return rpcResponse;
    }

    @Override
    public void sendRequestAsync(RpcRequest request, FutureCallback futureCallback) {
        // TODO
    }

    @Override
    public void sendOneWayRequest(RpcRequest request) {
        // TODO
    }

    @Override
    public void onResponse(RpcResponse response) {
        String requestId = response.getRequestId();
        rpcResponseMap.put(requestId, response);
        Thread thread = waitConditionMap.get(requestId);
        LockSupport.unpark(thread);
    }

    @Override
    public void handleRequest(Channel channel, RpcRequest request) {
        throw new IllegalStateException();
    }
}
