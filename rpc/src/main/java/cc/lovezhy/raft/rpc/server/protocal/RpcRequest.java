package cc.lovezhy.raft.rpc.server.protocal;

import java.util.List;

public class RpcRequest {
    private String requestId;
    private RpcRequestType requestType;

    private String clazz;
    private String method;
    private List<Object> args;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RpcRequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RpcRequestType requestType) {
        this.requestType = requestType;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<Object> getArgs() {
        return args;
    }

    public void setArgs(List<Object> args) {
        this.args = args;
    }
}
