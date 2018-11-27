package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.storage.StorageEntry;
import cc.lovezhy.raft.server.utils.kryo.KryoUtils;

public class LogEntry {
    private Command command;
    private Long term;

    public LogEntry() {}

    public LogEntry(Command command, Long term) {
        this.command = command;
        this.term = term;
    }

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

    public StorageEntry toStorageEntry() {
        byte[] values = KryoUtils.serializeLogEntry(this);
        StorageEntry storageEntry = new StorageEntry();
        storageEntry.setValues(values);
        return storageEntry;
    }
}
