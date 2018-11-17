package cc.lovezhy.raft.server;

import org.junit.Assert;
import org.junit.Test;

import static cc.lovezhy.raft.server.RaftConstants.getRandomStartElectionTimeout;

public class RaftConstantsTest {

    @Test
    public void getRandomStartElectionTimeoutTest() {
        for (int i = 0; i < 100; i++) {
            long timeout = getRandomStartElectionTimeout();
            Assert.assertTrue(timeout <= 200 && timeout >= 150);
        }
    }
}
