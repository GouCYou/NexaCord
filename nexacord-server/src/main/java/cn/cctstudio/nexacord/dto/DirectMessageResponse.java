package cn.cctstudio.nexacord.dto;

import cn.cctstudio.nexacord.controller.UserController;
import cn.cctstudio.nexacord.model.DirectAttachment;
import cn.cctstudio.nexacord.model.DirectMessage;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Data
@Builder
public class DirectMessageResponse {
    private Long id;
    private Long conversationId;
    private String content;
    private UserResponse author;
    private Instant createdAt;
    private Instant updatedAt;
    private Boolean edited;
    private Boolean deleted;
    private List<DirectAttachmentResponse> attachments;

    public static DirectMessageResponse from(DirectMessage message) {
        return DirectMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .content(message.getContent())
                .author(UserController.toUserResponse(message.getAuthor()))
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .edited(Boolean.TRUE.equals(message.getEdited()))
                .deleted(Boolean.TRUE.equals(message.getDeleted()))
                .attachments(message.getAttachments() == null
                        ? List.of()
                        : message.getAttachments()
                        .stream()
                        .sorted(Comparator.comparing(DirectAttachment::getId, Comparator.nullsLast(Long::compareTo)))
                        .map(DirectAttachmentResponse::from)
                        .toList())
                .build();
    }
}
