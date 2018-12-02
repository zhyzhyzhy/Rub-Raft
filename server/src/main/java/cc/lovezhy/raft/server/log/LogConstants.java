package cc.lovezhy.raft.server.log;

class LogConstants {
    private static final String INITIAL_LOG_ENTRY_COMMAND_KEY = "DUMMY";
    private static final String INITIAL_LOG_ENTRY_COMMAND_VALUE = "";
    static final LogEntry INITIAL_LOG_ENTRY = LogEntry.of(DefaultCommand.setCommand(INITIAL_LOG_ENTRY_COMMAND_KEY, INITIAL_LOG_ENTRY_COMMAND_VALUE), 0L);
}
