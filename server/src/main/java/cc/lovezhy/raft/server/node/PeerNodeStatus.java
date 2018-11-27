package cc.lovezhy.raft.server.node;

public enum PeerNodeStatus {
    //正常情况
    NORMAL,

    //正在探索缺失的位置
    PROBE,

    //正在安装快照
    INSTALLSNAPSHOT
}
