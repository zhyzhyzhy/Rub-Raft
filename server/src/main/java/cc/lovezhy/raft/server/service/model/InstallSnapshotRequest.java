package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.log.LogEntry;
import cc.lovezhy.raft.server.log.Snapshot;
import cc.lovezhy.raft.server.node.NodeId;

public class InstallSnapshotRequest {
    private Snapshot snapshot;
    private Long term;
    private NodeId leaderId;
    /**
     * 保留最后一个CommitIndex的LogEntry，防止进行PrevIndex的发现找不到
     */
    private LogEntry logEntry;

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Long getTerm() {
        return term;
    }

    public void setTerm(Long term) {
        this.term = term;
    }

    public NodeId getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(NodeId leaderId) {
        this.leaderId = leaderId;
    }

    public LogEntry getLogEntry() {
        return logEntry;
    }

    public void setLogEntry(LogEntry logEntry) {
        this.logEntry = logEntry;
    }
}
