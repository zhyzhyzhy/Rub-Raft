package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.RpcClientOptions;
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

import static cc.lovezhy.raft.server.RaftConstants.getRandomStartElectionTimeout;
import static cc.lovezhy.raft.server.utils.ReflectUtils.getObjectMember;

public class ClusterManager implements Mock6824Config {

    private static final Logger log = LoggerFactory.getLogger(ClusterManager.class);

    public static ClusterManager newCluster(int servers) {
        ClusterManager clusterManager = new ClusterManager();
        clusterManager.makeCluster(servers);
        return clusterManager;
    }

    private List<RaftNode> raftNodes;

    private ClusterManager() {
    }

    private void makeCluster(int servers) {
        Preconditions.checkArgument(servers > 0);
        List<RaftNode> raftNodes = Lists.newArrayList();
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", String.valueOf(servers));

        AtomicInteger nodeRpcPortAllocator = new AtomicInteger(5283);

        List<String> nodeList = Lists.newArrayList();
        for (int i = 0; i < servers; i++) {
            nodeList.add("localhost:" + nodeRpcPortAllocator.getAndAdd(2) + ":" + i);
        }
        nodeList.forEach(currentNode -> {
            properties.setProperty("local", currentNode);
            List<String> peerNode = Lists.newLinkedList(nodeList);
            peerNode.remove(currentNode);
            properties.setProperty("peer", Joiner.on(',').join(peerNode));
            raftNodes.add(new RaftStarter().start(properties));
        });
        this.raftNodes = raftNodes;
        this.raftNodes.forEach(RaftNode::init);
    }

    @Override
    public RaftNode checkOneLeader() {
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
                return leaders.get(lastTermWithLeader).get(0);
            }
        }
        fail("expected one leader, got none");
        return null;
    }

    @Override
    public int checkTerms() {
        return 0;
    }

    @Override
    public void checkNoLeader() {

    }

    @Override
    public void end() {
        if (Objects.nonNull(raftNodes)) {
            raftNodes.forEach(RaftNode::close);
        }
    }

    /**
     * 判断一个节点是否在Net上
     */
    private boolean isOnNet(RaftNode raftNode) {
        boolean isOnNet = false;
        List<PeerRaftNode> peerRaftNodes = getObjectMember(raftNode, "peerRaftNodes");
        for (PeerRaftNode peerRaftNode : peerRaftNodes) {
            RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
            isOnNet = isOnNet || rpcClientOptions.isOnNet();
        }
        return isOnNet;
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
