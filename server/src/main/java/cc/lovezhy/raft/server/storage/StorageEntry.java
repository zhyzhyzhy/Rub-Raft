package cc.lovezhy.raft.server.storage;

import cc.lovezhy.raft.server.log.LogEntry;
import cc.lovezhy.raft.server.utils.kryo.KryoUtils;
import com.google.common.base.Preconditions;

public class StorageEntry {
    /**
     * 实际的数据
     */
    private byte[] values;

    public byte[] getValues() {
        return values;
    }

    public void setValues(byte[] values) {
        this.values = values;
    }

    public LogEntry toLogEntry() {
        Preconditions.checkNotNull(values);
        return KryoUtils.deserializeLogEntry(values);
    }
}
