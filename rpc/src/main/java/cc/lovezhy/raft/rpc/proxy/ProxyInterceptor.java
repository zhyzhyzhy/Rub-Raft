package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.protocal.annotation.Async;
import cc.lovezhy.raft.rpc.util.ReflectUtils;
import com.alibaba.fastjson.JSON;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static cc.lovezhy.raft.rpc.util.IdFactory.generateId;

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
        request.setRequestId(generateId());
        request.setClazz(superClass.getName());
        request.setMethod(method.getName());
        request.setArgs(objects);
        log.info("send RpcRequest={}", JSON.toJSONString(request));

        switch (RpcRequestType.getRpcRequestType(method)) {
            case ONE_WAY: {
                consumerRpcService.sendOneWayRequest(request);
                return null;
            }
            case ASYNC: {
                Async asyncAnnotation = ReflectUtils.getAnnotation(method, Async.class);
                RpcResponse rpcResponse = consumerRpcService.sendRequestAsync(request, asyncAnnotation.waitTimeOutMills());
                return rpcResponse.getResponseBody();
            }
            case NORMAL: {
                RpcResponse rpcResponse = consumerRpcService.sendRequest(request);
                return rpcResponse.getResponseBody();
            }
        }
        throw new IllegalStateException("should not happen");
    }

}
