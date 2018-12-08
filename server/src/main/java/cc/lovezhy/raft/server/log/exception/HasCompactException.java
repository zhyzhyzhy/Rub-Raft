package cc.lovezhy.raft.server.log.exception;

/**
 * log has been compact
 * indicate to send snapshot
 */
public class HasCompactException extends RuntimeException {
    public HasCompactException(String message) {
        super(message);
    }
}
