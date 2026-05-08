package cn.cctstudio.nexacord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DirectAttachmentRequest {
    @NotBlank(message = "文件名不能为空。")
    private String fileName;

    private String fileType;

    @NotNull(message = "文件大小不能为空。")
    private Long fileSize;

    @NotBlank(message = "文件地址不能为空。")
    private String url;
}
