package cc.lovezhy.raft.rpc.util;


import javax.annotation.concurrent.ThreadSafe;
import java.util.UUID;

@ThreadSafe
public class IdFactory {

    private IdFactory() {
    }

    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
