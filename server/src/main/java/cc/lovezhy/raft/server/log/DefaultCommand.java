package cc.lovezhy.raft.server.log;


import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import javax.annotation.concurrent.Immutable;

@Immutable
public class DefaultCommand implements Command {

    private static final String EMPTY_VALUE = "";

    private DefaultCommandEnum commandEnum;

    private String key;

    private String value;


    public static DefaultCommand setCommand(String key, String value) {
        return new DefaultCommand(DefaultCommandEnum.SET, key, value);
    }

    public static DefaultCommand removeCommand(String key) {
        return new DefaultCommand(DefaultCommandEnum.REMOVE, key, EMPTY_VALUE);
    }

    private DefaultCommand() {}
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultCommand that = (DefaultCommand) o;
        return commandEnum == that.commandEnum &&
                Objects.equal(key, that.key) &&
                Objects.equal(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(commandEnum, key, value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("commandEnum", commandEnum)
                .add("key", key)
                .add("value", value)
                .toString();
    }
}
