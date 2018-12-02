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

    static StorageFile create(String category, String fileName) {
        return new StorageFileImpl(category, fileName);
    }


    private RandomAccessFile randomAccessFile;

    private volatile long writePointer = 0;

    private StorageFileImpl(String category, String fileName) {
        if (!FileUtils.createCategoryIfNotExist(category)) {
            throw new IllegalStateException("category can not be create!");
        }
        String filePath = category + File.pathSeparator + fileName;
        FileUtils.checkFileNotExist(filePath);
        try {
            randomAccessFile = new RandomAccessFile(fileName, "rw");
        } catch (FileNotFoundException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
        log.info("success create file, category={}, fileName={}", category, fileName);
    }

    @Override
    public void changeName(String name) {
        //TODO
    }

    @Override
    public long getLength() {
        try {
            return randomAccessFile.length();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void writeInt(int value) {
        try {
            randomAccessFile.seek(writePointer);
            randomAccessFile.write(value);
            writePointer += 2;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void writeBytes(byte[] values) {
        try {
            randomAccessFile.seek(writePointer);
            randomAccessFile.write(values);
            writePointer += values.length;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public int readInt() {
        try {
            return randomAccessFile.readInt();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public byte[] getBytes(int offset, int len) {
        byte[] values = new byte[len];
        try {
            randomAccessFile.read(values, offset, len);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
        return values;
    }

    @Override
    public byte[] getBytes(int len) {
        byte[] values = new byte[len];
        try {
            randomAccessFile.read(values);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
        return values;
    }

    @Override
    public void skip(int len) {
        try {
            randomAccessFile.skipBytes(len);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
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
    public void resetReadPointer(long offset) {
        try {
            randomAccessFile.seek(offset);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public long getReadPointer() {
        try {
            return randomAccessFile.getFilePointer();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void delete() {
    }
}
