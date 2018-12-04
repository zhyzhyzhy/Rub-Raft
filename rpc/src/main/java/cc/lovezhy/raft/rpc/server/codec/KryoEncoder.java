package cc.lovezhy.raft.rpc.server.codec;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ByteArrayOutputStream;

public class KryoEncoder extends MessageToByteEncoder {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        Kryo kryo = KryoUtils.pool.borrow();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);
        kryo.writeClassAndObject(output, msg);
        output.flush();
        output.close();

        byte[] res = byteArrayOutputStream.toByteArray();
        out.writeInt(res.length);
        out.writeBytes(res);
        KryoUtils.pool.release(kryo);
    }
}
