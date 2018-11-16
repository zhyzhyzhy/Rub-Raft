package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.utils.TimeCountDownUtil;
import org.junit.Test;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ListenerTest {

    private static AtomicLong i = new AtomicLong();
    private static AtomicLong counter = new AtomicLong();

    @Test
    public void testListener() {
//        TimeCountDownUtil.addSchedulerListener(2, TimeUnit.MILLISECONDS, () -> {
//            long l = counter.incrementAndGet();
//            System.out.println(1);
//            if (l == 1000) {
//                new IllegalStateException().printStackTrace();
//            }
//        }, this::testListener);
//        Scanner scanner = new Scanner(System.in);
//        long k = i.incrementAndGet();
//        if (k == 1) {
//            scanner.next();
//        }
    }

}
