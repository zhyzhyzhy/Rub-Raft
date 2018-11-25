package cc.lovezhy.raft.server.node;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeId nodeId = (NodeId) o;
        return Objects.equal(peerId, nodeId.peerId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(peerId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("peerId", peerId)
                .toString();
    }
}
