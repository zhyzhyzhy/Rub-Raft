package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.StateMachine;
import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.storage.*;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LogServiceImpl implements LogService {

    private StorageService storageService;
    private StateMachine stateMachine;
    private volatile Long commitIndex;
    private volatile Long lastApplied;


    public static final Long NO_LOG = -1L;

    /**
     * 日志的开头，因为有些可能已经被压缩了
     */
    private volatile Long start;

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
        this.commitIndex = NO_LOG;
        this.lastApplied = NO_LOG;
        this.stateMachine = stateMachine;
    }


    @Override
    @Nullable
    public LogEntry get(long index) throws HasCompactException, IOException {
        //如果日志已经被压缩
        if (index < start) {
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
    public List<LogEntry> get(long start, long end) {
        if (start == end) {
            return Collections.emptyList();
        }
        //TODO
        return Collections.emptyList();
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
        this.stateMachine.apply(logEntry.getCommand());
        return true;
    }

    @Override
    public void appendLog(ReplicatedLogRequest replicatedLogRequest) throws IOException {
        Preconditions.checkNotNull(replicatedLogRequest);
        for (LogEntry entry : replicatedLogRequest.getEntries()) {
            storageService.append(entry.toStorageEntry());
        }
    }

    @Override
    public Long getLastCommitLogTerm() throws IOException {
        if (commitIndex.equals(NO_LOG)) {
            return NO_LOG;
        }
        StorageEntry storageEntry = storageService.get((int) (commitIndex - start));
        Preconditions.checkNotNull(storageEntry);
        return storageEntry.toLogEntry().getTerm();
    }

    @Override
    public Long getLastCommitLogIndex() {
        if (commitIndex.equals(NO_LOG)) {
            return NO_LOG;
        }
        return commitIndex;
    }

    @Override
    public Long getLastLogIndex() {
        if (storageService.getLen() == 0) {
            return NO_LOG;
        }
        return storageService.getLen() + start;
    }

    @Override
    public Long getLastLogTerm() throws IOException {
        if (storageService.getLen() == 0) {
            return NO_LOG;
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
    public Snapshot createSnapshot() {
        stateMachine.takeSnapShot();
        return new Snapshot();
    }
}
