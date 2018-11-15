package cc.lovezhy.raft.server.service.model;

public class VoteResponse {

    private Long term;

    private Boolean voteGranted;

    public VoteResponse() {
    }

    public VoteResponse(Long term, Boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public Long getTerm() {
        return term;
    }

    public void setTerm(Long term) {
        this.term = term;
    }

    public Boolean getVoteGranted() {
        return voteGranted;
    }

    public void setVoteGranted(Boolean voteGranted) {
        this.voteGranted = voteGranted;
    }
}
