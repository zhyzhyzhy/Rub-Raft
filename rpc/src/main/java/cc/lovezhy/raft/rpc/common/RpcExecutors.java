package cc.lovezhy.raft.rpc.common;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class RpcExecutors {

    private RpcExecutors() {
    }

    private static final int RPC_FIXED_EXECUTE_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    private static final int RPC_SCHEDULE_EXECUTE_THREADS = Runtime.getRuntime().availableProcessors();

    private static final ExecutorService FIXED_EXECUTOR = Executors.newFixedThreadPool(RPC_FIXED_EXECUTE_THREADS);

    private static final ScheduledExecutorService SCHEDULE_EXECUTOR = Executors.newScheduledThreadPool(RPC_SCHEDULE_EXECUTE_THREADS);

    public static ExecutorService commonExecutor() {
        return FIXED_EXECUTOR;
    }

    public static ScheduledExecutorService commonScheduledExecutor() {
        return SCHEDULE_EXECUTOR;
    }

    public static ListeningScheduledExecutorService listeningScheduledExecutor() {
        return MoreExecutors.listeningDecorator(SCHEDULE_EXECUTOR);
    }

}
