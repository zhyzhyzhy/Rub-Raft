package cc.lovezhy.raft.rpc.service;

import cc.lovezhy.raft.rpc.common.RpcExecutors;
import cc.lovezhy.raft.rpc.server.service.ServiceClient;
import cc.lovezhy.raft.rpc.server.service.ServiceServer;
import cc.lovezhy.raft.rpc.server.utils.EndPoint;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class ServerTest {

    private EndPoint endPoint;

    private ServiceClient client;
    private ServiceServer server;

    @Before
    public void setUp() {
        endPoint = EndPoint.create("127.0.0.1", 5283);

        client = new ServiceClient(endPoint);
        server = new ServiceServer(endPoint);
    }

    @Test
    public void testServerStart() {
        SettableFuture<Void> start = server.start();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        start.addListener(countDownLatch::countDown, RpcExecutors.commonExecutor());

        client.connect();
        server.closeAsync();

        client.closeSync();

    }

}
