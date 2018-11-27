package cc.lovezhy.raft.server.log;

import com.google.common.base.MoreObjects;

public class Snapshot {
    //最后一条日志的索引号
    private Long lastLogIndex;

    //最后一条日志的任期
    private Long lastLogTerm;

    //快照的数据
    private byte[] data;

    public Long getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(Long lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public Long getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(Long lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("lastLogIndex", lastLogIndex)
                .add("lastLogTerm", lastLogTerm)
                .toString();
    }
}
