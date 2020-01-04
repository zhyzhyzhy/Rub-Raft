package cc.lovezhy.raft.rpc;

import com.google.common.util.concurrent.SettableFuture;

public class RpcContext {

    private static final ThreadLocal<SettableFuture<Object>> ASYNC_RESPONSE_HOLDER = new ThreadLocal<>();

    @SuppressWarnings("unchecked")
    public static <T> SettableFuture<T> getContextFuture() {
        SettableFuture<Object> objectSettableFuture = ASYNC_RESPONSE_HOLDER.get();
        ASYNC_RESPONSE_HOLDER.remove();
        return (SettableFuture<T>) objectSettableFuture;
    }

    static void setAsyncResponse(SettableFuture<Object> future) {
        ASYNC_RESPONSE_HOLDER.set(future);
    }
}
