package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.UUID;

public class ProxyInterceptor implements MethodInterceptor {

    private ConsumerRpcService consumerRpcService;

    public ProxyInterceptor(ConsumerRpcService consumerRpcService) {
        this.consumerRpcService = consumerRpcService;
    }

    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClazz(o.getClass().getName());
        request.setMethod(method.getName());
        request.setArgs(objects);
        RpcResponse rpcResponse = consumerRpcService.sendRequest(request);
        return rpcResponse.getResponseBody();
    }
}
