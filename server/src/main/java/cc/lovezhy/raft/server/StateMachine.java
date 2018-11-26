package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.log.Command;

public interface StateMachine {
    boolean apply(Command command);
    byte[] takeSnapShot();
}
