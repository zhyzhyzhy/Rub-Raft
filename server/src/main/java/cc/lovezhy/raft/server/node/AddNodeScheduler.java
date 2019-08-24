package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.server.log.LogEntry;
import cc.lovezhy.raft.server.log.LogService;
import cc.lovezhy.raft.server.log.Snapshot;
import cc.lovezhy.raft.server.service.model.InstallSnapshotRequest;
import cc.lovezhy.raft.server.service.model.InstallSnapshotResponse;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.service.model.ReplicatedLogResponse;
import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.SettableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


public class AddNodeScheduler {

    private final Logger log = LoggerFactory.getLogger(AddNodeScheduler.class);

    private PeerRaftNode peerRaftNode;
    private PeerNodeStateMachine peerNodeStateMachine;
    private NodeId leaderNodeId;
    private Long term;
    private LogService logService;

    public static AddNodeScheduler create(NodeId leaderNodeId, NodeId nodeId, EndPoint endPoint, Long term, LogService logService) {
        AddNodeScheduler addNodeScheduler = new AddNodeScheduler(leaderNodeId, nodeId, endPoint);
        addNodeScheduler.term = term;
        addNodeScheduler.logService = logService;
        return addNodeScheduler;
    }

    private AddNodeScheduler(NodeId leaderNodeId, NodeId nodeId, EndPoint endPoint) {
        this.leaderNodeId = leaderNodeId;
        this.peerRaftNode = new PeerRaftNode(nodeId, endPoint);
        this.peerNodeStateMachine = PeerNodeStateMachine.create(1L);
        this.peerRaftNode.connect(leaderNodeId);
    }


    public PeerRaftNode getPeerRaftNode() {
        return peerRaftNode;
    }

    public PeerNodeStateMachine getPeerNodeStateMachine() {
        return peerNodeStateMachine;
    }

    private AtomicLong retryTimes = new AtomicLong(10);

    public SettableFuture<Boolean> startSyncLog() {
        SettableFuture<Boolean> res = SettableFuture.create();
        syncLog(res);
        return res;
    }

    private void syncLog(SettableFuture<Boolean> syncFuture) {
        SettableFuture<Boolean> requestAppendLogFuture = SettableFuture.create();
        peerNodeStateMachine.appendFirst(prepareAppendLog(peerRaftNode, peerNodeStateMachine, requestAppendLogFuture));
        requestAppendLogFuture.addListener(() -> {
            if (!hasSyncDone()) {
                retryTimes.decrementAndGet();
                if (retryTimes.get() == 0) {
                    syncFuture.set(false);
                } else {
                    syncLog(syncFuture);
                }
            } else {
                syncFuture.set(true);
            }
        }, RpcExecutors.commonExecutor());
    }

    private boolean hasSyncDone() {
        return peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.NORMAL);
    }



    private Runnable prepareAppendLog(PeerRaftNode peerRaftNode, PeerNodeStateMachine peerNodeStateMachine, SettableFuture<Boolean> appendLogResult) {
        return () -> {
            try {
                log.info("prepareAppendLog, to {}", peerRaftNode.getNodeId().getPeerId());
                long currentLastLogIndex = logService.getLastLogIndex();
                long currentLastCommitLogIndex = logService.getLastCommitLogIndex();
                long preLogIndex = peerNodeStateMachine.getNextIndex() - 1;

                ReplicatedLogRequest replicatedLogRequest = new ReplicatedLogRequest();
                replicatedLogRequest.setTerm(term);
                replicatedLogRequest.setLeaderId(leaderNodeId);
                replicatedLogRequest.setLeaderCommit(currentLastCommitLogIndex);
                if (logService.hasInSnapshot(preLogIndex)) {
                    if (!peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT)) {
                        Runnable runnable = prepareInstallSnapshot(peerRaftNode, peerNodeStateMachine);
                        peerNodeStateMachine.appendFirst(runnable);
                    }
                    appendLogResult.set(false);
                    return;
                }
                replicatedLogRequest.setPrevLogIndex(preLogIndex);
                replicatedLogRequest.setPrevLogTerm(logService.get(preLogIndex).getTerm());
                List<LogEntry> logEntries = logService.get(peerNodeStateMachine.getNextIndex(), currentLastLogIndex);
                replicatedLogRequest.setEntries(logEntries);
                //同步方法
                log.info("send to {} replicatedLogRequest={}", JSON.toJSONString(peerRaftNode), JSON.toJSONString(replicatedLogRequest));
                ReplicatedLogResponse replicatedLogResponse = peerRaftNode.getRaftService().requestAppendLog(replicatedLogRequest);
                log.info("receive from {} replicatedLogResponse={}", JSON.toJSONString(peerRaftNode), JSON.toJSONString(replicatedLogResponse));

                if (replicatedLogResponse.getSuccess()) {
                    peerNodeStateMachine.setNextIndex(currentLastLogIndex + 1);
                    peerNodeStateMachine.setMatchIndex(currentLastLogIndex);
                    if (!peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.NORMAL)) {
                        peerNodeStateMachine.setNodeStatus(PeerNodeStatus.NORMAL);
                    }
                    if (peerNodeStateMachine.needSendAppendLogImmediately() || peerNodeStateMachine.getMatchIndex() < logService.getLastLogIndex()) {
                        Runnable runnable = prepareAppendLog(peerRaftNode, peerNodeStateMachine, SettableFuture.create());
                        peerNodeStateMachine.appendFirst(runnable);
                    }
                } else {
                    if (!peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT)) {
                        long nextPreLogIndex = replicatedLogResponse.getLastCommitIndex();
                        //如果已经是在Snapshot中
                        if (logService.hasInSnapshot(nextPreLogIndex)) {
                            Runnable runnable = prepareInstallSnapshot(peerRaftNode, peerNodeStateMachine);
                            peerNodeStateMachine.appendFirst(runnable);
                        } else {
                            peerNodeStateMachine.setNextIndex(nextPreLogIndex + 1);
                            Runnable runnable = prepareAppendLog(peerRaftNode, peerNodeStateMachine, SettableFuture.create());
                            peerNodeStateMachine.appendFirst(runnable);
                        }
                    }
                }
                appendLogResult.set(replicatedLogResponse.getSuccess());
            } catch (Exception e) {
                appendLogResult.set(Boolean.FALSE);
                log.error("fail appendLog to {}", JSON.toJSONString(peerRaftNode));
                log.error("isConnectAlive={}", peerRaftNode.isConnectAlive());
                log.error(e.getMessage(), e);
            }
        };
    }

    private Runnable prepareInstallSnapshot(PeerRaftNode peerRaftNode, PeerNodeStateMachine peerNodeStateMachine) {
        return () -> {
            try {
                if (peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT) || peerNodeStateMachine.getNodeStatus().equals(PeerNodeStatus.PROBE)) {
                    return;
                }
                peerNodeStateMachine.setNodeStatus(PeerNodeStatus.INSTALLSNAPSHOT);
                Snapshot snapShot = logService.getSnapShot();
                InstallSnapshotRequest installSnapShotRequest = new InstallSnapshotRequest();
                installSnapShotRequest.setLeaderId(leaderNodeId);
                installSnapShotRequest.setTerm(term);
                installSnapShotRequest.setSnapshot(snapShot);
                installSnapShotRequest.setLogEntry(logService.get(snapShot.getLastLogIndex()));
                InstallSnapshotResponse installSnapshotResponse = peerRaftNode.getRaftService().requestInstallSnapShot(installSnapShotRequest);
                if (installSnapshotResponse.getSuccess()) {
                    peerNodeStateMachine.setNodeStatus(PeerNodeStatus.PROBE);
                    peerNodeStateMachine.setNextIndex(snapShot.getLastLogIndex() + 1);
                    peerNodeStateMachine.setMatchIndex(snapShot.getLastLogIndex());
                } else {
                    throw new IllegalStateException();
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        };
    }



}
