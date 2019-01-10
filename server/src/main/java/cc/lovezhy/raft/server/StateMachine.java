package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.log.DefaultCommand;

public interface StateMachine {

    byte[] getValue(String key);

    boolean apply(DefaultCommand command);

    byte[] takeSnapShot();

    boolean fromSnapShot(byte[] bytes);
}
