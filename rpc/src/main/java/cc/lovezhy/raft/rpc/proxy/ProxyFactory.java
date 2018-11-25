package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.RpcClientOptions;
import net.sf.cglib.proxy.Enhancer;

import static java.util.Objects.requireNonNull;

public class ProxyFactory {

    @SuppressWarnings("unchecked")
    public static <T> T createRpcProxy(Class<T> clazz, ConsumerRpcService consumerRpcService, RpcClientOptions rpcClientOptions) {
        requireNonNull(clazz);
        requireNonNull(consumerRpcService);

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        enhancer.setCallback(new ProxyInterceptor(clazz, rpcClientOptions, consumerRpcService));
        return (T) enhancer.create();
    }

}
