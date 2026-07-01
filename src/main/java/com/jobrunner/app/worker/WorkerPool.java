package com.jobrunner.app.worker;

import com.jobrunner.app.core.HandlerRegistry;
import com.jobrunner.app.core.JobHandler;
import com.jobrunner.app.model.Job;
import com.jobrunner.app.model.JobStatus;
import com.jobrunner.app.repo.JobRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class WorkerPool {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

    private final JobRepository repo;
    private final JobQueue queue;
    private final HandlerRegistry handlers;

    @Value("${taskrunner.workers:4}")
    private int workerCount;

    @Value("${taskrunner.retry-backoff-ms:500}")
    private long backoffBaseMs;

    private ScheduledExecutorService workers;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = true;

    public WorkerPool(JobRepository repo, JobQueue queue, HandlerRegistry handlers) {
        this.repo = repo;
        this.queue = queue;
        this.handlers = handlers;
    }

    @PostConstruct
    void start() {
        // workers do the actual job execution; scheduler handles retry delays
        workers = Executors.newScheduledThreadPool(workerCount, r -> {
            Thread t = new Thread(r);
            t.setName("job-worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-scheduler");
            t.setDaemon(true);
            return t;
        });

        // re-seed queue from DB on boot (in case the process died mid-run)
        List<Job> resumable = repo.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.QUEUED
                        || j.getStatus() == JobStatus.RUNNING
                        || j.getStatus() == JobStatus.RETRY_PENDING)
                .toList();
        for (Job j : resumable) {
            // anything stuck in RUNNING from a previous crash → put back in queue
            if (j.getStatus() == JobStatus.RUNNING) {
                j.setStatus(JobStatus.QUEUED);
                repo.save(j);
            }
            queue.offer(j.getId());
        }
        if (!resumable.isEmpty()) {
            log.info("re-queued {} job(s) from previous run", resumable.size());
        }

        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::workerLoop);
        }
        log.info("started {} worker thread(s)", workerCount);
    }

    private void workerLoop() {
        while (running) {
            String jobId;
            try {
                jobId = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                process(jobId);
            } catch (Throwable t) {
                // never let an exception kill the worker thread
                log.error("unexpected worker error for job {}", jobId, t);
            }
        }
    }

    private void process(String jobId) {
        Optional<Job> opt = repo.findById(jobId);
        if (opt.isEmpty()) {
            log.warn("job {} not found, skipping", jobId);
            return;
        }
        Job job = opt.get();

        JobHandler handler = handlers.get(job.getType());
        if (handler == null) {
            job.setStatus(JobStatus.FAILED);
            job.setLastError("no handler registered for type: " + job.getType());
            job.setFinishedAt(Instant.now());
            repo.save(job);
            return;
        }

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setAttempts(job.getAttempts() + 1);
        repo.save(job);

        try {
            String result = handler.handle(job);
            job.setStatus(JobStatus.SUCCEEDED);
            job.setResult(result);
            job.setFinishedAt(Instant.now());
            repo.save(job);
        } catch (Exception ex) {
            job.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            if (job.getAttempts() >= job.getMaxAttempts()) {
                job.setStatus(JobStatus.FAILED);
                job.setFinishedAt(Instant.now());
                repo.save(job);
                log.warn("job {} failed permanently after {} attempts", job.getId(), job.getAttempts());
            } else {
                // exponential backoff: base * 2^(attempts-1)
                long delay = backoffBaseMs * (1L << (job.getAttempts() - 1));
                job.setStatus(JobStatus.RETRY_PENDING);
                job.setNextRunAt(Instant.now().plusMillis(delay));
                repo.save(job);
                final String id = job.getId();
                scheduler.schedule(() -> {
                    // flip back to QUEUED and push id onto the queue
                    repo.findById(id).ifPresent(j -> {
                        j.setStatus(JobStatus.QUEUED);
                        repo.save(j);
                        queue.offer(id);
                    });
                }, delay, TimeUnit.MILLISECONDS);
                log.info("job {} retrying in {}ms (attempt {}/{})", id, delay, job.getAttempts(), job.getMaxAttempts());
            }
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        if (workers != null) workers.shutdownNow();
        if (scheduler != null) scheduler.shutdownNow();
    }
}
