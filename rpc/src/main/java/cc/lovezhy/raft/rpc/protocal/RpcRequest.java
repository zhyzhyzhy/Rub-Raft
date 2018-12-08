package cc.lovezhy.raft.rpc.protocal;

import com.google.common.base.MoreObjects;

public class RpcRequest {

    private String requestId;

    private String clazz;

    private String method;

    private Object[] args;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("requestId", requestId)
                .add("clazz", clazz)
                .add("method", method)
                .add("args", args)
                .toString();
    }
}
