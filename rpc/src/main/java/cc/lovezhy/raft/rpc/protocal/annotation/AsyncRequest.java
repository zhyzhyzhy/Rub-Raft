package cc.lovezhy.raft.rpc.protocal.annotation;

/**
 * 异步方法
 */
public @interface AsyncRequest {
    /**
     * @return 等待超时时间
     */
    int waitTimeOutMills() default 200;
}
