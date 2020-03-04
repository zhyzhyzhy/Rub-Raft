package cc.lovezhy.raft.server.storage;

public class RaftRecord {
    private long index;
    private String record;

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public String getRecord() {
        return record;
    }

    public void setRecord(String record) {
        this.record = record;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RaftRecord{");
        sb.append("index=").append(index);
        sb.append(", record='").append(record).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
