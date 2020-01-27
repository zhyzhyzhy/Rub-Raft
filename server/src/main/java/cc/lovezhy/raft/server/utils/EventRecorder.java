package cc.lovezhy.raft.server.utils;

import cc.lovezhy.raft.rpc.util.IdFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日志添加的时序
 */
public class EventRecorder {

    public enum Event {
        //preVote
        PRE_VOTE("preVote"),
        //选举Leader
        VOTE("vote"),
        //提交日志
        LOG("log"),
        //日志的SnapShot
        SnapShot("snapshot");

        private String urlPath;

        Event(String urlPath) {
            this.urlPath = urlPath;
        }

        public String getUrlPath() {
            return urlPath;
        }

        public static Event fromPath(String path) {
            for (Event event : values()) {
                if (event.getUrlPath().equals(path)) {
                    return event;
                }
            }
            return null;
        }
    }

    private Map<Event, Map<String, Object>> eventRecorderListMap;
    private Logger log;

    public EventRecorder(Logger logger) {
        Preconditions.checkNotNull(logger);
        this.log = logger;
        ImmutableMap.Builder<Event, Map<String, Object>> eventRecorderListMapBuilder = ImmutableMap.builder();
        for (Event event : Event.values()) {
            eventRecorderListMapBuilder.put(event, Collections.synchronizedMap(new LinkedHashMap<>()));
        }
        eventRecorderListMap = eventRecorderListMapBuilder.build();
    }

    public void add(Event event, Object record) {
        log.info("{}", record);
        eventRecorderListMap.get(event).put(new Date().toString() + IdFactory.generateId(), record);
    }

    public Map<String, Object> eventRecorders(Event event) {
        return ImmutableMap.copyOf(eventRecorderListMap.get(event));
    }

}
