package com.run_run_run.backend.social;

import com.run_run_run.backend.social.dto.CommentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/social")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    @PostMapping("/follow/{userId}")
    public ResponseEntity<Map<String, String>> follow(Authentication authentication, @PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("status", socialService.followUser(authentication.getName(), userId)));
    }

    @DeleteMapping("/follow/{userId}")
    public ResponseEntity<Map<String, String>> unfollow(Authentication authentication, @PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("status", socialService.unfollowUser(authentication.getName(), userId)));
    }

    @PostMapping("/activities/{activityId}/like")
    public ResponseEntity<Map<String, String>> like(Authentication authentication, @PathVariable Long activityId) {
        return ResponseEntity.ok(Map.of("status", socialService.likeActivity(authentication.getName(), activityId)));
    }

    @DeleteMapping("/activities/{activityId}/like")
    public ResponseEntity<Map<String, String>> unlike(Authentication authentication, @PathVariable Long activityId) {
        return ResponseEntity.ok(Map.of("status", socialService.unlikeActivity(authentication.getName(), activityId)));
    }

    @PostMapping("/activities/{activityId}/comment")
    public ResponseEntity<Map<String, String>> comment(Authentication authentication,
                                                       @PathVariable Long activityId,
                                                       @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.ok(Map.of("status", socialService.commentOnActivity(authentication.getName(), activityId, request)));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, String>> deleteComment(Authentication authentication, @PathVariable Long commentId) {
        return ResponseEntity.ok(Map.of("status", socialService.deleteComment(authentication.getName(), commentId)));
    }

    @GetMapping("/activities/{activityId}/counts")
    public ResponseEntity<Map<String, Long>> activityCounts(@PathVariable Long activityId) {
        return ResponseEntity.ok(socialService.socialCounts(activityId));
    }

    @GetMapping("/me/follows/counts")
    public ResponseEntity<Map<String, Long>> myFollowCounts(Authentication authentication) {
        return ResponseEntity.ok(socialService.myFollowCounts(authentication.getName()));
    }
}
