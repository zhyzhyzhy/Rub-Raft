package cc.lovezhy.raft.server.storage;

import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import com.google.common.collect.Lists;

import java.util.List;

public class StorageService {
    private List<LogEntry> logs;

    private volatile int commitIndex;
    private volatile int lastApplied;

    public StorageService() {
        logs = Lists.newLinkedList();
        logs.add(new LogEntry(null, 0L));
        commitIndex = 0;
        lastApplied = 0;
    }

    public void appendLogs(ReplicatedLogRequest replicatedLogRequest) {

    }

    public Long getLastCommitLogTerm() {
        return logs.get(commitIndex).getTerm();
    }

    public int getLastCommitLogIndex() {
        return commitIndex;
    }

    public int getLastLogIndex() {
        return logs.size() - 1;
    }

    public Long getLastLogTerm() {
        return logs.get(logs.size() - 1).getTerm();
    }

    // 日志比较的原则是，如果本地的最后一条log entry的term更大，则term大的更新，如果term一样大，则log index更大的更新
    public boolean isNewerThanMe(int lastLogIndex, Long lastLogTerm) {
        if (lastLogTerm > getLastLogTerm()) {
            return true;
        }
        if (lastLogTerm.equals(getLastLogTerm()) && lastLogIndex >= getLastLogIndex()) {
            return true;
        }
        return false;
    }
}
