package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.StateMachine;
import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.storage.*;
import cc.lovezhy.raft.server.utils.EventRecorder;
import com.alibaba.fastjson.JSON;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class LogServiceImpl implements LogService {

    private Logger log = LoggerFactory.getLogger(LogServiceImpl.class);

    private StorageService storageService;
    private StateMachine stateMachine;

    private volatile Long lastCommitLogIndex;
    private volatile Long lastCommitLogTerm;

    private volatile Snapshot snapshot;


    @VisibleForTesting
    public static final int MAX_LOG_BEFORE_TAKE_SNAPSHOT = 20;
    private AtomicInteger appliedLogInMemoryCounter = new AtomicInteger(0);

    /**
     * 日志的开头，因为有些可能已经被压缩了
     */
    private volatile int start;

    private EventRecorder eventRecorder;

    private ReentrantLock LOG_LOCK = new ReentrantLock(true);

    public LogServiceImpl(StateMachine stateMachine, StorageType storageType, EventRecorder eventRecorder) throws IOException {
        Preconditions.checkNotNull(stateMachine);
        Preconditions.checkNotNull(storageType);
        Preconditions.checkNotNull(eventRecorder);

        switch (storageType) {
            case FILE:
                this.storageService = FileStorageService.create("/private/var/log/raft/");
                break;
            case MEMORY:
//                this.storageService = MemoryStorageService.create();
                this.storageService = FileStorageService.create("/private/var/log/raft/");
                break;
            default:
                throw new IllegalStateException();
        }
        this.start = 0;
        this.stateMachine = stateMachine;
        /*
          提交一个DUMMY的LogEntry
         */
        this.stateMachine.apply(((DefaultCommand) LogConstants.getInitialLogEntry().getCommand()));
        this.storageService.append(LogConstants.getInitialLogEntry().toStorageEntry());
        this.lastCommitLogIndex = 0L;
        this.lastCommitLogTerm = 0L;
//        this.lastAppliedLogIndex = 0L;
//        this.lastAppliedLogTerm = 0L;
        this.eventRecorder = eventRecorder;
    }

    @Override
    public LogEntry get(long index) {
        Preconditions.checkState(index >= 0, String.format("index=%d", index));
        //如果日志已经被压缩
        if (index < start) {
            log.error("Log Has Been Compact, start={}, requestIndex={}", start, index);
            throw new HasCompactException(String.format("start=%d, index=%d", start, index));
        }
        //如果还未有这个Index，返回空
        if (index >= start + storageService.getLen()) {
            return null;
        }
        StorageEntry storageEntry = storageService.get((int) (index - start));
        Preconditions.checkNotNull(storageEntry);
        return storageEntry.toLogEntry();
    }

    /**
     * [start, end]
     */
    @Override
    public List<LogEntry> get(long start, long end) {
        log.debug("get logEntry, start={}, end={}", start, end);
        if (start > end) {
            return Collections.emptyList();
        }
        if (start < this.start) {
            throw new HasCompactException(String.format("logStart=%d, requestStart=%d", this.start, start));
        }
        if (end > this.start + storageService.getLen()) {
            throw new IndexOutOfBoundsException();
        }
        List<StorageEntry> storageEntries = storageService.range(((int) start - this.start), ((int) end - this.start));
        List<LogEntry> logEntries = Lists.newArrayList();
        storageEntries.forEach(storageEntry -> logEntries.add(storageEntry.toLogEntry()));
        return logEntries;
    }

    @Override
    public boolean hasInSnapshot(long index) {
        Preconditions.checkState(index >= 0, String.format("index < 0, index = %d", index));
        if (index > start + storageService.getLen()) {
            throw new IndexOutOfBoundsException();
        }
        return index < start;
    }

    /**
     * file中的set不太好做，直接remove再Append吧
     * @param index
     */
//    @Override
//    public boolean set(long index, LogEntry entry) {
//        Preconditions.checkNotNull(entry);
//        if (index < start) {
//            throw new HasCompactException(String.format("start=%d, index=%d", start, index));
//        }
//        return storageService.set((int) (index - start), entry.toStorageEntry());
//    }

    @Override
    public void commit(long index) {
        if (index > start + storageService.getLen() - 1) {
            return;
        }
        if (index == this.lastCommitLogIndex) {
            return;
        }
        //attention i debug a day
        long expectNextIndex = this.lastCommitLogIndex + 1;
        while (index >= expectNextIndex) {
            LogEntry logEntry = get(expectNextIndex);
            log.info("commit logEntry={}", JSON.toJSONString(logEntry));
            if (Objects.nonNull(logEntry)) {
                if (logEntry.getCommand() instanceof ClusterConfCommand) {

                } else {
                    this.stateMachine.apply(((DefaultCommand) logEntry.getCommand()));
                }
            }
            expectNextIndex++;
        }
        this.lastCommitLogIndex = index;
        this.lastCommitLogTerm = get(index).getTerm();
        //暂时不需要创建快照
//        createSnapShotIfNecessary();
    }

    @Override
    public synchronized int appendLog(LogEntry logEntry) {
        return appendLog(storageService.getLen() + start, Collections.singletonList(logEntry));
    }

    @Override
    public synchronized int appendLog(long fromIndex, LogEntry logEntry) {
        return appendLog(fromIndex, Collections.singletonList(logEntry));
    }

    @Override
    public synchronized int appendLog(List<LogEntry> entries) {
        return appendLog(storageService.getLen() - 1 + start, entries);
    }

    @Override
    public synchronized int appendLog(long fromIndex, List<LogEntry> entries) {
        Preconditions.checkNotNull(entries);
        /**
         * origin bug
         * 如果为空，根据fromIndex，要将后面的log进行remove
         */
        if (entries.isEmpty()) {
            if (fromIndex <= (storageService.getLen() - 1 + start)) {
                System.out.println("origin bug");
            }
        }
        LOG_LOCK.lock();
        try {
            while (fromIndex <= (storageService.getLen() - 1 + start)) {
                storageService.remove(Math.toIntExact(fromIndex));
            }
            for (LogEntry entry : entries) {
                storageService.append(entry.toStorageEntry());
            }
            return storageService.getLen() - 1 + start;
        } finally {
            LOG_LOCK.unlock();
        }
    }

    @Override
    public long getLastCommitLogTerm() {
        return this.lastCommitLogTerm;
    }

    @Override
    public long getLastCommitLogIndex() {
        return this.lastCommitLogIndex;
    }

    @Override
    public long getLastLogTerm() {
        StorageEntry storageEntry = storageService.get(storageService.getLen() - 1);
        if (Objects.isNull(storageEntry)) {
            System.out.println("cdvfd");
        }
        return storageEntry.toLogEntry().getTerm();
    }

    @Override
    public long getLastLogIndex() {
        return storageService.getLen() - 1 + (long) start;
    }

    // 日志比较的原则是，如果本地的最后一条log entry的term更大，则term大的更新，如果term一样大，则log index更大的更新
    @Override
    public boolean isNewerThanSelf(long lastLogTerm, long lastLogIndex) {
        log.info("lastLogTerm={}, lastLogIndex={}", getLastLogTerm(), getLastLogIndex());
        if (lastLogTerm > getLastLogTerm()) {
            return true;
        }
        if (lastLogTerm == getLastLogTerm() && lastLogIndex >= getLastLogIndex()) {
            return true;
        }
        return false;
    }

    @Override
    public Snapshot getSnapShot() {
        Preconditions.checkNotNull(snapshot);
        return snapshot;
    }

    private void createSnapShotIfNecessary() {
        int counter = appliedLogInMemoryCounter.incrementAndGet();
        if (counter >= MAX_LOG_BEFORE_TAKE_SNAPSHOT) {
            eventRecorder.add(EventRecorder.Event.SnapShot, String.format("ready to take snapshot, appliedLogInMemory=%d", counter));
            createSnapshot();
            appliedLogInMemoryCounter.set(0);
        }
    }

    @Override
    public void createSnapshot() {
        LOG_LOCK.lock();
        try {
            eventRecorder.add(EventRecorder.Event.SnapShot, String.format("before snapshot, start=%d, lastCommitLogIndex=%d", this.start, getLastCommitLogIndex()));
            byte[] snapshotValues = stateMachine.takeSnapShot();
            Long lastCommitLogIndex = getLastCommitLogIndex();
            Long lastCommitLogTerm = getLastCommitLogTerm();
            Snapshot snapshot = new Snapshot();
            snapshot.setData(snapshotValues);
            snapshot.setLastLogIndex(lastCommitLogIndex);
            snapshot.setLastLogTerm(lastCommitLogTerm);
            this.snapshot = snapshot;
            eventRecorder.add(EventRecorder.Event.SnapShot, String.format("after snapshot, start=%d, lastCommitLogIndex=%d", this.start, getLastCommitLogIndex()));
        } finally {
            LOG_LOCK.unlock();
        }
    }

    @Override
    public synchronized boolean installSnapshot(Snapshot snapshot, LogEntry logEntry) {
        Preconditions.checkNotNull(snapshot);
        LOG_LOCK.lock();
        try {
            stateMachine.fromSnapShot(snapshot.getData());
            this.lastCommitLogIndex = snapshot.getLastLogIndex();
            storageService.discard(storageService.getLen());
            storageService.append(logEntry.toStorageEntry());
            this.start = Math.toIntExact(this.lastCommitLogIndex);
        } finally {
            LOG_LOCK.unlock();
        }
        return true;
    }

    @Override
    public void execInLock(Runnable action) {
        LOG_LOCK.lock();
        try {
            action.run();
        } finally {
            LOG_LOCK.unlock();
        }
    }

    @Override
    public StorageService getStorageService() {
        return storageService;
    }

    @Override
    public StateMachine getStateMachine() {
        return stateMachine;
    }
}
