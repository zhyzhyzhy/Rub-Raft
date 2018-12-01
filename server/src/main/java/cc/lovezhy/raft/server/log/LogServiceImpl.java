package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.StateMachine;
import cc.lovezhy.raft.server.log.exception.HasCompactException;
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
    private volatile Long commitIndex;
    private volatile Long lastApplied;

    private Snapshot snapshot;

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
            case MEMORY:
                this.storageService = MemoryStorageService.create();
        }
        this.start = 0L;
        this.commitIndex = LogConstants.ON_LOG;
        this.lastApplied = LogConstants.ON_LOG;
        this.stateMachine = stateMachine;
    }


    @Override
    @Nullable
    public LogEntry get(long index) throws HasCompactException, IOException {
        //如果日志已经被压缩
        if (index < start) {
            log.error("Log Has Been Compact, start={}, requestIndex={}", start, index);
            throw new HasCompactException();
        }
        //如果还未有这个Index，返回空
        if (index > start + storageService.getLen()) {
            return null;
        }
        StorageEntry storageEntry = storageService.get((int) (index - start));
        Preconditions.checkNotNull(storageEntry);
        return storageEntry.toLogEntry();
    }

    @Override
    public List<LogEntry> get(long start, long end) throws IOException, HasCompactException {
        if (start >= end) {
            return Collections.emptyList();
        }
        if (start < this.start) {
            throw new HasCompactException();
        }
        if (end > this.start + storageService.getLen()) {
            throw new IndexOutOfBoundsException();
        }
        List<StorageEntry> storageEntries = storageService.range((int) ((int) start - this.start), (int) ((int) end - this.start));
        return storageEntries.stream().map(StorageEntry::toLogEntry).collect(Collectors.toList());
    }

    @Override
    public boolean hasInSnapshot(long index) {
        return start > index;
    }

    @Override
    public boolean set(long index, LogEntry entry) throws HasCompactException, IOException {
        Preconditions.checkNotNull(entry);
        if (index < start) {
            throw new HasCompactException();
        }
        return storageService.set((int) (index - start), entry.toStorageEntry());
    }

    @Override
    public boolean commit(long index) throws IOException, HasCompactException {
        LogEntry logEntry = get(index);
        if (Objects.nonNull(logEntry)) {
            this.stateMachine.apply(logEntry.getCommand());
        }
        this.commitIndex = index;
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
        if (commitIndex.equals(LogConstants.ON_LOG)) {
            return LogConstants.ON_LOG;
        }
        StorageEntry storageEntry = storageService.get((int) (commitIndex - start));
        Preconditions.checkNotNull(storageEntry);
        return storageEntry.toLogEntry().getTerm();
    }

    @Override
    public Long getLastCommitLogIndex() {
        if (commitIndex.equals(LogConstants.ON_LOG)) {
            return LogConstants.ON_LOG;
        }
        return commitIndex;
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
    public void createSnapShot() {

    }

    @Override
    public boolean installSnapShot(Snapshot snapshot) {
        return false;
    }
}
