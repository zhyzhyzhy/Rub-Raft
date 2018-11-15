package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.node.NodeId;

public class VoteRequest {

    private Long term;

    private NodeId candidateId;

    private Long lastLogIndex;

    private Long lastLogTerm;

    public VoteRequest() {
    }

    public VoteRequest(Long term, NodeId candidateId, Long lastLogIndex, Long lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public Long getTerm() {
        return term;
    }

    public void setTerm(Long term) {
        this.term = term;
    }

    public NodeId getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(NodeId candidateId) {
        this.candidateId = candidateId;
    }

    public Long getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(Long lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public Long getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(Long lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }
}
