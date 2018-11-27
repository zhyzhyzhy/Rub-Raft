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
    public synchronized StorageEntry get(long index) throws IOException {
        storageFile.resetReadPointer(0);
        while (index > 0) {
            int entryLength = storageFile.readInt();
            storageFile.skip(entryLength);
            index--;
        }
        long offset = storageFile.getReadPointer();
        int entryLength = storageFile.readInt();
        byte[] values = storageFile.getBytes(entryLength);
        StorageEntry storageEntry = new StorageEntry();
        storageEntry.setOffset(offset);
        storageEntry.setSize(entryLength);
        storageEntry.setValues(values);
        return storageEntry;
    }

    @Override
    public boolean set(long index, StorageEntry storageEntry) throws IOException {
        storageFile.resetReadPointer(0);
        while (index > 0) {
            int entryLength = storageFile.readInt();
            storageFile.skip(entryLength);
            index--;
        }
        storageEntry.setOffset(storageFile.getReadPointer());
        storageFile.writeInt(storageEntry.getSize());
        storageFile.writeBytes(storageEntry.getValues());
        return true;
    }

    @Override
    public boolean append(StorageEntry storageEntry) throws IOException {
        storageEntry.setOffset(storageFile.getWritePointer());
        storageFile.writeInt(storageEntry.getSize());
        storageFile.writeBytes(storageEntry.getValues());
        len++;
        return true;
    }

    @Override
    public long getLen() {
        return len;
    }
}
