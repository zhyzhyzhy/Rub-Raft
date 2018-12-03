package cc.lovezhy.raft.server.kryo;

import cc.lovezhy.raft.server.log.LogConstants;
import cc.lovezhy.raft.server.log.LogEntry;
import cc.lovezhy.raft.server.storage.StorageEntry;

public class KryoTest {
    public static void main(String[] args) {
        for (int i = 0; i < 100000000; i++) {
            LogEntry initialLogEntry = LogConstants.INITIAL_LOG_ENTRY;
            StorageEntry storageEntry = initialLogEntry.toStorageEntry();
            storageEntry.toLogEntry();
        }
    }
}
