package cc.lovezhy.raft.rpc.service;

public interface ExampleService {

    String echo(String message);

    int plusOne(int i);

    void sout(String name);
}
