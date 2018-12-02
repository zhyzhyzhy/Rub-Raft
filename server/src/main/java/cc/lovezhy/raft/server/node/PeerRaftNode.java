package cc.lovezhy.raft.server.node;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcClient;
import cc.lovezhy.raft.rpc.RpcClientOptions;
import cc.lovezhy.raft.rpc.RpcContext;
import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.server.log.LogService;
import cc.lovezhy.raft.server.log.Snapshot;
import cc.lovezhy.raft.server.service.RaftService;
import cc.lovezhy.raft.server.service.model.InstallSnapshotRequest;
import cc.lovezhy.raft.server.service.model.InstallSnapshotResponse;
import cc.lovezhy.raft.server.service.model.ReplicatedLogRequest;
import cc.lovezhy.raft.server.service.model.ReplicatedLogResponse;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class PeerRaftNode implements Closeable {

    private Logger log = LoggerFactory.getLogger(PeerRaftNode.class);

    private NodeId nodeId;

    private EndPoint endPoint;

    private Long nextIndex;

    private Long matchIndex;

    private RaftService raftService;

    private PeerNodeStatus nodeStatus;

    private RpcClient<RaftService> rpcClient;

    private RpcClientOptions rpcClientOptions;

    private Semaphore canSendMsg = new Semaphore(1, true);

    private Optional<Future> tickHeartBeatFuture = Optional.empty();

    /**
     * 上一次发送Tick的时间戳
     */
    private AtomicLong lastSendTickTime = new AtomicLong();

    public PeerRaftNode(NodeId nodeId, EndPoint endPoint) {
        Preconditions.checkNotNull(nodeId);
        Preconditions.checkNotNull(endPoint);
        this.nodeId = nodeId;
        this.endPoint = endPoint;
        nodeStatus = PeerNodeStatus.NORMAL;
        RpcClientOptions rpcClientOptions = new RpcClientOptions();
        rpcClientOptions.defineMethodRequestType("requestPreVote", RpcRequestType.ASYNC);
        rpcClientOptions.defineMethodRequestType("requestVote", RpcRequestType.ASYNC);
        rpcClientOptions.defineMethodRequestType("requestAppendLog", RpcRequestType.ASYNC);
        rpcClientOptions.defineMethodRequestType("requestInstallSnapShot", RpcRequestType.ASYNC);
        this.rpcClientOptions = rpcClientOptions;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public EndPoint getEndPoint() {
        return endPoint;
    }

    public Long getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(Long nextIndex) {
        this.nextIndex = nextIndex;
    }

    public Long getMatchIndex() {
        return matchIndex;
    }

    public void setMatchIndex(Long matchIndex) {
        this.matchIndex = matchIndex;
    }

    public RaftService getRaftService() {
        return raftService;
    }

    public PeerNodeStatus getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(PeerNodeStatus nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public void connect() {
        if (Objects.isNull(rpcClient) || (Objects.nonNull(rpcClient.isConnectAlive()) && !rpcClient.isConnectAlive())) {
            this.rpcClient = RpcClient.create(RaftService.class, endPoint, rpcClientOptions);
            this.raftService = rpcClient.getInstance();
        } else {
            this.rpcClient.connect();
        }
    }

    public void tickHeartBeat(ReplicatedLogRequest replicatedLogRequest, LogService logService, long currentLastLogIndex) {
        try {
            acquireSendMsg();
            long currentTerm = replicatedLogRequest.getTerm();
            getRaftService().requestAppendLog(replicatedLogRequest);
            log.debug("send heartbeat to={}", getNodeId());
            SettableFuture<ReplicatedLogResponse> responseSettableFuture = RpcContext.getContextFuture();
            Futures.addCallback(responseSettableFuture, new FutureCallback<ReplicatedLogResponse>() {
                @Override
                public void onSuccess(@Nullable ReplicatedLogResponse result) {
                    /*
                     * 可能发生
                     * 成为Leader后直接被网络分区了
                     * 然后又好了，此时另外一个分区已经有Leader且Term比自己大
                     */
                    if (result.getTerm() > currentTerm) {
                        log.error("currentTerm={}, remoteServerTerm={}", currentTerm, result.getTerm());
                        log.debug("may have network isolate");
                        //TODO
                    }
                    if (!result.getSuccess()) {
                        if (!getNodeStatus().equals(PeerNodeStatus.INSTALLSNAPSHOT)) {
                            long nextPreLogIndex = getNextIndex() - 1;
                            //如果已经是在Snapshot中
                            if (logService.hasInSnapshot(nextPreLogIndex)) {
                                setNodeStatus(PeerNodeStatus.INSTALLSNAPSHOT);
                                Snapshot snapShot = logService.getSnapShot();
                                InstallSnapshotRequest installSnapShotRequest = new InstallSnapshotRequest();
                                installSnapShotRequest.setLeaderId(nodeId);
                                installSnapShotRequest.setTerm(currentTerm);
                                installSnapShotRequest.setSnapshot(snapShot);
                                getRaftService().requestInstallSnapShot(installSnapShotRequest);
                                SettableFuture<InstallSnapshotResponse> responseSettableFuture = RpcContext.getContextFuture();
                                Futures.addCallback(responseSettableFuture, new FutureCallback<InstallSnapshotResponse>() {
                                    @Override
                                    public void onSuccess(@Nullable InstallSnapshotResponse result) {
                                        if (result.getSuccess()) {
                                            setNodeStatus(PeerNodeStatus.PROBE);
                                            setMatchIndex(snapShot.getLastLogIndex());
                                            setNextIndex(snapShot.getLastLogIndex() + 1);
                                        } else {
                                            throw new IllegalStateException("install SnapShot fail");
                                        }
                                    }

                                    @Override
                                    public void onFailure(Throwable t) {
                                        t.printStackTrace();
                                    }
                                }, RpcExecutors.commonExecutor());
                            } else {
                                setNextIndex(getNextIndex() - 1);
                            }
                        }
                    } else {
                        if (!getNodeStatus().equals(PeerNodeStatus.NORMAL)) {
                            setNodeStatus(PeerNodeStatus.NORMAL);
                            setNextIndex(currentLastLogIndex + 1);
                            setMatchIndex(currentLastLogIndex);
                        }
                    }
                    releaseSendMsg();
                }

                @Override
                public void onFailure(Throwable t) {
                    releaseSendMsg();
                    log.error(t.getMessage(), t);
                }
            }, RpcExecutors.commonExecutor());
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            releaseSendMsg();
        }
    }
    public void acquireSendMsg() throws InterruptedException {
        canSendMsg.acquire();
    }

    public void releaseSendMsg() {
        canSendMsg.release();
    }

    @Override
    public void close() {
        if (Objects.nonNull(rpcClient)) {
            rpcClient.close();
            log.debug("close rpcClient, nodeId={}", nodeId.getPeerId());
        }
    }
}
