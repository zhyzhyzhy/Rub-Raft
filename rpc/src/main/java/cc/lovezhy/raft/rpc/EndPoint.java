package cc.lovezhy.raft.rpc;

import com.google.common.base.MoreObjects;

public class EndPoint {

    private String host;

    private int port;

    private EndPoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static EndPoint create(String host, int port) {
        return new EndPoint(host, port);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("host", host)
                .add("port", port)
                .toString();
    }
}
