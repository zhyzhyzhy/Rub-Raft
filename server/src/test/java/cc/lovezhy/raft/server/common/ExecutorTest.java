package cc.lovezhy.raft.server.common;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
}
