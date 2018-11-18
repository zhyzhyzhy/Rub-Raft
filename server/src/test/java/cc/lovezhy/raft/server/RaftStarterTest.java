package cc.lovezhy.raft.server;

import java.util.Properties;

public class RaftStarterTest {


    public static void starter1() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "3");
        properties.setProperty("local", "localhost:5283:0");
        properties.setProperty("peer", "localhost:5285:1,localhost:5287:2");
        new RaftStarter().start(properties);
    }

    public static void starter2() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "3");
        properties.setProperty("local", "localhost:5285:1");
        properties.setProperty("peer", "localhost:5283:0,localhost:5287:2");
        new RaftStarter().start(properties);
    }

    public static void starter3() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "3");
        properties.setProperty("local", "localhost:5287:2");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0");
        new RaftStarter().start(properties);
    }

    public static void main(String[] args) {
        starter1();
        starter2();
        starter3();
    }

}
