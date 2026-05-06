package cn.cctstudio.nexacord.dto;

import cn.cctstudio.nexacord.model.Friendship;
import cn.cctstudio.nexacord.model.User;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FriendshipResponse {
    private Long id;
    private String status;
    private String direction;
    private UserResponse user;
    private Instant createdAt;
    private Instant updatedAt;

    public static FriendshipResponse from(Friendship friendship, User currentUser) {
        boolean outgoing = friendship.getRequester().getId().equals(currentUser.getId());
        User otherUser = outgoing ? friendship.getAddressee() : friendship.getRequester();

        return FriendshipResponse.builder()
                .id(friendship.getId())
                .status(friendship.getStatus().name())
                .direction(outgoing ? "OUTGOING" : "INCOMING")
                .user(toUserResponse(otherUser))
                .createdAt(friendship.getCreatedAt())
                .updatedAt(friendship.getUpdatedAt())
                .build();
    }

    private static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .bannerUrl(user.getBannerUrl())
                .bio(user.getBio())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
