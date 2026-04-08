package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.service.ChannelService;
import cn.cctstudio.nexacord.service.ServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {
    private final ChannelService channelService;
    private final ServerService serverService;

    @PostMapping("/server/{serverId}")
    public ResponseEntity<Channel> createChannel(@PathVariable Long serverId, @Valid @RequestBody Channel channel, @AuthenticationPrincipal User currentUser) {
        Server server = serverService.getServerById(serverId);
        // 检查用户是否是服务器所有者或管理员
        // TODO: 添加权限检查
        Channel createdChannel = channelService.createChannel(channel, server);
        return new ResponseEntity<>(createdChannel, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Channel> getChannelById(@PathVariable Long id) {
        Channel channel = channelService.getChannelById(id);
        return new ResponseEntity<>(channel, HttpStatus.OK);
    }

    @GetMapping("/server/{serverId}")
    public ResponseEntity<List<Channel>> getChannelsByServer(@PathVariable Long serverId) {
        List<Channel> channels = channelService.getChannelsByServerId(serverId);
        return new ResponseEntity<>(channels, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Channel> updateChannel(@PathVariable Long id, @Valid @RequestBody Channel channel, @AuthenticationPrincipal User currentUser) {
        Channel existingChannel = channelService.getChannelById(id);
        // 检查用户是否是服务器所有者或管理员
        // TODO: 添加权限检查
        channel.setId(id);
        Channel updatedChannel = channelService.updateChannel(channel);
        return new ResponseEntity<>(updatedChannel, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        Channel channel = channelService.getChannelById(id);
        // 检查用户是否是服务器所有者或管理员
        // TODO: 添加权限检查
        channelService.deleteChannel(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}