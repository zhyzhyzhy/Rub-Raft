package cc.lovezhy.raft.server.node;

import java.io.Closeable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PeerNodeStateMachine implements Closeable {

    public static PeerNodeStateMachine create(Long nextIndex) {
        return new PeerNodeStateMachine(nextIndex);
    }

    private Long nextIndex;
    private Long matchIndex;
    private PeerNodeStatus nodeStatus;
    private LinkedBlockingDeque<Runnable> taskQueue;
    private ThreadPoolExecutor executor;

    private PeerNodeStateMachine(Long nextIndex) {
        this.taskQueue = new LinkedBlockingDeque<>(10);
        executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES, taskQueue);
        this.nextIndex = nextIndex;
        this.matchIndex = 0L;
        this.nodeStatus = PeerNodeStatus.NORMAL;
    }

    /**
     * 把任务抢占到队列开头，优先执行
     */
    public void appendFirst(Runnable task) {
        taskQueue.addFirst(task);
    }

    public void append(Runnable task) {
        taskQueue.add(task);
    }

    public Long getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(Long nextIndex) {
        this.nextIndex = nextIndex;
    }

    public Long getMatchIndex() {
        return matchIndex;
    }

    public void setMatchIndex(Long matchIndex) {
        this.matchIndex = matchIndex;
    }

    public PeerNodeStatus getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(PeerNodeStatus nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    @Override
    public void close() {
        taskQueue.clear();
        executor.shutdown();
    }
}
