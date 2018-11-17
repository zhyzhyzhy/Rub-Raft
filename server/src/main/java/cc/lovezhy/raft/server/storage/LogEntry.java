package cc.lovezhy.raft.server.storage;

public class LogEntry {
    private Command command;
    private Long term;

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }

    public Long getTerm() {
        return term;
    }

    public void setTerm(Long term) {
        this.term = term;
    }
}
