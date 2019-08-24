package cc.lovezhy.raft.server.log;

public interface Command {
    default CommandType type() {
        if (this instanceof ClusterConfCommand) {
            return CommandType.CLUSTER_CONF;
        }
        return CommandType.DEFAULT;
    }
}
