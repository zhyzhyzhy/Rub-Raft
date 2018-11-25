package cc.lovezhy.raft.server;

import java.util.concurrent.TimeUnit;

public class RaftConstants {

    public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MILLISECONDS;

    //心跳时间
    public static final long HEART_BEAT_TIME_INTERVAL = 80;

    public static final long HEART_BEAT_TIME_INTERVAL_TIMEOUT = 160;

    //选举超时时间,150 ~ 200ms之间
    public static long getRandomStartElectionTimeout() {
        return (long) (150 + Math.random() * 150);
    }
}
