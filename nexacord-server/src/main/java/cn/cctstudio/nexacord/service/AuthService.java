package cn.cctstudio.nexacord.service;

import cn.cctstudio.nexacord.dto.AuthResponse;
import cn.cctstudio.nexacord.dto.LoginRequest;
import cn.cctstudio.nexacord.dto.PasswordResetRequest;
import cn.cctstudio.nexacord.dto.RegisterRequest;
import cn.cctstudio.nexacord.dto.UserResponse;
import cn.cctstudio.nexacord.exception.BadRequestException;
import cn.cctstudio.nexacord.exception.SessionReplacedException;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.UserRepository;
import cn.cctstudio.nexacord.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        String username = registerRequest.getUsername().trim();
        String displayName = normalizeDisplayName(registerRequest.getDisplayName(), username);
        String email = normalizeEmail(registerRequest.getEmail());
        String deviceName = normalizeDeviceName(registerRequest.getDeviceName());

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("用户名已被占用。");
        }

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("邮箱已被占用。");
        }

        emailVerificationService.verifyCode(
                email,
                EmailVerificationService.Purpose.REGISTER,
                registerRequest.getVerificationCode()
        );

        User user = User.builder()
                .username(username)
                .displayName(displayName)
                .email(email)
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .status("online")
                .build();

        assignNewSession(user, deviceName);
        User savedUser = userRepository.save(user);

        return buildAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsernameOrEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = (User) authentication.getPrincipal();
        String deviceName = normalizeDeviceName(loginRequest.getDeviceName());
        boolean shouldNotifyOldSession = StringUtils.hasText(user.getActiveSessionId());

        assignNewSession(user, deviceName);
        User savedUser = userRepository.save(user);
        if (shouldNotifyOldSession) {
            publishSessionReplacement(savedUser);
        }

        return buildAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken, String deviceName) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("刷新令牌无效。");
        }
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BadRequestException("刷新令牌类型无效。");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        String sessionId = jwtTokenProvider.getSessionIdFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("用户不存在。"));

        if (StringUtils.hasText(user.getActiveSessionId()) && !Objects.equals(user.getActiveSessionId(), sessionId)) {
            throw new SessionReplacedException(user.getActiveDeviceName());
        }

        if (!StringUtils.hasText(user.getActiveSessionId())) {
            assignNewSession(user, normalizeDeviceName(deviceName));
            user = userRepository.save(user);
        }

        return buildAuthResponse(user);
    }

    public void sendEmailCode(String email, String purpose) {
        EmailVerificationService.Purpose parsedPurpose = emailVerificationService.parsePurpose(purpose);
        String normalizedEmail = normalizeEmail(email);

        if (parsedPurpose == EmailVerificationService.Purpose.REGISTER && userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("邮箱已被占用。");
        }

        if (parsedPurpose == EmailVerificationService.Purpose.RESET_PASSWORD && userRepository.findByEmail(normalizedEmail).isEmpty()) {
            throw new BadRequestException("没有找到使用该邮箱的账号。");
        }

        if (parsedPurpose == EmailVerificationService.Purpose.CHANGE_EMAIL && userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("邮箱已被占用。");
        }

        emailVerificationService.sendCode(normalizedEmail, parsedPurpose.name());
    }

    public void resetPassword(PasswordResetRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("没有找到使用该邮箱的账号。"));

        emailVerificationService.verifyCode(
                email,
                EmailVerificationService.Purpose.RESET_PASSWORD,
                request.getVerificationCode()
        );

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
    }

    private UserResponse toUserResponse(User user) {
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

    private void assignNewSession(User user, String deviceName) {
        user.setActiveSessionId(UUID.randomUUID().toString());
        user.setActiveDeviceName(deviceName);
        user.setActiveSessionIssuedAt(Instant.now());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getActiveSessionId(), user.getActiveDeviceName());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getActiveSessionId(), user.getActiveDeviceName());
        return new AuthResponse(accessToken, refreshToken, "Bearer", toUserResponse(user));
    }

    private void publishSessionReplacement(User user) {
        messagingTemplate.convertAndSend(
                "/topic/auth/user/" + user.getId(),
                Map.of(
                        "type", "SESSION_REPLACED",
                        "deviceName", StringUtils.hasText(user.getActiveDeviceName()) ? user.getActiveDeviceName() : "另一台设备",
                        "loggedInAt", user.getActiveSessionIssuedAt() == null ? Instant.now().toString() : user.getActiveSessionIssuedAt().toString()
                )
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeDisplayName(String displayName, String fallbackUsername) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return fallbackUsername;
        }

        return displayName.trim();
    }

    private String normalizeDeviceName(String deviceName) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            return "未知设备";
        }

        String normalized = deviceName.trim().replaceAll("\\s+", " ");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }
}
