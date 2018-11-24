package cc.lovezhy.raft.rpc.util;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdFactoryTest {

    private static final Logger log = LoggerFactory.getLogger(IdFactoryTest.class);

    @Test
    public void generateIdTest() {
        for (int i = 0; i < 10; i++) {
            String id = IdFactory.generateId();
            log.info("id={}", id);
            Assert.assertFalse(id.contains("-"));
            Assert.assertFalse(id.contains(" "));
        }
    }
}
