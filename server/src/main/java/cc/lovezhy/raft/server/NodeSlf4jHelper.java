package cc.lovezhy.raft.server;

import cc.lovezhy.raft.server.node.NodeId;
import com.google.common.base.Preconditions;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class NodeSlf4jHelper {

    private static final Logger log = LoggerFactory.getLogger(NodeSlf4jHelper.class);

    private static final String LAYOUT_PATTERN = "%d{HH:mm:ss.SSS} [%-22t] %-5level - %msg%n";
    private static final String LOG_FIELD_NAME = "log";
    private static final String FILE_DIR = "/var/log/raft/";

    public static void initialize(NodeId nodeId) {
        Preconditions.checkNotNull(nodeId);
        String nodeLogName = parseLoggerName(nodeId);
        String fileLogName = nodeId.getPeerId() + ".log";

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();

        Layout layout = PatternLayout.newBuilder().withPattern(LAYOUT_PATTERN).build();
        Appender appender = FileAppender.newBuilder()
                .withName(nodeLogName)
                .withFileName(FILE_DIR + fileLogName)
                .withAppend(false)
                .withLayout(layout)
                .build();

        appender.start();
        configuration.addAppender(appender);

        AppenderRef ref = AppenderRef.createAppenderRef(appender.getName(), Level.INFO, null);
        AppenderRef[] appenderRefs = new AppenderRef[]{ref};

        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.INFO, nodeLogName, "false", appenderRefs, null, configuration, null);

        loggerConfig.addAppender(appender, Level.INFO, null);
        configuration.addLogger(nodeLogName, loggerConfig);
        context.updateLoggers();
    }

    public static String parseLoggerName(NodeId nodeId) {
        Preconditions.checkNotNull(nodeId);
        return "node[" + nodeId.getPeerId() + "]";
    }

    public static void changeObjectLogger(NodeId nodeId, Object targetObject) {
        Preconditions.checkNotNull(nodeId);
        try {
            Preconditions.checkNotNull(targetObject);
            Field logField = targetObject.getClass().getDeclaredField(LOG_FIELD_NAME);
            logField.setAccessible(true);
            Logger logger = LoggerFactory.getLogger(parseLoggerName(nodeId));
            logField.set(targetObject, logger);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public static void changeObjectLogger(Object copyOfObject, Object targetObject) {
        try {
            Field copyOfObjectLogField = copyOfObject.getClass().getDeclaredField(LOG_FIELD_NAME);
            copyOfObjectLogField.setAccessible(true);
            Object logger = copyOfObjectLogField.get(copyOfObject);
            Field targetObjectLogField = targetObject.getClass().getDeclaredField(LOG_FIELD_NAME);
            targetObjectLogField.setAccessible(true);
            targetObjectLogField.set(targetObject, logger);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
