package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.StateMachine;
import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.log.exception.NoSuchLogException;
import cc.lovezhy.raft.server.storage.*;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 避免空指针问题，全部用NO_LOG表示没有Log
 */
public class LogServiceImpl implements LogService {

    private static final Logger log = LoggerFactory.getLogger(LogServiceImpl.class);
    private StorageService storageService;
    private StateMachine stateMachine;

    private volatile Long lastCommitLogIndex;
    private volatile Long lastCommitLogTerm;

    private volatile Long lastAppliedLogIndex;
    private volatile Long lastAppliedLogTerm;

    private volatile Snapshot snapshot;

    /**
     * 日志的开头，因为有些可能已经被压缩了
     */
    private volatile Long start;

    private ReentrantLock LOG_LOCK = new ReentrantLock(true);

    public LogServiceImpl(StateMachine stateMachine, StorageType storageType) throws FileNotFoundException {
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
        this.start = 0L;
        this.lastCommitLogIndex = LogConstants.ON_LOG;
        this.lastCommitLogTerm = 0L;
        this.lastAppliedLogIndex = LogConstants.ON_LOG;
        this.lastAppliedLogTerm = 0L;
        this.stateMachine = stateMachine;
    }


    @Override
    @Nullable
    public LogEntry get(long index) throws IOException {
        Preconditions.checkState(index >= 0);
        //如果日志已经被压缩
        if (index < start) {
            log.error("Log Has Been Compact, start={}, requestIndex={}", start, index);
            throw new HasCompactException();
        }
        //如果还未有这个Index，返回空
        if (index >= start + storageService.getLen()) {
            throw new NoSuchLogException();
        }
        StorageEntry storageEntry = storageService.get((int) (index - start));
        Preconditions.checkNotNull(storageEntry);
        return storageEntry.toLogEntry();
    }

    /**
     * [start, end]
     */
    @Override
    public List<LogEntry> get(long start, long end) throws IOException, HasCompactException {
        if (start > end) {
            return Collections.emptyList();
        }
        if (start < this.start) {
            throw new HasCompactException();
        }
        if (end >= this.start + storageService.getLen()) {
            throw new IndexOutOfBoundsException();
        }
        List<StorageEntry> storageEntries = storageService.range((int) ((int) start - this.start), (int) ((int) end - this.start));
        return storageEntries.stream().map(StorageEntry::toLogEntry).collect(Collectors.toList());
    }

    @Override
    public boolean hasInSnapshot(long index) {
        Preconditions.checkState(index >= 0);
        if (index >= start + storageService.getLen()) {
            throw new NoSuchLogException();
        }
        return start > index;
    }

    @Override
    public boolean set(long index, LogEntry entry) throws IOException {
        Preconditions.checkNotNull(entry);
        if (index < start) {
            throw new HasCompactException();
        }
        return storageService.set((int) (index - start), entry.toStorageEntry());
    }

    @Override
    public boolean commit(long index) throws IOException {
        LogEntry logEntry = get(index);
        if (Objects.nonNull(logEntry)) {
            this.stateMachine.apply(logEntry.getCommand());
        }
        this.lastCommitLogIndex = index;
        return true;
    }

    @Override
    public void appendLog(List<LogEntry> entries) throws IOException {
        Preconditions.checkNotNull(entries);
        if (entries.isEmpty()) {
            return;
        }
        try {
            LOG_LOCK.lock();
            for (LogEntry entry : entries) {
                storageService.append(entry.toStorageEntry());
            }
        } finally {
            LOG_LOCK.unlock();
        }

    }

    @Override
    public Long getLastCommitLogTerm() throws IOException {
        if (this.lastCommitLogIndex.equals(LogConstants.ON_LOG)) {
            return 0L;
        }
        StorageEntry storageEntry = storageService.get((int) (this.lastCommitLogIndex - start));
        Preconditions.checkNotNull(storageEntry);
        return storageEntry.toLogEntry().getTerm();
    }

    @Override
    public Long getLastCommitLogIndex() {
        return lastCommitLogIndex;
    }

    @Override
    public Long getLastLogIndex() {
        if (storageService.getLen() == 0) {
            return LogConstants.ON_LOG;
        }
        return storageService.getLen() + start;
    }

    @Override
    public Long getLastLogTerm() throws IOException {
        if (storageService.getLen() == 0) {
            return LogConstants.ON_LOG;
        }
        StorageEntry storageEntry = storageService.get((int) (storageService.getLen() - 1));
        Preconditions.checkNotNull(storageEntry);
        return storageEntry.toLogEntry().getTerm();
    }

    // 日志比较的原则是，如果本地的最后一条log entry的term更大，则term大的更新，如果term一样大，则log index更大的更新
    @Override
    public boolean isNewerThanSelf(long lastLogTerm, long lastLogIndex) throws IOException {
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

    @Override
    public void createSnapshot() throws IOException {
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
        } finally {
            LOG_LOCK.unlock();
        }
    }

    @Override
    public boolean installSnapShot(Snapshot snapshot) {
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
}
