package cc.lovezhy.raft.server.storage;


import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class LocalFile implements Closeable {

    public static LocalFile create(String name) throws IOException {
        return new LocalFile(name);
    }

    private String name;
    private FileChannel fileChannel;

    private LocalFile(String name) throws IOException {
        this.name = name;
        this.fileChannel = new RandomAccessFile(this.name, "rw").getChannel();
    }

    public void appendLong(long number) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8).putLong(number);
        byteBuffer.flip();
        this.fileChannel.write(byteBuffer);
    }

    public void appendLenAndBuf(byte[] bytes) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + bytes.length)
                .putInt(bytes.length)
                .put(bytes);
        byteBuffer.flip();
        this.fileChannel.write(byteBuffer);
    }

    public void position(long position) throws IOException {
        fileChannel.position(position);
    }

    public long position() throws IOException {
        return fileChannel.position();
    }

    public long size() throws IOException {
        return fileChannel.size();
    }

    public long readLong() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        this.fileChannel.read(byteBuffer);
        byteBuffer.flip();
        return byteBuffer.getLong();
    }

    public String readLenAndBuffer() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        this.fileChannel.read(byteBuffer);
        byteBuffer.flip();
        int len = byteBuffer.getInt();
        byteBuffer = ByteBuffer.allocate(len);
        this.fileChannel.read(byteBuffer);
        return new String(byteBuffer.array());
    }

    public void truncate(long size) throws IOException {
        System.out.println(size);
        this.fileChannel.truncate(size);
    }


    @Override
    public void close() throws IOException {
        this.fileChannel.close();
    }

}
