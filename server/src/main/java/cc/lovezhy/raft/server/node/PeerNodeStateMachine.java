package cc.lovezhy.raft.server.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.*;

public class PeerNodeStateMachine implements Closeable {

    public static PeerNodeStateMachine create(Long nextIndex) {
        return new PeerNodeStateMachine(nextIndex);
    }

    private static final Logger log = LoggerFactory.getLogger(PeerNodeStateMachine.class);

    private Long nextIndex;
    private Long matchIndex;
    private PeerNodeStatus nodeStatus;
    private LinkedBlockingDeque<Runnable> taskQueue;
    private Executor taskExecutor;
    private Executor schedulerExecutor;

    private Runnable scheduleTask = () -> {
        for (;;) {
            Runnable task = null;
            try {
                task = taskQueue.take();
            } catch (InterruptedException e) {
                // ignore
            }
            if (Objects.isNull(task)) {
                continue;
            }
            taskExecutor.execute(task);
        }
    };
    private PeerNodeStateMachine(Long nextIndex) {
        this.taskQueue = new LinkedBlockingDeque<>(10);
        this.taskExecutor = Executors.newSingleThreadExecutor();
        this.schedulerExecutor = Executors.newSingleThreadExecutor();
        this.nextIndex = nextIndex;
        this.matchIndex = 0L;
        this.nodeStatus = PeerNodeStatus.NORMAL;

        this.schedulerExecutor.execute(scheduleTask);
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
    }
}
