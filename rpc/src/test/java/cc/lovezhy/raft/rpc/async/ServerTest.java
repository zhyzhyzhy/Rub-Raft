package cc.lovezhy.raft.rpc.async;

import cc.lovezhy.raft.rpc.*;
import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.rpc.service.ExampleService;
import cc.lovezhy.raft.rpc.service.ExampleServiceImpl;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerTest {

    private static Logger log = LoggerFactory.getLogger(ServerTest.class);

    private EndPoint endPoint;
    private RpcServer rpcServer;
    private AsyncExampleService asyncExampleService;

    @Before
    public void setUp() {
        endPoint = EndPoint.create("127.0.0.1", 5283);
        rpcServer = new RpcServer();
        rpcServer.registerService(AsyncExampleServiceImpl.class);
        rpcServer.start(endPoint);

        RpcClientOptions rpcClientOptions = new RpcClientOptions();
        rpcClientOptions.defineMethodRequestType("getStdName", RpcRequestType.ASYNC);
        asyncExampleService = RpcClient.create(AsyncExampleService.class, endPoint, rpcClientOptions).getInstance();
    }

    @Test
    public void testServerStart() {
        String stdName = asyncExampleService.getStdName();
        Assert.assertNull(stdName);
        SettableFuture<String> stdNameFuture = RpcContext.getContextFuture();
        Futures.addCallback(stdNameFuture, new FutureCallback<String>() {
            @Override
            public void onSuccess(@Nullable String result) {
                System.out.println(result);
            }

            @Override
            public void onFailure(Throwable t) {

            }
        }, RpcExecutors.commonExecutor());

        try {
            Thread.sleep(300000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @After
    public void closeServer() {
//        System.exit(0);
    }

}
