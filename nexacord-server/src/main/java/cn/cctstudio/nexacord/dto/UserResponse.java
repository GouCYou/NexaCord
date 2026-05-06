package cn.cctstudio.nexacord.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String bannerUrl;
    private String bannerColor;
    private String bio;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
