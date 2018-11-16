package cc.lovezhy.raft.rpc.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ReflectUtils {

    private static final Map<Method, Set<Annotation>> METHOD_ANNOTATION_CACHE;

    static {
        METHOD_ANNOTATION_CACHE = Maps.newHashMap();
    }

    public static boolean containsAnnotation(Method method, Annotation annotation) {
        Preconditions.checkNotNull(annotation);
        Preconditions.checkNotNull(method);
        Set<Annotation> annotationSet = METHOD_ANNOTATION_CACHE.get(method);
        if (Objects.isNull(annotationSet)) {
            annotationSet = Sets.newHashSet(method.getAnnotations());
        }
        return annotationSet.contains(annotation);
    }
}
