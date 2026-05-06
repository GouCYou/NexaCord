package cn.cctstudio.nexacord.websocket;

import cn.cctstudio.nexacord.dto.websocket.VoiceSignalMessage;
import cn.cctstudio.nexacord.dto.websocket.WebSocketMessage;
import cn.cctstudio.nexacord.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class WebSocketMessageController {
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Map<Long, Map<Long, VoiceSignalMessage.UserInfo>> voiceParticipants = new ConcurrentHashMap<>();

    // 处理聊天消息
    @MessageMapping("/channel/{channelId}/send-message")
    public void handleChatMessage(
            @DestinationVariable Long channelId,
            @Payload WebSocketMessage message,
            Principal principal
    ) {
        User user = resolveUser(principal);
        if (user == null) {
            return;
        }

        // 设置消息的作者信息
        message.setAuthor(toWebSocketUserInfo(user));
        message.setType(WebSocketMessage.WebSocketMessageType.CHAT);

        // 广播消息到频道
        messagingTemplate.convertAndSend("/topic/channel/" + channelId, message);
    }

    // 处理用户开始输入事件
    @MessageMapping("/channel/{channelId}/typing")
    public void handleTyping(@DestinationVariable Long channelId, Principal principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return;
        }

        WebSocketMessage message = new WebSocketMessage();
        message.setChannelId(channelId);
        
        message.setAuthor(toWebSocketUserInfo(user));
        message.setType(WebSocketMessage.WebSocketMessageType.TYPING);

        // 广播正在输入事件到频道
        messagingTemplate.convertAndSend("/topic/channel/" + channelId + "/typing", message);
    }

    // 处理用户停止输入事件
    @MessageMapping("/channel/{channelId}/stop-typing")
    public void handleStopTyping(@DestinationVariable Long channelId, Principal principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return;
        }

        WebSocketMessage message = new WebSocketMessage();
        message.setChannelId(channelId);
        
        message.setAuthor(toWebSocketUserInfo(user));
        message.setType(WebSocketMessage.WebSocketMessageType.STOP_TYPING);

        // 广播停止输入事件到频道
        messagingTemplate.convertAndSend("/topic/channel/" + channelId + "/typing", message);
    }

    // 处理用户在线状态更新
    @MessageMapping("/status/update")
    public void handleStatusUpdate(@Payload String status, Principal principal) {
        User user = resolveUser(principal);
        if (user == null) {
            return;
        }

        user.setStatus(status);
        
        WebSocketMessage message = new WebSocketMessage();
        message.setAuthor(toWebSocketUserInfo(user));
        message.setType(WebSocketMessage.WebSocketMessageType.ONLINE_STATUS);

        // 广播状态更新到所有关注该用户的客户端
        messagingTemplate.convertAndSend("/topic/users/" + user.getId() + "/status", message);
    }

    @MessageMapping("/voice/{channelId}/join")
    public void handleVoiceJoin(@DestinationVariable Long channelId, Principal principal) {
        User user = resolveUser(principal);
        if (user == null || user.getId() == null) {
            return;
        }

        Map<Long, VoiceSignalMessage.UserInfo> participants = voiceParticipants.computeIfAbsent(
                channelId,
                ignored -> new ConcurrentHashMap<>()
        );

        VoiceSignalMessage.UserInfo sender = toVoiceUserInfo(user);
        participants.put(user.getId(), sender);

        VoiceSignalMessage message = buildVoiceMessage(
                channelId,
                VoiceSignalMessage.VoiceSignalType.VOICE_JOIN,
                sender
        );
        message.setParticipants(new ArrayList<>(participants.values()));

        messagingTemplate.convertAndSend("/topic/voice/" + channelId, message);
    }

    @MessageMapping("/voice/{channelId}/leave")
    public void handleVoiceLeave(@DestinationVariable Long channelId, Principal principal) {
        User user = resolveUser(principal);
        if (user == null || user.getId() == null) {
            return;
        }

        removeVoiceParticipant(channelId, user.getId(), toVoiceUserInfo(user));
    }

    @MessageMapping("/voice/{channelId}/signal")
    public void handleVoiceSignal(
            @DestinationVariable Long channelId,
            @Payload VoiceSignalMessage message,
            Principal principal
    ) {
        User user = resolveUser(principal);
        if (user == null || user.getId() == null) {
            return;
        }

        message.setChannelId(channelId);
        message.setSender(toVoiceUserInfo(user));
        messagingTemplate.convertAndSend("/topic/voice/" + channelId, message);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        User user = resolveUser(event.getUser());
        if (user == null || user.getId() == null) {
            return;
        }

        VoiceSignalMessage.UserInfo sender = toVoiceUserInfo(user);
        voiceParticipants.keySet().forEach((channelId) -> removeVoiceParticipant(channelId, user.getId(), sender));
    }

    private VoiceSignalMessage buildVoiceMessage(
            Long channelId,
            VoiceSignalMessage.VoiceSignalType type,
            VoiceSignalMessage.UserInfo sender
    ) {
        VoiceSignalMessage message = new VoiceSignalMessage();
        message.setChannelId(channelId);
        message.setType(type);
        message.setSender(sender);
        return message;
    }

    private void removeVoiceParticipant(Long channelId, Long userId, VoiceSignalMessage.UserInfo sender) {
        if (channelId == null || userId == null) {
            return;
        }

        Map<Long, VoiceSignalMessage.UserInfo> participants = voiceParticipants.get(channelId);
        if (participants == null || participants.remove(userId) == null) {
            return;
        }

        if (participants.isEmpty()) {
            voiceParticipants.remove(channelId);
        }

        VoiceSignalMessage message = buildVoiceMessage(
                channelId,
                VoiceSignalMessage.VoiceSignalType.VOICE_LEAVE,
                sender
        );
        message.setParticipants(new ArrayList<>(participants.values()));

        messagingTemplate.convertAndSend("/topic/voice/" + channelId, message);
    }

    private User resolveUser(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof User user) {
            return user;
        }

        return null;
    }

    private WebSocketMessage.UserInfo toWebSocketUserInfo(User user) {
        WebSocketMessage.UserInfo userInfo = new WebSocketMessage.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setDisplayName(user.getDisplayName());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        userInfo.setStatus(user.getStatus());
        return userInfo;
    }

    private VoiceSignalMessage.UserInfo toVoiceUserInfo(User user) {
        VoiceSignalMessage.UserInfo userInfo = new VoiceSignalMessage.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setDisplayName(user.getDisplayName());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        userInfo.setStatus(user.getStatus());
        return userInfo;
    }
}
