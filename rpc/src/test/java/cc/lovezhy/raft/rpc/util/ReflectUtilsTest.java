package cc.lovezhy.raft.rpc.util;

import cc.lovezhy.raft.rpc.protocal.annotation.Async;
import cc.lovezhy.raft.rpc.protocal.annotation.OneWay;
import com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

public class ReflectUtilsTest {

    private Map<String, Method> methodMap;

    @Before
    public void setUp() {
        methodMap = Maps.newHashMap();

        Arrays.stream(ExampleService.class.getDeclaredMethods())
                .forEach(method -> methodMap.put(method.getName(), method));
    }

    @Test
    public void getOneWayAnnotationTest() {
        Method sendOneWayMethod = methodMap.get("sendOneWay");
        Assert.assertNotNull(sendOneWayMethod);
        Assert.assertTrue(ReflectUtils.containsAnnotation(sendOneWayMethod, OneWay.class));
    }

    @Test
    public void getAsyncAnnotationTest() {
        Method sendAsyncMethod = methodMap.get("sendAsync");
        Assert.assertNotNull(sendAsyncMethod);
        Assert.assertTrue(ReflectUtils.containsAnnotation(sendAsyncMethod, Async.class));
    }

    @Test
    public void getAsyncAnnotationWaitTimeOutTest() {
        Method sendAsyncMethod = methodMap.get("sendAsyncWaitOut");
        Assert.assertNotNull(sendAsyncMethod);
        Assert.assertTrue(ReflectUtils.containsAnnotation(sendAsyncMethod, Async.class));
        Async asyncAnnotation = ReflectUtils.getAnnotation(sendAsyncMethod, Async.class);
        Assert.assertEquals(300, asyncAnnotation.waitTimeOutMills());
    }


}

class ExampleService {
    @OneWay
    void sendOneWay() {
    }

    @Async
    void sendAsync() {
    }

    @Async(waitTimeOutMills = 300)
    void sendAsyncWaitOut() {
    }

}
