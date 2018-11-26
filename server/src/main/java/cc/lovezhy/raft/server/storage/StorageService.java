package cc.lovezhy.raft.server.storage;

public interface StorageService {

    /**
     * 拿到位于Index的StorageEntry
     */
    StorageEntry get(long index);

    /**
     * 设置Index位置的StorageEntry
     */
    boolean set(long index, StorageEntry storageEntry);

    /**
     * append一个Entry，
     * 自动补充offset
     */
    boolean append(StorageEntry storageEntry);

    /**
     * storage存的长度
     */
    long getLen();
}
