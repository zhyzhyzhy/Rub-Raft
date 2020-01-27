package cc.lovezhy.raft.rpc;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author zhuyichen
 */
@ThreadSafe
public class RpcStatistics {

    public static RpcStatistics create() {
        return new RpcStatistics();
    }

    /**
     * 进来的Rpc个数
     */
    private LongAdder incomingRequestCounter;

    private RpcStatistics() {
        this.incomingRequestCounter = new LongAdder();
    }

    public void incrIncomingRequestAsync() {
        this.incomingRequestCounter.increment();
    }

    public long fetchIncomingRequestCount() {
        return this.incomingRequestCounter.sum();
    }

    public StatisticsModel toModel() {
        StatisticsModel statisticsModel = new StatisticsModel();
        statisticsModel.setIncomingRequest(fetchIncomingRequestCount());
        return statisticsModel;
    }


    public static class StatisticsModel {
        private Long incomingRequest;

        public Long getIncomingRequest() {
            return incomingRequest;
        }

        public void setIncomingRequest(Long incomingRequest) {
            this.incomingRequest = incomingRequest;
        }
    }

}
