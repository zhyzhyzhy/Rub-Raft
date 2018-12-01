package cc.lovezhy.raft.server.service.model;

import cc.lovezhy.raft.server.log.Snapshot;
import cc.lovezhy.raft.server.node.NodeId;

public class InstallSnapShotRequest {
    private Snapshot snapshot;
    private Long term;
    private NodeId leaderId;

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Long getTerm() {
        return term;
    }

    public void setTerm(Long term) {
        this.term = term;
    }

    public NodeId getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(NodeId leaderId) {
        this.leaderId = leaderId;
    }
}
