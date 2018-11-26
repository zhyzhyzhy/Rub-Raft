package cc.lovezhy.raft.server.storage;

public class StorageServiceImpl implements StorageService {

    @Override
    public StorageEntry get(long index) {
        return null;
    }

    @Override
    public boolean set(long index, StorageEntry storageEntry) {
        return false;
    }

    @Override
    public boolean append(StorageEntry storageEntry) {
        return false;
    }

    @Override
    public long getLen() {
        return 0;
    }
}
