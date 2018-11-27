package cc.lovezhy.raft.server.storage;

import java.io.IOException;

public interface StorageService {

    /**
     * 拿到位于Index的StorageEntry
     */
    StorageEntry get(int index) throws IOException;

    /**
     * 设置Index位置的StorageEntry
     */
    boolean set(int index, StorageEntry storageEntry) throws IOException;

    /**
     * append一个Entry，
     * 自动补充offset
     */
    boolean append(StorageEntry storageEntry) throws IOException;

    /**
     * storage存的长度
     */
    long getLen();
}
