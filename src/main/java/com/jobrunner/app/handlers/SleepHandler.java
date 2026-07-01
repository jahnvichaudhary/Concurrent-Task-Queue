package com.jobrunner.app.handlers;

import com.jobrunner.app.core.JobHandler;
import com.jobrunner.app.model.Job;
import org.springframework.stereotype.Component;

@Component
public class SleepHandler implements JobHandler {
    @Override public String type() { return "sleep"; }
    @Override public String handle(Job job) throws Exception {
        long ms = 1000;
        try { ms = Long.parseLong(job.getPayload().trim()); } catch (Exception ignored) {}
        Thread.sleep(ms);
        return "slept " + ms + "ms";
    }
}
