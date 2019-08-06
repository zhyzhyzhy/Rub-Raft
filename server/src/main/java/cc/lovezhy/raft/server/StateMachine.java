package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.log.DefaultCommand;

public interface StateMachine {

    byte[] getValue(String key);

    boolean apply(DefaultCommand command);

    byte[] takeSnapShot();

    void fromSnapShot(byte[] bytes);
}
