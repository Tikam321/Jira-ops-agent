package com.jiraops.agent.repository;

import com.jiraops.agent.model.entity.ExecutionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, Long> {
}
