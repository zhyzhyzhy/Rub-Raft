package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.RpcClientOptions;
import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
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
    private RpcClientOptions rpcClientOptions;

    ProxyInterceptor(Class<?> superClass, RpcClientOptions rpcClientOptions, ConsumerRpcService consumerRpcService) {
        this.superClass = superClass;
        this.consumerRpcService = consumerRpcService;
        this.rpcClientOptions = rpcClientOptions;
    }

    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws RequestTimeoutException {
        RpcRequest request = new RpcRequest();
        request.setRequestId(generateId());
        request.setClazz(superClass.getName());
        request.setMethod(method.getName());
        request.setArgs(objects);
        log.info("send RpcRequest={}", JSON.toJSONString(request));

        RpcRequestType requestType = rpcClientOptions.getRpcRequestType(method.getName());
        switch (requestType) {
            case NORMAL: {
                RpcResponse rpcResponse = consumerRpcService.sendRequest(request);
                return rpcResponse.getResponseBody();
            }
            case ONE_WAY: {
                consumerRpcService.sendOneWayRequest(request);
                return null;
            }
            case ASYNC: {
                RpcResponse rpcResponse = consumerRpcService.sendRequestAsync(request);
                return null;
            }
        }
        throw new IllegalStateException("should not happen");
    }

}
