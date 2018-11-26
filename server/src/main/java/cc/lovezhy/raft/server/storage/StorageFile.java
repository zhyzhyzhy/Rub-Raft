package cc.lovezhy.raft.server.storage;

import java.io.IOException;

public interface StorageFile {

    void changeName(String name);

    long getLength() throws IOException;

    void writeLong(long value) throws IOException;

    void writeBytes(byte[] values) throws IOException;

    /**
     * 从指定位置拿到一个long
     */
    long getLong(long offset) throws IOException;

    /**
     * 取指定位置的bytes
     * @param offset 偏移位置
     * @param len 长度
     * @return
     */
    byte[] getBytes(int offset, int len) throws IOException;

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
     * 清除自己
     */
    void delete();
}
