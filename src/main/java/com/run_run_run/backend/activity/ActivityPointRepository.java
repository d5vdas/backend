package com.run_run_run.backend.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityPointRepository extends JpaRepository<ActivityPoint, Long> {
    List<ActivityPoint> findByActivityOrderBySequenceNoAsc(Activity activity);
}
