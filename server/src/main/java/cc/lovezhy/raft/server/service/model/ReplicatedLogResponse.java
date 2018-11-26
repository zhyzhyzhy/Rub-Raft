package cc.lovezhy.raft.server.service.model;

public class ReplicatedLogResponse {

    private Boolean success;

    private Long term;

    public ReplicatedLogResponse() {
    }

    public ReplicatedLogResponse(Long term, Boolean success) {
        this.term = term;
        this.success = success;
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
