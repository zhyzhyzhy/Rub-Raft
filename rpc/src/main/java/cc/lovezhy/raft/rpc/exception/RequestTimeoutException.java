package cc.lovezhy.raft.rpc.exception;

public class RequestTimeoutException extends Exception {

    public RequestTimeoutException(String message) {
        super(message);
    }
}
