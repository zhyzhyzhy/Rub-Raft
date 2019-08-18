package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.annotation.ForTesting;
import cc.lovezhy.raft.rpc.annotation.ForTestingMethod;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.Map;

/**
 * 用来配置异步请求
 * TestFor6.824 Connect() DisConnect()
 */
public class RpcClientOptions {

    // methodName -> RpcRequestType
    private Map<String, RpcRequestType> requestTypeOption;

    @ForTesting
    private volatile boolean isOnNet;

    @ForTesting
    private volatile boolean reliable;

    @ForTesting
    private volatile boolean longReordering;

    public RpcClientOptions() {
        requestTypeOption = Collections.synchronizedMap(Maps.newHashMap());
        isOnNet = true;
        reliable = true;
        longReordering = false;
    }

    public void defineMethodRequestType(String methodName, RpcRequestType requestType) {
        Preconditions.checkNotNull(methodName);
        Preconditions.checkNotNull(requestType);
        if (requestTypeOption.containsKey(methodName)) {
            RpcRequestType originRequestType = requestTypeOption.get(methodName);
            throw new IllegalStateException(String.format("method=%s has redefined already, originRequestType=%s", methodName, originRequestType));
        }
        requestTypeOption.put(methodName, requestType);
    }

    public RpcRequestType getRpcRequestType(String methodName) {
        return requestTypeOption.getOrDefault(methodName, RpcRequestType.NORMAL);
    }

    @ForTestingMethod
    public void setOnNet(boolean onNet) {
        this.isOnNet = onNet;
    }

    @ForTestingMethod
    public void setReliable(boolean reliable) {
        this.reliable = reliable;
    }

    @ForTestingMethod
    public boolean isOnNet() {
        return isOnNet;
    }


    @ForTestingMethod
    public boolean isLongReordering() {
        return longReordering;
    }

    @ForTestingMethod
    public void setLongReordering(boolean longReordering) {
        this.longReordering = longReordering;
    }

    @ForTestingMethod
    public boolean isReliable() {
        return reliable;
    }
}
