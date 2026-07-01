package com.jobrunner.app.worker;

import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thin wrapper around LinkedBlockingQueue<String> (job ids).
 * The actual job row lives in MySQL — the queue is just an in-memory
 * "what to pick up next" buffer. On boot we re-seed it from the DB.
 */
@Component
public class JobQueue {

    private final LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>();

    public void offer(String jobId) {
        q.offer(jobId);
    }

    public String take() throws InterruptedException {
        return q.take();
    }

    public int depth() {
        return q.size();
    }
}
