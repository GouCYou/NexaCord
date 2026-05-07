package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.dto.EmailVerificationConfirmRequest;
import cn.cctstudio.nexacord.dto.PasswordChangeRequest;
import cn.cctstudio.nexacord.dto.UserProfileUpdateRequest;
import cn.cctstudio.nexacord.dto.UserResponse;
import cn.cctstudio.nexacord.exception.BadRequestException;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.UserRepository;
import cn.cctstudio.nexacord.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        return new ResponseEntity<>(toUserResponse(currentUser), HttpStatus.OK);
    }

    @PostMapping("/me/email-code")
    public ResponseEntity<Void> sendCurrentEmailCode(@AuthenticationPrincipal User currentUser) {
        emailVerificationService.sendCode(currentUser.getEmail(), EmailVerificationService.Purpose.CHANGE_EMAIL.name());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/me/email-code/verify")
    public ResponseEntity<Void> verifyCurrentEmailCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        emailVerificationService.checkCode(
                currentUser.getEmail(),
                EmailVerificationService.Purpose.CHANGE_EMAIL,
                request.getVerificationCode()
        );
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BadRequestException("当前用户不存在。"));

        emailVerificationService.verifyCode(
                user.getEmail(),
                EmailVerificationService.Purpose.RESET_PASSWORD,
                request.getVerificationCode()
        );

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @Valid @RequestBody UserProfileUpdateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BadRequestException("当前用户不存在。"));

        if (hasText(request.getUsername()) && !request.getUsername().equals(user.getUsername())) {
            throw new BadRequestException("当前版本暂不支持直接修改登录用户名。");
        }

        if (hasText(request.getDisplayName())) {
            user.setDisplayName(request.getDisplayName().trim());
        }

        if (hasText(request.getEmail()) && !request.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
            String normalizedEmail = request.getEmail().trim().toLowerCase();
            if (userRepository.existsByEmail(normalizedEmail)) {
                throw new BadRequestException("邮箱已被占用。");
            }

            emailVerificationService.verifyCode(
                    user.getEmail(),
                    EmailVerificationService.Purpose.CHANGE_EMAIL,
                    request.getEmailVerificationCode()
            );
            user.setEmail(normalizedEmail);
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(normalizeNullable(request.getAvatarUrl()));
        }

        if (request.getBannerUrl() != null) {
            user.setBannerUrl(normalizeNullable(request.getBannerUrl()));
        }

        if (request.getBannerColor() != null) {
            user.setBannerColor(normalizeBannerColor(request.getBannerColor()));
        }

        if (request.getBio() != null) {
            user.setBio(normalizeNullable(request.getBio()));
        }

        boolean statusChanged = hasText(request.getStatus()) && !request.getStatus().trim().equals(user.getStatus());
        if (hasText(request.getStatus())) {
            user.setStatus(request.getStatus().trim());
        }

        User savedUser = userRepository.save(user);
        if (statusChanged) {
            broadcastUserStatus(savedUser);
        }

        return new ResponseEntity<>(toUserResponse(savedUser), HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam("query") String query,
            @AuthenticationPrincipal User currentUser
    ) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            return new ResponseEntity<>(List.of(), HttpStatus.OK);
        }

        List<UserResponse> users = userRepository
                .findTop12ByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        normalizedQuery,
                        normalizedQuery
                )
                .stream()
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .map(UserController::toUserResponse)
                .toList();

        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    public static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .bannerUrl(user.getBannerUrl())
                .bannerColor(user.getBannerColor())
                .bio(user.getBio())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private static String normalizeBannerColor(String value) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue == null) {
            return null;
        }

        if (!normalizedValue.matches("^#[0-9a-fA-F]{6}$")) {
            throw new BadRequestException("横幅颜色格式不正确。");
        }

        return normalizedValue;
    }

    private void broadcastUserStatus(User user) {
        cn.cctstudio.nexacord.dto.websocket.WebSocketMessage message =
                new cn.cctstudio.nexacord.dto.websocket.WebSocketMessage();
        cn.cctstudio.nexacord.dto.websocket.WebSocketMessage.UserInfo userInfo =
                new cn.cctstudio.nexacord.dto.websocket.WebSocketMessage.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setDisplayName(user.getDisplayName());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        userInfo.setStatus(user.getStatus());
        message.setAuthor(userInfo);
        message.setType(cn.cctstudio.nexacord.dto.websocket.WebSocketMessage.WebSocketMessageType.ONLINE_STATUS);
        messagingTemplate.convertAndSend("/topic/users/status", message);
    }
}
