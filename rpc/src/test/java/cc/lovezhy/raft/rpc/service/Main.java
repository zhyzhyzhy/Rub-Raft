package cc.lovezhy.raft.rpc.service;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.rpc.RpcClient;
import cc.lovezhy.raft.rpc.RpcServer;

public class Main {
    public static void main(String[] args) {
        EndPoint endPoint = EndPoint.create("127.0.0.1", 5283);
        RpcServer rpcServer = new RpcServer();
        rpcServer.registerService(ExampleServiceImpl.class);
        rpcServer.start(endPoint);
        ExampleService exampleService = RpcClient.create(ExampleService.class, endPoint);
        System.out.println(exampleService.plusOne(23));
        exampleService.sout("zhuyichen");
    }
}
