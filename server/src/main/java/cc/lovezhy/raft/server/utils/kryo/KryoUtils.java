package cc.lovezhy.raft.server.utils.kryo;

import cc.lovezhy.raft.server.log.DefaultCommand;
import cc.lovezhy.raft.server.log.DefaultCommandEnum;
import cc.lovezhy.raft.server.log.LogEntry;
import cc.lovezhy.raft.server.storage.StorageEntry;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class KryoUtils {

    private KryoUtils() {}

    private static KryoPool pool;

    static {
        KryoFactory factory = () -> {
            Kryo kryo = new Kryo();
            kryo.register(DefaultCommandEnum.class);
            kryo.register(DefaultCommand.class);
            kryo.register(LogEntry.class);
            kryo.register(StorageEntry.class);
            return kryo;
        };
        pool = new KryoPool.Builder(factory).build();
    }

    public static LogEntry deserializeLogEntry(byte[] bytes) {
        Preconditions.checkNotNull(bytes);
        Kryo kryo = pool.borrow();
        Input input = new Input(new ByteArrayInputStream(bytes));
        LogEntry logEntry = kryo.readObject(input, LogEntry.class);
        input.close();
        pool.release(kryo);
        return logEntry;
    }

    public static byte[] serializeLogEntry(LogEntry logEntry) {
        Preconditions.checkNotNull(logEntry);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);
        Kryo kryo = pool.borrow();
        kryo.writeObject(output, logEntry);
        output.flush();
        output.close();
        byte[] bytes = byteArrayOutputStream.toByteArray();
        pool.release(kryo);
        return bytes;
    }

}
