package cc.lovezhy.raft.server.mock6824;

import cc.lovezhy.raft.server.ClusterManager;
import cc.lovezhy.raft.server.Mock6824Config;
import cc.lovezhy.raft.server.log.Command;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.utils.Pair;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.server.mock6824.Utils.*;

public class Mock6824Test {

    private final Logger log = LoggerFactory.getLogger(Mock6824Test.class);

    private static final long RAFT_ELECTION_TIMEOUT = 1000;

    private Mock6824Config clusterConfig;

    @Test
    public void testInitialElection2A() {
        int servers = 3;
        clusterConfig = ClusterManager.newCluster(servers, false);
        clusterConfig.begin("Test (2A): initial election");

        clusterConfig.checkOneLeader();
        pause(TimeUnit.MILLISECONDS.toMillis(50));

        long term1 = clusterConfig.checkTerms();
        pause(TimeUnit.MILLISECONDS.toMillis(50));

        long term2 = clusterConfig.checkTerms();
        if (term1 != term2) {
            log.warn("warning: term changed even though there were no failures");
        }
        clusterConfig.checkOneLeader();
        clusterConfig.end();
    }

    @Test
    public void testReElection2A() {
        int servers = 3;
        clusterConfig = ClusterManager.newCluster(servers, false);

        clusterConfig.begin("Test (2A): election after network failure");

        NodeId leaderNode1 = clusterConfig.checkOneLeader();
        clusterConfig.disconnect(leaderNode1);

        clusterConfig.checkOneLeader();

        clusterConfig.connect(leaderNode1);
        NodeId leaderNode2 = clusterConfig.checkOneLeader();

        clusterConfig.disconnect(leaderNode2);
        NodeId nextNodeId = clusterConfig.nextNode(leaderNode2);
        clusterConfig.disconnect(nextNodeId);

        pause(2 * RAFT_ELECTION_TIMEOUT);

        clusterConfig.checkNoLeader();
        clusterConfig.connect(nextNodeId);

        NodeId leader = clusterConfig.checkOneLeader();
        clusterConfig.connect(leaderNode2);

        clusterConfig.checkOneLeader();
        clusterConfig.end();
    }


    /**
     * 这里有个不一致的地方，6.824中，成为Leader之后不进行一个Dummy Log的Commit
     */
    @Test
    public void testBasicAgree2B() {
        int servers = 5;
        clusterConfig = ClusterManager.newCluster(servers, false);
        clusterConfig.begin("Test (2B): basic agreement");

        //由于提交了两条dummy，所以从2开始
        for (int i = 2; i < 6; i++) {
            Pair<Integer, Command> integerCommandPair = clusterConfig.nCommitted(i);
            if (integerCommandPair.getKey() > 0) {
                fail("some one has committed before Start()");
            }
            int xindex = clusterConfig.one(randomCommand(), servers, false);
            if (xindex != i) {
                fail("got index {} but expected {}", xindex, i);
            }
        }
        clusterConfig.end();

    }
//
//    @Test
//    public void testFailAgree2B() {
//        int servers = 3;
//        raftNodes = makeCluster(servers);
//        raftNodes.forEach(RaftNode::init);
//
//        log.info("Test (2B): agreement despite follower disconnection");
//
//        one(raftNodes, randomCommand(), servers, false);
//        RaftNode leader = checkOneLeader(raftNodes);
//        int leaderIndex = raftNodes.indexOf(leader);
//        disConnect(raftNodes.get((leaderIndex + 1) % raftNodes.size()), raftNodes);
//
//        one(raftNodes, randomCommand(), servers - 1, false);
//        one(raftNodes, randomCommand(), servers - 1, false);
//        pause(RAFT_ELECTION_TIMEOUT);
//
//        connect(raftNodes.get((leaderIndex + 1) % raftNodes.size()), raftNodes);
//
//        one(raftNodes, randomCommand(), servers, false);
//        one(raftNodes, randomCommand(), servers, false);
//
//    }
//
//    @Test
//    public void testFailNoAgree2B() {
//        int servers = 5;
//        raftNodes = makeCluster(5);
//        raftNodes.forEach(RaftNode::init);
//        log.info("Test (2B): no agreement if too many followers disconnect");
//        one(raftNodes, randomCommand(), servers, false);
//
//        RaftNode leader = checkOneLeader(raftNodes);
//        int leaderIndex = raftNodes.indexOf(leader);
//        disConnect(raftNodes.get((leaderIndex + 1) % raftNodes.size()), raftNodes);
//        disConnect(raftNodes.get((leaderIndex + 2) % raftNodes.size()), raftNodes);
//        disConnect(raftNodes.get((leaderIndex + 3) % raftNodes.size()), raftNodes);
//
//        boolean appendSuccess = leader.getOuterService().appendLog(randomCommand()).getBoolean("selfAppend");
//        if (!appendSuccess) {
//            fail("leader rejected AppendLog");
//        }
//
//        long lastLogIndex = leader.getLogService().getLastLogIndex();
//        if (lastLogIndex != 3) {
//            fail("expected index 2, got {}", lastLogIndex);
//        }
//        pause(2 * RAFT_ELECTION_TIMEOUT);
//
//        Pair<Integer, Command> integerCommandPair = nCommitted(raftNodes, Math.toIntExact(lastLogIndex));
//        if (integerCommandPair.getKey() > 0) {
//            fail("{} committed but no majority", integerCommandPair.getKey());
//        }
//        connect(raftNodes.get((leaderIndex + 1) % raftNodes.size()), raftNodes);
//        connect(raftNodes.get((leaderIndex + 2) % raftNodes.size()), raftNodes);
//        connect(raftNodes.get((leaderIndex + 3) % raftNodes.size()), raftNodes);
//
//        RaftNode leader2 = checkOneLeader(raftNodes);
//        appendSuccess = leader2.getOuterService().appendLog(randomCommand()).getBoolean("success");
//        long lastLogIndex2 = leader.getLogService().getLastLogIndex();
//        if (!appendSuccess) {
//            fail("leader2 rejected appendLog");
//        }
//        if (lastLogIndex2 < 3 || lastLogIndex2 > 4) {
//            fail("unexpected index {}", lastLogIndex2);
//        }
//        one(raftNodes, randomCommand(), servers, false);
//
//    }
//
//    @Test
//    public void testConcurrentStarts2B() throws InterruptedException {
//        int servers = 3;
//        raftNodes = makeCluster(servers);
//        raftNodes.forEach(RaftNode::init);
//        log.info("Test (2B): concurrent Start()s");
//
//        boolean success = false;
//        boolean jump = false;
//        while (!jump) {
//            jump = true;
//            for (int tryi = 0; tryi < 5; tryi++) {
//                if (tryi > 0) {
//                    pause(3 * TimeUnit.SECONDS.toMillis(1));
//                }
//
//                RaftNode leader = checkOneLeader(raftNodes);
//                boolean appendLogSuccess = leader.getOuterService().appendLog(randomCommand()).getBoolean("success");
//                long term = leader.getLogService().get(leader.getLogService().getLastLogIndex()).getTerm();
//                if (!appendLogSuccess) {
//                    continue;
//                }
//                ExecutorService executorService = Executors.newCachedThreadPool();
//                CountDownLatch latch = new CountDownLatch(5);
//                List<Long> indexList = Lists.newArrayList();
//                int iter = 5;
//                for (int i = 0; i < iter; i++) {
//                    int tempi = i;
//                    executorService.execute(() -> {
//                        try {
//                            JsonObject jsonObject = leader.getOuterService().appendLog(defineNumberCommand(100 + tempi));
//                            boolean appendLogSuccess1 = jsonObject.getBoolean("success");
//                            long lastLogIndex = jsonObject.getInteger("index");
//                            Long term1 = leader.getLogService().get(lastLogIndex).getTerm();
//                            if (term1 != term) {
//                                return;
//                            }
//                            if (!appendLogSuccess1) {
//                                return;
//                            }
//                            indexList.add(lastLogIndex);
//                        } finally {
//                            latch.countDown();
//                        }
//                    });
//                }
//                latch.await();
//
//                boolean breakToLoop = false;
//                for (RaftNode raftNode : raftNodes) {
//                    if (raftNode.getCurrentTerm() != term) {
//                        jump = false;
//                        breakToLoop = true;
//                        break;
//                    }
//                }
//                if (breakToLoop) {
//                    break;
//                }
//
//                boolean failed = false;
//                List<Command> commands = Lists.newArrayList();
//                for (long index : indexList) {
//                    Command command = waitNCommitted(raftNodes, Math.toIntExact(index), servers, term);
//                    if (Objects.nonNull(command)) {
//                        commands.add(command);
//                    } else {
//                        failed = true;
//                        jump = true;
//                        break;
//                    }
//                }
//
//                if (failed) {
//                    //ignore
//                }
//
//                for (int i = 0; i < iter; i++) {
//                    Command command = defineNumberCommand(100 + i);
//                    boolean ok = false;
//                    for (int j = 0; j < commands.size(); j++) {
//                        if (Objects.equals(commands.get(j), command)) {
//                            ok = true;
//                        }
//                    }
//                    if (!ok) {
//                        fail("cmd {} missing in {}", command, commands);
//                    }
//
//                }
//                success = true;
//                jump = true;
//                break;
//            }
//        }
//        if (!success) {
//            fail("term changed too often");
//        }
//
//    }
//
//    @Test
//    public void testRejoin2B() {
//        int servers = 3;
//        raftNodes = makeCluster(servers);
//        raftNodes.forEach(RaftNode::init);
//
//        log.info("Test (2B): rejoin of partitioned leader");
//        one(raftNodes, defineNumberCommand(101), servers, true);
//        RaftNode leader1 = checkOneLeader(raftNodes);
//        disConnect(leader1, raftNodes);
//        leader1.getOuterService().appendLog(defineNumberCommand(102));
//        leader1.getOuterService().appendLog(defineNumberCommand(103));
//        leader1.getOuterService().appendLog(defineNumberCommand(104));
//
//
//        one(raftNodes, defineNumberCommand(103), 2, true);
//
//        RaftNode leader2 = checkOneLeader(raftNodes);
//        disConnect(leader2, raftNodes);
//
//        connect(leader1, raftNodes);
//
//        one(raftNodes, defineNumberCommand(104), 2, true);
//        connect(leader2, raftNodes);
//        one(raftNodes, defineNumberCommand(105), servers, true);
//    }
//
//    @Test
//    public void testBackup2B() {
//        int servers = 5;
//        raftNodes = makeCluster(servers);
//        raftNodes.forEach(RaftNode::init);
//        log.info("Test (2B): leader backs up quickly over incorrect follower logs");
//
//        one(raftNodes, randomCommand(), servers, false);
//
//        RaftNode leader1 = checkOneLeader(raftNodes);
//        int leader1Index = raftNodes.indexOf(leader1);
//        disConnect(raftNodes.get((leader1Index + 2) % raftNodes.size()), raftNodes);
//        disConnect(raftNodes.get((leader1Index + 3) % raftNodes.size()), raftNodes);
//        disConnect(raftNodes.get((leader1Index + 4) % raftNodes.size()), raftNodes);
//
//        for (int i = 0; i < 50; i++) {
//            leader1.getOuterService().appendLog(randomCommand());
//        }
//
//        pause(RAFT_ELECTION_TIMEOUT * 2);
//
//        disConnect(raftNodes.get((leader1Index + 0) % raftNodes.size()), raftNodes);
//        disConnect(raftNodes.get((leader1Index + 1) % raftNodes.size()), raftNodes);
//
//        connect(raftNodes.get((leader1Index + 2) % raftNodes.size()), raftNodes);
//        connect(raftNodes.get((leader1Index + 3) % raftNodes.size()), raftNodes);
//        connect(raftNodes.get((leader1Index + 4) % raftNodes.size()), raftNodes);
//
//        for (int i = 0; i < 50; i++) {
//            System.out.println(i);
//            one(raftNodes, randomCommand(), 3, true);
//        }
//
//        RaftNode leader2 = checkOneLeader(raftNodes);
//        int leader2Index = raftNodes.indexOf(leader2);
//        RaftNode other = raftNodes.get((leader1Index + 2) % raftNodes.size());
//        if (leader2 == other) {
//            other = raftNodes.get((leader2Index + 1) % raftNodes.size());
//        }
//        disConnect(other, raftNodes);
//
//        for (int i = 0; i < 50; i++) {
//            System.out.println(i);
//            leader2.getOuterService().appendLog(randomCommand());
//        }
//
//        pause(RAFT_ELECTION_TIMEOUT * 2);
//
//        raftNodes.forEach(raftNode -> {
//            disConnect(raftNode, raftNodes);
//        });
//
//        connect(raftNodes.get((leader1Index + 0) % servers), raftNodes);
//        connect(raftNodes.get((leader1Index + 1) % servers), raftNodes);
//        connect(other, raftNodes);
//
//        for (int i = 0; i < 50; i++) {
//            System.out.println(i);
//            one(raftNodes, randomCommand(), 3, true);
//        }
//
//        raftNodes.forEach(raftNode -> {
//            connect(raftNode, raftNodes);
//        });
//
//        one(raftNodes, randomCommand(), servers, true);
//    }
//
//    //    @Test
//    public void testCount2B() {
//        int servers = 3;
//        raftNodes = makeCluster(servers);
//        raftNodes.forEach(RaftNode::init);
//
//        log.info("Test (2B): RPC counts aren't too high");
//
//        //TODO 测这个有啥用。。。
//    }
//
//    //    @Test
//    public void testPersist12C() {
//        int servers = 3;
//        raftNodes = makeCluster(servers);
//        raftNodes.forEach(RaftNode::init);
//
//        log.info("Test (2C): basic persistence");
//
//        one(raftNodes, defineNumberCommand(11), servers, true);
//
//        for (RaftNode raftNode : raftNodes) {
//            start1(raftNode);
//        }
//
//        for (RaftNode raftNode : raftNodes) {
//            disConnect(raftNode, raftNodes);
//            connect(raftNode, raftNodes);
//        }
//
//        one(raftNodes, defineNumberCommand(12), servers, true);
//
//        RaftNode leader1 = checkOneLeader(raftNodes);
//        disConnect(leader1, raftNodes);
//        start1(leader1);
//        connect(leader1, raftNodes);
//
//        one(raftNodes, defineNumberCommand(13), servers, true);
//
//        RaftNode leader2 = checkOneLeader(raftNodes);
//        disConnect(leader2, raftNodes);
//        one(raftNodes, defineNumberCommand(14), servers - 1, true);
//        start1(leader2);
//        connect(leader2, raftNodes);
//
//        waitNCommitted(raftNodes, 4, servers, -1);
//
//
//    }

}
