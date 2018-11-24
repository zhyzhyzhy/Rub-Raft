package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.protocal.annotation.Async;
import cc.lovezhy.raft.rpc.util.ReflectUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.Future;

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
        log.info("{}", JSON.toJSONString(request));
        RpcResponse rpcResponse;

        Optional<Annotation> asyncAnnotation = ReflectUtils.getAnnotation(method, Async.class);
        if (asyncAnnotation.isPresent()) {
            long timeOutMills = ((Async)asyncAnnotation.get()).waitTimeOutMills();
            Preconditions.checkState(method.getReturnType().equals(Future.class));
            rpcResponse = consumerRpcService.sendRequestAsync(request, timeOutMills);
        } else {
            rpcResponse = consumerRpcService.sendRequest(request);
        }
        return rpcResponse.getResponseBody();
    }

}
