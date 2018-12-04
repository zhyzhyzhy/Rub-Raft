package cc.lovezhy.raft.server.log;

public class LogConstants {
    private static final String DUMMY_LOG_ENTRY_COMMAND_KEY = "DUMMY";
    private static final String DUMMY_LOG_ENTRY_COMMAND_VALUE = "";


    public static DefaultCommand getDummyCommand() {
        return DefaultCommand.setCommand(DUMMY_LOG_ENTRY_COMMAND_KEY, DUMMY_LOG_ENTRY_COMMAND_VALUE);
    }

    public static LogEntry getInitialLogEntry() {
        return LogEntry.of(getDummyCommand(), 0L);
    }
}
