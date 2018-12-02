package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.DefaultStateMachine;
import cc.lovezhy.raft.server.StateMachine;
import cc.lovezhy.raft.server.storage.StorageType;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.List;

public class LogServiceTest {

    private StateMachine stateMachine;
    private LogService logService;

    private List<LogEntry>  logEntries;

    private static final Logger log = LoggerFactory.getLogger(LogServiceTest.class);

    @Before
    public void setUp() throws FileNotFoundException {
        this.stateMachine = new DefaultStateMachine();
        this.logService = new LogServiceImpl(stateMachine, StorageType.MEMORY);
        this.logEntries = Lists.newArrayList();
        this.logEntries.add(LogEntry.of(DefaultCommand.setCommand("zhuyichen", "0"), 0L));
        this.logEntries.add(LogEntry.of(DefaultCommand.setCommand("zhuyichen1", "1"), 1L));
        this.logEntries.add(LogEntry.of(DefaultCommand.setCommand("zhuyichen2", "2"), 2L));
        this.logEntries.add(LogEntry.of(DefaultCommand.setCommand("zhuyichen3", "3"), 3L));
    }

    @Test(expected = IllegalStateException.class)
    public void getEntryTestOfWrongIndex() {
        logService.get(-1);
    }

    @Test
    public void getEntryTestOfNull() {
        Assert.assertNotNull(logService.get(0));
    }

    @Test
    public void appendLogEntriesTest() {
        logService.appendLog(this.logEntries);
    }

    @Test
    public void getEntryTestOfNotNull() {
        logService.appendLog(this.logEntries);
        for (int i = 0; i < this.logEntries.size(); i++) {
            Assert.assertEquals(this.logEntries.get(i), logService.get(i + 1));
        }
    }

    @Test
    public void getEntryTestOfRange() {
        logService.appendLog(this.logEntries);
        for (int i = 0; i < this.logEntries.size(); i++) {
            for (int j = i; j < this.logEntries.size(); j++) {
                log.info("i={}, j={}", i, j);
                Assert.assertEquals(this.logEntries.subList(i, j), logService.get(i + 1, j));
            }
        }
    }

    @Test
    public void hasInSnapshotTest() {
        logService.appendLog(this.logEntries);
        for (int i = 0; i < this.logEntries.size(); i++) {
            Assert.assertFalse(logService.hasInSnapshot(i));
        }
    }


}
