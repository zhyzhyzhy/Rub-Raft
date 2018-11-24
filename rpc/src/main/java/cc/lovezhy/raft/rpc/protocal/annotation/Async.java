package cc.lovezhy.raft.rpc.protocal.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 异步方法
 * 返回结果是ListenFuture
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Async {
    /**
     * @return 等待超时时间
     */
    int waitTimeOutMills() default 200;
}
