package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.storage.LogEntry;

import java.util.List;

public class ReplicatedLogRequest {

    private Integer term;

    private NodeId leaderId;

    private Integer prevLogIndex;

    private Integer prevLogTerm;

    private List<LogEntry> entries;

    private Integer leaderCommit;

    public Integer getTerm() {
        return term;
    }

    public void setTerm(Integer term) {
        this.term = term;
    }

    public NodeId getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(NodeId leaderId) {
        this.leaderId = leaderId;
    }

    public Integer getPrevLogIndex() {
        return prevLogIndex;
    }

    public void setPrevLogIndex(Integer prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public Integer getPrevLogTerm() {
        return prevLogTerm;
    }

    public void setPrevLogTerm(Integer prevLogTerm) {
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
