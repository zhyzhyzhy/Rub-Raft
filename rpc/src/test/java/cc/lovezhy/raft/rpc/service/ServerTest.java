package cc.lovezhy.raft.rpc.service;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcClient;
import cc.lovezhy.raft.rpc.RpcProvider;
import org.junit.Before;
import org.junit.Test;

public class ServerTest {

    private EndPoint endPoint;
    private ExampleService exampleService;


    @Before
    public void setUp() {
        System.out.println("cccccc");
        endPoint = EndPoint.create("127.0.0.1", 5283);
        RpcProvider.create(ExampleServiceImpl.class, endPoint);
        exampleService = RpcClient.create(ExampleService.class, endPoint);
    }

    @Test
    public void testServerStart() {
        System.out.println(exampleService.echo("Hello"));
        System.out.println(exampleService.plusOne(2));
    }

}
