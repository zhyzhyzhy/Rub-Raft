package cc.lovezhy.raft.server.web;

import cc.lovezhy.raft.server.log.ClusterConfCommand;
import cc.lovezhy.raft.server.log.DefaultCommand;
import cc.lovezhy.raft.server.node.RaftNode;
import cc.lovezhy.raft.server.utils.EventRecorder;
import com.google.common.base.Preconditions;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.impl.BodyHandlerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class ClientHttpService extends AbstractVerticle {

    private final Logger log = LoggerFactory.getLogger(ClientHttpService.class);

    private final String COMMAND_FILE_NAME = ClientHttpService.class.getResource("/index.html").getFile();

    private HttpServer httpServer;
    private int port;

    private RaftNode.OuterService outerService;

    public ClientHttpService(RaftNode.OuterService outerService, int port) {
        Preconditions.checkNotNull(outerService);
        this.outerService = outerService;
        this.port = port;
        this.vertx = Vertx.vertx();
    }

    public void createHttpServer() {
        this.httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.route().handler(new BodyHandlerImpl());
        /*
         * 查看节点状态
         */
        router.get("/status").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            JsonObject jsonObject = new JsonObject(outerService.getNodeStatus().toString());
            response.end(jsonObject.toString());
        });

        router.get("/command").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response.sendFile(COMMAND_FILE_NAME);
        });
        /*
         * 向集群写value
         */
        router.post("/command").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            JsonObject bodyJson = routingContext.getBodyAsJson();
            DefaultCommand command = bodyJson.mapTo(DefaultCommand.class);
            JsonObject jsonObject = this.outerService.appendLog(command);
            response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.end(jsonObject.toString());
        });

        /*
         * KV的数据
         */
        router.get("/data").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            JsonObject kvData = outerService.getKVData();
            JsonObject res = new JsonObject();
            res.put("data", kvData);
            res.put("size", kvData.size());
            response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.end(res.toString());
        });

        router.get("/key/:key").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            String key = routingContext.request().getParam("key");
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("value", new String(outerService.getKey(key)));
            response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.end(jsonObject.toString());
        });

        /*
         * 修改节点配置
         */
        router.post("/conf").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            JsonObject bodyJson = routingContext.getBodyAsJson();
            ClusterConfCommand clusterConfCommand = bodyJson.mapTo(ClusterConfCommand.class);
            response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.end("OK");
        });

        /*
         * 查看节点日志
         */
        router.get("/log/:event").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            String eventString = routingContext.request().getParam("event");
            EventRecorder.Event event = EventRecorder.Event.fromPath(eventString);
            if (Objects.isNull(event)) {
                response.setStatusCode(401);
                return;
            }
            JsonObject eventLog = outerService.getEventLog(event);
            response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            response.end(eventLog.toString());
        });

        router.get("/snapshot").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response.end(this.outerService.getSnapShot().toString());
        });

        router.post("/node/restart").handler(routingContext -> {
            this.outerService.restartNode();
            HttpServerResponse response = routingContext.response();
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("success", true);
            response.end(jsonObject.toString());
        });



        this.httpServer.requestHandler(router::accept).listen(this.port);
        log.info("start httpServer at port={}", this.port);
    }

    public void close() {
        try {
            this.httpServer.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
