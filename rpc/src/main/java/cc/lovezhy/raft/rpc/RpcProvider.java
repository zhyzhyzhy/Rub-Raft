package cc.lovezhy.raft.rpc;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class RpcProvider {

    private static final Logger log = LoggerFactory.getLogger(RpcProvider.class);

    static RpcProvider create(Class<?> providerClazz) {
        return new RpcProvider(providerClazz);
    }

    private Object instance;
    private Map<String, Method> methodMap;

    private RpcProvider(Class<?> providerClazz) {
        this.methodMap = Maps.newHashMap();

        for (Method method : providerClazz.getDeclaredMethods()) {
            methodMap.put(method.getName(), method);
        }
        try {
            this.instance = providerClazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            log.error(e.getMessage(), e);
        }
    }

    Object invoke(String methodName, Object[] params) {
        Method method = methodMap.get(methodName);
        try {
            return method.invoke(instance, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return new IllegalStateException();
    }


}
