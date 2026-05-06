package cn.cctstudio.nexacord.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FriendRequestCreateRequest {
    @NotBlank(message = "请输入用户名或邮箱。")
    private String usernameOrEmail;
}
