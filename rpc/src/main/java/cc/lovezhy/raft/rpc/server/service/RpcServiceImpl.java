package cc.lovezhy.raft.rpc.server.service;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNull;

public class RpcServiceImpl implements RpcService {

    private Channel channel;

    // requestId -> Response
    private ConcurrentHashMap<String, RpcResponse> requestIdResponseMap = new ConcurrentHashMap<>();

    private ReentrantLock syncLock = new ReentrantLock();
    private ConcurrentHashMap<String, Condition> syncCondition = new ConcurrentHashMap<>();

    private RpcServiceImpl(Channel channel) {
        requireNonNull(channel);
        this.channel = channel;
    }

    @Override
    public void handleRequest(RpcRequest request) {

    }

    @Override
    public RpcResponse sendRequest(RpcRequest request) {
        channel.writeAndFlush(request);
        Condition responseCondition = syncLock.newCondition();
        syncCondition.put(request.getRequestId(), responseCondition);
        try {
            responseCondition.wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return requestIdResponseMap.get(request.getRequestId());
    }

    @Override
    public void sendRequestAsync(RpcRequest request, FutureCallback future) {
        channel.writeAndFlush(request);
    }

    @Override
    public void sendOneWayRequest(RpcRequest request) {

    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void onResponse(RpcResponse response) {
        requestIdResponseMap.put(response.getRequestId(), response);
        syncCondition.get(response.getRequestId()).signal();
    }

}
