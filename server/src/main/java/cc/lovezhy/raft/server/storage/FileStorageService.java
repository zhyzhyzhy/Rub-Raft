package cc.lovezhy.raft.server.storage;

import java.util.List;

/**
 * |index|len|values|len|index|len|values|len|
 * |0    |2  |ab    |2  |1    |3  |req   |3  |
 *
 */
public class FileStorageService implements StorageService {

    public static StorageService create(String category, String fileName) {
        return new FileStorageService(category, fileName);
    }

    private StorageFile storageFile;

    private volatile long len = 0;

    private FileStorageService(String category, String fileName) {
        this.storageFile = StorageFileImpl.create(category, fileName);
    }


    @Override
    public StorageEntry get(int index) {
        return null;
    }

    @Override
    public List<StorageEntry> range(int start, int end) {
        return null;
    }

    @Override
    public boolean set(int index, StorageEntry storageEntry) {
        return false;
    }

    @Override
    public boolean append(StorageEntry storageEntry) {
        return false;
    }

    @Override
    public int getLen() {
        return 0;
    }

    @Override
    public void discard(int toIndex) {
        //TODO
    }
}
