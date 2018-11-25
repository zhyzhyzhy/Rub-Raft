package cc.lovezhy.raft.server.storage;

public class LogEntry {
    private Command command;
    private Integer term;

    public LogEntry() {}

    public LogEntry(Command command, Integer term) {
        this.command = command;
        this.term = term;
    }

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }

    public Integer getTerm() {
        return term;
    }

    public void setTerm(Integer term) {
        this.term = term;
    }
}
