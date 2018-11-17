package cc.lovezhy.raft.rpc.service;

public class ExampleServiceImpl implements ExampleService {
    @Override
    public String echo(String message) {
        return message;
    }

    @Override
    public int plusOne(int i) {
        return i + 1;
    }

    @Override
    public void sout(String name) {
        System.out.println(name);
    }
}
