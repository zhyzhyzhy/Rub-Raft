package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static cc.lovezhy.raft.server.util.NodeUtils.create3RaftNodes;
import static cc.lovezhy.raft.server.util.NodeUtils.create5RaftNodes;

public class NodeElectionTest {

    private final Logger log = LoggerFactory.getLogger(NodeElectionTest.class);

    /**
     * 3节点，选出一个Leader
     */
    @Test
    public void election3Test() {
        List<RaftNode> raftNodes = create3RaftNodes();
        raftNodes.forEach(RaftNode::init);
        log.info("check one leader");
        checkElection(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    /**
     * 5节点，选出一个Leader
     */
    @Test
    public void election5Test() {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        log.info("check one leader");
        checkElection(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    /**
     * 5节点选出Leader
     * Leader Down
     * 4节点选出Leader
     */
    @Test
    public void electionLeaderDownTest() {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        log.info("check one leader");
        RaftNode leader = checkElection(raftNodes);
        log.info("leader down");
        leader.close();
        log.info("check one leader");
        checkElection(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    /**
     * 5节点选出Leader
     * Leader Down
     * 4节点选出Leader
     * Leader Down
     * 3节点选出Leader
     */
    @Test
    public void electionLeaderDownAndLeaderDownTest() throws InterruptedException {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        log.info("check one leader");
        RaftNode leader = checkElection(raftNodes);
        log.info("leader down");
        leader.close();
        log.info("check one leader");
        leader = checkElection(raftNodes);
        log.info("leader down");
        leader.close();
        log.info("check one leader");
        checkElection(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    /**
     * 5节点选出Leader
     * Leader Down
     * 4节点选出Leader
     * Last Leader Up
     * 只有一个Leader
     */
    @Test
    public void electionLeaderDownAndReconnectTest() throws InterruptedException {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        log.info("check one leader");
        RaftNode leader = checkElection(raftNodes);
        log.info("leader down");
        leader.close();
        log.info("check one leader");
        checkElection(raftNodes);
        log.info("Downed Node Up");
        leader.init();
        log.info("check one leader");
        checkElection(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    /**
     * 5节点选出Leader
     * 1个Follower Down
     * Leader保持不变
     * Follower Up
     * 只有一个Leader
     */
    @Test
    public void election1FollowerDownTest() throws InterruptedException {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        log.info("check one leader");
        RaftNode leader = checkElection(raftNodes);
        Set<RaftNode> raftNodeSet = Sets.newHashSet(raftNodes);
        raftNodeSet.remove(leader);
        RaftNode follower = raftNodeSet.iterator().next();
        log.info(MessageFormat.format("ready to down, follower={0}", follower.getNodeId().toString()));
        follower.close();

        log.info("check one leader");
        Assert.assertEquals(checkElection(raftNodes), leader);
        log.info("leader is still " + leader.getNodeId());

        Thread.sleep(3000);
        log.info("try to reconnect");
        follower.init();
        log.info("check one leader");
        Assert.assertEquals(checkElection(raftNodes), leader);
        raftNodes.forEach(RaftNode::close);
    }

    /**
     * 5节点选出Leader
     * 2个Follower Down
     * Leader保持不变
     * Followers up
     * 只有一个Leader
     */
    @Test
    public void election2FollowerDownTest() throws InterruptedException {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        log.info("check one leader");
        RaftNode leader = checkElection(raftNodes);
        Set<RaftNode> raftNodeSet = Sets.newHashSet(raftNodes);
        raftNodeSet.remove(leader);
        Iterator<RaftNode> iterator = raftNodeSet.iterator();
        RaftNode follower1 = iterator.next();
        RaftNode follower2 = iterator.next();
        log.info(MessageFormat.format("ready to down, follower={0}", follower1.getNodeId().toString()));
        log.info(MessageFormat.format("ready to down, follower={0}", follower2.getNodeId().toString()));
        follower1.close();
        follower2.close();

        log.info("check one leader");
        Assert.assertEquals(checkElection(raftNodes), leader);
        log.info("leader is still " + leader.getNodeId());

        Thread.sleep(3000);
        log.info("try to reconnect");
        follower1.init();
        follower2.init();
        log.info("check one leader");
        Assert.assertEquals(checkElection(raftNodes), leader);
        raftNodes.forEach(RaftNode::close);
    }

    /**
     * @param raftNodes running raftNodes
     * @return Leader Node
     */
    private RaftNode checkElection(List<RaftNode> raftNodes) {
        int num = 0;
        NodeId nodeId = null;
        long term = 0;
        RaftNode leader = null;
        for (int i = 0; i < 200; i++) {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                //ignore
            }
            num = 0;
            for (RaftNode raftNode : raftNodes) {
                if (raftNode.getNodeScheduler().isLeader()) {
                    num++;
                    nodeId = raftNode.getNodeId();
                    term = raftNode.getCurrentTerm();
                    leader = raftNode;
                }
            }
        }
        log.info("leader num ={}", num);
        log.info("leader is ={} term={}", nodeId, term);
        Assert.assertEquals(1, num);
        return leader;
    }
}
