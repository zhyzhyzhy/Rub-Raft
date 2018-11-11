package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.server.netty.NettyServer;
import cc.lovezhy.raft.rpc.server.netty.RpcService;
import io.netty.channel.Channel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RpcProvider implements RpcService {

    public static RpcProvider create(Class<?> providerClazz, EndPoint endPoint) {
        return new RpcProvider(providerClazz, endPoint);
    }

    private NettyServer nettyServer;
    private Class<?> providerClazz;
    private Object instance;
    private Map<String, Method> methodMap = new HashMap<>();


    public RpcProvider(Class<?> providerClazz, EndPoint endPoint) {
        this.providerClazz = providerClazz;

        for (Method method : providerClazz.getDeclaredMethods()) {
            methodMap.put(method.getName(), method);
        }

        try {
            this.instance = providerClazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        this.nettyServer = new NettyServer(endPoint, this);
        this.nettyServer.start();
    }

    @Override
    public void onResponse(RpcResponse response) {
        throw new IllegalStateException();
    }

    @Override
    public void handleRequest(Channel channel, RpcRequest request) {
        RpcResponse rpcResponse = new RpcResponse();
        rpcResponse.setRequestId(request.getRequestId());

        String methodName = request.getMethod();
        Method method = methodMap.get(methodName);
        Object responseObject = null;
        try {
            responseObject = method.invoke(instance, request.getArgs());
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        rpcResponse.setResponseBody(responseObject);
        channel.writeAndFlush(rpcResponse);
    }
}
