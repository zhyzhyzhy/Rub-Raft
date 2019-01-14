package cc.lovezhy.raft.server.mock6824;

import cc.lovezhy.raft.server.log.Command;
import cc.lovezhy.raft.server.node.RaftNode;
import cc.lovezhy.raft.server.utils.Pair;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.server.mock6824.Utils.*;
import static cc.lovezhy.raft.server.util.NodeUtils.makeCluster;

public class Mock6824Test {

    private final Logger log = LoggerFactory.getLogger(Mock6824Test.class);

    private static final long RAFT_ELECTION_TIMEOUT = 1000;

    @Test
    public void testInitialElection2A() {
        List<RaftNode> raftNodes = makeCluster(3);
        raftNodes.forEach(RaftNode::init);
        log.info("Test (2A): initial election");
        checkOneLeader(raftNodes);


        pause(TimeUnit.MILLISECONDS.toMillis(50));
        long term1 = checkTerms(raftNodes);
        pause(TimeUnit.MILLISECONDS.toMillis(50));
        long term2 = checkTerms(raftNodes);

        if (term1 != term2) {
            log.warn("warning: term changed even though there were no failures");
        }
        checkOneLeader(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    @Test
    public void testReElection2A() {
        List<RaftNode> raftNodes = makeCluster(3);
        raftNodes.forEach(RaftNode::init);

        log.info("Test (2A): election after network failure");

        RaftNode leader1 = checkOneLeader(raftNodes);
        leader1.close();

        checkOneLeader(raftNodes);

        leader1.init();
        RaftNode leader2 = checkOneLeader(raftNodes);

        leader2.close();
        int leader2Index = raftNodes.indexOf(leader2);
        raftNodes.get((leader2Index + 1) % raftNodes.size() ).close();
        pause(2 * RAFT_ELECTION_TIMEOUT);

        checkNoLeader(raftNodes);
        raftNodes.get((leader2Index+ 1) % raftNodes.size()).init();

        checkOneLeader(raftNodes);
        leader2.init();

        checkOneLeader(raftNodes);

        raftNodes.forEach(RaftNode::close);
    }


    /**
     * 这里有个不一致的地方，6.824中，成为Leader之后不进行一个Dummy Log的Commit
     */
    @Test
    public void testBasicAgree2B() {
        int servers = 5;
        List<RaftNode> raftNodes = makeCluster(servers);
        raftNodes.forEach(RaftNode::init);
        log.info("Test (2B): basic agreement");

        //由于提交了两条dummy，所以从2开始
        for (int i = 2; i < 6; i++) {
            Pair<Integer, Command> integerCommandPair = nCommitted(raftNodes, i);
            if (integerCommandPair.getKey() > 0) {
                fail("some one has committed before Start()");
            }
            int xindex = one(raftNodes, randomCommand(), servers, false);
            if (xindex != i) {
                fail("got index {} but expected {}", xindex, i);
            }
        }

        raftNodes.forEach(RaftNode::close);
    }

    @Test
    public void testFailAgree2B() {
        int servers = 3;
        List<RaftNode> raftNodes = makeCluster(servers);
        raftNodes.forEach(RaftNode::init);

        log.info("Test (2B): agreement despite follower disconnection");

        one(raftNodes, randomCommand(), servers, false);
        RaftNode leader = checkOneLeader(raftNodes);
        int leaderIndex = raftNodes.indexOf(leader);
        raftNodes.get((leaderIndex + 1) % raftNodes.size()).close();

        one(raftNodes, randomCommand(), servers - 1, false);
        one(raftNodes, randomCommand(), servers - 1, false);
        pause(RAFT_ELECTION_TIMEOUT);

        raftNodes.get((leaderIndex + 1) % raftNodes.size()).init();

        one(raftNodes, randomCommand(), servers, false);
        one(raftNodes, randomCommand(), servers, false);

        raftNodes.forEach(RaftNode::close);
    }

    @Test
    public void testFailNoAgree2B() {
        int servers = 5;
        List<RaftNode> raftNodes = makeCluster(5);
        raftNodes.forEach(RaftNode::init);
        log.info("Test (2B): no agreement if too many followers disconnect");
        one(raftNodes, randomCommand(), servers, false);

        RaftNode leader = checkOneLeader(raftNodes);
        int leaderIndex = raftNodes.indexOf(leader);
        raftNodes.get((leaderIndex + 1) % raftNodes.size()).close();
        raftNodes.get((leaderIndex + 2) % raftNodes.size()).close();
        raftNodes.get((leaderIndex + 3) % raftNodes.size()).close();

        boolean appendSuccess = leader.getOuterService().appendLog(randomCommand());
        if (!appendSuccess) {
            fail("leader rejected AppendLog");
        }

        long lastLogIndex = leader.getLogService().getLastLogIndex();
        if (lastLogIndex != 3) {
            fail("expected index 2, got {}", lastLogIndex);
        }
        pause(2 * RAFT_ELECTION_TIMEOUT);

        Pair<Integer, Command> integerCommandPair = nCommitted(raftNodes, Math.toIntExact(lastLogIndex));
        if (integerCommandPair.getKey() > 0) {
            fail("{} committed but no majority", integerCommandPair.getKey());
        }
        raftNodes.get((leaderIndex + 1) % raftNodes.size()).init();
        raftNodes.get((leaderIndex + 2) % raftNodes.size()).init();
        raftNodes.get((leaderIndex + 3) % raftNodes.size()).init();

        RaftNode leader2 = checkOneLeader(raftNodes);
        appendSuccess = leader2.getOuterService().appendLog(randomCommand());
        long lastLogIndex2 = leader.getLogService().getLastLogIndex();
        if (!appendSuccess) {
            fail("leader2 rejected appendLog");
        }
        if (lastLogIndex2 < 3 || lastLogIndex2 > 4) {
            fail("unexpected index {}", lastLogIndex2);
        }
        one(raftNodes, randomCommand(), servers, false);

        raftNodes.forEach(RaftNode::close);
    }

}
