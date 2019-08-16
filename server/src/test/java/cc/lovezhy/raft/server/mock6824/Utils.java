package cc.lovezhy.raft.server.mock6824;

import cc.lovezhy.raft.rpc.RpcClientOptions;
import cc.lovezhy.raft.server.FailException;
import cc.lovezhy.raft.server.log.*;
import cc.lovezhy.raft.server.node.PeerRaftNode;
import cc.lovezhy.raft.server.node.RaftNode;
import cc.lovezhy.raft.server.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static cc.lovezhy.raft.server.mock6824.Mock6824Utils.checkOneLeader;

public class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);


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

    public static void setNetReliable(List<RaftNode> raftNodes, boolean reliable) {
        raftNodes.forEach(raftNode -> {
            List<PeerRaftNode> peerRaftNodes = getObjectMember(raftNode, "peerRaftNodes");
            peerRaftNodes.forEach(peerRaftNode -> {
                RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
                rpcClientOptions.setReliable(reliable);
            });
        });
    }

    public static void setIsOnNet(RaftNode targetNode, List<RaftNode> raftNodes, boolean isOnNet) {
        List<PeerRaftNode> targetNodePeerRaftNodes = getObjectMember(targetNode, "peerRaftNodes");
        targetNodePeerRaftNodes.forEach(peerRaftNode -> {
            RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
            rpcClientOptions.setOnNet(isOnNet);
        });
        raftNodes.forEach(raftNode -> {
            if (raftNode == targetNode) {
                return;
            }
            List<PeerRaftNode> peerRaftNodes = getObjectMember(raftNode, "peerRaftNodes");
            peerRaftNodes.forEach(peerRaftNode -> {
                if (peerRaftNode.getNodeId().equals(targetNode.getNodeId())) {
                    RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
                    rpcClientOptions.setOnNet(isOnNet);
                }
            });
        });
    }


    public static void fail(String errMsg, Object... objects) {
        log.error(errMsg, objects);
        throw new FailException();
    }

    /**
     * 判断一个节点是否在Net上
     */
    public static boolean isOnNet(RaftNode raftNode) {
        boolean isOnNet = false;
        List<PeerRaftNode> peerRaftNodes = getObjectMember(raftNode, "peerRaftNodes");
        for (PeerRaftNode peerRaftNode : peerRaftNodes) {
            RpcClientOptions rpcClientOptions = getObjectMember(peerRaftNode, "rpcClientOptions");
            isOnNet = isOnNet | rpcClientOptions.isOnNet();
        }
        return isOnNet;
    }


    @SuppressWarnings("unchecked")
    private static <T> T getObjectMember(Object object, String memberName) {
        try {
            Field field = object.getClass().getDeclaredField(memberName);
            field.setAccessible(true);
            Object o = field.get(object);
            return (T) o;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error(e.getMessage());
        }
        return null;
    }
}
