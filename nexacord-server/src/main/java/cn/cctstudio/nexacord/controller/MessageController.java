package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Message;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.service.ChannelService;
import cn.cctstudio.nexacord.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;
    private final ChannelService channelService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/channels/{channelId}/messages")
    public ResponseEntity<Message> createMessage(
            @PathVariable Long channelId,
            @RequestBody Message message,
            @AuthenticationPrincipal User currentUser) {
        Channel channel = channelService.getChannelById(channelId);
        Message createdMessage = messageService.createMessage(message, channel, currentUser);
        messagingTemplate.convertAndSend("/topic/messages/new", createdMessage);
        return new ResponseEntity<>(createdMessage, HttpStatus.CREATED);
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<Message> getMessageById(@PathVariable Long messageId) {
        Message message = messageService.getMessageById(messageId);
        return new ResponseEntity<>(message, HttpStatus.OK);
    }

    @GetMapping("/channels/{channelId}/messages")
    public ResponseEntity<List<Message>> getMessagesByChannel(
            @PathVariable Long channelId,
            @RequestParam(required = false, defaultValue = "desc") String sort) {
        List<Message> messages;
        if ("asc".equalsIgnoreCase(sort)) {
            messages = messageService.getMessagesByChannelIdOrderByCreatedAtAsc(channelId);
        } else {
            messages = messageService.getMessagesByChannelIdOrderByCreatedAtDesc(channelId);
        }
        return new ResponseEntity<>(messages, HttpStatus.OK);
    }

    @PutMapping("/messages/{messageId}")
    public ResponseEntity<Message> updateMessage(
            @PathVariable Long messageId,
            @RequestBody Message message,
            @AuthenticationPrincipal User currentUser) {
        Message existingMessage = messageService.getMessageById(messageId);
        // 检查是否是消息作者
        if (!existingMessage.getAuthor().getId().equals(currentUser.getId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        message.setId(messageId);
        Message updatedMessage = messageService.updateMessage(message);
        messagingTemplate.convertAndSend("/topic/messages/update", updatedMessage);
        return new ResponseEntity<>(updatedMessage, HttpStatus.OK);
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal User currentUser) {
        Message existingMessage = messageService.getMessageById(messageId);
        // 检查是否是消息作者
        if (!existingMessage.getAuthor().getId().equals(currentUser.getId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        messageService.deleteMessage(messageId);
        messagingTemplate.convertAndSend("/topic/messages/delete", messageId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
