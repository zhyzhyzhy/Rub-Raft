package cc.lovezhy.raft.rpc;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

@Immutable
public class EndPoint {

    private String host;

    private int port;

    private EndPoint(String host, int port) {
        Preconditions.checkNotNull(host);
        this.host = host;
        this.port = port;
    }

    public static EndPoint create(String host, int port) {
        return new EndPoint(host, port);
    }

    public static EndPoint create(String host, String port) {
        return new EndPoint(host, Integer.parseInt(port));
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("host", host)
                .add("port", port)
                .toString();
    }
}
