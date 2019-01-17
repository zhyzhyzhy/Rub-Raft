package cc.lovezhy.raft.server.mock6824;

import cc.lovezhy.raft.server.log.*;
import cc.lovezhy.raft.server.node.RaftNode;
import cc.lovezhy.raft.server.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.server.RaftConstants.getRandomStartElectionTimeout;

public class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static RaftNode checkOneLeader(List<RaftNode> raftNodes) {
        for (int i = 0; i < 10; i++) {
            pause(getRandomStartElectionTimeout() + 200);
            //term -> raftNodes
            Map<Long, List<RaftNode>> leaders = Maps.newHashMap();
            raftNodes.forEach(raftNode -> {
                if (raftNode.isOnNet() && raftNode.getNodeScheduler().isLeader()) {
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
            if (raftNode.isOnNet() && raftNode.getNodeScheduler().isLeader()) {
                fail("expected no leader, but {} claims to be leader", raftNode.getNodeId());
            }
        }
    }

    public static Pair<Integer, Command> nCommitted(List<RaftNode> raftNodes, int index) {
        int count = 0;
        Command defaultCommand = LogConstants.getDummyCommand();

        for (RaftNode raftNode : raftNodes) {
            LogService logService = raftNode.getLogService();
            LogEntry entry = null;
            if (index <= logService.getLastCommitLogIndex()) {
                entry = logService.get(index);
            }
            if (Objects.nonNull(entry)) {
                if (count > 0 && !Objects.equals(defaultCommand, entry.getCommand())) {
                    fail("committed values do not match: index {}, {}, {}", index, defaultCommand, entry.getCommand());
                }
                count++;
                defaultCommand = entry.getCommand();
            }
        }
        return Pair.of(count, defaultCommand);
    }

    /**
     * 我理解的就是commit一个Command，然后返回LogEntry的Index
     */
    public static int one(List<RaftNode> raftNodes, Command command, int expectedServers, boolean retry) {
        RaftNode leader = null;
        long current = System.currentTimeMillis();
        while (System.currentTimeMillis() - current < TimeUnit.SECONDS.toMillis(10)) {
            try {
                leader = checkOneLeader(raftNodes);
                if (Objects.nonNull(leader)) {
                    break;
                }
            } catch (Exception e) {
                //ignore
            }
        }
        if (Objects.isNull(leader)) {
            fail("one({}) failed to reach agreement", command);
        }

        leader.getOuterService().appendLog((DefaultCommand) command);
        long lastLogIndex = leader.getLogService().getLastLogIndex();

        int times = retry ? 2 : 1;
        while (times >= 1) {
            long t0 = System.currentTimeMillis();
            while (System.currentTimeMillis() - t0 < TimeUnit.SECONDS.toMillis(2)) {
                Pair<Integer, Command> commandPair = nCommitted(raftNodes, Math.toIntExact(lastLogIndex));
                if (commandPair.getKey() > 0 && commandPair.getKey() >= expectedServers) {
                    if (Objects.equals(command, commandPair.getValue())) {
                        return Math.toIntExact(lastLogIndex);
                    }
                }
            }
            times--;
        }
        fail("one({}) failed to reach agreement", command);
        return -1;
    }


    public static DefaultCommand randomCommand() {
        return DefaultCommand.setCommand(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public static DefaultCommand defineNumberCommand(int i) {
        return DefaultCommand.setCommand(String.valueOf(i), String.valueOf(i));
    }

    public static void pause(long mills) {
        try {
            Thread.sleep(mills);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
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

    public static Command waitNCommitted(List<RaftNode> raftNodes, int index, int n, long startTerm) {
        long to = TimeUnit.MILLISECONDS.toMillis(10);
        for (int i = 0; i < 30; i++) {
            Pair<Integer, Command> integerCommandPair = nCommitted(raftNodes, index);
            if (integerCommandPair.getKey() >= n) {
                break;
            }
            pause(to);
            if (to < TimeUnit.SECONDS.toMillis(1)) {
                to *= 2;
            }
            if (startTerm > -1) {
                for (RaftNode raftNode : raftNodes) {
                    if (raftNode.getCurrentTerm() > startTerm) {
                        return null;
                    }
                }
            }
        }
        Pair<Integer, Command> integerCommandPair = nCommitted(raftNodes, index);
        if (integerCommandPair.getKey() < n) {
            fail("only {} decided for index {}; wanted {}", integerCommandPair.getKey(), index, n);
        }
        return integerCommandPair.getValue();
    }


    public static void start1(RaftNode raftNode) {
        raftNode.close();
        raftNode.init();
    }

    public static void fail(String errMsg, Object... objects) {
        log.error(errMsg, objects);
        throw new FailException();
    }
}
