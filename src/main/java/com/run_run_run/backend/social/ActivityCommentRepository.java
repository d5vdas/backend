package com.run_run_run.backend.social;

import com.run_run_run.backend.activity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityCommentRepository extends JpaRepository<ActivityComment, Long> {
    long countByActivity(Activity activity);
}
