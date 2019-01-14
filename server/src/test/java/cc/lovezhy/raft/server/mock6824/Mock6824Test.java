package cc.lovezhy.raft.server.mock6824;

import cc.lovezhy.raft.server.node.RaftNode;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.server.mock6824.Utils.*;
import static cc.lovezhy.raft.server.util.NodeUtils.makeCluster;

public class TestInitialElection2A {

    private final Logger log = LoggerFactory.getLogger(TestInitialElection2A.class);

    @Test
    public void test() {
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





}
