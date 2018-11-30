package cc.lovezhy.raft.server.log;


import javax.annotation.concurrent.Immutable;

@Immutable
public class DefaultCommand implements Command {

    private static final String EMPTY_VALUE = "";

    private DefaultCommandEnum commandEnum;

    private String key;

    private String value;


    public static DefaultCommand setValue(String key, String value) {
        return new DefaultCommand(DefaultCommandEnum.SET, key, value);
    }

    public static DefaultCommand removeKey(String key) {
        return new DefaultCommand(DefaultCommandEnum.REMOVE, key, EMPTY_VALUE);
    }

    private DefaultCommand(DefaultCommandEnum commandEnum, String key, String value) {
        this.commandEnum = commandEnum;
        this.key = key;
        this.value = value;
    }

    public DefaultCommandEnum getCommandEnum() {
        return commandEnum;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
