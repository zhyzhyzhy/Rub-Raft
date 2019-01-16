package cc.lovezhy.raft.server;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.util.IdFactory;
import cc.lovezhy.raft.server.log.DefaultCommand;
import cc.lovezhy.raft.server.log.LogConstants;
import cc.lovezhy.raft.server.log.LogServiceImpl;
import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import io.vertx.core.json.JsonObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cc.lovezhy.raft.server.NodeElectionTest.checkElection;
import static cc.lovezhy.raft.server.RaftConstants.HEART_BEAT_TIME_INTERVAL;
import static cc.lovezhy.raft.server.util.NodeUtils.findLeader;
import static cc.lovezhy.raft.server.util.NodeUtils.makeCluster;
import static cc.lovezhy.raft.server.utils.HttpUtils.getValue;
import static cc.lovezhy.raft.server.utils.HttpUtils.postCommand;

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
        Assert.assertEquals(leader, checkElection(raftNodes));

        Preconditions.checkNotNull(leader, "Leader Not Found!");

        EndPoint rpcEndPoint = leader.getEndPoint();
        EndPoint httpEndPoint = EndPoint.create(rpcEndPoint.getHost(), rpcEndPoint.getPort() + 1);
        long l = System.currentTimeMillis();
        for (int i = 0; i < LogServiceImpl.MAX_LOG_BEFORE_TAKE_SNAPSHOT * 3; i++) {
            if (i % 100 == 0) {
                log.info("i = {}", i);
            }
            DefaultCommand command = DefaultCommand.setCommand(IdFactory.generateId(), IdFactory.generateId());
            JsonObject jsonObject = postCommand(httpEndPoint, command);
            if (jsonObject.getBoolean("success")) {
                putData(command);
            } else {
                log.error("put command {} fail", command);
            }
        }
        assertData();
    }

    private void putData(DefaultCommand defaultCommand) {
        expectKVData.put(defaultCommand.getKey(), defaultCommand.getValue());
    }

    private void assertData() {
        pause(HEART_BEAT_TIME_INTERVAL * 20);
        raftNodes.forEach(raftNode -> {
            expectKVData.forEach((key, value) -> {
                String v = getValue(raftNode, key);
                Assert.assertEquals(raftNode.getNodeId().toString(), value, v);
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
