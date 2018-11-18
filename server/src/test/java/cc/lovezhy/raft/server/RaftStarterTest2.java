package cc.lovezhy.raft.server;

import java.util.Properties;

public class RaftStarterTest2 {
    public static void starter1() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5283:0");
        properties.setProperty("peer", "localhost:5285:1,localhost:5287:2,localhost:5289:3,localhost:5291:4");
        new RaftStarter().start(properties);
    }

    public static void starter2() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5285:1");
        properties.setProperty("peer", "localhost:5283:0,localhost:5287:2,localhost:5289:3,localhost:5291:4");
        new RaftStarter().start(properties);
    }

    public static void starter3() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5287:2");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5289:3,localhost:5291:4");
        new RaftStarter().start(properties);
    }

    public static void starter4() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5289:3");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5287:2,localhost:5291:4");
        new RaftStarter().start(properties);
    }

    public static void starter5() {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5291:4");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5287:2,localhost:5289:3");
        new RaftStarter().start(properties);
    }

    public static void main(String[] args) {
        starter1();
        starter2();
        starter3();
        starter4();
        starter5();
    }
}
