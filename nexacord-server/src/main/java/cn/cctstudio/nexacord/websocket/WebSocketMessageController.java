package cn.cctstudio.nexacord.websocket;

import cn.cctstudio.nexacord.dto.websocket.WebSocketMessage;
import cn.cctstudio.nexacord.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketMessageController {
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    // 处理聊天消息
    @MessageMapping("/channel/{channelId}/send-message")
    public void handleChatMessage(@DestinationVariable Long channelId, @Payload WebSocketMessage message, @AuthenticationPrincipal User user) {
        // 设置消息的作者信息
        WebSocketMessage.UserInfo authorInfo = new WebSocketMessage.UserInfo();
        authorInfo.setId(user.getId());
        authorInfo.setUsername(user.getUsername());
        authorInfo.setAvatarUrl(user.getAvatarUrl());
        authorInfo.setStatus(user.getStatus());
        message.setAuthor(authorInfo);
        message.setType(WebSocketMessage.WebSocketMessageType.CHAT);

        // 广播消息到频道
        messagingTemplate.convertAndSend("/topic/channel/" + channelId, message);
    }

    // 处理用户开始输入事件
    @MessageMapping("/channel/{channelId}/typing")
    public void handleTyping(@DestinationVariable Long channelId, @AuthenticationPrincipal User user) {
        WebSocketMessage message = new WebSocketMessage();
        message.setChannelId(channelId);
        
        WebSocketMessage.UserInfo authorInfo = new WebSocketMessage.UserInfo();
        authorInfo.setId(user.getId());
        authorInfo.setUsername(user.getUsername());
        message.setAuthor(authorInfo);
        message.setType(WebSocketMessage.WebSocketMessageType.TYPING);

        // 广播typing事件到频道
        messagingTemplate.convertAndSend("/topic/channel/" + channelId + "/typing", message);
    }

    // 处理用户停止输入事件
    @MessageMapping("/channel/{channelId}/stop-typing")
    public void handleStopTyping(@DestinationVariable Long channelId, @AuthenticationPrincipal User user) {
        WebSocketMessage message = new WebSocketMessage();
        message.setChannelId(channelId);
        
        WebSocketMessage.UserInfo authorInfo = new WebSocketMessage.UserInfo();
        authorInfo.setId(user.getId());
        authorInfo.setUsername(user.getUsername());
        message.setAuthor(authorInfo);
        message.setType(WebSocketMessage.WebSocketMessageType.STOP_TYPING);

        // 广播stop-typing事件到频道
        messagingTemplate.convertAndSend("/topic/channel/" + channelId + "/typing", message);
    }

    // 处理用户在线状态更新
    @MessageMapping("/status/update")
    public void handleStatusUpdate(@Payload String status, @AuthenticationPrincipal User user) {
        user.setStatus(status);
        
        WebSocketMessage message = new WebSocketMessage();
        
        WebSocketMessage.UserInfo authorInfo = new WebSocketMessage.UserInfo();
        authorInfo.setId(user.getId());
        authorInfo.setUsername(user.getUsername());
        authorInfo.setAvatarUrl(user.getAvatarUrl());
        authorInfo.setStatus(status);
        message.setAuthor(authorInfo);
        message.setType(WebSocketMessage.WebSocketMessageType.ONLINE_STATUS);

        // 广播状态更新到所有关注该用户的客户端
        messagingTemplate.convertAndSend("/topic/users/" + user.getId() + "/status", message);
    }
}