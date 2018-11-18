package cc.lovezhy.raft.server.node;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

@Immutable
public class NodeId {

    public static NodeId create(Integer peerId) {
        Preconditions.checkNotNull(peerId);
        return new NodeId(peerId);
    }

    private Integer peerId;

    private NodeId() {
    }

    private NodeId(Integer peerId) {
        this.peerId = peerId;
    }

    public Integer getPeerId() {
        return peerId;
    }
}
