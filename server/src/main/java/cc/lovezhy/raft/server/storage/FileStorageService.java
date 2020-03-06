package cc.lovezhy.raft.server.storage;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class FileStorageService implements StorageService {

    public static StorageService create(String category) throws IOException {
        return new FileStorageService(category);
    }

    private RaftFileRecordManager raftFileRecordManager;

    private FileStorageService(String category) throws IOException {
        this.raftFileRecordManager = RaftFileRecordManager.create(category);
    }


    @Override
    public StorageEntry get(int index) {
        try {
            RaftRecord raftRecord = raftFileRecordManager.fetchRecord(index);
            if (Objects.nonNull(raftRecord)) {
                return new StorageEntry(raftRecord.getRecord());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<StorageEntry> range(int start, int end) {
        try {
            List<RaftRecord> raftRecords = raftFileRecordManager.fetchRecords(start, end - start + 1);
            List<StorageEntry> storageEntries = Lists.newArrayList();
            if (raftRecords != null) {
                raftRecords.forEach(raftRecord -> {
                    storageEntries.add(new StorageEntry(raftRecord.getRecord()));
                });
            }
            return storageEntries;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }


    @Override
    public boolean append(StorageEntry storageEntry) {
        try {
            RaftRecord raftRecord = new RaftRecord();
            raftRecord.setRecord(storageEntry.getValues());
            raftFileRecordManager.appendRecord(raftRecord);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public int getLen() {
        long len = 0;
        try {
            len = raftFileRecordManager.getLen();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (int) len;
    }

    @Override
    public void discard(int toIndex) {
        try {
            raftFileRecordManager.discardFromIndex(toIndex + 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void remove(int fromIndex) {
        try {
            raftFileRecordManager.discardFromIndex(fromIndex);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
