package com.run_run_run.backend.social;

import com.run_run_run.backend.activity.Activity;
import com.run_run_run.backend.activity.ActivityRepository;
import com.run_run_run.backend.social.dto.CommentRequest;
import com.run_run_run.backend.user.User;
import com.run_run_run.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("User not found for token"));
    }
}
