package com.run_run_run.backend.social;

import com.run_run_run.backend.activity.Activity;
import com.run_run_run.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityCommentRepository extends JpaRepository<ActivityComment, Long> {
    long countByActivity(Activity activity);

    List<ActivityComment> findTop30ByActivity_UserOrderByCreatedAtDesc(User user);
}
