package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.StateMachine;
import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.storage.StorageEntry;
import cc.lovezhy.raft.server.storage.StorageService;
import cc.lovezhy.raft.server.storage.StorageServiceImpl;
import cc.lovezhy.raft.server.utils.kryo.KryoUtils;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;

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

    public LogServiceImpl(StateMachine stateMachine) {
        storageService = new StorageServiceImpl();
        start = 0L;
        commitIndex = NO_LOG;
        lastApplied = NO_LOG;
        this.stateMachine = stateMachine;
    }


    @Override
    @Nullable
    public LogEntry get(Long index) throws HasCompactException {
        //如果日志已经被压缩
        if (index < start) {
            throw new HasCompactException();
        }
        //如果还未有这个Index，返回空
        if (index > start + storageService.getLen()) {
            return null;
        }
        StorageEntry storageEntry = storageService.get(index - start);
        return KryoUtils.deserializeLogEntry(storageEntry.getValues());
    }

    @Override
    public boolean set(Long index, LogEntry entry) throws HasCompactException {
        Preconditions.checkNotNull(entry);
        if (index < start) {
            throw new HasCompactException();
        }
        StorageEntry storageEntry = new StorageEntry();
        byte[] values = KryoUtils.serializeLogEntry(entry);
        storageEntry.setSize(values.length);
        storageEntry.setValues(values);
        return storageService.set(index - start, storageEntry);
    }

    @Override
    public boolean commit(Long index) {
        return false;
    }

    @Override
    public void appendLog(ReplicatedLogRequest replicatedLogRequest) {

    }

    @Override
    public Long getLastCommitLogTerm() {
        if (commitIndex.equals(NO_LOG)) {
            return NO_LOG;
        }
        StorageEntry storageEntry = storageService.get(commitIndex - start);
        LogEntry logEntry = KryoUtils.deserializeLogEntry(storageEntry.getValues());
        return logEntry.getTerm();
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
    public Long getLastLogTerm() {
        if (storageService.getLen() == 0) {
            return NO_LOG;
        }
        StorageEntry storageEntry = storageService.get(storageService.getLen() - 1);
        LogEntry logEntry = KryoUtils.deserializeLogEntry(storageEntry.getValues());
        return logEntry.getTerm();
    }

    // 日志比较的原则是，如果本地的最后一条log entry的term更大，则term大的更新，如果term一样大，则log index更大的更新
    @Override
    public boolean isNewerThanSelf(Long lastLogTerm, Long lastLogIndex) {
        Preconditions.checkNotNull(lastLogTerm);
        Preconditions.checkNotNull(lastLogIndex);
        if (lastLogTerm > getLastLogTerm()) {
            return true;
        }
        if (lastLogTerm.equals(getLastLogTerm()) && lastLogIndex >= getLastLogIndex()) {
            return true;
        }
        return false;
    }

    @Override
    public SnapShot compactLog() {
        stateMachine.takeSnapShot();
        return new SnapShot();
    }

    @Override
    public boolean applyLogRequest(ReplicatedLogRequest replicatedLogRequest) {
        return false;
    }
}
