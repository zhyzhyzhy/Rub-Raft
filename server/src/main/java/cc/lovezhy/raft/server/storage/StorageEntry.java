package cc.lovezhy.raft.server.storage;

import cc.lovezhy.raft.server.log.LogEntry;
import cc.lovezhy.raft.server.utils.KryoUtils;
import com.google.common.base.Preconditions;

public class StorageEntry {

    public StorageEntry(byte[] values) {
        this.values = values;
    }
    /**
     * 实际的数据
     */
    private volatile byte[] values;

    public LogEntry toLogEntry() {
        Preconditions.checkNotNull(values);
        return KryoUtils.deserializeLogEntry(values);
    }
}
