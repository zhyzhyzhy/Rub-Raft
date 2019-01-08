package cc.lovezhy.raft.server.storage;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.List;

public class MemoryStorageService implements StorageService {

    private List<StorageEntry> entries = Lists.newLinkedList();

    public static StorageService create() {
        return new MemoryStorageService();
    }

    private MemoryStorageService() {
    }

    @Override
    public synchronized StorageEntry get(int index) {
        Preconditions.checkState(entries.size() > index, String.format("entries.size=[%d], requestIndex=[%d]", entries.size(), index));
        return entries.get(index);
    }

    @Override
    public List<StorageEntry> range(int start, int end) {
        return entries.subList(start, end + 1);
    }

    @Override
    public synchronized boolean set(int index, StorageEntry storageEntry) {
        Preconditions.checkState(entries.size() > index);
        entries.set(index, storageEntry);
        return true;
    }

    @Override
    public synchronized boolean append(StorageEntry storageEntry) {
        entries.add(storageEntry);
        return true;
    }

    @Override
    public int getLen() {
        return entries.size();
    }

    @Override
    public void discard(int toIndex) {
        entries = Lists.newLinkedList(this.entries.subList(toIndex, entries.size()));
    }
}
