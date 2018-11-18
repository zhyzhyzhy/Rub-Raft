package cc.lovezhy.raft.server.raftStarter;

import cc.lovezhy.raft.server.RaftStarter;

import java.util.Properties;

public class Starter {

}

class Starter1 {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5283:0");
        properties.setProperty("peer", "localhost:5285:1,localhost:5287:2,localhost:5289:3,localhost:5291:4");
        new RaftStarter().start(properties);
    }
}

class Starter2 {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5285:1");
        properties.setProperty("peer", "localhost:5283:0,localhost:5287:2,localhost:5289:3,localhost:5291:4");
        new RaftStarter().start(properties);
    }
}

class Starter3 {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5287:2");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5289:3,localhost:5291:4");
        new RaftStarter().start(properties);
    }
}

class Starter4 {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5289:3");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5287:2,localhost:5291:4");
        new RaftStarter().start(properties);
    }
}

class Starter5 {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("cluster.nodes", "5");
        properties.setProperty("local", "localhost:5291:4");
        properties.setProperty("peer", "localhost:5285:1,localhost:5283:0,localhost:5287:2,localhost:5289:3");
        new RaftStarter().start(properties);
    }
}