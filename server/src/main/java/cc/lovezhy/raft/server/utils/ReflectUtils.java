package cc.lovezhy.raft.server.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class ReflectUtils {

    private static final Logger log = LoggerFactory.getLogger(ReflectUtils.class);

    @SuppressWarnings("unchecked")
    public static <T> T getObjectMember(Object object, String memberName) {
        try {
            Field field = object.getClass().getDeclaredField(memberName);
            field.setAccessible(true);
            Object o = field.get(object);
            return (T) o;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error(e.getMessage());
        }
        return null;
    }
}
