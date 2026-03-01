package com.run_run_run.backend.social;

import com.run_run_run.backend.activity.Activity;
import com.run_run_run.backend.activity.ActivityRepository;
import com.run_run_run.backend.social.dto.CommentRequest;
import com.run_run_run.backend.user.User;
import com.run_run_run.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SocialService {

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final FollowRepository followRepository;
    private final ActivityLikeRepository activityLikeRepository;
    private final ActivityCommentRepository activityCommentRepository;

    public SocialService(UserRepository userRepository,
                         ActivityRepository activityRepository,
                         FollowRepository followRepository,
                         ActivityLikeRepository activityLikeRepository,
                         ActivityCommentRepository activityCommentRepository) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.followRepository = followRepository;
        this.activityLikeRepository = activityLikeRepository;
        this.activityCommentRepository = activityCommentRepository;
    }

    @Transactional
    public String followUser(String email, Long followeeId) {
        User follower = getUserByEmail(email);
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (follower.getId().equals(followee.getId())) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }

        if (!followRepository.existsByFollowerAndFollowee(follower, followee)) {
            Follow follow = new Follow();
            follow.setFollower(follower);
            follow.setFollowee(followee);
            followRepository.save(follow);
        }

        return "followed";
    }

    @Transactional
    public String unfollowUser(String email, Long followeeId) {
        User follower = getUserByEmail(email);
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        followRepository.deleteByFollowerAndFollowee(follower, followee);
        return "unfollowed";
    }

    @Transactional
    public String likeActivity(String email, Long activityId) {
        User user = getUserByEmail(email);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));

        if (!activityLikeRepository.existsByUserAndActivity(user, activity)) {
            ActivityLike like = new ActivityLike();
            like.setUser(user);
            like.setActivity(activity);
            activityLikeRepository.save(like);
        }

        return "liked";
    }

    @Transactional
    public String unlikeActivity(String email, Long activityId) {
        User user = getUserByEmail(email);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));

        activityLikeRepository.deleteByUserAndActivity(user, activity);
        return "unliked";
    }

    @Transactional
    public String commentOnActivity(String email, Long activityId, CommentRequest request) {
        User user = getUserByEmail(email);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));

        ActivityComment comment = new ActivityComment();
        comment.setUser(user);
        comment.setActivity(activity);
        comment.setText(request.text().trim());
        activityCommentRepository.save(comment);

        return "comment added";
    }

    @Transactional
    public String deleteComment(String email, Long commentId) {
        User user = getUserByEmail(email);
        ActivityComment comment = activityCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can delete only your own comment");
        }

        activityCommentRepository.delete(comment);
        return "comment deleted";
    }

    @Transactional(readOnly = true)
    public Map<String, Long> socialCounts(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));

        long likes = activityLikeRepository.countByActivity(activity);
        long comments = activityCommentRepository.countByActivity(activity);
        long followers = followRepository.countByFollowee(activity.getUser());

        return Map.of(
                "likes", likes,
                "comments", comments,
                "ownerFollowers", followers
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Long> myFollowCounts(String email) {
        User user = getUserByEmail(email);
        long followers = followRepository.countByFollowee(user);
        long following = followRepository.countByFollower(user);
        return Map.of("followers", followers, "following", following);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> feed(String email) {
        User me = getUserByEmail(email);
        List<User> followees = followRepository.findByFollowerOrderByCreatedAtDesc(me)
                .stream()
                .map(Follow::getFollowee)
                .distinct()
                .toList();

        if (followees.isEmpty()) return List.of();

        List<Activity> activities = activityRepository.findTop50ByUserInOrderByStartedAtDesc(followees);
        return activities.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("activityId", a.getId());
            row.put("type", a.getType());
            row.put("status", a.getStatus().name());
            row.put("startedAt", a.getStartedAt());
            row.put("durationSeconds", a.getDurationSeconds());
            row.put("distanceMeters", a.getDistanceMeters());
            row.put("userId", a.getUser().getId());
            row.put("userName", a.getUser().getName());
            row.put("userEmail", a.getUser().getEmail());
            row.put("likes", activityLikeRepository.countByActivity(a));
            row.put("comments", activityCommentRepository.countByActivity(a));
            row.put("likedByMe", activityLikeRepository.existsByUserAndActivity(me, a));
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchUsers(String email, String q) {
        User me = getUserByEmail(email);
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) return List.of();

        return userRepository.findTop20ByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByNameAsc(query, query)
                .stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "name", u.getName(),
                        "email", u.getEmail(),
                        "isSelf", u.getId().equals(me.getId()),
                        "isFollowing", !u.getId().equals(me.getId()) && followRepository.existsByFollowerAndFollowee(me, u)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> notifications(String email) {
        User me = getUserByEmail(email);
        List<Map<String, Object>> items = new ArrayList<>();

        for (Follow f : followRepository.findTop20ByFolloweeOrderByCreatedAtDesc(me)) {
            items.add(Map.of(
                    "type", "follow",
                    "createdAt", f.getCreatedAt(),
                    "message", f.getFollower().getName() + " started following you"
            ));
        }

        for (ActivityLike l : activityLikeRepository.findTop30ByActivity_UserOrderByCreatedAtDesc(me)) {
            if (l.getUser().getId().equals(me.getId())) continue;
            items.add(Map.of(
                    "type", "like",
                    "createdAt", l.getCreatedAt(),
                    "message", l.getUser().getName() + " liked your activity #" + l.getActivity().getId()
            ));
        }

        for (ActivityComment c : activityCommentRepository.findTop30ByActivity_UserOrderByCreatedAtDesc(me)) {
            if (c.getUser().getId().equals(me.getId())) continue;
            items.add(Map.of(
                    "type", "comment",
                    "createdAt", c.getCreatedAt(),
                    "message", c.getUser().getName() + " commented on your activity #" + c.getActivity().getId()
            ));
        }

        return items.stream()
                .sorted(Comparator.comparing((Map<String, Object> x) -> String.valueOf(x.get("createdAt"))).reversed())
                .limit(25)
                .toList();
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("User not found for token"));
    }
}
