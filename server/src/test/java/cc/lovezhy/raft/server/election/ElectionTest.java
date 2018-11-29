package cc.lovezhy.raft.server.election;

import cc.lovezhy.raft.server.RaftStarter;
import cc.lovezhy.raft.server.node.NodeId;
import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class ElectionTest {
    @Test
    public void election3Test() throws InterruptedException {
        List<RaftNode> raftNodes = create3RaftNodes();
        raftNodes.forEach(RaftNode::init);
        System.out.println("check one leader");
        election(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    @Test
    public void election5Test() throws InterruptedException {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        System.out.println("check one leader");
        election(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    @Test
    public void electionLeaderDownTest() throws InterruptedException {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        System.out.println("check one leader");
        RaftNode leader = election(raftNodes);
        System.out.println("leader down");
        leader.close();
        System.out.println("check one leader");
        election(raftNodes);
        raftNodes.forEach(RaftNode::close);
    }

    @Test
    public void electionFollowerDownTest() throws InterruptedException {
        List<RaftNode> raftNodes = create5RaftNodes();
        raftNodes.forEach(RaftNode::init);
        System.out.println("check one leader");
        RaftNode leader = election(raftNodes);
        Set<RaftNode> raftNodeSet = Sets.newHashSet(raftNodes);
        raftNodeSet.remove(leader);
        RaftNode follower = raftNodeSet.iterator().next();
        System.out.println(MessageFormat.format("ready to down, follower={0}", follower.getNodeId().toString()));
        follower.close();

        System.out.println("check one leader");
        Assert.assertEquals(election(raftNodes), leader);
        System.out.println("leader is still " + leader.getNodeId());

        Thread.sleep(3000);
        System.out.println("try to reconnect");
        follower.init();
        raftNodes.forEach(RaftNode::reconnect);
        System.out.println("check one leader");
        Assert.assertEquals(election(raftNodes), leader);
    }

    //return leaderNode
    private RaftNode election(List<RaftNode> raftNodes) throws InterruptedException {
        int num = 0;
        NodeId nodeId = null;
        long term = 0;
        RaftNode leader = null;
        for (int i = 0; i < 100; i++) {
            Thread.sleep(20L);
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
        Assert.assertEquals(1, num);
        System.out.println("leader num = " + num);
        System.out.println("leader is =" + nodeId + " term=" + term);
        return leader;
    }

    private List<RaftNode> create3RaftNodes() {
        List<RaftNode> raftNodes = Lists.newArrayList();
        Properties properties = new Properties();

        properties.setProperty("cluster.nodes", "3");
        properties.setProperty("local", "localhost:5283:0");
        properties.setProperty("peer", "localhost:5285:1,localhost:5287:2");
        raftNodes.add(new RaftStarter().start(properties));

        properties.setProperty("cluster.nodes", "3");
        properties.setProperty("local", "localhost:5285:1");
        properties.setProperty("peer", "localhost:5283:0,localhost:5287:2");
        raftNodes.add(new RaftStarter().start(properties));

        properties.setProperty("cluster.nodes", "3");
        properties.setProperty("local", "localhost:5287:2");
        properties.setProperty("peer", "localhost:5283:0,localhost:5285:1");
        raftNodes.add(new RaftStarter().start(properties));
        return raftNodes;
    }

    private List<RaftNode> create5RaftNodes() {
        List<RaftNode> raftNodes = Lists.newArrayList();
        Properties properties = new Properties();

        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5283:0");
        properties.setProperty("peer", "localhost:5285:1,localhost:5287:2,localhost:5289:3,localhost:5291:4");
        raftNodes.add(new RaftStarter().start(properties));

        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5285:1");
        properties.setProperty("peer", "localhost:5283:0,localhost:5287:2,localhost:5289:3,localhost:5291:4");
        raftNodes.add(new RaftStarter().start(properties));

        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5287:2");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5289:3,localhost:5291:4");
        raftNodes.add(new RaftStarter().start(properties));


        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5289:3");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5287:2,localhost:5291:4");
        raftNodes.add(new RaftStarter().start(properties));

        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5291:4");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5287:2,localhost:5289:3");
        raftNodes.add(new RaftStarter().start(properties));
        return raftNodes;
    }
}
