package cc.lovezhy.raft.rpc;

import io.vertx.core.json.JsonObject;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@ThreadSafe
public class RpcStatistics {

    public static RpcStatistics create() {
        return new RpcStatistics();
    }

    /**
     * 进来的Rpc个数
     */
    private static final String INCOMING_TOTAL_REQUEST = "incomingTotalRequest";
    private AtomicLong incomingTotalRequest;

    private RpcStatistics() {
        this.incomingTotalRequest = new AtomicLong(0L);
    }

    public void incrIncomingRequestAsync() {
        CompletableFuture.runAsync(() -> incomingTotalRequest.incrementAndGet());
    }

    public JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put(INCOMING_TOTAL_REQUEST, incomingTotalRequest);
        return jsonObject;
    }
}
