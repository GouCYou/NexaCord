package cn.cctstudio.nexacord.websocket;

import cn.cctstudio.nexacord.dto.websocket.DirectCallSignalMessage;
import cn.cctstudio.nexacord.dto.websocket.VoiceSignalMessage;
import cn.cctstudio.nexacord.dto.websocket.VoiceStateMessage;
import cn.cctstudio.nexacord.dto.websocket.WebSocketMessage;
import cn.cctstudio.nexacord.model.Friendship;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.FriendshipRepository;
import cn.cctstudio.nexacord.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Controller
public class WebSocketMessageController {
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private final Map<Long, Map<Long, VoiceSignalMessage.UserInfo>> voiceParticipants = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> authorizedDirectVoiceParticipants = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUsers = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

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

        updateUserStatus(user.getId(), normalizeStatus(status), true);
    }

    @MessageMapping("/voice/{channelId}/join")
    public void handleVoiceJoin(@DestinationVariable Long channelId, Principal principal) {
        User user = resolveUser(principal);
        if (user == null || user.getId() == null) {
            return;
        }

        if (isDirectVoiceChannel(channelId) && !isAuthorizedForDirectVoice(channelId, user.getId())) {
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
        broadcastVoiceState();
    }

    @MessageMapping("/voice/{channelId}/leave")
    public void handleVoiceLeave(@DestinationVariable Long channelId, Principal principal) {
        User user = resolveUser(principal);
        if (user == null || user.getId() == null) {
            return;
        }

        removeVoiceParticipant(channelId, user.getId(), toVoiceUserInfo(user));
    }

    @MessageMapping("/voice/state")
    public void handleVoiceStateRequest() {
        broadcastVoiceState();
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
        if (isDirectVoiceChannel(channelId) && !isAuthorizedForDirectVoice(channelId, user.getId())) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/voice/" + channelId, message);
    }

    @MessageMapping("/direct-call/{targetUserId}/request")
    public void handleDirectCallRequest(@DestinationVariable Long targetUserId, Principal principal) {
        User caller = resolveUser(principal);
        User callee = userRepository.findById(targetUserId).orElse(null);
        if (caller == null || caller.getId() == null || callee == null || callee.getId() == null) {
            return;
        }

        if (caller.getId().equals(callee.getId()) || !areAcceptedFriends(caller.getId(), callee.getId())) {
            return;
        }

        Long channelId = directRoomIdFor(caller.getId(), callee.getId());
        DirectCallSignalMessage message = buildDirectCallMessage(
                channelId,
                DirectCallSignalMessage.DirectCallType.DIRECT_CALL_REQUEST,
                caller,
                callee
        );
        messagingTemplate.convertAndSend("/topic/direct-call/user/" + callee.getId(), message);
    }

    @MessageMapping("/direct-call/{targetUserId}/accept")
    public void handleDirectCallAccept(@DestinationVariable Long targetUserId, Principal principal) {
        User callee = resolveUser(principal);
        User caller = userRepository.findById(targetUserId).orElse(null);
        if (callee == null || callee.getId() == null || caller == null || caller.getId() == null) {
            return;
        }

        if (!areAcceptedFriends(callee.getId(), caller.getId())) {
            return;
        }

        Long channelId = directRoomIdFor(callee.getId(), caller.getId());
        authorizedDirectVoiceParticipants
                .computeIfAbsent(channelId, ignored -> ConcurrentHashMap.newKeySet())
                .addAll(Set.of(callee.getId(), caller.getId()));

        DirectCallSignalMessage message = buildDirectCallMessage(
                channelId,
                DirectCallSignalMessage.DirectCallType.DIRECT_CALL_ACCEPT,
                caller,
                callee
        );
        messagingTemplate.convertAndSend("/topic/direct-call/user/" + caller.getId(), message);
        messagingTemplate.convertAndSend("/topic/direct-call/user/" + callee.getId(), message);
    }

    @MessageMapping("/direct-call/{targetUserId}/decline")
    public void handleDirectCallDecline(@DestinationVariable Long targetUserId, Principal principal) {
        publishDirectCallTerminalEvent(targetUserId, principal, DirectCallSignalMessage.DirectCallType.DIRECT_CALL_DECLINE);
    }

    @MessageMapping("/direct-call/{targetUserId}/cancel")
    public void handleDirectCallCancel(@DestinationVariable Long targetUserId, Principal principal) {
        publishDirectCallTerminalEvent(targetUserId, principal, DirectCallSignalMessage.DirectCallType.DIRECT_CALL_CANCEL);
    }

    @MessageMapping("/direct-call/{targetUserId}/end")
    public void handleDirectCallEnd(@DestinationVariable Long targetUserId, Principal principal) {
        publishDirectCallTerminalEvent(targetUserId, principal, DirectCallSignalMessage.DirectCallType.DIRECT_CALL_END);
    }

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        User user = resolveUser(accessor.getUser());
        String sessionId = accessor.getSessionId();
        if (user == null || user.getId() == null || sessionId == null) {
            return;
        }

        sessionUsers.put(sessionId, user.getId());
        userSessions.computeIfAbsent(user.getId(), ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
        updateUserStatus(user.getId(), "online", false);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        Long userId = sessionUsers.remove(event.getSessionId());
        if (userId == null) {
            User user = resolveUser(event.getUser());
            userId = user == null ? null : user.getId();
        }

        if (userId == null) {
            return;
        }

        Long disconnectedUserId = userId;
        Set<String> sessions = userSessions.get(disconnectedUserId);
        if (sessions != null) {
            sessions.remove(event.getSessionId());
            if (sessions.isEmpty()) {
                userSessions.remove(disconnectedUserId);
                updateUserStatus(disconnectedUserId, "offline", true);
            }
        }

        User user = userRepository.findById(disconnectedUserId).orElse(null);
        if (user == null) {
            return;
        }

        VoiceSignalMessage.UserInfo sender = toVoiceUserInfo(user);
        voiceParticipants.keySet().forEach((channelId) -> removeVoiceParticipant(channelId, disconnectedUserId, sender));
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
            if (isDirectVoiceChannel(channelId)) {
                authorizedDirectVoiceParticipants.remove(channelId);
            }
        }

        VoiceSignalMessage message = buildVoiceMessage(
                channelId,
                VoiceSignalMessage.VoiceSignalType.VOICE_LEAVE,
                sender
        );
        message.setParticipants(new ArrayList<>(participants.values()));

        messagingTemplate.convertAndSend("/topic/voice/" + channelId, message);
        broadcastVoiceState();
    }

    private void broadcastVoiceState() {
        Map<Long, List<VoiceSignalMessage.UserInfo>> state = voiceParticipants.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ArrayList<>(entry.getValue().values())
                ));
        messagingTemplate.convertAndSend(
                "/topic/voice/state",
                VoiceStateMessage.builder()
                        .participantsByChannel(state)
                        .build()
        );
    }

    private void publishDirectCallTerminalEvent(
            Long targetUserId,
            Principal principal,
            DirectCallSignalMessage.DirectCallType type
    ) {
        User sender = resolveUser(principal);
        User target = userRepository.findById(targetUserId).orElse(null);
        if (sender == null || sender.getId() == null || target == null || target.getId() == null) {
            return;
        }

        Long channelId = directRoomIdFor(sender.getId(), target.getId());
        if (type == DirectCallSignalMessage.DirectCallType.DIRECT_CALL_END) {
            authorizedDirectVoiceParticipants.remove(channelId);
        }

        DirectCallSignalMessage message = buildDirectCallMessage(channelId, type, sender, target);
        messagingTemplate.convertAndSend("/topic/direct-call/user/" + target.getId(), message);
        messagingTemplate.convertAndSend("/topic/direct-call/user/" + sender.getId(), message);
    }

    private DirectCallSignalMessage buildDirectCallMessage(
            Long channelId,
            DirectCallSignalMessage.DirectCallType type,
            User caller,
            User callee
    ) {
        DirectCallSignalMessage message = new DirectCallSignalMessage();
        message.setChannelId(channelId);
        message.setType(type);
        message.setCaller(toDirectCallUserInfo(caller));
        message.setCallee(toDirectCallUserInfo(callee));
        return message;
    }

    private boolean areAcceptedFriends(Long userId, Long otherUserId) {
        return friendshipRepository.findBetweenUsers(userId, otherUserId)
                .map(friendship -> friendship.getStatus() == Friendship.Status.ACCEPTED)
                .orElse(false);
    }

    private boolean isDirectVoiceChannel(Long channelId) {
        return channelId != null && channelId < 0;
    }

    private boolean isAuthorizedForDirectVoice(Long channelId, Long userId) {
        Set<Long> authorizedUsers = authorizedDirectVoiceParticipants.get(channelId);
        return authorizedUsers != null && authorizedUsers.contains(userId);
    }

    private long directRoomIdFor(long leftUserId, long rightUserId) {
        long left = Math.min(leftUserId, rightUserId);
        long right = Math.max(leftUserId, rightUserId);
        int hash = 0x811c9dc5;
        for (char currentChar : (left + ":" + right).toCharArray()) {
            hash ^= currentChar;
            hash *= 0x01000193;
        }

        return -Math.abs((long) (hash == 0 ? 1 : hash));
    }

    private User resolveUser(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof User user) {
            return user;
        }

        return null;
    }

    private void updateUserStatus(Long userId, String nextStatus, boolean force) {
        if (userId == null || nextStatus == null) {
            return;
        }

        userRepository.findById(userId).ifPresent(user -> {
            String currentStatus = user.getStatus();
            String resolvedStatus = nextStatus;

            if (!force && "online".equals(nextStatus) && isUserSelectedStatus(currentStatus)) {
                resolvedStatus = currentStatus;
            }

            if (!resolvedStatus.equals(currentStatus)) {
                user.setStatus(resolvedStatus);
                userRepository.save(user);
            }

            broadcastUserStatus(user);
        });
    }

    private boolean isUserSelectedStatus(String status) {
        return "online".equals(status) || "away".equals(status) || "dnd".equals(status) || "offline".equals(status);
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "online";
        }

        String normalizedStatus = status.trim().replace("\"", "");
        if (normalizedStatus.contains(":")) {
            normalizedStatus = normalizedStatus
                    .replace("{", "")
                    .replace("}", "")
                    .replace("status", "")
                    .replace(":", "")
                    .trim();
        }

        return switch (normalizedStatus) {
            case "away", "dnd", "offline" -> normalizedStatus;
            default -> "online";
        };
    }

    private void broadcastUserStatus(User user) {
        WebSocketMessage message = new WebSocketMessage();
        message.setAuthor(toWebSocketUserInfo(user));
        message.setType(WebSocketMessage.WebSocketMessageType.ONLINE_STATUS);
        messagingTemplate.convertAndSend("/topic/users/status", message);
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

    private DirectCallSignalMessage.UserInfo toDirectCallUserInfo(User user) {
        DirectCallSignalMessage.UserInfo userInfo = new DirectCallSignalMessage.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setDisplayName(user.getDisplayName());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        userInfo.setStatus(user.getStatus());
        return userInfo;
    }
}
