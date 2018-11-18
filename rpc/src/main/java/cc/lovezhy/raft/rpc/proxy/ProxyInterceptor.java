package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import com.alibaba.fastjson.JSON;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.UUID;

public class ProxyInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ProxyInterceptor.class);

    private ConsumerRpcService consumerRpcService;
    private Class<?> superClass;

    ProxyInterceptor(Class<?> superClass, ConsumerRpcService consumerRpcService) {
        this.superClass = superClass;
        this.consumerRpcService = consumerRpcService;
    }

    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws RequestTimeoutException {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClazz(superClass.getName());
        request.setMethod(method.getName());
        request.setArgs(objects);
        log.info("{}", JSON.toJSONString(request));
        RpcResponse rpcResponse = null;
        rpcResponse = consumerRpcService.sendRequest(request);
        return rpcResponse.getResponseBody();
    }
}
