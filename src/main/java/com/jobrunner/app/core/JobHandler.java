package com.jobrunner.app.core;

import com.jobrunner.app.model.Job;

// Implement one of these per job "type". Throw to trigger a retry.
public interface JobHandler {
    String type();
    String handle(Job job) throws Exception;
}
