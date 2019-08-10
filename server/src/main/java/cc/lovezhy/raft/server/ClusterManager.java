package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.RpcClientOptions;
import cc.lovezhy.raft.rpc.RpcServer;
import cc.lovezhy.raft.rpc.RpcServerOptions;
import cc.lovezhy.raft.server.log.*;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.node.PeerRaftNode;
import cc.lovezhy.raft.server.node.RaftNode;
import cc.lovezhy.raft.server.utils.Pair;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private Map<NodeId, RaftNode> nodeIdRaftNodeMap = Maps.newHashMap();
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
            nodeIdRaftNodeMap.put(node.getNodeId(), node);
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
    public Pair<Integer, Command> nCommitted(int index) {
        int count = 0;
        Command defaultCommand = LogConstants.getDummyCommand();

        for (RaftNode raftNode : raftNodes) {
            LogService logService = raftNode.getLogService();
            LogEntry entry = null;
            if (index <= logService.getLastCommitLogIndex()) {
                entry = logService.get(index);
            }
            if (Objects.nonNull(entry)) {
                if (count > 0 && !Objects.equals(defaultCommand, entry.getCommand())) {
                    fail("committed values do not match: index {}, {}, {}", index, defaultCommand, entry.getCommand());
                }
                count++;
                defaultCommand = entry.getCommand();
            }
        }
        return Pair.of(count, defaultCommand);
    }

    @Override
    public int one(Command command, int expectedServers, boolean retry) {
        NodeId leaderNodeId = null;
        long current = System.currentTimeMillis();
        while (System.currentTimeMillis() - current < TimeUnit.SECONDS.toMillis(10)) {
            try {
                leaderNodeId = checkOneLeader();
                if (Objects.nonNull(leaderNodeId)) {
                    break;
                }
            } catch (Exception e) {
                //ignore
            }
        }
        if (Objects.isNull(leaderNodeId)) {
            fail("one({}) failed to reach agreement", command);
        }
        RaftNode leaderRaftNode = nodeIdRaftNodeMap.get(leaderNodeId);
        leaderRaftNode.getOuterService().appendLog((DefaultCommand) command);
        long lastLogIndex = leaderRaftNode.getLogService().getLastLogIndex();

        int times = retry ? 2 : 1;
        while (times >= 1) {
            long t0 = System.currentTimeMillis();
            while (System.currentTimeMillis() - t0 < TimeUnit.SECONDS.toMillis(2)) {
                Pair<Integer, Command> commandPair = nCommitted(Math.toIntExact(lastLogIndex));
                if (commandPair.getKey() > 0 && commandPair.getKey() >= expectedServers) {
                    if (Objects.equals(command, commandPair.getValue())) {
                        return Math.toIntExact(lastLogIndex);
                    }
                }
            }
            times--;
        }
        fail("one({}) failed to reach agreement", command);
        return -1;
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
        setNetStatus(nodeId, false);
    }

    @Override
    public void connect(NodeId nodeId) {
        setNetStatus(nodeId, true);
    }

    private void setNetStatus(NodeId nodeId, boolean isOnNet) {
        //当前节点断开与peerNode的网络，当前节点向其他节点发送请求
        RaftNode currentNode = raftNodes.stream().filter(raftNode -> raftNode.getNodeId().equals(nodeId)).findAny().get();
        List<PeerRaftNode> currentNodePeerRaftNodes = getObjectMember(currentNode, "peerRaftNodes");
        currentNodePeerRaftNodes.forEach(peerRaftNode -> {
            RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
            Preconditions.checkNotNull(rpcClientOptions);
            rpcClientOptions.setOnNet(isOnNet);
        });

        //当前节点能否回复请求
        RpcServer rpcServer = getObjectMember(currentNode, "rpcServer");
        Preconditions.checkNotNull(rpcServer);
        RpcServerOptions rpcServerOptions = getObjectMember(rpcServer, "rpcServerOptions");
        Preconditions.checkNotNull(rpcServerOptions);
        rpcServerOptions.setOnNet(isOnNet);

        raftNodeExtConfigMap.get(nodeId).setOnNet(isOnNet);
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

    @Override
    public StartResponse start(NodeId nodeId, DefaultCommand command) {
        RaftNode node = nodeIdRaftNodeMap.get(nodeId);
        node.getOuterService().appendLog(command);
        boolean isLeader = node.getNodeScheduler().isLeader();
        int term = Math.toIntExact(node.getCurrentTerm());
        int lastLogIndex = Math.toIntExact(node.getLogService().getLastLogIndex());
        return StartResponse.create(lastLogIndex, term, isLeader);
    }

    @Override
    public Command wait(int index, int n, long startTerm) {
        long to = TimeUnit.MILLISECONDS.toMillis(10);
        for (int i = 0; i < 30; i++) {
            Pair<Integer, Command> integerCommandPair = nCommitted(index);
            if (integerCommandPair.getKey() >= n) {
                break;
            }
            pause(to);
            if (to < TimeUnit.SECONDS.toMillis(1)) {
                to *= 2;
            }
            if (startTerm > -1) {
                for (RaftNode raftNode : raftNodes) {
                    if (raftNode.getCurrentTerm() > startTerm) {
                        return null;
                    }
                }
            }
        }
        Pair<Integer, Command> integerCommandPair = nCommitted(index);
        if (integerCommandPair.getKey() < n) {
            fail("only {} decided for index {}; wanted {}", integerCommandPair.getKey(), index, n);
        }
        return integerCommandPair.getValue();
    }

    @Override
    public int fetchTerm(NodeId nodeId) {
        return Math.toIntExact(nodeIdRaftNodeMap.get(nodeId).getCurrentTerm());
    }

    @Override
    public Collection<NodeId> fetchAllNodeId() {
        return nodeIdRaftNodeMap.keySet();
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

    @Override
    public void status() {
        System.out.println(JSON.toJSONString(raftNodeExtConfigMap));
    }

    private void pause(long mills) {
        try {
            Thread.sleep(mills);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }
}
