package cc.lovezhy.raft.server.storage;

public class RaftRecord {
    private long index;
    private byte[] record;

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public byte[] getRecord() {
        return record;
    }

    public void setRecord(byte[] record) {
        this.record = record;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RaftRecord{");
        sb.append("index=").append(index);
        sb.append(", record='").append(new String(record)).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
