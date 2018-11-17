package cc.lovezhy.raft.server.storage;

import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import com.google.common.collect.Lists;

import java.util.List;

public class StorageService {
    private List<LogEntry> logs;

    private volatile Long commitIndex;
    private volatile Long lastApplied;

    public StorageService() {
        logs = Lists.newLinkedList();
        commitIndex = 0L;
        lastApplied = 0L;
    }

    public void appendLogs(ReplicatedLogRequest replicatedLogRequest) {
        List<LogEntry> entries = replicatedLogRequest.getEntries();
        if (entries == null || entries.size() == 0) {
            return;
        }
        commitIndex += entries.size();
        logs.addAll(entries);
    }

    //TODO int还是long
    public Long getLastCommitLogTerm() {
        return logs.get(commitIndex.intValue()).getTerm();
    }

    public List<LogEntry> getLogs() {
        return logs;
    }

    public void setLogs(List<LogEntry> logs) {
        this.logs = logs;
    }

    public Long getCommitIndex() {
        return commitIndex;
    }

    public void setCommitIndex(Long commitIndex) {
        this.commitIndex = commitIndex;
    }

    public Long getLastApplied() {
        return lastApplied;
    }

    public void setLastApplied(Long lastApplied) {
        this.lastApplied = lastApplied;
    }
}
