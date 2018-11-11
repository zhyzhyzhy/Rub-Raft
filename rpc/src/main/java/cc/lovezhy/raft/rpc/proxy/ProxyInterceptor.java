package cc.lovezhy.raft.rpc.proxy;

import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.server.service.ServiceClient;
import cc.lovezhy.raft.rpc.server.utils.EndPoint;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyInterceptor implements MethodInterceptor {

    private EndPoint endPoint;
    private ServiceClient client;

    private AtomicBoolean connect = new AtomicBoolean(false);

    public ProxyInterceptor(EndPoint endPoint) {
        this.endPoint = endPoint;
        client = new ServiceClient(endPoint);
        SettableFuture<Void> connectFuture = client.connect();
        Futures.addCallback(connectFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                connect.set(true);
            }

            @Override
            public void onFailure(Throwable t) {
                //ignore
            }
        }, RpcExecutors.commonExecutor());

    }

    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        RpcRequest request = new RpcRequest();

        request.setRequestId(UUID.randomUUID().toString());
        request.setClazz(o.getClass().getName());
        request.setArgs(objects);


        return null;
    }
}
