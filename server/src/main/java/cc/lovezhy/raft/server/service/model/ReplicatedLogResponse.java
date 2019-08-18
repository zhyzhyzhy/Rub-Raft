package cc.lovezhy.raft.server.service.model;

public class ReplicatedLogResponse {

    private Boolean success;

    private Long term;

    private Long lastCommitIndex;

    public ReplicatedLogResponse() {
    }

    public ReplicatedLogResponse(Long term, Boolean success, Long lastCommitIndex) {
        this.term = term;
        this.success = success;
        this.lastCommitIndex = lastCommitIndex;
    }

    public Long getLastCommitIndex() {
        return lastCommitIndex;
    }

    public void setLastCommitIndex(Long lastCommitIndex) {
        this.lastCommitIndex = lastCommitIndex;
    }

    public Long getTerm() {
        return term;
    }

    public void setTerm(Long term) {
        this.term = term;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
