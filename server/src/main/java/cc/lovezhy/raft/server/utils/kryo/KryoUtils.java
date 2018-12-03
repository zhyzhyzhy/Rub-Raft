package cc.lovezhy.raft.server.utils.kryo;

import cc.lovezhy.raft.server.log.LogEntry;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Preconditions;

import java.io.ByteArrayOutputStream;

public class KryoUtils {


    private static KryoPool pool;

    static {
        KryoFactory factory = Kryo::new;
        pool = new KryoPool.Builder(factory).softReferences().build();
    }

    public static LogEntry deserializeLogEntry(byte[] bytes) {
        Preconditions.checkNotNull(bytes);
        Input input = new Input(bytes);
        Kryo kryo = pool.borrow();
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
        pool.release(kryo);
        return byteArrayOutputStream.toByteArray();
    }

}
