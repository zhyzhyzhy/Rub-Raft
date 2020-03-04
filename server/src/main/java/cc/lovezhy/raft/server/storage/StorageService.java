package cc.lovezhy.raft.server.storage;

import java.util.List;

public interface StorageService {

    /**
     * 拿到位于Index的StorageEntry
     */
    StorageEntry get(int index);

    /**
     * 获取一个范围
     */
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
     * 删除从FromIndex开始的往后的
     */
    void remove(int fromIndex);

    /**
     * storage存的长度
     */
    int getLen();

    void discard(int toIndex);
}
