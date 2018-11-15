package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.storage.LogEntry;

public interface StateMachine {
    void apply(LogEntry entry);
}
