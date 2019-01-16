package cc.lovezhy.raft.server.common;

import cc.lovezhy.raft.rpc.common.RpcExecutors;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static cc.lovezhy.raft.server.mock6824.Utils.pause;

public class ExecutorTest {

    private ExecutorService executorService;

    private final Object object = new Object();

    private AtomicBoolean atomicBoolean = new AtomicBoolean(false);

    @Before
    public void setUp() {
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            synchronized (object) {
                try {
                    object.wait();
                } catch (InterruptedException e) {
                    //ignore
                }
            }
            atomicBoolean.set(true);
        });
    }

    @Test
    public void testNotify() throws InterruptedException {
        Thread.sleep(2000);
        synchronized (object) {
            object.notify();
        }
        Thread.sleep(2000);
        Assert.assertTrue(atomicBoolean.get());
    }

    @Test
    public void testFuture() {
        SettableFuture<Boolean> settableFuture = SettableFuture.create();
        settableFuture.set(true);
        Futures.addCallback(settableFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(@Nullable Boolean result) {

            }

            @Override
            public void onFailure(Throwable t) {

            }
        }, RpcExecutors.commonExecutor());

        pause(20000);
    }
}
