package cc.lovezhy.raft.server.log;

import com.google.common.base.Preconditions;
import jdk.nashorn.internal.ir.annotations.Immutable;

@Immutable
public class DefaultCommand implements Command {

    private String command;

    public static DefaultCommand create(String command) {
        return new DefaultCommand(command);
    }

    private DefaultCommand(String command) {
        Preconditions.checkNotNull(command);
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
