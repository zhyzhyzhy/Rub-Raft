package cc.lovezhy.raft.server.storage;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * |index|len|values|len|index|len|values|len|
 * |0    |2  |ab    |2  |1    |3  |req   |3  |
 *
 */
public class FileStorageService implements StorageService {

    public static StorageService create(String category, String fileName) throws FileNotFoundException {
        return new FileStorageService(category, fileName);
    }

    private StorageFile storageFile;

    private volatile long len = 0;

    private FileStorageService(String category, String fileName) throws FileNotFoundException {
        this.storageFile = StorageFileImpl.create(category, fileName);
    }


    @Override
    public StorageEntry get(int index) throws IOException {
        return null;
    }

    @Override
    public boolean set(int index, StorageEntry storageEntry) throws IOException {
        return false;
    }

    @Override
    public boolean append(StorageEntry storageEntry) throws IOException {
        return false;
    }

    @Override
    public long getLen() {
        return 0;
    }
}
