package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;

import java.io.IOException;
import java.util.List;

public interface LogService {

    LogEntry get(long index) throws HasCompactException, IOException;

    List<LogEntry> get(long start, long end);

    boolean hasInSnapshot(long index);

    boolean set(long index, LogEntry entry) throws HasCompactException, IOException;

    boolean commit(long index) throws IOException, HasCompactException;

    void appendLog(ReplicatedLogRequest replicatedLogRequest) throws IOException;

    Long getLastCommitLogTerm() throws IOException;

    Long getLastCommitLogIndex();

    Long getLastLogIndex();

    Long getLastLogTerm() throws IOException;

    boolean isNewerThanSelf(long lastLogTerm, long lastLogIndex) throws IOException;

    Snapshot createSnapshot();
}
