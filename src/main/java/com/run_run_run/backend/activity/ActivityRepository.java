package com.run_run_run.backend.activity;

import com.run_run_run.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByUserOrderByStartedAtDesc(User user);
    Optional<Activity> findByIdAndUser(Long id, User user);
}
