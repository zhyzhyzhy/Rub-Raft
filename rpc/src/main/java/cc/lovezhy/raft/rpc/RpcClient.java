package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.rpc.exception.RequestTimeoutException;
import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import cc.lovezhy.raft.rpc.proxy.ConsumerService;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.rpc.util.LockObjectFactory.getLockObject;

public class RpcClient<T> implements ConsumerService, RpcService {

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
        if (Objects.nonNull(nettyClient.getChannel())) {
            return nettyClient.getChannel().isRegistered();
        }
        return false;
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
        writeRequest(request);
        LockObject lockObject = getLockObject();
        waitConditionMap.put(requestId, lockObject);

        synchronized (lockObject) {
            if (!rpcResponseMap.containsKey(requestId)) {
                try {
                    lockObject.wait(TimeUnit.MILLISECONDS.toMillis(60));
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        RpcResponse rpcResponse = rpcResponseMap.get(requestId);
        waitConditionMap.remove(requestId);
        rpcResponseMap.remove(requestId);
        lockObject.recycle();
        if (Objects.isNull(rpcResponse)) {
            throw new RequestTimeoutException("request time out");
        }
        return rpcResponse;
    }

    @Override
    public void sendRequestAsync(RpcRequest request) {
        String requestId = request.getRequestId();
        SettableFuture<Object> settableFuture = SettableFuture.create();
        rpcFutureMap.put(requestId, settableFuture);
        writeRequest(request);
        RpcContext.setAsyncResponse(settableFuture);
        RpcExecutors.listeningScheduledExecutor().schedule(() -> {
            if (!settableFuture.isDone()) {
                settableFuture.setException(new RequestTimeoutException("request time out"));
                rpcFutureMap.remove(requestId);
            }
        }, 60, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendOneWayRequest(RpcRequest request) {
        writeRequest(request);
    }

    @Override
    public void onResponse(RpcResponse response) {

        // drop response
        if (!rpcClientOptions.isReliable() && (ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE) % 1000 ) < 100) {
            log.debug("drop");
            return;
        }

        //delay
        if (rpcClientOptions.isLongReordering() && (ThreadLocalRandom.current().nextInt(900) ) < 600) {
            //这里其实意义不大，超过60ms就是超时了，在这儿sleep反而会拉长ForkJoinPool的压力
            int ms = 200 + ThreadLocalRandom.current().nextInt(0, ThreadLocalRandom.current().nextInt(1,2000));
            return;
//            try {
//                Thread.sleep(ms);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }


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
//                log.error("non async !!!!");
            }
        }
    }

    @Override
    public void handleRequest(Channel channel, RpcRequest request) {
        throw new UnsupportedOperationException("RpcClient not support RpcRequest");
    }

    private void writeRequest(RpcRequest request) {
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        //TODO, requestConnect不可以被isOnNet阻拦
        if (rpcClientOptions.isOnNet() || request.getMethod().equals("requestConnect")) {
            if (!rpcClientOptions.isReliable()) {
                try {
                    Thread.sleep(threadLocalRandom.nextInt(Integer.MAX_VALUE) % 27);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
            if (!rpcClientOptions.isReliable() && (threadLocalRandom.nextInt(Integer.MAX_VALUE) % 1000) % 1000 < 100) {
                log.debug("drop");
                return;
            }
            nettyClient.getChannel().writeAndFlush(request);
        } else {
            //ignore
        }
    }
}
