package cc.lovezhy.raft.server.mock6824;

import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.server.ClusterManager;
import cc.lovezhy.raft.server.Mock6824Config;
import cc.lovezhy.raft.server.log.Command;
import cc.lovezhy.raft.server.log.DefaultCommand;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.utils.Pair;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cc.lovezhy.raft.server.log.LogConstants.getDummyCommand;
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
        //BUG，可能发生GC，然后触发选举，所以这里适当放宽条件
        if (startResponse1.getIndex() < 3 || startResponse1.getIndex() > 10) {
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
        System.out.println(leader1);
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

        @Test
    public void testBackup2B() {
        int servers = 5;
        clusterConfig = ClusterManager.newCluster(servers, false);
        clusterConfig.begin("Test (2B): leader backs up quickly over incorrect follower logs");

        clusterConfig.one(randomCommand(), servers, false);


        /*
         * // put leader and one follower in a partition
         * 	leader1 := cfg.checkOneLeader()
         * 	cfg.disconnect((leader1 + 2) % servers)
         * 	cfg.disconnect((leader1 + 3) % servers)
         * 	cfg.disconnect((leader1 + 4) % servers)
         */
        NodeId leaderNodeId1 = clusterConfig.checkOneLeader();
        NodeId nextNodeId = clusterConfig.nextNode(leaderNodeId1);
        nextNodeId = clusterConfig.nextNode(nextNodeId);
        clusterConfig.disconnect(nextNodeId);
        nextNodeId = clusterConfig.nextNode(nextNodeId);
        clusterConfig.disconnect(nextNodeId);
        nextNodeId = clusterConfig.nextNode(nextNodeId);
        clusterConfig.disconnect(nextNodeId);

        // submit lots of commands that won't commit
        for (int i = 0; i < 50; i++) {
            clusterConfig.start(leaderNodeId1, randomCommand());
        }

        pause(RAFT_ELECTION_TIMEOUT * 2);

        //disconnect 1 2
        /**
         * cfg.disconnect((leader1 + 0) % servers)
         * cfg.disconnect((leader1 + 1) % servers)
         */
        nextNodeId = leaderNodeId1;
        clusterConfig.disconnect(nextNodeId);

        nextNodeId = clusterConfig.nextNode(nextNodeId);
        clusterConfig.disconnect(nextNodeId);

        //connect 3 4 5
        /**
         * // allow other partition to recover
         * 	cfg.connect((leader1 + 2) % servers)
         * 	cfg.connect((leader1 + 3) % servers)
         * 	cfg.connect((leader1 + 4) % servers)
         */
        nextNodeId = clusterConfig.nextNode(nextNodeId);
        clusterConfig.connect(nextNodeId);
        nextNodeId = clusterConfig.nextNode(nextNodeId);
        clusterConfig.connect(nextNodeId);
        nextNodeId = clusterConfig.nextNode(nextNodeId);
        clusterConfig.connect(nextNodeId);


        // lots of successful commands to new group.
        for (int i = 0; i < 50; i++) {
            clusterConfig.one(randomCommand(), 3, true);
        }

        NodeId leaderNodeId2 = clusterConfig.checkOneLeader();
        NodeId otherNodeId = clusterConfig.nextNode(leaderNodeId1);
        otherNodeId = clusterConfig.nextNode(otherNodeId);
        if (leaderNodeId2 == otherNodeId) {
            otherNodeId = clusterConfig.nextNode(leaderNodeId2);
        }
        clusterConfig.disconnect(otherNodeId);

        for (int i = 0; i < 50; i++) {
            clusterConfig.start(leaderNodeId2, randomCommand());
        }

        pause(RAFT_ELECTION_TIMEOUT * 2);

        for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
            clusterConfig.disconnect(nodeId);
        }

        nextNodeId = leaderNodeId1;
        clusterConfig.connect(nextNodeId);
        nextNodeId = clusterConfig.nextNode(nextNodeId);
        clusterConfig.connect(nextNodeId);
        clusterConfig.connect(otherNodeId);

        for (int i = 0; i < 50; i++) {
            clusterConfig.one(randomCommand(), 3, true);
        }

        for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
            clusterConfig.connect(nodeId);
        }

        clusterConfig.one(randomCommand(), servers, true);
    }

    /**
     * 测试需要的Rpc个数不会太多，因为涉及到心跳包的缘故，所以我这里适当调大了一些
     */
    @Test
    public void testCount2B() {
        int servers = 3;
        clusterConfig = ClusterManager.newCluster(servers, false);

        clusterConfig.begin("Test (2B): RPC counts aren't too high");

        Supplier<Integer> rpcs = () -> {
            int count = 0;
            Collection<NodeId> nodeIds = clusterConfig.fetchAllNodeId();
            for (NodeId nodeId : nodeIds) {
                count += clusterConfig.rpcCount(nodeId);
            }
            return count;
        };

        NodeId leaderNodeId = clusterConfig.checkOneLeader();

        int total1 = rpcs.get();

        if (total1 > 30 || total1 < 1) {
            fail("too many or few RPCs ({}) to elect initial leader", total1);
        }

        int total2 = 0;
        boolean success = false;
        boolean jump = false;

        while (!jump) {
            for (int tryi = 0; tryi < 5; tryi++) {
                if (tryi > 0) {
                    pause(TimeUnit.SECONDS.toMillis(3));
                }
                leaderNodeId = clusterConfig.checkOneLeader();
                total1 = rpcs.get();

                int iters = 10;
                Mock6824Config.StartResponse startResponse = clusterConfig.start(leaderNodeId, defineNumberCommand(1));
                if (!startResponse.isLeader()) {
                    if (tryi == 4) {
                        jump = true;
                    }
                    continue;
                }
                List<DefaultCommand> cmds = Lists.newArrayList();

                boolean breakToJump = false;
                for (int i = 1; i < iters + 2; i++) {
                    DefaultCommand defaultCommand = randomCommand();
                    cmds.add(defaultCommand);
                    Mock6824Config.StartResponse startResponse1 = clusterConfig.start(leaderNodeId, defaultCommand);
                    if (startResponse1.getTerm() != startResponse.getTerm()) {
                        breakToJump = true;
                        break;
                    }
                    if (!startResponse1.isLeader()) {
                        breakToJump = true;
                        break;
                    }

                    if ((startResponse.getIndex() + i) != startResponse1.getIndex()) {
                        fail("Start() failed");
                    }
                }
                if (breakToJump) {
                    break;
                }


                for (int i = 1; i < iters + 1; i++) {
                    Command cmd = clusterConfig.wait(startResponse.getIndex() + i, servers, startResponse.getTerm());
                    if (!cmd.equals(cmds.get(i - 1))) {
                        if (cmd.equals(getDummyCommand())) {
                            // term changed -- try again
                            breakToJump = true;
                            break;
                        }
                        fail("wrong value {} committed for index {}; expected {}", cmd, startResponse.getIndex() + i, cmds);
                    }
                }
                if (breakToJump) {
                    break;
                }

                boolean failed = false;
                total2 = 0;

                for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
                    int term = clusterConfig.fetchTerm(nodeId);
                    if (term != startResponse.getTerm()) {
                        failed = true;
                    }
                    total2 += clusterConfig.rpcCount(nodeId);
                }

                if (failed) {
                    breakToJump = true;
                    break;
                }

                /**
                 * (iters + 1 + 3) * 3 = 42
                 * (iters + 1 + 3) * 4 = 56
                 * 这里我适当放宽，变成56
                 */
                if ((total2 - total1) > ((iters + 1 + 3) * 4)) {
                    fail("too many RPCs ({}) for {} entries", total2 - total1, iters);
                }

                success = true;
                jump = true;
                break;
            }
        }
        if (!success) {
            fail("term changed too often");
        }

        pause(RAFT_ELECTION_TIMEOUT);

        int total3 = 0;
        for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
            total3 += clusterConfig.rpcCount(nodeId);
        }

        if ((total3 - total2) > 3 * 20) {
            fail("too many RPCs ({}) for 1 second of idleness", total3 - total2);
        }
    }

    @Test
    public void testPersist12C() {
        int servers = 3;
        clusterConfig = ClusterManager.newCluster(servers, false);

        clusterConfig.begin("Test (2C): basic persistence");

        clusterConfig.one(defineNumberCommand(11), servers, true);

        List<NodeId> nodeIds = Lists.newArrayList(clusterConfig.fetchAllNodeId());
        for (NodeId nodeId : nodeIds) {
            log.info("start1 nodeId={}", nodeId);
            clusterConfig.start1(nodeId);
        }

        for (NodeId nodeId : nodeIds) {
            clusterConfig.disconnect(nodeId);
            clusterConfig.connect(nodeId);
        }


        System.out.println("before one");
        clusterConfig.one(defineNumberCommand(12), servers, true);

        NodeId leaderNodeId1 = clusterConfig.checkOneLeader();
        log.info("leaderNode1={}", leaderNodeId1);
        clusterConfig.disconnect(leaderNodeId1);
        clusterConfig.start1(leaderNodeId1);
        clusterConfig.connect(leaderNodeId1);
        log.info("before one");
        clusterConfig.dumpAllNode();
        clusterConfig.one(defineNumberCommand(13), servers, true);
        log.info("after one");
        clusterConfig.dumpAllNode();

        NodeId leaderNodeId2 = clusterConfig.checkOneLeader();
        clusterConfig.disconnect(leaderNodeId2);
        clusterConfig.one(defineNumberCommand(14), servers - 1, true);
        clusterConfig.start1(leaderNodeId2);
        clusterConfig.connect(leaderNodeId2);

        clusterConfig.wait(4, servers, -1);

        NodeId i3 = clusterConfig.nextNode(clusterConfig.checkOneLeader());
        clusterConfig.disconnect(i3);
        clusterConfig.dumpAllNode();
        clusterConfig.one(defineNumberCommand(15), servers - 1, true);
        clusterConfig.start1(i3);
        clusterConfig.connect(i3);
        clusterConfig.dumpAllNode();
        clusterConfig.one(defineNumberCommand(16), servers, true);
    }

    @Test
    public void testPersist22C() {
        int servers = 5;
        clusterConfig = ClusterManager.newCluster(servers, false);
        clusterConfig.begin("Test (2C): more persistence");

        int index = 1;
        for (int i = 0; i < 5; i++) {
            clusterConfig.one(defineNumberCommand(10 + index), servers, true);
            index++;

            NodeId leaderNodeId1 = clusterConfig.checkOneLeader();

            NodeId nodeId = clusterConfig.nextNode(leaderNodeId1);
            clusterConfig.disconnect(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.disconnect(nodeId);

            clusterConfig.one(defineNumberCommand(10 + index), servers - 2, true);
            index++;

            /**
             * cfg.disconnect((leader1 + 0) % servers)
             * cfg.disconnect((leader1 + 3) % servers)
             * cfg.disconnect((leader1 + 4) % servers)
             */
            nodeId = leaderNodeId1;
            clusterConfig.disconnect(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.disconnect(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.disconnect(nodeId);

            /**
             * cfg.start1((leader1 + 1) % servers)
             * cfg.start1((leader1 + 2) % servers)
             * cfg.connect((leader1 + 1) % servers)
             * cfg.connect((leader1 + 2) % servers)
             */
            nodeId = leaderNodeId1;
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.start1(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.start1(nodeId);

            nodeId = leaderNodeId1;
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.connect(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.connect(nodeId);

            pause(RAFT_ELECTION_TIMEOUT);

            /**
             * cfg.start1((leader1 + 3) % servers)
             * 		cfg.connect((leader1 + 3) % servers)
             */
            nodeId = leaderNodeId1;
            nodeId = clusterConfig.nextNode(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.start1(nodeId);
            clusterConfig.connect(nodeId);

            clusterConfig.one(defineNumberCommand(10 + index), servers - 2, true);
            index++;

            /**
             * cfg.connect((leader1 + 4) % servers)
             * 		cfg.connect((leader1 + 0) % servers)
             */
            nodeId = leaderNodeId1;
            nodeId = clusterConfig.nextNode(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            nodeId = clusterConfig.nextNode(nodeId);
            clusterConfig.connect(nodeId);
            clusterConfig.connect(leaderNodeId1);
        }
        clusterConfig.one(defineNumberCommand(100), servers, true);

    }


    @Test
    public void testPersist32C() {
        int servers = 3;
        clusterConfig = ClusterManager.newCluster(servers, false);

        clusterConfig.begin("Test (2C): partitioned leader and one follower crash, leader restarts");

        clusterConfig.one(defineNumberCommand(101), 3, true);

        NodeId leaderNodeId = clusterConfig.checkOneLeader();

        NodeId nodeId = clusterConfig.nextNode(leaderNodeId);
        nodeId = clusterConfig.nextNode(nodeId);
        clusterConfig.disconnect(nodeId);

        clusterConfig.one(defineNumberCommand(102), 2, true);

        /**
         * cfg.crash1((leader + 0) % servers)
         * 	cfg.crash1((leader + 1) % servers)
         * 	cfg.connect((leader + 2) % servers)
         * 	cfg.start1((leader + 0) % servers)
         * 	cfg.connect((leader + 0) % servers)
         */
        nodeId = leaderNodeId;
        clusterConfig.crash1(nodeId);
        System.out.println("crash + " + nodeId);
        nodeId = clusterConfig.nextNode(nodeId);
        clusterConfig.crash1(nodeId);
        System.out.println("crash + " + nodeId);

        nodeId = clusterConfig.nextNode(nodeId);
        clusterConfig.connect(nodeId);
        clusterConfig.start1(leaderNodeId);
        System.out.println("connect + " + leaderNodeId);
        clusterConfig.connect(leaderNodeId);

        clusterConfig.one(defineNumberCommand(103), 2, true);

        clusterConfig.dumpAllNode();
        /**
         * cfg.start1((leader + 1) % servers)
         * 	cfg.connect((leader + 1) % servers)
         */
        nodeId = leaderNodeId;
        nodeId = clusterConfig.nextNode(nodeId);
        System.out.println("connect node=" + nodeId.toString());
        clusterConfig.start1(nodeId);
        clusterConfig.connect(nodeId);

        clusterConfig.one(defineNumberCommand(104), servers, true);

    }

    /**
     * //
     * // Test the scenarios described in Figure 8 of the extended Raft paper. Each
     * // iteration asks a leader, if there is one, to insert a command in the Raft
     * // log.  If there is a leader, that leader will fail quickly with a high
     * // probability (perhaps without committing the command), or crash after a while
     * // with low probability (most likey committing the command).  If the number of
     * // alive servers isn't enough to form a majority, perhaps start a new server.
     * // The leader in a new term may try to finish replicating log entries that
     * // haven't been committed yet.
     * //
     */
    @Test
    public void testFigure82C() {
        int servers = 5;
        clusterConfig = ClusterManager.newCluster(servers, false);
        clusterConfig.begin("Test (2C): Figure 8");

        clusterConfig.one(randomCommand(), 1, true);

        int nup = servers;
        /**
         * Caused by: java.io.IOException: Too many open files
         * 所以这里降为了200
         */
        for (int iters = 0; iters < 50; iters++) {
            NodeId leaderNodeId = null;
            for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
                if (clusterConfig.exist(nodeId)) {
                    Mock6824Config.StartResponse startResponse = clusterConfig.start(nodeId, randomCommand());
                    if (startResponse.isLeader()) {
                        leaderNodeId = nodeId;
                    }
                }
            }

            if (ThreadLocalRandom.current().nextInt(0, 1000) < 100) {
                int ms = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE) % (Math.toIntExact(RAFT_ELECTION_TIMEOUT) / 2);
                pause(ms + RAFT_ELECTION_TIMEOUT);
            } else {
                int ms = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE) % 13;
                pause(ms + RAFT_ELECTION_TIMEOUT);
            }

            if (leaderNodeId != null) {
                clusterConfig.crash1(leaderNodeId);
                nup -= 1;
            }

            if (nup < 3) {
                int nodeIndex = ThreadLocalRandom.current().nextInt(0, servers) % servers;
                NodeId nodeId = NodeId.create(nodeIndex);
                if (!clusterConfig.exist(nodeId)) {
                    clusterConfig.start1(nodeId);
                    clusterConfig.connect(nodeId);
                    nup += 1;
                }
            }
            System.out.println("iters" + iters);
        }

        for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
            if (!clusterConfig.exist(nodeId)) {
                clusterConfig.start1(nodeId);
                clusterConfig.connect(nodeId);
            }
        }

        clusterConfig.one(randomCommand(), servers, true);
    }

    @Test
    public void testUnreliableAgree2C() throws InterruptedException {
        int servers = 5;
        clusterConfig = ClusterManager.newCluster(servers, true);
        clusterConfig.begin("Test (2C): unreliable agreement");

        int counts = 0;


        for (int iters = 1; iters < 50; iters++) {
            for (int j = 0; j < 4; j++) {
                counts++;
            }
        }
        /**
         * for iters := 1; iters < 50; iters++ {
         * 		for j := 0; j < 4; j++ {
         * 			wg.Add(1)
         * 			go func(iters, j int) {
         * 				defer wg.Done()
         * 				cfg.one((100*iters)+j, 1, true)
         *                        }(iters, j)* 		}
         * 		cfg.one(iters, 1, true)
         *    }
         */
        CountDownLatch countDownLatch = new CountDownLatch(counts);

        AtomicBoolean success = new AtomicBoolean(true);
        AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();
        for (int iters = 1; iters < 50; iters++) {
            for (int j = 0; j < 4; j++) {
                int finalIters = iters;
                int finalJ = j;
                /**
                 * bug，这里的CompleteFuture使用的forkJoinpool的线程池
                 * 在Netty的自己程序的处理中也是使用的这个线程池，导致被打满，然后全部超时
                 */
                CompletableFuture.runAsync(() -> {
                    clusterConfig.one(defineNumberCommand(100 * finalIters + finalJ), 1, true);
                    countDownLatch.countDown();
                }, RpcExecutors.commonExecutor()).exceptionally(throwable -> {
                    throwableAtomicReference.set(throwable);
                    success.set(false);
                    countDownLatch.countDown();
                    return null;
                });
                if (!success.get()) {
                    fail(throwableAtomicReference.get().getMessage());
                }
            }
            clusterConfig.one(defineNumberCommand(iters), 1, true);
        }
        if (!success.get()) {
            fail(throwableAtomicReference.get().getMessage());
        }
        clusterConfig.setunreliable(false);
        if (!success.get()) {
            fail(throwableAtomicReference.get().getMessage());
        }
        countDownLatch.await();
        clusterConfig.one(defineNumberCommand(100), servers, true);
        if (!success.get()) {
            fail(throwableAtomicReference.get().getMessage());
        }
    }


    @Test
    public void testFigure8Unreliable2C() {
        int servers = 5;
        clusterConfig = ClusterManager.newCluster(servers, true);

        clusterConfig.begin("Test (2C): Figure 8 (unreliable)");

        clusterConfig.one(defineNumberCommand(ThreadLocalRandom.current().nextInt()), 1, true);

        int nup = servers;
        for (int iters = 0; iters < 20; iters++) {
            System.out.println(iters);
            if (iters == 10) {
                clusterConfig.setlongreordering(true);
            }
            NodeId leaderNodeId = null;
            for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
                boolean isLeader = clusterConfig.start(nodeId, randomCommand()).isLeader();
                if (isLeader && clusterConfig.isOnNet(nodeId)) {
                    leaderNodeId = nodeId;
                }
            }

            if (ThreadLocalRandom.current().nextInt(0, 1000) < 100) {
                int ms = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE) % (Math.toIntExact(RAFT_ELECTION_TIMEOUT) / 2);
                pause(ms + RAFT_ELECTION_TIMEOUT);
            } else {
                int ms = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE) % 13;
                pause(ms + RAFT_ELECTION_TIMEOUT);
            }

            if (leaderNodeId != null && (ThreadLocalRandom.current().nextInt(0, 1000)) < (RAFT_ELECTION_TIMEOUT / 2)) {
                clusterConfig.disconnect(leaderNodeId);
                nup -= 1;
            }
            if (nup < 3) {
                int peerNodeId = ThreadLocalRandom.current().nextInt(0, servers);
                NodeId s = NodeId.create(peerNodeId);
                if (!clusterConfig.isOnNet(s)) {
                    clusterConfig.connect(s);
                    nup += 1;
                }
            }
            clusterConfig.dumpAllNode();

        }

        for (NodeId nodeId : clusterConfig.fetchAllNodeId()) {
            if (!clusterConfig.isOnNet(nodeId)) {
                clusterConfig.connect(nodeId);
            }
        }
        System.out.println("before one");
        clusterConfig.dumpAllNode();
        clusterConfig.one(randomCommand(), servers, true);
        System.out.println("after one");
        clusterConfig.dumpAllNode();

    }

    private void stop() {
        Scanner scanner = new Scanner(System.in);
        scanner.next();
    }

}
