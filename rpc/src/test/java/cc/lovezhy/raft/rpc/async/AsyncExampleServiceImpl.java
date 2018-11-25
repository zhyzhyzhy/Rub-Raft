package cc.lovezhy.raft.rpc.async;

public class AsyncExampleServiceImpl implements AsyncExampleService {
    @Override
    public String getStdName() {
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "zhuyichen";
    }
}
