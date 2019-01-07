package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.log.LogEntry;
import com.google.common.base.MoreObjects;

import java.util.List;

public class ReplicatedLogRequest {

    private Long term;

    private NodeId leaderId;

    private Long prevLogIndex;

    private Long prevLogTerm;

    private List<LogEntry> entries;

    private Long leaderCommit;

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

    public Long getPrevLogIndex() {
        return prevLogIndex;
    }

    public void setPrevLogIndex(Long prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public Long getPrevLogTerm() {
        return prevLogTerm;
    }

    public void setPrevLogTerm(Long prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<LogEntry> entries) {
        this.entries = entries;
    }

    public Long getLeaderCommit() {
        return leaderCommit;
    }

    public void setLeaderCommit(Long leaderCommit) {
        this.leaderCommit = leaderCommit;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("term", term)
                .add("leaderId", leaderId)
                .add("prevLogIndex", prevLogIndex)
                .add("prevLogTerm", prevLogTerm)
                .add("entries", entries)
                .add("leaderCommit", leaderCommit)
                .toString();
    }
}
