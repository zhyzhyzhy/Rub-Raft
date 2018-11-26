package cc.lovezhy.raft.server.storage;

public class StorageEntry {
    /**
     * 在文件中的offset
     */
    private long offset;
    /**
     * bytes的大小
     */
    private long size;
    /**
     * 实际的数据
     */
    private byte[] values;

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public byte[] getValues() {
        return values;
    }

    public void setValues(byte[] values) {
        this.values = values;
    }
}
