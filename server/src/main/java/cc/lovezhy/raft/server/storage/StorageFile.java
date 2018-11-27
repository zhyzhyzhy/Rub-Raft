package cc.lovezhy.raft.server.storage;

import java.io.IOException;

public interface StorageFile {

    void changeName(String name);

    long getLength() throws IOException;

    void writeInt(int value) throws IOException;

    void writeBytes(byte[] values) throws IOException;

    /**
     * 从读指针位置拿到一个int
     */
    int readInt() throws IOException;
    /**
     * 取指定位置的bytes
     * @param offset 偏移位置
     * @param len 长度
     */
    byte[] getBytes(int offset, int len) throws IOException;

    /**
     * 取当前读位置的bytes
     * @param len 长度
     */
    byte[] getBytes(int len) throws IOException;

    /**
     * 跳过多少字节
     * @param len 长度
     */
    void skip(int len) throws IOException;
    /**
     * 从指定位置开始dup一个新文件
     *
     * @param offset 偏移位置
     * @return
     */
    StorageFile dup(long offset);

    /**
     * 重置写指针位置
     */
    void resetWritePointer(long offset);

    /**
     * 得到写指针位置
     */
    long getWritePointer();


    /**
     * 重置读指针位置
     */
    void resetReadPointer(long offset) throws IOException;

    /**
     * 得到读指针位置
     */
    long getReadPointer() throws IOException;
    /**
     * 清除自己
     */
    void delete();
}
