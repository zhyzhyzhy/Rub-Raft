package cc.lovezhy.raft.rpc.server.codec;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class KryoDecoder extends ByteToMessageDecoder {

    private final Kryo kryo = new Kryo();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) {
            return;
        }
        in.markReaderIndex();
        int len = in.readInt();

        if (in.readableBytes() < len) {
            in.resetReaderIndex();
            return;
        }

        byte[] buf = new byte[len];
        in.readBytes(buf, 0, len);
        Input input = new Input(buf);
        Object object = kryo.readClassAndObject(input);
        out.add(object);
    }
}
