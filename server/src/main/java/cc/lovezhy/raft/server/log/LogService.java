package cc.lovezhy.raft.server.log;

import java.util.List;

public interface LogService {

    LogEntry get(long index);

    List<LogEntry> get(long start, long end);

    boolean hasInSnapshot(long index);

    boolean set(long index, LogEntry entry);

    boolean commit(long index);

    int appendLog(LogEntry logEntry);
    int appendLog(List<LogEntry> entries);

    long getLastCommitLogTerm();
    long getLastCommitLogIndex();

    long getLastLogTerm();
    long getLastLogIndex();

    boolean isNewerThanSelf(long lastLogTerm, long lastLogIndex);

    Snapshot getSnapShot();

    void createSnapshot();

    boolean installSnapshot(Snapshot snapshot);

    void execInLock(Runnable action);
}
