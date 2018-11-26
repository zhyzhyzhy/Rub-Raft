package cc.lovezhy.raft.server.log;

public class SnapShot {
    //最后一条日志的索引号
    private Long lastLogIndex;

    //最后一条日志的任期
    private Long lastLogTerm;

    //快照的数据
    private byte[] data;
}
