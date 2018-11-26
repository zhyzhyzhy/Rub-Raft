package cc.lovezhy.raft.server.utils;

import java.io.File;

public class FileUtils {
    public static boolean createCategoryIfNotExist(String category) {
        File file = new File(category);
        if (file.exists()) {
            if (!file.isDirectory()) {
                throw new IllegalStateException("File exist! But is not category!");
            }
        } else {
            return file.mkdir();
        }
        return true;
    }

    public static boolean checkFileNotExist(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            throw new IllegalStateException("File exist");
        }
        return true;
    }
}
