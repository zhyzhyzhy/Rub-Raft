package cc.lovezhy.raft.rpc.protocal;

import cc.lovezhy.raft.rpc.protocal.annotation.Async;
import cc.lovezhy.raft.rpc.protocal.annotation.OneWay;
import cc.lovezhy.raft.rpc.util.ReflectUtils;
import com.google.common.base.Preconditions;

import java.lang.reflect.Method;

public enum RpcRequestType {
    NORMAL,
    ASYNC,
    ONE_WAY;

    public static RpcRequestType getRpcRequestType(Method method) {
        Preconditions.checkNotNull(method);

        if (ReflectUtils.containsAnnotation(method, Async.class)) {
            return ASYNC;
        }

        if (ReflectUtils.containsAnnotation(method, OneWay.class)) {
            return ONE_WAY;
        }

        return NORMAL;
    }
}
