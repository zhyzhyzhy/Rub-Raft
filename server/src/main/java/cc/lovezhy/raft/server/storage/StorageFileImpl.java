package cc.lovezhy.raft.server.storage;

import cc.lovezhy.raft.server.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class StorageFileImpl implements StorageFile {

    private static final Logger log = LoggerFactory.getLogger(StorageFileImpl.class);

    static StorageFile create(String category, String fileName) throws FileNotFoundException {
        return new StorageFileImpl(category, fileName);
    }


    private RandomAccessFile randomAccessFile;

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
        log.info("success create file, category={}, fileName={}", category, fileName);
    }

    @Override
    public void changeName(String name) {
        //TODO
    }

    @Override
    public long getLength() throws IOException {
        return randomAccessFile.length();
    }

    @Override
    public void writeInt(int value) throws IOException {
        randomAccessFile.seek(writePointer);
        randomAccessFile.write(value);
        writePointer += 2;
    }

    @Override
    public void writeBytes(byte[] values) throws IOException {
        randomAccessFile.seek(writePointer);
        randomAccessFile.write(values);
        writePointer += values.length;
    }

    @Override
    public int readInt() throws IOException {
        return randomAccessFile.readInt();
    }

    @Override
    public byte[] getBytes(int offset, int len) throws IOException {
        byte[] values = new byte[len];
        randomAccessFile.read(values, offset, len);
        return values;
    }

    @Override
    public byte[] getBytes(int len) throws IOException {
        byte[] values = new byte[len];
        randomAccessFile.read(values);
        return values;
    }

    @Override
    public void skip(int len) throws IOException {
        randomAccessFile.skipBytes(len);
    }

    @Override
    public StorageFile dup(long offset) {
        return null;
    }

    @Override
    public void resetWritePointer(long offset) {
        writePointer = offset;
    }

    @Override
    public long getWritePointer() {
        return writePointer;
    }

    @Override
    public void resetReadPointer(long offset) throws IOException {
        randomAccessFile.seek(offset);
    }

    @Override
    public long getReadPointer() throws IOException {
        return randomAccessFile.getFilePointer();
    }

    @Override
    public void delete() {
    }
}
