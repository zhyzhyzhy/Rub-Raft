package cc.lovezhy.raft.server.utils;

import cc.lovezhy.raft.rpc.EndPoint;
import cc.lovezhy.raft.server.log.DefaultCommand;
import cc.lovezhy.raft.server.node.RaftNode;
import com.alibaba.fastjson.JSON;
import io.vertx.core.json.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class HttpUtils {


    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);

    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType MEDIA_JSON = MediaType.parse("application/json;charset=utf-8");


    private HttpUtils() {
    }

    public static boolean postCommand(EndPoint endPoint, DefaultCommand defaultCommand) {

        Request request = new Request.Builder()
                .url(endPoint.toUrl() + "/command")
                .post(RequestBody.create(MEDIA_JSON, JSON.toJSONString(defaultCommand)))
                .build();

        Response response = null;
        try {
            response = client.newCall(request).execute();
            return true;
        } catch (IOException e) {
            //ignore
            return false;
        } finally {
            if (Objects.nonNull(response)) {
                response.close();
            }
        }
    }

    public static Map<String, Object> getKVData(RaftNode raftNode) {
        EndPoint httpEndPoint = EndPoint.create(raftNode.getEndPoint().getHost(), raftNode.getEndPoint().getPort() + 1);
        Request request = new Request.Builder()
                .url(httpEndPoint.toUrl() + "/data")
                .get()
                .build();

        Response response = null;
        try {
            response = client.newCall(request).execute();
            JsonObject jsonObject = new JsonObject(response.body().string());
            return jsonObject.getJsonObject("data").getMap();
        } catch (IOException e) {
            //ignore
            LOG.error(e.getMessage(), e);

        } finally {
            if (Objects.nonNull(response)) {
                response.close();
            }
        }
        return Collections.emptyMap();
    }
}
