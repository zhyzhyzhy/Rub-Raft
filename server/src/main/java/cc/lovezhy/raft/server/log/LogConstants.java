package cc.lovezhy.raft.server.log;

public class LogConstants {
    private static final String DUMMY_LOG_ENTRY_COMMAND_KEY = "DUMMY";
    private static final String DUMMY_LOG_ENTRY_COMMAND_VALUE = "";
    public static final DefaultCommand DUMMY_COMMAND = DefaultCommand.setCommand(DUMMY_LOG_ENTRY_COMMAND_KEY, DUMMY_LOG_ENTRY_COMMAND_VALUE);
    static final LogEntry INITIAL_LOG_ENTRY = LogEntry.of(DUMMY_COMMAND, 0L);
}
