package cc.lovezhy.raft.server.utils.kryo;

import cc.lovezhy.raft.server.log.LogEntry;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.base.Preconditions;

import java.io.ByteArrayOutputStream;

public class KryoUtils {
    private static final Kryo kryo;

    static {
        kryo = new Kryo();
    }

    public static LogEntry deserializeLogEntry(byte[] bytes) {
        Preconditions.checkNotNull(bytes);
        return kryo.readObject(new Input(bytes), LogEntry.class);
    }

    public static byte[] serializeLogEntry(LogEntry logEntry) {
        Preconditions.checkNotNull(logEntry);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);
        kryo.writeObject(output, logEntry);
        output.flush();
        output.close();
        return byteArrayOutputStream.toByteArray();
    }

}