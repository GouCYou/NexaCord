package cn.cctstudio.nexacord.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailVerificationConfirmRequest {
    @NotBlank(message = "邮箱验证码不能为空。")
    private String verificationCode;
}
