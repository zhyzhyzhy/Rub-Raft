package cc.lovezhy.raft.server.mock6824;

import cc.lovezhy.raft.server.ClusterManager;
import cc.lovezhy.raft.server.Mock6824Config;
import cc.lovezhy.raft.server.log.Command;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.utils.Pair;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.server.mock6824.Utils.*;

public class Mock6824Test {

    private final Logger log = LoggerFactory.getLogger(Mock6824Test.class);

    private static final long RAFT_ELECTION_TIMEOUT = 1000;

    private Mock6824Config clusterConfig;

    @After
    public void after() {
        clusterConfig.end();
    }

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

    }

    @Test
    public void testFailAgree2B() {
        int servers = 3;
        clusterConfig = ClusterManager.newCluster(servers, false);

        clusterConfig.begin("Test (2B): agreement despite follower disconnection");

        clusterConfig.one(randomCommand(), servers, false);
        NodeId leaderNodeId = clusterConfig.checkOneLeader();
        NodeId nextNodeId = clusterConfig.nextNode(leaderNodeId);
        clusterConfig.disconnect(nextNodeId);

        clusterConfig.one(randomCommand(), servers - 1, false);
        clusterConfig.one(randomCommand(), servers - 1, false);
        pause(RAFT_ELECTION_TIMEOUT);
        clusterConfig.one(randomCommand(), servers - 1, false);
        clusterConfig.one(randomCommand(), servers - 1, false);

        clusterConfig.connect(nextNodeId);

        clusterConfig.one(randomCommand(), servers, false);
        pause(RAFT_ELECTION_TIMEOUT);
        clusterConfig.one(randomCommand(), servers, false);

    }

    @Test
    public void testFailNoAgree2B() {
        int servers = 5;
        clusterConfig = ClusterManager.newCluster(5, false);

        clusterConfig.begin("Test (2B): no agreement if too many followers disconnect");
        clusterConfig.one(randomCommand(), servers, false);

        NodeId leaderNodeId = clusterConfig.checkOneLeader();
        NodeId nextNodeId1 = clusterConfig.nextNode(leaderNodeId);
        clusterConfig.disconnect(nextNodeId1);
        NodeId nextNodeId2 = clusterConfig.nextNode(nextNodeId1);
        clusterConfig.disconnect(nextNodeId2);
        NodeId nextNodeId3 = clusterConfig.nextNode(nextNodeId2);
        clusterConfig.disconnect(nextNodeId3);

        Mock6824Config.StartResponse startResponse = clusterConfig.start(leaderNodeId, randomCommand());
        if (!startResponse.isLeader()) {
            fail("leader rejected AppendLog");
        }

        if (startResponse.getIndex() != 3) {
            fail("expected index 3, got {}", startResponse.getIndex());
        }
        pause(2 * RAFT_ELECTION_TIMEOUT);

        Pair<Integer, Command> integerCommandPair = clusterConfig.nCommitted(Math.toIntExact(startResponse.getIndex()));
        if (integerCommandPair.getKey() > 0) {
            fail("{} committed but no majority", integerCommandPair.getKey());
        }

        clusterConfig.connect(nextNodeId1);
        clusterConfig.connect(nextNodeId2);
        clusterConfig.connect(nextNodeId3);

        NodeId leaderNodeId2 = clusterConfig.checkOneLeader();
        Mock6824Config.StartResponse startResponse1 = clusterConfig.start(leaderNodeId2, randomCommand());

        if (!startResponse1.isLeader()) {
            fail("leader2 rejected appendLog");
        }
        if (startResponse1.getIndex() < 3 || startResponse1.getIndex() > 4) {
            fail("unexpected index {}", startResponse1.getIndex());
        }
        clusterConfig.one(randomCommand(), servers, true);
    }

    @Test
    public void testConcurrentStarts2B() throws InterruptedException {
        int servers = 3;
        clusterConfig = ClusterManager.newCluster(servers, false);
        clusterConfig.begin("Test (2B): concurrent Start()s");

        boolean success = false;
        boolean jump = false;
        while (!jump) {
            jump = true;
            for (int tryi = 0; tryi < 5; tryi++) {
                if (tryi > 0) {
                    pause(3 * TimeUnit.SECONDS.toMillis(1));
                }

                NodeId leaderNodeId = clusterConfig.checkOneLeader();
                Mock6824Config.StartResponse startResponse = clusterConfig.start(leaderNodeId, randomCommand());
                if (!startResponse.isLeader()) {
                    continue;
                }
                ExecutorService executorService = Executors.newCachedThreadPool();
                CountDownLatch latch = new CountDownLatch(5);
                List<Integer> indexList = Lists.newArrayList();
                int iter = 5;
                for (int i = 0; i < iter; i++) {
                    int tempi = i;
                    executorService.execute(() -> {
                        try {
                            Mock6824Config.StartResponse startResponse1 = clusterConfig.start(leaderNodeId, defineNumberCommand(100 + tempi));
                            if (startResponse1.getTerm() != startResponse.getTerm()) {
                                return;
                            }
                            if (!startResponse1.isLeader()) {
                                return;
                            }
                            indexList.add(startResponse1.getIndex());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await();

                boolean breakToLoop = false;
                for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
                    if (clusterConfig.fetchTerm(nodeId) != startResponse.getTerm()) {
                        jump = false;
                        breakToLoop = true;
                        break;
                    }
                }
                if (breakToLoop) {
                    break;
                }

                boolean failed = false;
                List<Command> commands = Lists.newArrayList();
                for (long index : indexList) {
                    Command command = clusterConfig.wait(Math.toIntExact(index), servers, startResponse.getTerm());
                    if (Objects.nonNull(command)) {
                        commands.add(command);
                    } else {
                        failed = true;
                        jump = true;
                        break;
                    }
                }

                if (failed) {
                    //ignore
                }

                for (int i = 0; i < iter; i++) {
                    Command command = defineNumberCommand(100 + i);
                    boolean ok = false;
                    for (int j = 0; j < commands.size(); j++) {
                        if (Objects.equals(commands.get(j), command)) {
                            ok = true;
                        }
                    }
                    if (!ok) {
                        fail("cmd {} missing in {}", command, commands);
                    }

                }
                success = true;
                jump = true;
                break;
            }
        }
        if (!success) {
            fail("term changed too often");
        }

    }

    @Test
    public void testRejoin2B() {
        int servers = 3;
        clusterConfig = ClusterManager.newCluster(servers, false);

        clusterConfig.begin("Test (2B): rejoin of partitioned leader");
        clusterConfig.one(defineNumberCommand(101), servers, true);
        NodeId leader1 = clusterConfig.checkOneLeader();
        clusterConfig.disconnect(leader1);
        clusterConfig.start(leader1, defineNumberCommand(102));
        clusterConfig.start(leader1, defineNumberCommand(103));
        clusterConfig.start(leader1, defineNumberCommand(104));


        clusterConfig.one(defineNumberCommand(103), 2, true);

        NodeId leader2 = clusterConfig.checkOneLeader();
        clusterConfig.disconnect(leader2);

        clusterConfig.connect(leader1);

        clusterConfig.one(defineNumberCommand(104), 2, true);
        clusterConfig.connect(leader2);
        clusterConfig.one(defineNumberCommand(105), servers, true);
    }
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
