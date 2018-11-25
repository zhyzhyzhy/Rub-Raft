package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.storage.LogEntry;

import java.util.List;

public class ReplicatedLogRequest {

    private Long term;

    private NodeId leaderId;

    private Long prevLogIndex;

    private Long prevLogTerm;

    private List<LogEntry> entries;

    private Integer leaderCommit;

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

    public Integer getLeaderCommit() {
        return leaderCommit;
    }

    public void setLeaderCommit(Integer leaderCommit) {
        this.leaderCommit = leaderCommit;
    }
}
