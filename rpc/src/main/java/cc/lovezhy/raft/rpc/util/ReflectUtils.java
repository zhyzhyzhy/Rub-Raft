package cc.lovezhy.raft.rpc.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import javax.annotation.concurrent.ThreadSafe;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

@ThreadSafe
public class ReflectUtils {

    private static final Map<Method, Set<Annotation>> METHOD_ANNOTATION_CACHE;
    private static final Set<Annotation> NOT_CONTAINS_SET;

    static {
        METHOD_ANNOTATION_CACHE = Maps.newHashMap();
        NOT_CONTAINS_SET = Sets.newHashSet();
    }

    public static <T extends Annotation> boolean containsAnnotation(Method method, Class<T> annotationClass) {
        Preconditions.checkNotNull(annotationClass);
        Preconditions.checkNotNull(method);
        return !Objects.isNull(method.getAnnotation(annotationClass));
    }

    public static <T extends Annotation> T getAnnotation(Method method, Class<T> annotationClass) {
        Preconditions.checkNotNull(method);
        Preconditions.checkNotNull(annotationClass);
        T res = method.getAnnotation(annotationClass);
        Preconditions.checkNotNull(res);
        return res;
    }
}
