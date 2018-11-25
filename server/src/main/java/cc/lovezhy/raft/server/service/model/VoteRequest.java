package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.node.NodeId;

public class VoteRequest {

    private Integer term;

    private NodeId candidateId;

    private Integer lastLogIndex;

    private Integer lastLogTerm;


    public VoteRequest() {
    }

    public Integer getTerm() {
        return term;
    }

    public void setTerm(Integer term) {
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

    public Integer getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(Integer lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }
}
