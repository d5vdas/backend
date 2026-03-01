package com.run_run_run.backend.social;

import com.run_run_run.backend.activity.Activity;
import com.run_run_run.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLikeRepository extends JpaRepository<ActivityLike, Long> {
    boolean existsByUserAndActivity(User user, Activity activity);

    long countByActivity(Activity activity);

    void deleteByUserAndActivity(User user, Activity activity);

    List<ActivityLike> findTop30ByActivity_UserOrderByCreatedAtDesc(User user);
}
