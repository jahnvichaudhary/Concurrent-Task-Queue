package com.jobrunner.app.handlers;

import com.jobrunner.app.core.JobHandler;
import com.jobrunner.app.model.Job;
import org.springframework.stereotype.Component;

@Component
public class EchoHandler implements JobHandler {
    @Override public String type() { return "echo"; }
    @Override public String handle(Job job) {
        // just bounces the payload back. useful for smoke-testing.
        return job.getPayload() == null ? "" : job.getPayload();
    }
}
