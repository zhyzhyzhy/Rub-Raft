package cc.lovezhy.raft.server.node;

public enum NodeStatus {

    LEADER,

    FOLLOWER,

    PRE_CANDIDATE,

    CANDIDATE
}
