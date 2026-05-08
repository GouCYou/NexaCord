package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.dto.*;
import cn.cctstudio.nexacord.exception.AccessDeniedException;
import cn.cctstudio.nexacord.exception.BadRequestException;
import cn.cctstudio.nexacord.exception.ResourceNotFoundException;
import cn.cctstudio.nexacord.model.*;
import cn.cctstudio.nexacord.repository.DirectConversationRepository;
import cn.cctstudio.nexacord.repository.DirectMessageRepository;
import cn.cctstudio.nexacord.repository.FriendshipRepository;
import cn.cctstudio.nexacord.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/api/direct-conversations")
@RequiredArgsConstructor
public class DirectMessageController {
    private final DirectConversationRepository conversationRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<List<DirectConversationResponse>> getConversations(
            @AuthenticationPrincipal User currentUser
    ) {
        List<DirectConversationResponse> conversations = conversationRepository.findByUserId(currentUser.getId())
                .stream()
                .map(conversation -> DirectConversationResponse.from(
                        conversation,
                        currentUser,
                        findLastMessage(conversation)
                ))
                .sorted(Comparator.comparing(
                        DirectConversationResponse::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        return new ResponseEntity<>(conversations, HttpStatus.OK);
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<DirectConversationResponse> getConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User currentUser
    ) {
        DirectConversation conversation = requireParticipant(conversationId, currentUser);
        return new ResponseEntity<>(
                DirectConversationResponse.from(conversation, currentUser, findLastMessage(conversation)),
                HttpStatus.OK
        );
    }

    @PostMapping("/users/{userId}")
    public ResponseEntity<DirectConversationResponse> createOrGetConversation(
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("不能和自己创建私聊。");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("没有找到这位用户。"));

        Friendship friendship = friendshipRepository.findBetweenUsers(currentUser.getId(), targetUser.getId())
                .orElseThrow(() -> new AccessDeniedException("只有好友之间可以开启私聊。"));
        if (friendship.getStatus() != Friendship.Status.ACCEPTED) {
            throw new AccessDeniedException("好友请求通过后才能开启私聊。");
        }

        DirectConversation conversation = conversationRepository
                .findBetweenUsers(currentUser.getId(), targetUser.getId())
                .orElseGet(() -> conversationRepository.save(buildConversation(currentUser, targetUser)));

        publishConversationEvent(conversation, null);
        return new ResponseEntity<>(
                DirectConversationResponse.from(conversation, currentUser, findLastMessage(conversation)),
                HttpStatus.OK
        );
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<DirectMessageResponse>> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(required = false, defaultValue = "asc") String sort,
            @AuthenticationPrincipal User currentUser
    ) {
        DirectConversation conversation = requireParticipant(conversationId, currentUser);
        List<DirectMessage> messages = "desc".equalsIgnoreCase(sort)
                ? directMessageRepository.findVisibleByConversationIdOrderByCreatedAtDesc(conversation.getId())
                : directMessageRepository.findVisibleByConversationIdOrderByCreatedAtAsc(conversation.getId());

        return new ResponseEntity<>(
                messages.stream().map(DirectMessageResponse::from).toList(),
                HttpStatus.OK
        );
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<DirectMessageResponse> createMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody DirectMessageCreateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        DirectConversation conversation = requireParticipant(conversationId, currentUser);
        String content = normalizeContent(request.getContent());
        List<DirectAttachmentRequest> attachmentRequests = request.getAttachments() == null
                ? List.of()
                : request.getAttachments();

        if (content.isBlank() && attachmentRequests.isEmpty()) {
            throw new BadRequestException("消息内容或图片不能为空。");
        }

        DirectMessage message = DirectMessage.builder()
                .conversation(conversation)
                .author(currentUser)
                .content(content)
                .edited(Boolean.FALSE)
                .deleted(Boolean.FALSE)
                .attachments(new LinkedHashSet<>())
                .build();
        attachmentRequests.stream()
                .map(DirectMessageController::toDirectAttachment)
                .forEach(attachment -> {
                    attachment.setMessage(message);
                    message.getAttachments().add(attachment);
                });

        DirectMessage savedMessage = directMessageRepository.save(message);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
        publishConversationEvent(conversation, savedMessage);

        return new ResponseEntity<>(DirectMessageResponse.from(savedMessage), HttpStatus.CREATED);
    }

    @PutMapping("/{conversationId}/messages/{messageId}")
    public ResponseEntity<DirectMessageResponse> updateMessage(
            @PathVariable Long conversationId,
            @PathVariable Long messageId,
            @Valid @RequestBody DirectMessageUpdateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        DirectConversation conversation = requireParticipant(conversationId, currentUser);
        DirectMessage message = requireMessage(conversation, messageId);
        requireAuthor(message, currentUser);

        message.setContent(request.getContent().trim());
        message.setEdited(Boolean.TRUE);
        DirectMessage savedMessage = directMessageRepository.save(message);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
        publishConversationEvent(conversation, savedMessage, "MESSAGE_UPDATED");

        return new ResponseEntity<>(DirectMessageResponse.from(savedMessage), HttpStatus.OK);
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long conversationId,
            @PathVariable Long messageId,
            @AuthenticationPrincipal User currentUser
    ) {
        DirectConversation conversation = requireParticipant(conversationId, currentUser);
        DirectMessage message = requireMessage(conversation, messageId);
        requireAuthor(message, currentUser);

        message.setDeleted(Boolean.TRUE);
        directMessageRepository.save(message);
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
        publishConversationEvent(conversation, message, "MESSAGE_DELETED");

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private DirectConversation requireParticipant(Long conversationId, User currentUser) {
        DirectConversation conversation = conversationRepository.findWithUsersById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("没有找到这条私聊会话。"));

        if (
                !conversation.getUserOne().getId().equals(currentUser.getId())
                        && !conversation.getUserTwo().getId().equals(currentUser.getId())
        ) {
            throw new AccessDeniedException("你不能访问这条私聊会话。");
        }

        return conversation;
    }

    private DirectConversation buildConversation(User currentUser, User targetUser) {
        boolean currentUserFirst = currentUser.getId() < targetUser.getId();
        return DirectConversation.builder()
                .userOne(currentUserFirst ? currentUser : targetUser)
                .userTwo(currentUserFirst ? targetUser : currentUser)
                .build();
    }

    private DirectMessage findLastMessage(DirectConversation conversation) {
        return directMessageRepository
                .findVisibleWithAttachmentsByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void publishConversationEvent(DirectConversation conversation, DirectMessage message) {
        publishConversationEvent(
                conversation,
                message,
                message == null ? "CONVERSATION_UPDATED" : "MESSAGE_CREATED"
        );
    }

    private void publishConversationEvent(DirectConversation conversation, DirectMessage message, String type) {
        publishConversationEventForUser(conversation, conversation.getUserOne(), message, type);
        publishConversationEventForUser(conversation, conversation.getUserTwo(), message, type);
    }

    private void publishConversationEventForUser(
            DirectConversation conversation,
            User user,
            DirectMessage message,
            String type
    ) {
        DirectMessage lastMessage = message == null || "MESSAGE_DELETED".equals(type)
                ? findLastMessage(conversation)
                : message;
        messagingTemplate.convertAndSend(
                "/topic/direct/user/" + user.getId(),
                DirectRealtimeEvent.builder()
                        .type(type)
                        .conversation(DirectConversationResponse.from(conversation, user, lastMessage))
                        .message(message == null ? null : DirectMessageResponse.from(message))
                        .build()
        );
    }

    private DirectMessage requireMessage(DirectConversation conversation, Long messageId) {
        DirectMessage message = directMessageRepository.findWithAttachmentsById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("没有找到这条私信消息。"));
        if (!message.getConversation().getId().equals(conversation.getId())) {
            throw new AccessDeniedException("你不能访问这条私信消息。");
        }
        return message;
    }

    private void requireAuthor(DirectMessage message, User currentUser) {
        if (!message.getAuthor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("只能修改或删除自己发送的消息。");
        }
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }

    private static DirectAttachment toDirectAttachment(DirectAttachmentRequest request) {
        validateImageAttachment(request.getFileType(), request.getUrl());
        return DirectAttachment.builder()
                .fileName(request.getFileName().trim())
                .fileType(request.getFileType())
                .fileSize(request.getFileSize())
                .url(request.getUrl().trim())
                .build();
    }

    private static void validateImageAttachment(String fileType, String url) {
        boolean imageContentType = fileType != null && fileType.toLowerCase().startsWith("image/");
        boolean imageUrl = url != null && url.toLowerCase().matches(".*\\.(png|jpe?g|gif|webp|avif|svg)(\\?.*)?$");
        if (!imageContentType && !imageUrl) {
            throw new BadRequestException("当前只支持发送图片。");
        }
    }
}
