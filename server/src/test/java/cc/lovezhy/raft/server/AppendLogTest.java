package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.server.log.DefaultCommand;
import cc.lovezhy.raft.server.log.LogConstants;
import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cc.lovezhy.raft.server.RaftConstants.HEART_BEAT_TIME_INTERVAL;
import static cc.lovezhy.raft.server.log.LogServiceImpl.MAX_LOG_BEFORE_TAKE_SNAPSHOT;
import static cc.lovezhy.raft.server.util.HttpUtils.getKVData;
import static cc.lovezhy.raft.server.util.HttpUtils.postCommand;
import static cc.lovezhy.raft.server.util.NodeUtils.findLeader;
import static cc.lovezhy.raft.server.util.NodeUtils.makeCluster;

public class AppendLogTest {


    private static final Logger log = LoggerFactory.getLogger(AppendLogTest.class);

    private List<RaftNode> raftNodes;
    private Map<String, Object> expectKVData = Maps.newHashMap();


    @Before
    public void setUp() {
        expectKVData.clear();
        expectKVData.put(LogConstants.getDummyCommand().getKey(), LogConstants.getDummyCommand().getValue());
    }


    @After
    public void clear() {
        if (Objects.nonNull(raftNodes)) {
            raftNodes.forEach(RaftNode::close);
        }
    }


    @Test
    public void appendLogTest() {
        this.raftNodes = makeCluster(5);
        this.raftNodes.forEach(raftNode -> {
            log.info("{} => {}", raftNode.getNodeId(), raftNode.getEndPoint());
        });
        raftNodes.forEach(RaftNode::init);
        RaftNode leader = findLeader(raftNodes);
        Preconditions.checkNotNull(leader, "Leader Not Found!");

        EndPoint rpcEndPoint = leader.getEndPoint();
        EndPoint httpEndPoint = EndPoint.create(rpcEndPoint.getHost(), rpcEndPoint.getPort() + 1);

        for (int i = 0; i < MAX_LOG_BEFORE_TAKE_SNAPSHOT - 10; i++) {
            DefaultCommand command = DefaultCommand.setCommand(String.valueOf(i), String.valueOf(i));
            postCommand(httpEndPoint, command);
            putData(command);
        }
        assertData();
    }

    private void putData(DefaultCommand defaultCommand) {
        expectKVData.put(defaultCommand.getKey(), defaultCommand.getValue());
    }

    private void assertData() {
        pause(HEART_BEAT_TIME_INTERVAL * 20);
        raftNodes.forEach(raftNode -> {
            Map<String, Object> nodeData = getKVData(raftNode);
            expectKVData.forEach((key, value) -> {
                Assert.assertEquals(raftNode.getNodeId().toString(), value, nodeData.get(key));
            });
        });
    }

    private void pause(long mills) {
        Preconditions.checkState(mills > 0);
        try {
            Thread.sleep(mills);
        } catch (InterruptedException e) {
            //ignore
        }
    }
}
