package com.jobrunner.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// app entry point. nothing fancy here on purpose.
@SpringBootApplication
@EnableScheduling
public class TaskrunnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskrunnerApplication.class, args);
    }
}
