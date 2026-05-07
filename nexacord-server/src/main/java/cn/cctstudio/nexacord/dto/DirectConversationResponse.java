package cn.cctstudio.nexacord.dto;

import cn.cctstudio.nexacord.controller.UserController;
import cn.cctstudio.nexacord.model.DirectConversation;
import cn.cctstudio.nexacord.model.DirectMessage;
import cn.cctstudio.nexacord.model.User;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DirectConversationResponse {
    private Long id;
    private UserResponse otherUser;
    private DirectMessageResponse lastMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public static DirectConversationResponse from(
            DirectConversation conversation,
            User currentUser,
            DirectMessage lastMessage
    ) {
        User otherUser = conversation.getUserOne().getId().equals(currentUser.getId())
                ? conversation.getUserTwo()
                : conversation.getUserOne();

        return DirectConversationResponse.builder()
                .id(conversation.getId())
                .otherUser(UserController.toUserResponse(otherUser))
                .lastMessage(lastMessage == null ? null : DirectMessageResponse.from(lastMessage))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
