package com.jobrunner.app.api;

import com.jobrunner.app.core.HandlerRegistry;
import com.jobrunner.app.model.Job;
import com.jobrunner.app.model.JobStatus;
import com.jobrunner.app.repo.JobRepository;
import com.jobrunner.app.worker.JobQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobRepository repo;
    private final JobQueue queue;
    private final HandlerRegistry handlers;

    @Value("${taskrunner.max-retries:3}")
    private int defaultMaxRetries;

    public JobController(JobRepository repo, JobQueue queue, HandlerRegistry handlers) {
        this.repo = repo;
        this.queue = queue;
        this.handlers = handlers;
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        if (type == null || type.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing 'type'"));
        }
        if (handlers.get(type) == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "no handler for type: " + type,
                    "available", handlers.registeredTypes()
            ));
        }
        Object payloadRaw = body.get("payload");
        String payload = payloadRaw == null ? null : payloadRaw.toString();

        int maxRetries = defaultMaxRetries;
        Object mr = body.get("maxRetries");
        if (mr instanceof Number n) maxRetries = n.intValue();

        Job job = Job.newJob(type, payload, maxRetries);
        repo.save(job);
        queue.offer(job.getId());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", job.getId());
        resp.put("status", job.getStatus().name());
        resp.put("statusUrl", "http://localhost:8080/api/jobs/" + job.getId());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> status(@PathVariable String id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "not found")));
    }

    @GetMapping
    public Map<String, Object> recent(@RequestParam(defaultValue = "25") int limit) {
        List<Job> jobs = repo.findTop50ByOrderByCreatedAtDesc();
        if (jobs.size() > limit) jobs = jobs.subList(0, limit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("queueDepth", queue.depth());
        out.put("running", repo.countByStatus(JobStatus.RUNNING));
        out.put("jobs", jobs);
        return out;
    }
}
