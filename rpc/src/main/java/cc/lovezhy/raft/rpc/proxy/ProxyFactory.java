package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.server.utils.EndPoint;
import net.sf.cglib.proxy.Enhancer;

import static java.util.Objects.requireNonNull;

public class ProxyFactory {

    @SuppressWarnings("unchecked")
    public static <T> T createRpcProxy(Class<T> clazz, EndPoint endPoint) {
        requireNonNull(clazz);
        requireNonNull(endPoint);

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        enhancer.setCallback(new ProxyInterceptor(endPoint));
        return (T) enhancer.create();
    }

}
