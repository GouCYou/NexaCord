package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.dto.AuthResponse;
import cn.cctstudio.nexacord.dto.EmailCodeRequest;
import cn.cctstudio.nexacord.dto.LoginRequest;
import cn.cctstudio.nexacord.dto.PasswordResetRequest;
import cn.cctstudio.nexacord.dto.RegisterRequest;
import cn.cctstudio.nexacord.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("收到注册请求：username={}, email={}", registerRequest.getUsername(), registerRequest.getEmail());
        AuthResponse response = authService.register(registerRequest);
        log.info("用户注册成功：{}", registerRequest.getUsername());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = authService.login(loginRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/email-code")
    public ResponseEntity<Void> sendEmailCode(@Valid @RequestBody EmailCodeRequest request) {
        authService.sendEmailCode(request.getEmail(), request.getPurpose());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request.getRefreshToken(), request.getDeviceName());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public static class RefreshTokenRequest {
        private String refreshToken;
        private String deviceName;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }
    }
}
