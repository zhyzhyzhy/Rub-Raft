package cc.lovezhy.raft.server.service.model;

public class VoteResponse {

    private Integer term;

    private Boolean voteGranted;

    public VoteResponse() {
    }

    public VoteResponse(Integer term, Boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public Integer getTerm() {
        return term;
    }

    public void setTerm(Integer term) {
        this.term = term;
    }

    public Boolean getVoteGranted() {
        return voteGranted;
    }

    public void setVoteGranted(Boolean voteGranted) {
        this.voteGranted = voteGranted;
    }
}
