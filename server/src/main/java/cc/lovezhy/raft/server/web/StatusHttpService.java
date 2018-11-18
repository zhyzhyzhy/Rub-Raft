package cc.lovezhy.raft.server.web;

import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class StatusHttpService extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(StatusHttpService.class);

    private RaftNode raftNode;
    private HttpServer httpServer;
    private Router router;

    public StatusHttpService(RaftNode raftNode) {
        Preconditions.checkNotNull(raftNode);
        this.raftNode = raftNode;
        this.vertx = Vertx.vertx();
    }

    private void createHttpServer() {
        this.httpServer = vertx.createHttpServer();
        this.router = Router.router(vertx);
        createRouters();
        this.httpServer.requestHandler(router::accept).listen(5282);
        log.info("start httpServer at port={}", 5282);
    }

    private void createRouters() {
        router.get("/status").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            Map<String, String> map = Maps.newHashMap();
            map.put("hello", "world");
            response.putHeader("content-type", "application/json");
            response.end(Json.encode(map));
        });
    }

    public void start() {
        try {
            createHttpServer();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void stop() {
        try {
            stop(Future.future());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

}
