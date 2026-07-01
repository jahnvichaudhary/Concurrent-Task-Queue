package com.jobrunner.app.handlers;

import com.jobrunner.app.core.JobHandler;
import com.jobrunner.app.model.Job;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class FlakyHandler implements JobHandler {
    @Override public String type() { return "flaky"; }
    @Override public String handle(Job job) throws Exception {
        // fails ~70% of the time so you can actually see retries kick in
        if (ThreadLocalRandom.current().nextDouble() < 0.7) {
            throw new RuntimeException("simulated downstream failure");
        }
        return "ok on attempt " + job.getAttempts();
    }
}
