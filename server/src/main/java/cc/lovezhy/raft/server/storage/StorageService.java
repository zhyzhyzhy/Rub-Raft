package cc.lovezhy.raft.server.storage;

import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import com.google.common.collect.Lists;

import java.util.List;

public class StorageService {
    private List<LogEntry> logs;

    private volatile Integer commitIndex;
    private volatile Integer lastApplied;

    public StorageService() {
        logs = Lists.newLinkedList();
        logs.add(new LogEntry(null, 0));
        commitIndex = 0;
        lastApplied = 0;
    }

    public void appendLogs(ReplicatedLogRequest replicatedLogRequest) {

    }

    public Integer getLastCommitLogTerm() {
        return logs.get(commitIndex).getTerm();
    }

    public Integer getLastCommitLogIndex() {
        return commitIndex;
    }

    public Integer getLastLogIndex() {
        return logs.size() - 1;
    }

    public Integer getLastLogTerm() {
        return logs.get(logs.size() - 1).getTerm();
    }

    // 日志比较的原则是，如果本地的最后一条log entry的term更大，则term大的更新，如果term一样大，则log index更大的更新
    public boolean isNewerThanMe(int lastLogIndex, Integer lastLogTerm) {
        if (lastLogTerm > getLastLogTerm()) {
            return true;
        }
        if (lastLogTerm.equals(getLastLogTerm()) && lastLogIndex >= getLastLogIndex()) {
            return true;
        }
        return false;
    }
}
