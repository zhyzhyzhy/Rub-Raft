package cc.lovezhy.raft.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RpcProvider {

    private static final Logger log = LoggerFactory.getLogger(RpcProvider.class);

    public static RpcProvider create(Class<?> providerClazz) {
        return new RpcProvider(providerClazz);
    }

    private Object instance;
    private Map<String, Method> methodMap = new HashMap<>();

    private RpcProvider(Class<?> providerClazz) {

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
