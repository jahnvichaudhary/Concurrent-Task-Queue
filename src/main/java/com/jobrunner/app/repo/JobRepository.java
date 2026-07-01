package com.jobrunner.app.repo;

import com.jobrunner.app.model.Job;
import com.jobrunner.app.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, String> {
    List<Job> findTop50ByOrderByCreatedAtDesc();
    long countByStatus(JobStatus status);
}
