package cn.cctstudio.nexacord.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MemberRoleUpdateRequest {
    @NotBlank(message = "请选择成员身份组。")
    private String role;
}
