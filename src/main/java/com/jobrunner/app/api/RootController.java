package com.jobrunner.app.api;

import com.jobrunner.app.core.HandlerRegistry;
import com.jobrunner.app.worker.JobQueue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    private final HandlerRegistry handlers;
    private final JobQueue queue;

    public RootController(HandlerRegistry handlers, JobQueue queue) {
        this.handlers = handlers;
        this.queue = queue;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", "taskrunner");
        m.put("baseUrl", "http://localhost:8080");
        m.put("endpoints", Map.of(
                "submit", "POST http://localhost:8080/api/jobs",
                "status", "GET http://localhost:8080/api/jobs/{id}",
                "recent", "GET http://localhost:8080/api/jobs?limit=25",
                "health", "GET http://localhost:8080/health"
        ));
        m.put("handlers", handlers.registeredTypes());
        return m;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("queueDepth", queue.depth());
        return m;
    }
}
