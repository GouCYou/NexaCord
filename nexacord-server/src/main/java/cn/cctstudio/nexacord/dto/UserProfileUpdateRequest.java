package cn.cctstudio.nexacord.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    @Size(min = 2, max = 32, message = "用户名长度需要在 2 到 32 个字符之间。")
    private String username;

    @Size(min = 1, max = 32, message = "昵称长度需要在 1 到 32 个字符之间。")
    private String displayName;

    @Email(message = "请输入有效的邮箱地址。")
    private String email;

    private String emailVerificationCode;

    private String avatarUrl;
    private String bannerUrl;
    private String bannerColor;

    @Size(max = 190, message = "个人简介不能超过 190 个字符。")
    private String bio;

    private String status;
}
