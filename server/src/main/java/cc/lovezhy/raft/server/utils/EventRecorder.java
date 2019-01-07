package cc.lovezhy.raft.server.utils;

import cc.lovezhy.raft.rpc.util.IdFactory;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日志添加的时序
 */
public class EventRecorder {

    public enum Event {
        PRE_VOTE("preVote"),

        VOTE("vote"),
        //提交日志
        LOG("log");

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

    public EventRecorder() {
        ImmutableMap.Builder<Event, Map<String, Object>> eventRecorderListMapBuilder = ImmutableMap.builder();
        for (Event event : Event.values()) {
            eventRecorderListMapBuilder.put(event, Collections.synchronizedMap(new LinkedHashMap<>()));
        }
        eventRecorderListMap = eventRecorderListMapBuilder.build();
    }

    public void add(Event event, Object record) {
        eventRecorderListMap.get(event).put(new DateTime().toString() + IdFactory.generateId(), record);
    }

    public Map<String, Object> eventRecorders(Event event) {
        return ImmutableMap.copyOf(eventRecorderListMap.get(event));
    }

}
