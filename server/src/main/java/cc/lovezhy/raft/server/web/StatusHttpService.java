package cc.lovezhy.raft.server.web;

import cc.lovezhy.raft.server.node.RaftNode;
import com.google.common.base.Preconditions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusHttpService extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(StatusHttpService.class);

    private HttpServer httpServer;
    private Router router;
    private int port;

    private RaftNode.NodeMonitor nodeMonitor;

    public StatusHttpService(RaftNode.NodeMonitor nodeMonitor, int port) {
        Preconditions.checkNotNull(nodeMonitor);
        this.nodeMonitor = nodeMonitor;
        this.port = port;
        this.vertx = Vertx.vertx();
    }

    public void createHttpServer() {
        this.httpServer = vertx.createHttpServer();
        this.router = Router.router(vertx);
        router.get("/status").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("status", nodeMonitor.getNodeStatus().toString());
            response.putHeader("content-type", "application/json");
            response.end(jsonObject.toString());
        });
        this.httpServer.requestHandler(router::accept).listen(this.port);
        log.info("start httpServer at port={}", this.port);
    }

    public void close() {
        try {
            stop();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
