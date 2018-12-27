package cc.lovezhy.raft.server.utils;

import cc.lovezhy.raft.rpc.common.RpcExecutors;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TimeCountDownUtil {

    public static Future addSchedulerTask(long delay,
                                          TimeUnit timeUnit,
                                          Runnable task,
                                          Supplier<Boolean> doWhenReturnTrue) {

        return RpcExecutors.listeningScheduledExecutor().schedule(() -> {
                    if (doWhenReturnTrue.get()) {
                        task.run();
                    }
                },
                delay, timeUnit);
    }

    public static void addSchedulerTaskWithListener(long delay,
                                                    TimeUnit timeUnit,
                                                    Runnable task,
                                                    Supplier<Boolean> doWhenReturnTrue,
                                                    Runnable listener) {
        RpcExecutors.listeningScheduledExecutor().schedule(() -> {
                    if (doWhenReturnTrue.get()) {
                        task.run();
                    }
                },
                delay, timeUnit).addListener(listener, RpcExecutors.commonExecutor());
    }


    public static void addSchedulerListener(long delay, TimeUnit timeUnit, Runnable task, Runnable listener) {
        RpcExecutors.listeningScheduledExecutor()
                .schedule(task, delay, timeUnit)
                .addListener(listener, RpcExecutors.commonExecutor());
    }

}
