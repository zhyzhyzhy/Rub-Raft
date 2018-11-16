package cc.lovezhy.raft.rpc.service;

public class ExampleServiceImpl implements ExampleService {
    @Override
    public String echo(String message) {
        return message;
    }

    @Override
    public int plusOne(int i) {
        try {
            Thread.sleep(300l);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return i + 1;
    }

    @Override
    public void sout(String name) {
        try {
            Thread.sleep(300L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(name);
    }
}
