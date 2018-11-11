package cc.lovezhy.raft.rpc.protocal;

import com.google.common.base.MoreObjects;

public class RpcResponse {

    private String requestId;

    private Object responseBody;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Object getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(Object responseBody) {
        this.responseBody = responseBody;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("requestId", requestId)
                .add("responseBody", responseBody)
                .toString();
    }
}
