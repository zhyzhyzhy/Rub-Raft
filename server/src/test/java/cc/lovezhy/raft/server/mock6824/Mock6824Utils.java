package cc.lovezhy.raft.server.mock6824;

import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cc.lovezhy.raft.server.RaftConstants.getRandomStartElectionTimeout;
import static cc.lovezhy.raft.server.mock6824.Utils.*;

public class Mock6824Utils {

    private static final Logger log = LoggerFactory.getLogger(Mock6824Utils.class);

    public static RaftNode checkOneLeader(List<RaftNode> raftNodes) {
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
                return leaders.get(lastTermWithLeader).get(0);
            }
        }
        fail("expected one leader, got none");
        return null;
    }

    public static void checkNoLeader(List<RaftNode> raftNodes) {
        for (RaftNode raftNode : raftNodes) {
            if (isOnNet(raftNode) && raftNode.getNodeScheduler().isLeader()) {
                fail("expected no leader, but {} claims to be leader", raftNode.getNodeId());
            }
        }
    }

    public static long checkTerms(List<RaftNode> raftNodes) {
        long term = -1;
        for (RaftNode raftNode : raftNodes) {
            if (term == -1) {
                term = raftNode.getCurrentTerm();
            } else if (term != raftNode.getCurrentTerm()) {
                log.error("servers disagree on term");
            }
        }
        return term;
    }

    public static void disConnect(RaftNode targetNode, List<RaftNode> raftNodeList) {
        setIsOnNet(targetNode, raftNodeList, false);
    }

    public static void connect(RaftNode targetNode, List<RaftNode> raftNodeList) {
        setIsOnNet(targetNode, raftNodeList, true);
    }
}
