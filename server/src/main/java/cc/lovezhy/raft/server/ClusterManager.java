package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.RpcClientOptions;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.node.PeerRaftNode;
import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static cc.lovezhy.raft.server.RaftConstants.getRandomStartElectionTimeout;
import static cc.lovezhy.raft.server.utils.ReflectUtils.getObjectMember;

public class ClusterManager implements Mock6824Config {

    private static final Logger log = LoggerFactory.getLogger(ClusterManager.class);

    public static ClusterManager newCluster(int servers, boolean unreliable) {
        ClusterManager clusterManager = new ClusterManager();
        clusterManager.makeCluster(servers, unreliable);
        return clusterManager;
    }

    private List<RaftNode> raftNodes;
    private Map<NodeId, RaftNodeExtConfig> raftNodeExtConfigMap = Maps.newHashMap();


    class RaftNodeExtConfig {

        RaftNodeExtConfig() {
            this.isOnNet = true;
        }

        private boolean isOnNet;

        public boolean isOnNet() {
            return isOnNet;
        }

        public void setOnNet(boolean onNet) {
            isOnNet = onNet;
        }
    }
    /**
     * @see #begin(String)
     */
    private long t0;
    private long rpcs0;
    private long cmds0;
    private long maxIndex0;

    private ClusterManager() {
    }

    private void makeCluster(int servers, boolean unreliable) {
        Preconditions.checkArgument(servers > 0);

        List<RaftNode> raftNodes = Lists.newArrayList();
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", String.valueOf(servers));

        AtomicInteger nodeRpcPortAllocator = new AtomicInteger(5283);

        List<String> nodeList = Lists.newArrayList();
        for (int i = 0; i < servers; i++) {
            nodeList.add("localhost:" + nodeRpcPortAllocator.getAndAdd(3) + ":" + i);
        }
        nodeList.forEach(currentNode -> {
            properties.setProperty("local", currentNode);
            List<String> peerNode = Lists.newLinkedList(nodeList);
            peerNode.remove(currentNode);
            properties.setProperty("peer", Joiner.on(',').join(peerNode));
            RaftNode node = new RaftStarter().start(properties);
            raftNodes.add(node);
            raftNodeExtConfigMap.put(node.getNodeId(), new RaftNodeExtConfig());
        });
        this.raftNodes = raftNodes;
        setNetReliable(!unreliable);
        this.raftNodes.forEach(RaftNode::init);
    }

    @Override
    public NodeId checkOneLeader() {
        for (int i = 0; i < 10; i++) {
            pause(getRandomStartElectionTimeout() + 200);
            //term -> raftNodes
            Map<Long, List<RaftNode>> leaders = Maps.newHashMap();
            raftNodes.forEach(raftNode -> {
                if (isOnNet(raftNode) && raftNode.getNodeScheduler().isLeader()) {
                    if (Objects.isNull(leaders.get(raftNode.getCurrentTerm()))) {
                        leaders.put(raftNode.getCurrentTerm(), Lists.newLinkedList());
                    }
                    leaders.get(raftNode.getCurrentTerm()).add(raftNode);
                }
            });
            long lastTermWithLeader = -1;
            for (Map.Entry<Long, List<RaftNode>> entry : leaders.entrySet()) {
                if (entry.getValue().size() > 1) {
                    fail("term {} has %d (>1) leaders", entry.getKey(), entry.getValue().size());
                }
                if (entry.getKey() > lastTermWithLeader) {
                    lastTermWithLeader = entry.getKey();
                }
            }
            if (leaders.size() != 0) {
                return leaders.get(lastTermWithLeader).get(0).getNodeId();
            }
        }
        fail("expected one leader, got none");
        return null;
    }

    @Override
    public NodeId nextNode(NodeId nodeId) {
        int index = 0;
        for (int i = 0; i < raftNodes.size(); i++) {
            if (raftNodes.get(i).getNodeId().equals(nodeId)) {
                index = i;
            }
        }
        index = (index + 1) % raftNodes.size();
        return raftNodes.get(index).getNodeId();
    }

    @Override
    public int checkTerms() {
        long term = -1;
        for (RaftNode raftNode : raftNodes) {
            if (isOnNet(raftNode)) {
                if (term == -1) {
                    term = raftNode.getCurrentTerm();
                } else if (term != raftNode.getCurrentTerm()) {
                    fail("servers disagree on term");
                }
            }
        }
        return Math.toIntExact(term);
    }

    @Override
    public void checkNoLeader() {
        for (RaftNode raftNode : raftNodes) {
            if (isOnNet(raftNode) && raftNode.getNodeScheduler().isLeader()) {
                fail("expected no leader, but {} claims to be leader", raftNode.getNodeId());
            }
        }
    }

    @Override
    public void begin(String description) {
        log.info("{} ...", description);
        this.t0 = System.currentTimeMillis();
        //TODO
        this.rpcs0 = 0;
        this.cmds0 = 0;
        //TODO
        this.maxIndex0 = 0;
    }

    @Override
    public void end() {
        if (Objects.nonNull(raftNodes)) {
            raftNodes.forEach(RaftNode::close);
        }
    }

    @Override
    public int rpcTotal() {
        //TODO
        return 0;
    }

    @Override
    public void disconnect(NodeId nodeId) {
        //当前节点断开与peerNode的网络
        setConnectStatus(nodeId, false);
    }

    @Override
    public void connect(NodeId nodeId) {
        setConnectStatus(nodeId, true);
    }

    private void setConnectStatus(NodeId nodeId, boolean onNet) {
        //当前节点与其他节点的网络
        RaftNode currentNode = raftNodes.stream().filter(raftNode -> raftNode.getNodeId().equals(nodeId)).findAny().get();
        List<PeerRaftNode> currentNodePeerRaftNodes = getObjectMember(currentNode, "peerRaftNodes");
        currentNodePeerRaftNodes.forEach(peerRaftNode -> {
            RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
            Preconditions.checkNotNull(rpcClientOptions);
            rpcClientOptions.setOnNet(onNet);
        });


        //其他节点与当前节点的网络
        List<RaftNode> otherNodes = raftNodes.stream().filter(raftNode -> !raftNode.getNodeId().equals(nodeId)).collect(Collectors.toList());
        otherNodes.forEach(raftNode -> {
            List<PeerRaftNode> peerRaftNodes = getObjectMember(raftNode, "peerRaftNodes");
            Preconditions.checkNotNull(peerRaftNodes);
            peerRaftNodes.forEach(peerRaftNode -> {
                if (peerRaftNode.getNodeId().equals(nodeId)) {
                    RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
                    Preconditions.checkNotNull(rpcClientOptions);
                    rpcClientOptions.setOnNet(onNet);
                }
            });
        });
        raftNodeExtConfigMap.get(nodeId).setOnNet(onNet);
    }

    /**
     * 设置网络的可靠性
     */
    private void setNetReliable(boolean reliable) {
        raftNodes.forEach(raftNode -> {
            List<PeerRaftNode> peerRaftNodes = getObjectMember(raftNode, "peerRaftNodes");
            Preconditions.checkNotNull(peerRaftNodes);
            peerRaftNodes.forEach(peerRaftNode -> {
                RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
                Preconditions.checkNotNull(rpcClientOptions);
                rpcClientOptions.setReliable(reliable);
            });
        });
    }

    /**
     * 判断一个节点是否在Net上
     */
    private boolean isOnNet(RaftNode raftNode) {
        return raftNodeExtConfigMap.get(raftNode.getNodeId()).isOnNet();
    }

    private void fail(String errMsg, Object... objects) {
        log.error(errMsg, objects);
        throw new FailException();
    }

    private void pause(long mills) {
        try {
            Thread.sleep(mills);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }
}
