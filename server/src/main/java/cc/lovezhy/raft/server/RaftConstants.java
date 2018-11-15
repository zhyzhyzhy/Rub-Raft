package cc.lovezhy.raft.server;

import java.util.concurrent.TimeUnit;

public class RaftConstants {

    //选举超时时间
    public static final long START_ELECTION_TIMEOUT = 200;

    public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MILLISECONDS;
}
