package cc.lovezhy.raft.server.vertx;

import cc.lovezhy.raft.server.log.DefaultCommand;
import com.alibaba.fastjson.JSON;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Test;

public class JsonObjectTest {

    @Test
    public void jsonObjectMapTest() {
        DefaultCommand defaultCommand = DefaultCommand.setCommand("zhuyichen", "23");
        String jsonString = JSON.toJSONString(defaultCommand);
        JsonObject jsonObject = new JsonObject(jsonString);
        Assert.assertEquals(defaultCommand, jsonObject.mapTo(DefaultCommand.class));
    }
}
