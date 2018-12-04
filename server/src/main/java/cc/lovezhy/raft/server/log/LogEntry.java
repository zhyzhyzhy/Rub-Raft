package cc.lovezhy.raft.server.log;

import cc.lovezhy.raft.server.storage.StorageEntry;
import cc.lovezhy.raft.server.utils.kryo.KryoUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class LogEntry {

    public static LogEntry of(DefaultCommand command, Long term) {
        Preconditions.checkNotNull(command);
        Preconditions.checkNotNull(term);
        return new LogEntry(command, term);
    }


    private DefaultCommand command;
    private Long term;

    public LogEntry() {}

    public LogEntry(DefaultCommand command, Long term) {
        this.command = command;
        this.term = term;
    }

    public DefaultCommand getCommand() {
        return command;
    }

    public Long getTerm() {
        return term;
    }

    public StorageEntry toStorageEntry() {
        byte[] values = KryoUtils.serializeLogEntry(this);
        return new StorageEntry(values);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEntry logEntry = (LogEntry) o;
        return Objects.equal(command, logEntry.command) &&
                Objects.equal(term, logEntry.term);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(command, term);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("command", command)
                .add("term", term)
                .toString();
    }
}
