package cn.cctstudio.nexacord.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DirectRealtimeEvent {
    private String type;
    private DirectConversationResponse conversation;
    private DirectMessageResponse message;
}
