package cc.lovezhy.raft.rpc;

import com.google.common.util.concurrent.SettableFuture;

public class RpcContext {

    private static final ThreadLocal<SettableFuture<Object>> asyncResponse = new ThreadLocal<>();

    @SuppressWarnings("unchecked")
    public static <T> SettableFuture<T> getContextFuture() {
        return (SettableFuture<T>) asyncResponse.get();
    }

    public static void setAsyncResponse(SettableFuture<Object> future) {
        asyncResponse.set(future);
    }
}
