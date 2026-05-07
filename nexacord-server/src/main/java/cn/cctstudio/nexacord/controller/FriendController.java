package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.dto.FriendRequestCreateRequest;
import cn.cctstudio.nexacord.dto.FriendshipResponse;
import cn.cctstudio.nexacord.exception.BadRequestException;
import cn.cctstudio.nexacord.exception.ResourceNotFoundException;
import cn.cctstudio.nexacord.model.Friendship;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.FriendshipRepository;
import cn.cctstudio.nexacord.repository.UserRepository;
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
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<List<FriendshipResponse>> getFriends(@AuthenticationPrincipal User currentUser) {
        return new ResponseEntity<>(
                friendshipRepository.findAcceptedByUserId(currentUser.getId())
                        .stream()
                        .map(friendship -> FriendshipResponse.from(friendship, currentUser))
                        .toList(),
                HttpStatus.OK
        );
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<List<FriendshipResponse>> getIncomingRequests(@AuthenticationPrincipal User currentUser) {
        return new ResponseEntity<>(
                friendshipRepository.findIncomingPendingByUserId(currentUser.getId())
                        .stream()
                        .map(friendship -> FriendshipResponse.from(friendship, currentUser))
                        .toList(),
                HttpStatus.OK
        );
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<List<FriendshipResponse>> getOutgoingRequests(@AuthenticationPrincipal User currentUser) {
        return new ResponseEntity<>(
                friendshipRepository.findOutgoingPendingByUserId(currentUser.getId())
                        .stream()
                        .map(friendship -> FriendshipResponse.from(friendship, currentUser))
                        .toList(),
                HttpStatus.OK
        );
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendshipResponse> createFriendRequest(
            @Valid @RequestBody FriendRequestCreateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        String usernameOrEmail = request.getUsernameOrEmail().trim();
        if (usernameOrEmail.startsWith("@") && usernameOrEmail.indexOf('@', 1) < 0) {
            usernameOrEmail = usernameOrEmail.substring(1);
        }
        User targetUser = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new ResourceNotFoundException("没有找到这个用户。"));

        if (targetUser.getId().equals(currentUser.getId())) {
            throw new BadRequestException("不能添加自己为好友。");
        }

        friendshipRepository.findBetweenUsers(currentUser.getId(), targetUser.getId())
                .ifPresent(friendship -> {
                    throw new BadRequestException("你们已经是好友，或者已有待处理的好友请求。");
                });

        Friendship friendship = Friendship.builder()
                .requester(currentUser)
                .addressee(targetUser)
                .status(Friendship.Status.PENDING)
                .build();

        Friendship savedFriendship = friendshipRepository.save(friendship);
        publishFriendEvent(savedFriendship, "REQUEST_CREATED");

        return new ResponseEntity<>(
                FriendshipResponse.from(savedFriendship, currentUser),
                HttpStatus.CREATED
        );
    }

    @PostMapping("/{friendshipId}/accept")
    public ResponseEntity<FriendshipResponse> acceptFriendRequest(
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal User currentUser
    ) {
        Friendship friendship = findFriendshipForCurrentUser(friendshipId, currentUser);
        if (!friendship.getAddressee().getId().equals(currentUser.getId())) {
            throw new BadRequestException("只能接受发给你的好友请求。");
        }

        friendship.setStatus(Friendship.Status.ACCEPTED);
        Friendship savedFriendship = friendshipRepository.save(friendship);
        publishFriendEvent(savedFriendship, "REQUEST_ACCEPTED");

        return new ResponseEntity<>(
                FriendshipResponse.from(savedFriendship, currentUser),
                HttpStatus.OK
        );
    }

    @DeleteMapping("/{friendshipId}")
    public ResponseEntity<Void> deleteFriendship(
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal User currentUser
    ) {
        Friendship friendship = findFriendshipForCurrentUser(friendshipId, currentUser);
        publishFriendEvent(friendship, "FRIENDSHIP_REMOVED");
        friendshipRepository.delete(friendship);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private Friendship findFriendshipForCurrentUser(Long friendshipId, User currentUser) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("没有找到这条好友关系。"));

        if (
                !friendship.getRequester().getId().equals(currentUser.getId())
                        && !friendship.getAddressee().getId().equals(currentUser.getId())
        ) {
            throw new BadRequestException("你不能操作这条好友关系。");
        }

        return friendship;
    }

    private void publishFriendEvent(Friendship friendship, String type) {
        publishFriendEventForUser(friendship, friendship.getRequester(), type);
        publishFriendEventForUser(friendship, friendship.getAddressee(), type);
    }

    private void publishFriendEventForUser(Friendship friendship, User user, String type) {
        messagingTemplate.convertAndSend(
                "/topic/friends/user/" + user.getId(),
                Map.of(
                        "type", type,
                        "friendship", FriendshipResponse.from(friendship, user)
                )
        );
    }
}
