package cn.cctstudio.nexacord.dto;

import cn.cctstudio.nexacord.model.Member;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class MemberResponse {
    private Long id;
    private UserResponse user;
    private String nickname;
    private String avatarUrl;
    private String role;
    private Instant joinedAt;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .user(UserControllerAdapter.toUserResponse(member))
                .nickname(member.getNickname())
                .avatarUrl(member.getAvatarUrl())
                .role(member.getRole().name())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private static final class UserControllerAdapter {
        private static UserResponse toUserResponse(Member member) {
            return UserResponse.builder()
                    .id(member.getUser().getId())
                    .username(member.getUser().getUsername())
                    .displayName(member.getUser().getDisplayName())
                    .email(member.getUser().getEmail())
                    .avatarUrl(member.getUser().getAvatarUrl())
                    .bannerUrl(member.getUser().getBannerUrl())
                    .bannerColor(member.getUser().getBannerColor())
                    .bio(member.getUser().getBio())
                    .status(member.getUser().getStatus())
                    .createdAt(member.getUser().getCreatedAt())
                    .updatedAt(member.getUser().getUpdatedAt())
                    .build();
        }
    }
}
