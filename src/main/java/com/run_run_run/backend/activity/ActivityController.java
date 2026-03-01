package com.run_run_run.backend.activity;

import com.run_run_run.backend.activity.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/start")
    public ResponseEntity<ActivityResponse> start(Authentication authentication,
                                                  @Valid @RequestBody StartActivityRequest request) {
        return ResponseEntity.ok(activityService.startActivity(authentication.getName(), request));
    }

    @PostMapping("/{id}/points")
    public ResponseEntity<Map<String, Integer>> addPoints(Authentication authentication,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody AddPointsRequest request) {
        int saved = activityService.addPoints(authentication.getName(), id, request);
        return ResponseEntity.ok(Map.of("savedPoints", saved));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<ActivityResponse> stop(Authentication authentication,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody StopActivityRequest request) {
        return ResponseEntity.ok(activityService.stopActivity(authentication.getName(), id, request));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ActivityResponse>> myActivities(Authentication authentication) {
        return ResponseEntity.ok(activityService.listMyActivities(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityResponse> getById(Authentication authentication,
                                                    @PathVariable Long id) {
        return ResponseEntity.ok(activityService.getActivity(authentication.getName(), id));
    }
}
