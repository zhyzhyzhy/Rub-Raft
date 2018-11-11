package cc.lovezhy.raft.rpc.proxy;

import net.sf.cglib.proxy.Enhancer;

import static java.util.Objects.requireNonNull;

public class ProxyFactory {

    @SuppressWarnings("unchecked")
    public static <T> T createRpcProxy(Class<T> clazz, ConsumerRpcService consumerRpcService) {
        requireNonNull(clazz);
        requireNonNull(consumerRpcService);

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        enhancer.setCallback(new ProxyInterceptor(consumerRpcService));
        return (T) enhancer.create();
    }

}
