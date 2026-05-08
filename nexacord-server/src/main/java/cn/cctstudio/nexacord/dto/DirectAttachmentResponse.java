package cn.cctstudio.nexacord.dto;

import cn.cctstudio.nexacord.model.DirectAttachment;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DirectAttachmentResponse {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String url;

    public static DirectAttachmentResponse from(DirectAttachment attachment) {
        return DirectAttachmentResponse.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileType(attachment.getFileType())
                .fileSize(attachment.getFileSize())
                .url(attachment.getUrl())
                .build();
    }
}
