package cc.lovezhy.raft.rpc.resource;

import cc.lovezhy.raft.rpc.RpcStatistics;
import com.alibaba.fastjson.JSON;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author zhuyichen
 */
public class RpcServerStateResource extends AbstractVerticle implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(RpcServerStateResource.class);

    public static RpcServerStateResource create(int port, RpcStatistics rpcStatistics) {
        checkNotNull(rpcStatistics);
        return new RpcServerStateResource(port, rpcStatistics);
    }

    private RpcStatistics rpcStatistics;

    private HttpServer httpServer;

    private int port;

    private RpcServerStateResource(int port, RpcStatistics rpcStatistics) {
        checkNotNull(rpcStatistics);
        this.port = port;
        this.rpcStatistics = rpcStatistics;
    }

    @Override
    public void start() {
        vertx = Vertx.vertx();
        httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        /**
         * get rpcServer statistic
         */
        router.get("/statistic").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.end(JSON.toJSONString(rpcStatistics.toModel()));
        });

        httpServer.requestHandler(router::accept)
                .listen(port);
    }

    @Override
    public void close() {
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            this.httpServer.close(as -> countDownLatch.countDown());
            countDownLatch.await();
        } catch (Exception e) {
            log.error("close httpServer error, errMsg={}", e.getMessage(), e);
        }
    }
}
