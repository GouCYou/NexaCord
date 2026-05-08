package cn.cctstudio.nexacord.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空。")
    @Size(min = 3, max = 30, message = "用户名长度需要在 3 到 30 个字符之间。")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能使用英文字母、数字和下划线。")
    private String username;

    @Size(max = 32, message = "昵称不能超过 32 个字符。")
    private String displayName;

    @NotBlank(message = "邮箱不能为空。")
    @Email(message = "请输入有效的邮箱地址。")
    private String email;

    @NotBlank(message = "密码不能为空。")
    @Size(min = 8, max = 20, message = "密码长度需要在 8 到 20 个字符之间。")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "密码至少需要包含大写字母、小写字母、数字和符号。"
    )
    private String password;

    @NotBlank(message = "邮箱验证码不能为空。")
    private String verificationCode;
}
