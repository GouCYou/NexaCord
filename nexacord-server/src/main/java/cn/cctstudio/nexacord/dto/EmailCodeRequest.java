package cn.cctstudio.nexacord.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailCodeRequest {
    @NotBlank(message = "邮箱不能为空。")
    @Email(message = "请输入有效的邮箱地址。")
    private String email;

    @NotBlank(message = "验证码用途不能为空。")
    private String purpose;
}
