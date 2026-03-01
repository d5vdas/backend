package com.run_run_run.backend.activity.dto;

import com.run_run_run.backend.activity.Activity;

import java.time.LocalDateTime;

public record ActivityResponse(
        Long id,
        String type,
        String status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationSeconds,
        Double distanceMeters,
        Double averagePaceSecondsPerKm
) {
    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getStatus().name(),
                activity.getStartedAt(),
                activity.getEndedAt(),
                activity.getDurationSeconds(),
                activity.getDistanceMeters(),
                activity.getAveragePaceSecondsPerKm()
        );
    }
}
