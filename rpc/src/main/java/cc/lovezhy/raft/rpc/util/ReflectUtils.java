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

    public static boolean containsAnnotation(Method method, Annotation annotation) {
        Preconditions.checkNotNull(annotation);
        Preconditions.checkNotNull(method);
        Set<Annotation> annotationSet = METHOD_ANNOTATION_CACHE.get(method);
        if (Objects.isNull(annotationSet)) {
            annotationSet = Sets.newConcurrentHashSet(Arrays.asList(method.getAnnotations()));
        } else if (annotationSet.equals(NOT_CONTAINS_SET)) {
            return false;
        }
        boolean contains = annotationSet.contains(annotation);
        if (contains) {
            METHOD_ANNOTATION_CACHE.put(method, annotationSet);
        }
        return contains;
    }

    public static <T extends Annotation> Optional<Annotation> getAnnotation(Method method, Class<T> annotationClass) {
        Preconditions.checkNotNull(method);
        Preconditions.checkNotNull(annotationClass);
        Annotation res = method.getAnnotation(annotationClass);
        if (Objects.isNull(res)) {
            return Optional.empty();
        }
        return Optional.of(res);
    }
}
