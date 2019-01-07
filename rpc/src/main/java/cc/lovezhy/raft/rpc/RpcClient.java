package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.proxy.ConsumerRpcService;
import cc.lovezhy.raft.rpc.proxy.ProxyFactory;
import cc.lovezhy.raft.rpc.server.netty.NettyClient;
import cc.lovezhy.raft.rpc.server.netty.RpcService;
import cc.lovezhy.raft.rpc.util.LockObjectFactory.LockObject;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.rpc.util.LockObjectFactory.getLockObject;

public class RpcClient<T> implements ConsumerRpcService, RpcService {

    public static <T> RpcClient<T> create(Class<T> clazz, EndPoint endPoint) {
        return create(clazz, endPoint, new RpcClientOptions());
    }

    public static <T> RpcClient<T> create(Class<T> clazz, EndPoint endPoint, RpcClientOptions rpcClientOptions) {
        Preconditions.checkNotNull(clazz);
        Preconditions.checkNotNull(endPoint);
        return new RpcClient<>(clazz, endPoint, rpcClientOptions);
    }

    private final Logger log = LoggerFactory.getLogger(RpcClient.class);

    private Class<T> clazz;
    private NettyClient nettyClient;
    private RpcClientOptions rpcClientOptions;

    //for close
    private Future connectFuture;

    private RpcClient(Class<T> clazz, EndPoint endPoint, RpcClientOptions rpcClientOptions) {
        Preconditions.checkNotNull(clazz);
        Preconditions.checkNotNull(endPoint);
        Preconditions.checkNotNull(rpcClientOptions);
        this.clazz = clazz;
        this.rpcClientOptions = rpcClientOptions;
        nettyClient = new NettyClient(endPoint, this);
    }

    public void connect(SettableFuture<Void> done) {
        SettableFuture<Void> connectResultFuture = nettyClient.connect();
        Futures.addCallback(connectResultFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                connectFuture = null;
                done.set(null);
            }

            @Override
            public void onFailure(Throwable t) {
                connectFuture = RpcExecutors.commonScheduledExecutor().schedule(() -> connect(done), 200, TimeUnit.MILLISECONDS);
            }
        }, RpcExecutors.listeningScheduledExecutor());
    }

    public boolean isConnectAlive() {
        return nettyClient.getChannel().isRegistered();
    }

    /**
     * shutdown NettyClient
     */
    public void close() {
        //still try reconnect
        if (Objects.nonNull(connectFuture)) {
            connectFuture.cancel(true);
        }
        this.nettyClient.closeSync();
    }

    public T getInstance() {
        return ProxyFactory.createRpcProxy(clazz, this, rpcClientOptions);
    }


    private Map<String, LockObject> waitConditionMap = new ConcurrentHashMap<>();
    private Map<String, RpcResponse> rpcResponseMap = new ConcurrentHashMap<>();
    private Map<String, SettableFuture<Object>> rpcFutureMap = new ConcurrentHashMap<>();

    @Override
    public RpcResponse sendRequest(RpcRequest request) throws RequestTimeoutException {
        String requestId = request.getRequestId();
        nettyClient.getChannel().writeAndFlush(request);
        LockObject lockObject = getLockObject();
        waitConditionMap.put(requestId, lockObject);

        synchronized (lockObject) {
            if (!rpcResponseMap.containsKey(requestId)) {
                try {
                    lockObject.wait(TimeUnit.MILLISECONDS.toMillis(80));
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        RpcResponse rpcResponse = rpcResponseMap.get(requestId);
        if (Objects.isNull(rpcResponse)) {
            throw new RequestTimeoutException("request time out");
        }
        waitConditionMap.remove(requestId);
        rpcResponseMap.remove(requestId);
        lockObject.recycle();
        return rpcResponse;
    }

    @Override
    public RpcResponse sendRequestAsync(RpcRequest request) {
        String requestId = request.getRequestId();
        nettyClient.getChannel().writeAndFlush(request);
        SettableFuture<Object> settableFuture = SettableFuture.create();
        rpcFutureMap.put(requestId, settableFuture);
        RpcContext.setAsyncResponse(settableFuture);
        RpcResponse rpcResponse = new RpcResponse();
        rpcResponse.setResponseBody(null);
        return rpcResponse;
    }

    @Override
    public void sendOneWayRequest(RpcRequest request) {
        nettyClient.getChannel().writeAndFlush(request);
    }

    @Override
    public void onResponse(RpcResponse response) {
        String requestId = response.getRequestId();
        LockObject lockObject = waitConditionMap.get(requestId);
        //normal
        if (Objects.nonNull(lockObject)) {
            synchronized (lockObject) {
                rpcResponseMap.put(requestId, response);
                lockObject.notify();
            }
        }
        //async
        else {
            SettableFuture<Object> settableFuture = rpcFutureMap.get(requestId);
            if (Objects.nonNull(settableFuture)) {
                if (response.getResponseBody() == null) {
                    log.error("responseBody is null");
                }
                settableFuture.set(response.getResponseBody());
                rpcFutureMap.remove(requestId);
            } else {
                log.error("non async !!!!");
            }
        }
    }

    @Override
    public void handleRequest(Channel channel, RpcRequest request) {
        throw new UnsupportedOperationException("RpcClient not support RpcRequest");
    }
}
