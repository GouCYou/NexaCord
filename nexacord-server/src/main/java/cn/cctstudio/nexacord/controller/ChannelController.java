package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.exception.AccessDeniedException;
import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.service.ChannelService;
import cn.cctstudio.nexacord.service.ServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {
    private final ChannelService channelService;
    private final ServerService serverService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/server/{serverId}")
    public ResponseEntity<Channel> createChannel(@PathVariable Long serverId, @Valid @RequestBody Channel channel, @AuthenticationPrincipal User currentUser) {
        requireManager(serverId, currentUser);
        Server server = serverService.getServerById(serverId);
        Channel createdChannel = channelService.createChannel(channel, server);
        publishChannelUpdate("CHANNEL_CREATED", serverId, createdChannel);
        return new ResponseEntity<>(createdChannel, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Channel> getChannelById(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        Channel channel = channelService.getChannelById(id);
        requireMember(channel.getServer().getId(), currentUser);
        return new ResponseEntity<>(channel, HttpStatus.OK);
    }

    @GetMapping("/server/{serverId}")
    public ResponseEntity<List<Channel>> getChannelsByServer(@PathVariable Long serverId, @AuthenticationPrincipal User currentUser) {
        requireMember(serverId, currentUser);
        List<Channel> channels = channelService.getChannelsByServerId(serverId);
        return new ResponseEntity<>(channels, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Channel> updateChannel(@PathVariable Long id, @Valid @RequestBody Channel channel, @AuthenticationPrincipal User currentUser) {
        Channel existingChannel = channelService.getChannelById(id);
        requireManager(existingChannel.getServer().getId(), currentUser);
        channel.setId(id);
        Channel updatedChannel = channelService.updateChannel(channel);
        publishChannelUpdate("CHANNEL_UPDATED", updatedChannel.getServer().getId(), updatedChannel);
        return new ResponseEntity<>(updatedChannel, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        Channel channel = channelService.getChannelById(id);
        requireManager(channel.getServer().getId(), currentUser);
        channelService.deleteChannel(id);
        publishChannelUpdate("CHANNEL_DELETED", channel.getServer().getId(), channel);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private void requireMember(Long serverId, User currentUser) {
        if (currentUser == null || !serverService.isServerMember(serverId, currentUser.getId())) {
            throw new AccessDeniedException("你不是这个服务器的成员。");
        }
    }

    private void requireManager(Long serverId, User currentUser) {
        if (currentUser == null || !serverService.isServerManager(serverId, currentUser.getId())) {
            throw new AccessDeniedException("你没有管理频道的权限。");
        }
    }

    private void publishChannelUpdate(String type, Long serverId, Channel channel) {
        messagingTemplate.convertAndSend(
                "/topic/channels/update",
                Map.of(
                        "type", type,
                        "serverId", serverId,
                        "channelId", channel.getId()
                )
        );
    }
}
