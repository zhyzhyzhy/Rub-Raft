package cc.lovezhy.raft.server.node;

public class NodeId {

    public static NodeId create(Integer peerId) {
        return new NodeId(peerId);
    }

    private Integer peerId;

    private NodeId(Integer peerId) {
        this.peerId = peerId;
    }

    public Integer getPeerId() {
        return peerId;
    }
}
