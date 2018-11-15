package cc.lovezhy.raft.rpc.service;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcClient;
import cc.lovezhy.raft.rpc.RpcProvider;
import cc.lovezhy.raft.rpc.RpcServer;
import org.apache.logging.log4j.message.ExitMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerTest {

    private static Logger log = LoggerFactory.getLogger(ServerTest.class);

    private EndPoint endPoint;
    private RpcServer rpcServer;
    private ExampleService exampleService;

    @Before
    public void setUp() {
        endPoint = EndPoint.create("127.0.0.1", 5283);
        rpcServer = new RpcServer();
        rpcServer.registerService(ExampleServiceImpl.class);
        rpcServer.start(endPoint);
        exampleService = RpcClient.create(ExampleService.class, endPoint);
    }

    @Test
    public void testServerStart() {
        log.info(exampleService.echo("Hello,World"));
        log.info("{}", exampleService.plusOne(23));
    }

    @After
    public void closeServer() {
       System.exit(0);
    }

}
