package cc.lovezhy.raft.server.utils;

import cc.lovezhy.raft.rpc.util.LockObjectFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class VoteAction {

    private int successMin;
    private int failMin;

    private AtomicInteger successCounter;
    private AtomicInteger failCounter;

    private final Object awaitObject = LockObjectFactory.getLockObject();

    public VoteAction(int successMin, int failMin) {
        this.successMin = successMin;
        this.failMin = failMin;
        this.successCounter = new AtomicInteger();
        this.failCounter = new AtomicInteger();
    }

    public void success() {
        successCounter.incrementAndGet();
        if (meetCondition()) {
            synchronized (awaitObject) {
                awaitObject.notifyAll();
            }
        }
    }

    public void fail() {
        failCounter.incrementAndGet();
        if (meetCondition()) {
            synchronized (awaitObject) {
                awaitObject.notifyAll();
            }
        }
    }

    public boolean meetCondition() {
        return successCounter.get() >= successMin || failCounter.get() >= failMin;
    }

    public boolean votedSuccess() {
        return successCounter.get() >= successMin;
    }

    public void await() {
        synchronized (awaitObject) {
            while (!meetCondition()) {
                try {
                    awaitObject.wait(20);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
    }


}
