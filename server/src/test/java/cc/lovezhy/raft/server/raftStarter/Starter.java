package cc.lovezhy.raft.server.raftStarter;

import cc.lovezhy.raft.server.RaftStarter;
import org.junit.Test;

import java.util.Properties;

public class Starter {

    @Test
    public void starterTest() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5283:0");
        properties.setProperty("peer", "localhost:5285:1,localhost:5287:2,localhost:5289:3,localhost:5291:4");
        new RaftStarter().start(properties);
    }
}