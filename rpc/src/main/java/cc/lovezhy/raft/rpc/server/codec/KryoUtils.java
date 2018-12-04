package cc.lovezhy.raft.rpc.server.codec;

import cc.lovezhy.raft.rpc.protocal.RpcRequest;
import cc.lovezhy.raft.rpc.protocal.RpcRequestType;
import cc.lovezhy.raft.rpc.protocal.RpcResponse;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;

public class KryoUtils {

    public static final KryoPool pool;

    static {
        KryoFactory factory = () -> {
            Kryo kryo = new Kryo();
            kryo.register(RpcRequest.class);
            kryo.register(RpcResponse.class);
            kryo.register(RpcRequestType.class);
            return kryo;
        };
        pool = new KryoPool.Builder(factory).softReferences().build();
    }
}
