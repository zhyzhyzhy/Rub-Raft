package cc.lovezhy.raft.rpc;

import java.util.Objects;

public class RpcServerOptions {

    static final EndPoint DEFAULT_ENDPOINT = EndPoint.create("localhost", 5283);

    private EndPoint startEndPoint;

    private volatile boolean isOnNet = true;


    public EndPoint getStartEndPoint() {
        if (Objects.isNull(startEndPoint)) {
            return DEFAULT_ENDPOINT;
        }
        return startEndPoint;
    }

    public void setStartEndPoint(EndPoint startEndPoint) {
        this.startEndPoint = startEndPoint;
    }

    public boolean isOnNet() {
        return isOnNet;
    }

    public void setOnNet(boolean onNet) {
        isOnNet = onNet;
    }
}
