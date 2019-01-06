package cc.lovezhy.raft.server.utils;

import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日志添加的时序
 */
public class EventRecorder {

    public enum Event {
        PRE_VOTE,
        VOTE,
        //提交日志
        COMMIT_LOG
    }

    private Map<Event, Map<String, Object>> eventRecorderListMap;

    public EventRecorder() {
        ImmutableMap.Builder<Event, Map<String, Object>> eventRecorderListMapBuilder = ImmutableMap.builder();
        for(Event event : Event.values()) {
            eventRecorderListMapBuilder.put(event, Collections.synchronizedMap(new LinkedHashMap<>()));
        }
        eventRecorderListMap = eventRecorderListMapBuilder.build();
    }

    public void add(Event event, Object record) {
        eventRecorderListMap.get(event).put(String.valueOf(System.currentTimeMillis()), record);
    }

    public Map<String, Object> eventRecorders(Event event) {
        return eventRecorderListMap.get(event);
    }

}
