package cc.lovezhy.raft.server.storage;

import cc.lovezhy.raft.server.utils.KryoUtils;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RaftFileRecordManager {

    private static final AtomicInteger counter = new AtomicInteger(0);

    private LocalFile recordFile;
    private LocalFile indexFile;


    public static RaftFileRecordManager create(String category) throws IOException {
        return new RaftFileRecordManager(category);
    }

    private RaftFileRecordManager(String category) throws IOException {
        int index = counter.getAndIncrement();
        this.recordFile = LocalFile.create(category + "/logFile" + index + ".txt");
        this.indexFile = LocalFile.create(category + "/indexFile" + index + ".txt");
    }

    public synchronized void appendRecord(RaftRecord raftRecord) throws IOException {
        int index = (int) (indexFile.size() / 16);
        indexFile.position(indexFile.size());
        indexFile.appendLong(index);
        indexFile.appendLong(recordFile.size());

        recordFile.position(recordFile.size());
        recordFile.appendLenAndBuf(raftRecord.getRecord());
    }

    public synchronized RaftRecord fetchRecord(int index) throws IOException {
        List<RaftRecord> raftRecords = fetchRecords(index, 1);
        return raftRecords.isEmpty() ? null : raftRecords.get(0);
    }

    public synchronized long getLen() throws IOException {
        return indexFile.size() / 16;
    }

    public synchronized List<RaftRecord> fetchRecords(int startIndex, int len) throws IOException {
        long position = searchRecordPosition(startIndex, false);
        if (position == -1) {
            return Lists.newArrayList();
        }
        recordFile.position(position);
        List<RaftRecord> raftRecords = new ArrayList<>();
        for (int i = 0; i < len && recordFile.position() < recordFile.size(); i++) {
            byte[] s = recordFile.readLenAndBuffer();
            RaftRecord raftRecord = new RaftRecord();
            raftRecord.setRecord(s);
            raftRecords.add(raftRecord);
        }
        return raftRecords;
    }

    private synchronized long searchRecordPosition(int index, boolean truncateIndexFile) throws IOException {
        long size = indexFile.size();
        long start = 0;
        long end = size;
        if (end <= 0) {
            return -1;
        }
        long lastMiddle = -1;
        while (start <= end) {
            long middle = (start + end) / 2;
            middle = middle - (middle % 16);
            if (lastMiddle != -1 && lastMiddle == middle) {
                return -1;
            } else {
                lastMiddle = middle;
            }
            indexFile.position(middle);
            long middleRecordIndex = indexFile.readLong();
            if (middleRecordIndex == index) {
                long l = indexFile.readLong();
                if (truncateIndexFile) {
                    indexFile.truncate(indexFile.position() - 16);
                }
                return l;
            } else if (middleRecordIndex < index) {
                start = middle;
            } else {
                end = middle;
            }
        }
        long l = indexFile.readLong();
        long offset = l == index ? indexFile.readLong() : -1;
        if (truncateIndexFile) {
            indexFile.truncate(indexFile.position() - 8);
        }
        return offset;
    }

    public void discardFromIndex(int index) throws IOException {
        long position = searchRecordPosition(index, true);
        this.recordFile.truncate(position);
    }

    public synchronized void dumpLogFile() throws IOException {
        recordFile.position(0);
        do {
            byte[] s = recordFile.readLenAndBuffer();
            System.out.println(KryoUtils.deserializeLogEntry(s));
        } while (recordFile.position() < recordFile.size());

        indexFile.position(0);
        do {
            long index = indexFile.readLong();
            long pos = indexFile.readLong();
            System.out.printf("%d %d\n", index, pos);
        } while (indexFile.position() < indexFile.size());
    }

    public static void main(String[] args) throws IOException {
        RaftFileRecordManager raftFileRecordManager = RaftFileRecordManager.create("/private/var/log/raft/");
        raftFileRecordManager.dumpLogFile();
    }


}
