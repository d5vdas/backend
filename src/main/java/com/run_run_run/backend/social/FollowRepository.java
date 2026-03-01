package com.run_run_run.backend.social;

import com.run_run_run.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    boolean existsByFollowerAndFollowee(User follower, User followee);

    long countByFollowee(User followee);

    long countByFollower(User follower);

    void deleteByFollowerAndFollowee(User follower, User followee);

    List<Follow> findByFollowerOrderByCreatedAtDesc(User follower);

    List<Follow> findTop20ByFolloweeOrderByCreatedAtDesc(User followee);
}
