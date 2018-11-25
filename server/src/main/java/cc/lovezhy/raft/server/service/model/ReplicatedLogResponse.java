package cc.lovezhy.raft.server.service.model;

public class ReplicatedLogResponse {

    private Integer term;

    private Boolean success;

    public ReplicatedLogResponse() {
    }

    public ReplicatedLogResponse(Integer term, Boolean success) {
        this.term = term;
        this.success = success;
    }

    public Integer getTerm() {
        return term;
    }

    public void setTerm(Integer term) {
        this.term = term;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
