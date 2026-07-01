package com.jobrunner.app.core;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HandlerRegistry {

    private final List<JobHandler> handlers;
    private final Map<String, JobHandler> byType = new HashMap<>();

    public HandlerRegistry(List<JobHandler> handlers) {
        this.handlers = handlers;
    }

    @PostConstruct
    void init() {
        for (JobHandler h : handlers) {
            // if two handlers claim the same type that's a programmer bug; fail loud
            if (byType.containsKey(h.type())) {
                throw new IllegalStateException("Duplicate handler for type: " + h.type());
            }
            byType.put(h.type(), h);
        }
    }

    public JobHandler get(String type) {
        return byType.get(type);
    }

    public Set<String> registeredTypes() {
        return byType.keySet();
    }
}
