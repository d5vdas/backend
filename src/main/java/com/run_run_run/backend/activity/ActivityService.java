package com.run_run_run.backend.activity;

import com.run_run_run.backend.activity.dto.*;
import com.run_run_run.backend.user.User;
import com.run_run_run.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityPointRepository activityPointRepository;
    private final UserRepository userRepository;

    public ActivityService(ActivityRepository activityRepository,
                           ActivityPointRepository activityPointRepository,
                           UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.activityPointRepository = activityPointRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ActivityResponse startActivity(String email, StartActivityRequest request) {
        User user = getUserByEmail(email);

        Activity activity = new Activity();
        activity.setUser(user);
        activity.setType(request.type().trim().toUpperCase());
        activity.setStartedAt(LocalDateTime.now());
        activity.setStatus(ActivityStatus.IN_PROGRESS);

        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional
    public int addPoints(String email, Long activityId, AddPointsRequest request) {
        User user = getUserByEmail(email);
        Activity activity = getOwnedActivity(activityId, user);

        if (activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new IllegalStateException("Activity is already completed");
        }

        List<ActivityPoint> points = request.points().stream().map(p -> {
            ActivityPoint point = new ActivityPoint();
            point.setActivity(activity);
            point.setLatitude(p.latitude());
            point.setLongitude(p.longitude());
            point.setRecordedAt(p.recordedAt());
            point.setSequenceNo(p.sequenceNo());
            return point;
        }).toList();

        activityPointRepository.saveAll(points);
        return points.size();
    }

    @Transactional
    public ActivityResponse stopActivity(String email, Long activityId, StopActivityRequest request) {
        User user = getUserByEmail(email);
        Activity activity = getOwnedActivity(activityId, user);

        if (activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new IllegalStateException("Activity is already completed");
        }

        LocalDateTime endedAt = request.endedAt();
        if (endedAt.isBefore(activity.getStartedAt())) {
            throw new IllegalArgumentException("endedAt cannot be before startedAt");
        }

        List<ActivityPoint> points = activityPointRepository.findByActivityOrderBySequenceNoAsc(activity);
        double distanceMeters = calculateDistanceMeters(points);
        long durationSeconds = Duration.between(activity.getStartedAt(), endedAt).getSeconds();
        Double pace = distanceMeters > 0 ? durationSeconds / (distanceMeters / 1000.0) : null;

        activity.setEndedAt(endedAt);
        activity.setDurationSeconds(durationSeconds);
        activity.setDistanceMeters(distanceMeters);
        activity.setAveragePaceSecondsPerKm(pace);
        activity.setStatus(ActivityStatus.COMPLETED);

        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listMyActivities(String email) {
        User user = getUserByEmail(email);
        return activityRepository.findByUserOrderByStartedAtDesc(user)
                .stream()
                .map(ActivityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ActivityResponse getActivity(String email, Long activityId) {
        User user = getUserByEmail(email);
        return ActivityResponse.from(getOwnedActivity(activityId, user));
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("User not found for token"));
    }

    private Activity getOwnedActivity(Long activityId, User user) {
        return activityRepository.findByIdAndUser(activityId, user)
                .orElseThrow(() -> new AccessDeniedException("Activity not found"));
    }

    private double calculateDistanceMeters(List<ActivityPoint> points) {
        if (points.size() < 2) return 0.0;

        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            ActivityPoint a = points.get(i - 1);
            ActivityPoint b = points.get(i);
            total += haversineMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }
        return total;
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
