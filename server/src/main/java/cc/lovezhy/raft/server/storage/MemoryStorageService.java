package cc.lovezhy.raft.server.storage;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;

public class MemoryStorageService implements StorageService {


    private List<StorageEntry> entries = Lists.newLinkedList();

    public static StorageService create() {
        return new MemoryStorageService();
    }

    private MemoryStorageService() {

    }

    @Override
    public synchronized StorageEntry get(int index) throws IOException {
        Preconditions.checkState(entries.size() > index);
        return entries.get(index);
    }

    @Override
    public synchronized boolean set(int index, StorageEntry storageEntry) throws IOException {
        Preconditions.checkState(entries.size() > index);
        entries.set(index, storageEntry);
        return true;
    }

    @Override
    public synchronized boolean append(StorageEntry storageEntry) throws IOException {
        entries.add(storageEntry);
        return true;
    }

    @Override
    public long getLen() {
        return entries.size();
    }
}
