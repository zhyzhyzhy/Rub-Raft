package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.StateMachine;
import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.storage.*;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class LogServiceImpl implements LogService {

    private static final Logger log = LoggerFactory.getLogger(LogServiceImpl.class);

    private StorageService storageService;
    private StateMachine stateMachine;

    private volatile Long lastCommitLogIndex;
    private volatile Long lastCommitLogTerm;

    private volatile Long lastAppliedLogIndex;
    private volatile Long lastAppliedLogTerm;

    private volatile Snapshot snapshot;


    private final int MAX_LOG_BEFORE_TAKE_SNAPSHOT = 10000;
    private AtomicInteger appliedLogInMemoryCounter = new AtomicInteger(0);

    /**
     * 日志的开头，因为有些可能已经被压缩了
     */
    private volatile int start;

    private ReentrantLock LOG_LOCK = new ReentrantLock(true);

    public LogServiceImpl(StateMachine stateMachine, StorageType storageType) {
        Preconditions.checkNotNull(stateMachine);
        Preconditions.checkNotNull(storageType);
        switch (storageType) {
            case FILE:
                this.storageService = FileStorageService.create("/Users/zhuyichen/tmp/raft", "raft.log");
                break;
            case MEMORY:
                this.storageService = MemoryStorageService.create();
                break;
            default:
                throw new IllegalStateException();
        }
        this.start = 0;
        this.stateMachine = stateMachine;
        /*
          提交一个DUMMY的LogEntry
         */
        this.stateMachine.apply(LogConstants.getInitialLogEntry().getCommand());
        this.storageService.append(LogConstants.getInitialLogEntry().toStorageEntry());
        this.lastCommitLogIndex = 0L;
        this.lastCommitLogTerm = 0L;
        this.lastAppliedLogIndex = 0L;
        this.lastAppliedLogTerm = 0L;

    }

    @Override
    @Nullable
    public LogEntry get(long index) {
        Preconditions.checkState(index >= 0, String.format("index=%d", index));
        //如果日志已经被压缩
        if (index < start) {
            log.error("Log Has Been Compact, start={}, requestIndex={}", start, index);
            throw new HasCompactException(String.format("start=%d, index=%d", start, index));
        }
        //如果还未有这个Index，返回空
        if (index > start + storageService.getLen()) {
            throw new IndexOutOfBoundsException();
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
        log.info("get logEntry, start={}, end={}", start, end);
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
        Preconditions.checkState(index >= 0);
        if (index > start + storageService.getLen()) {
            throw new IndexOutOfBoundsException();
        }
        return start > index;
    }

    @Override
    public boolean set(long index, LogEntry entry) {
        Preconditions.checkNotNull(entry);
        if (index < start) {
            throw new HasCompactException(String.format("start=%d, index=%d", start, index));
        }
        return storageService.set((int) (index - start), entry.toStorageEntry());
    }

    @Override
    public void commit(long index) {
        if (index == this.lastCommitLogIndex) {
            return;
        }
        LogEntry logEntry = get(index);
        if (Objects.nonNull(logEntry)) {
            createSnapShotIfNecessary();
            this.stateMachine.apply(logEntry.getCommand());
        }
        this.lastCommitLogIndex = index;
    }

    @Override
    public int appendLog(LogEntry logEntry) {
        return appendLog(Collections.singletonList(logEntry));
    }

    @Override
    public int appendLog(List<LogEntry> entries) {
        Preconditions.checkNotNull(entries);
        if (entries.isEmpty()) {
            return start;
        }
        try {
            LOG_LOCK.lock();
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
        LogEntry logEntry = storageService.get(storageService.getLen() - 1).toLogEntry();
        return logEntry.getTerm();
    }

    @Override
    public long getLastLogIndex() {
        return storageService.getLen() - 1 + start;
    }

    // 日志比较的原则是，如果本地的最后一条log entry的term更大，则term大的更新，如果term一样大，则log index更大的更新
    @Override
    public boolean isNewerThanSelf(long lastLogTerm, long lastLogIndex) {
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
            createSnapshot();
        }
    }

    @Override
    public void createSnapshot() {
        try {
            LOG_LOCK.lock();
            byte[] snapshotValues = stateMachine.takeSnapShot();
            Long lastCommitLogIndex = getLastCommitLogIndex();
            Long lastCommitLogTerm = getLastCommitLogTerm();
            Snapshot snapshot = new Snapshot();
            snapshot.setData(snapshotValues);
            snapshot.setLastLogIndex(lastCommitLogIndex);
            snapshot.setLastLogTerm(lastCommitLogTerm);
            this.snapshot = snapshot;
            this.storageService.discard((int) (lastCommitLogIndex - start));
            this.start = Math.toIntExact(lastCommitLogIndex);
        } finally {
            LOG_LOCK.unlock();
        }
    }

    @Override
    public boolean installSnapshot(Snapshot snapshot) {
        Preconditions.checkNotNull(snapshot);
        try {
            LOG_LOCK.lock();
            stateMachine.fromSnapShot(snapshot.getData());
            this.lastCommitLogIndex = snapshot.getLastLogIndex();
            this.lastAppliedLogIndex = snapshot.getLastLogIndex();
            this.lastAppliedLogTerm = snapshot.getLastLogTerm();
            this.lastAppliedLogIndex = snapshot.getLastLogTerm();
        } finally {
            LOG_LOCK.unlock();
        }
        return true;
    }

    @Override
    public void execInLock(Runnable action) {
        try {
            LOG_LOCK.lock();
            action.run();
        } finally {
            LOG_LOCK.unlock();
        }
    }

    @Override
    public StateMachine getStateMachine() {
        return stateMachine;
    }
}
