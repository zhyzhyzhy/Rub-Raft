package cc.lovezhy.raft.server.log;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface LogService {

    LogEntry get(long index) throws IOException;

    List<LogEntry> get(long start, long end) throws IOException;

    boolean hasInSnapshot(long index);

    boolean set(long index, LogEntry entry) throws IOException;

    boolean commit(long index) throws IOException;

    void appendLog(List<LogEntry> entries) throws IOException;

    LogEntry getLastCommitLog() throws IOException;

    LogEntry getLastLog() throws IOException;

    boolean isNewerThanSelf(long lastLogTerm, long lastLogIndex) throws IOException;

    Snapshot getSnapShot();

    void createSnapshot() throws IOException;

    boolean installSnapshot(Snapshot snapshot);

    void execInLock(Runnable action);
}
