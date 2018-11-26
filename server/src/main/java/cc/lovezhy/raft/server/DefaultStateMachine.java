package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.log.Command;

public class DefaultStateMachine implements StateMachine {
    @Override
    public boolean apply(Command command) {
        return false;
    }

    @Override
    public byte[] takeSnapShot() {
        return new byte[0];
    }
}
