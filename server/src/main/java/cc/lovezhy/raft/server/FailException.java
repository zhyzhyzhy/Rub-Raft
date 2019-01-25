package cc.lovezhy.raft.server;

public class FailException extends RuntimeException {

    public FailException() {
    }

    public FailException(String message) {
        super(message);
    }
}
