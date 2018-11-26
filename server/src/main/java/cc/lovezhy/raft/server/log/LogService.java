package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.log.exception.HasCompactException;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;

public interface LogService {

    LogEntry get(Long index) throws HasCompactException;

    boolean set(Long index, LogEntry entry) throws HasCompactException;

    boolean commit(Long index);

    void appendLog(ReplicatedLogRequest replicatedLogRequest);

    Long getLastCommitLogTerm();

    Long getLastCommitLogIndex();

    Long getLastLogIndex();

    Long getLastLogTerm();

    boolean isNewerThanSelf(Long lastLogTerm, Long lastLogIndex);

    SnapShot compactLog();

    boolean applyLogRequest(ReplicatedLogRequest replicatedLogRequest);
}
