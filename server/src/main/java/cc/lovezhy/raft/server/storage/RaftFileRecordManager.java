package cc.lovezhy.raft.server.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RaftFileRecordManager {

    private LocalFile recordFile;
    private LocalFile indexFile;

    public RaftFileRecordManager() throws IOException {
        this.recordFile = LocalFile.create("/Users/zhuyichen/logFile.txt");
        this.indexFile = LocalFile.create("/Users/zhuyichen/indexFile.txt");
    }

    public synchronized void appendRecord(RaftRecord raftRecord) throws IOException {
        indexFile.appendLong(raftRecord.getIndex());
        indexFile.appendLong(recordFile.size());

        recordFile.position(recordFile.size());
        recordFile.appendLong(raftRecord.getIndex());
        recordFile.appendLenAndBuf(raftRecord.getRecord().getBytes());
    }

    public synchronized RaftRecord fetchRecord(int index) throws IOException {
        List<RaftRecord> raftRecords = fetchRecords(index, 1);
        return raftRecords.isEmpty() ? null : raftRecords.get(0);
    }

    public synchronized List<RaftRecord> fetchRecords(int startIndex, int len) throws IOException {
        long position = searchRecordPosition(startIndex, false);
        if (position == -1) {
            return Collections.emptyList();
        }
        recordFile.position(position);
        List<RaftRecord> raftRecords = new ArrayList<>();
        for (int i = 0; i < len && recordFile.position() < recordFile.size(); i++) {
            long index = recordFile.readLong();
            String s = recordFile.readLenAndBuffer();
            RaftRecord raftRecord = new RaftRecord();
            raftRecord.setIndex(index);
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
            long index = recordFile.readLong();
            String s = recordFile.readLenAndBuffer();
        } while (recordFile.position() < recordFile.size());
    }


}
