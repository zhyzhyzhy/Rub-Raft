package cc.lovezhy.raft.rpc.exception;

public class RequestTimeoutException extends RuntimeException {

    public RequestTimeoutException(String message) {
        super(message);
    }
}
