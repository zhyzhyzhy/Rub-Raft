package cc.lovezhy.raft.rpc;

import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;

/**
 * 用来配置异步请求
 */
public class RpcClientOptions {

    // methodName -> RpcRequestType
    private Map<String, RpcRequestType> requestTypeOption;

    public RpcClientOptions() {
        requestTypeOption = Collections.synchronizedMap(Maps.newHashMap());
    }

    public void defineMethodRequestType(String methodName, RpcRequestType requestType) {
        Preconditions.checkNotNull(methodName);
        Preconditions.checkNotNull(requestType);
        if (requestTypeOption.containsKey(methodName)) {
            RpcRequestType originRequestType = requestTypeOption.get(methodName);
            throw new IllegalStateException(MessageFormat.format("method={} has redefined already, originRequestType={}", methodName, originRequestType));
        }
        requestTypeOption.put(methodName, requestType);
    }

    public RpcRequestType getRpcRequestType(String methodName) {
        return requestTypeOption.getOrDefault(methodName, RpcRequestType.NORMAL);
    }
}
