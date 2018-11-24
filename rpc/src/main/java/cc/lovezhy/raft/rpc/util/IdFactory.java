package cc.lovezhy.raft.rpc.util;


import java.util.UUID;

public class IdFactory {
    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
