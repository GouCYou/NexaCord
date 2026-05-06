package cn.cctstudio.nexacord.service;

import cn.cctstudio.nexacord.dto.AuthResponse;
import cn.cctstudio.nexacord.dto.LoginRequest;
import cn.cctstudio.nexacord.dto.PasswordResetRequest;
import cn.cctstudio.nexacord.dto.RegisterRequest;
import cn.cctstudio.nexacord.dto.UserResponse;
import cn.cctstudio.nexacord.exception.BadRequestException;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.UserRepository;
import cn.cctstudio.nexacord.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;

    public AuthResponse register(RegisterRequest registerRequest) {
        String username = registerRequest.getUsername().trim();
        String email = normalizeEmail(registerRequest.getEmail());

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
                .displayName(username)
                .email(email)
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .status("online")
                .build();

        User savedUser = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return new AuthResponse(accessToken, refreshToken, "Bearer", toUserResponse(savedUser));
    }

    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsernameOrEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = (User) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return new AuthResponse(accessToken, refreshToken, "Bearer", toUserResponse(user));
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("刷新令牌无效。");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("用户不存在。"));

        String newAccessToken = jwtTokenProvider.generateToken(user.getUsername());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return new AuthResponse(newAccessToken, newRefreshToken, "Bearer", toUserResponse(user));
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

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
