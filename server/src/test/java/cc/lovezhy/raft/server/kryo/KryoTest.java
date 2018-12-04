package cc.lovezhy.raft.server.kryo;

import cc.lovezhy.raft.server.log.DefaultCommand;
import cc.lovezhy.raft.server.log.LogConstants;
import cc.lovezhy.raft.server.log.LogEntry;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class KryoTest {
    public static void main(String[] args) throws InterruptedException {
//        LogEntry initialLogEntry = LogConstants.getInitialLogEntry();
//        initialLogEntry.toStorageEntry().toLogEntry();
//        new Thread(() -> {
//            LogConstants.getInitialLogEntry().toStorageEntry().toLogEntry();
//        }).start();
//        Thread.sleep(300L);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);
        LogEntry logEntry = LogConstants.getInitialLogEntry();
        DefaultCommand command = logEntry.getCommand();
        Kryo kryo = new Kryo();
        kryo.writeObject(output, command);
        output.flush();
        output.close();
        byte[] bytes11 = byteArrayOutputStream.toByteArray();

        Output output1 = new Output(64);
        kryo = new Kryo();
        kryo.writeObject(output1, command);
        output.flush();
        output.close();
        byte[] bytes = output1.toBytes();

        System.out.println(Arrays.toString(bytes11));
        System.out.println(Arrays.toString(bytes));

    }
}
