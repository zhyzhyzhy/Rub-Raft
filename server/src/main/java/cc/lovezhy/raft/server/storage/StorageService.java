package cc.lovezhy.raft.server.storage;

import java.util.List;

public interface StorageService {

    /**
     * 拿到位于Index的StorageEntry
     */
    StorageEntry get(int index);

    List<StorageEntry> range(int start, int end);

    /**
     * 设置Index位置的StorageEntry
     */
    boolean set(int index, StorageEntry storageEntry);

    /**
     * append一个Entry，
     * 自动补充offset
     */
    boolean append(StorageEntry storageEntry);

    /**
     * storage存的长度
     */
    int getLen();

    void discard(int toIndex);
}
