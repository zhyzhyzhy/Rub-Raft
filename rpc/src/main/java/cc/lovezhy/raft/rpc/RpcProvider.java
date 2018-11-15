package cc.lovezhy.raft.rpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RpcProvider {

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
            e.printStackTrace();
        }
    }

    public Object invoke(String methodName, Object[] params) {
        Method method = methodMap.get(methodName);
        try {
            return method.invoke(instance, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return new IllegalStateException();
    }


}
