package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import com.google.common.annotations.VisibleForTesting;
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

    private boolean isOnNet;

    public RpcClientOptions() {
        requestTypeOption = Collections.synchronizedMap(Maps.newHashMap());
        isOnNet = true;
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

    @VisibleForTesting
    public void disConnect() {
        this.isOnNet = false;
    }

    @VisibleForTesting
    public void connect() {
        this.isOnNet = true;
    }

    @VisibleForTesting
    public boolean isOnNet() {
        return isOnNet;
    }


}
