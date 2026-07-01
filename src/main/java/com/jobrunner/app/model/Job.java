package com.jobrunner.app.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_status", columnList = "status"),
        @Index(name = "idx_jobs_next_run", columnList = "nextRunAt")
})
public class Job {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String type;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JobStatus status;

    private int attempts;
    private int maxAttempts;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String result;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String lastError;

    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant nextRunAt;

    public Job() {}

    public static Job newJob(String type, String payload, int maxAttempts) {
        Job j = new Job();
        j.id = UUID.randomUUID().toString();
        j.type = type;
        j.payload = payload;
        j.status = JobStatus.QUEUED;
        j.attempts = 0;
        j.maxAttempts = maxAttempts;
        j.createdAt = Instant.now();
        j.nextRunAt = Instant.now();
        return j;
    }

    // getters / setters — keeping it plain, no lombok on the entity to dodge JPA quirks
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
}
