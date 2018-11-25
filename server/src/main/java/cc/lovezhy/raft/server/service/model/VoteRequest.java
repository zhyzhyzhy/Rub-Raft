package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.node.NodeId;

public class VoteRequest {

    private Long term;

    private NodeId candidateId;

    private Integer lastLogIndex;

    private Long lastLogTerm;


    public VoteRequest() {
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

    public Integer getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(Integer lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public Long getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(Long lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }
}
