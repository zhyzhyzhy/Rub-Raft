package cc.lovezhy.raft.server.storage;

public interface StorageFile {

    void changeName(String name);

    long getLength();

    void writeInt(int value);

    void writeBytes(byte[] values);

    /**
     * 从读指针位置拿到一个int
     */
    int readInt();
    /**
     * 取指定位置的bytes
     * @param offset 偏移位置
     * @param len 长度
     */
    byte[] getBytes(int offset, int len);

    /**
     * 取当前读位置的bytes
     * @param len 长度
     */
    byte[] getBytes(int len);

    /**
     * 跳过多少字节
     * @param len 长度
     */
    void skip(int len);
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
    void resetReadPointer(long offset);

    /**
     * 得到读指针位置
     */
    long getReadPointer();
    /**
     * 清除自己
     */
    void delete();
}
