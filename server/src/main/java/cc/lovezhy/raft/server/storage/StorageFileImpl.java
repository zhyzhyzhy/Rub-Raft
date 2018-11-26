package cc.lovezhy.raft.server.storage;

import cc.lovezhy.raft.server.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class StorageFileImpl implements StorageFile {

    private static final Logger log = LoggerFactory.getLogger(StorageFileImpl.class);

    public StorageFile create(String category, String fileName) throws FileNotFoundException {
        return new StorageFileImpl(category, fileName);
    }


    private RandomAccessFile randomAccessFile;
    private FileChannel fileChannel;

    private volatile long writePointer = 0;

    private StorageFileImpl(String category, String fileName) throws FileNotFoundException {
        if (!FileUtils.createCategoryIfNotExist(category)) {
            throw new IllegalStateException("category can not be create!");
        }
        String filePath = category + File.pathSeparator + fileName;
        FileUtils.checkFileNotExist(filePath);
        try {
            randomAccessFile = new RandomAccessFile(fileName, "rw");
        } catch (FileNotFoundException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
        fileChannel = randomAccessFile.getChannel();
        log.info("success create file, category={}, fileName={}", category, fileName);
    }

    @Override
    public void changeName(String name) {
    }

    @Override
    public long getLength() throws IOException {
        return randomAccessFile.length();
    }

    @Override
    public synchronized void writeLong(long value) throws IOException {
        randomAccessFile.seek(writePointer);
        randomAccessFile.writeLong(value);
    }

    @Override
    public synchronized void writeBytes(byte[] values) throws IOException {
        randomAccessFile.seek(writePointer);
        randomAccessFile.write(values);
    }

    @Override
    public synchronized long getLong(long offset) throws IOException {
        randomAccessFile.seek(offset);
        return randomAccessFile.readLong();
    }

    @Override
    public synchronized byte[] getBytes(int offset, int len) throws IOException {
        byte[] values = new byte[len];
        randomAccessFile.read(values, offset, len);
        return values;
    }

    @Override
    public StorageFile dup(long offset) {
        return null;
    }

    @Override
    public synchronized void resetWritePointer(long offset) {
        writePointer = offset;
    }

    @Override
    public long getWritePointer() {
        return writePointer;
    }

    @Override
    public void delete() {
    }
}
