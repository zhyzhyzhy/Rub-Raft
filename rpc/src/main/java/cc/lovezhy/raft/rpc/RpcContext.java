package cc.lovezhy.raft.rpc;

import com.google.common.util.concurrent.SettableFuture;

public class RpcContext {

    private static final ThreadLocal<SettableFuture<Object>> asyncResponse = new ThreadLocal<>();

    @SuppressWarnings("unchecked")
    public static <T> SettableFuture<T> getContextFuture() {
        SettableFuture<Object> objectSettableFuture = asyncResponse.get();
        asyncResponse.remove();
        return (SettableFuture<T>) objectSettableFuture;
    }

    static void setAsyncResponse(SettableFuture<Object> future) {
        asyncResponse.set(future);
    }
}
