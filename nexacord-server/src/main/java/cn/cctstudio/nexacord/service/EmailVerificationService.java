package cn.cctstudio.nexacord.service;

import cn.cctstudio.nexacord.exception.BadRequestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {
    public enum Purpose {
        REGISTER("注册 Nexacord"),
        CHANGE_EMAIL("更换邮箱"),
        RESET_PASSWORD("重置密码");

        private final String label;

        Purpose(String label) {
            this.label = label;
        }
    }

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalCode> fallbackCodes = new ConcurrentHashMap<>();

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public void sendCode(String email, String purposeValue) {
        Purpose purpose = parsePurpose(purposeValue);
        String normalizedEmail = normalizeEmail(email);
        String code = generateCode();
        String key = buildKey(normalizedEmail, purpose);

        storeCode(key, code);
        sendEmail(normalizedEmail, purpose, code);
    }

    public void verifyCode(String email, Purpose purpose, String code) {
        String key = validateCode(email, purpose, code);
        deleteCode(key);
    }

    public void checkCode(String email, Purpose purpose, String code) {
        validateCode(email, purpose, code);
    }

    private String validateCode(String email, Purpose purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code == null ? "" : code.trim();
        if (!StringUtils.hasText(normalizedCode)) {
            throw new BadRequestException("邮箱验证码不能为空。");
        }

        String key = buildKey(normalizedEmail, purpose);
        String storedCode = readCode(key);
        if (storedCode == null || !storedCode.equals(normalizedCode)) {
            throw new BadRequestException("邮箱验证码不正确或已过期。");
        }

        return key;
    }

    public Purpose parsePurpose(String value) {
        try {
            return Purpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BadRequestException("验证码用途不正确。");
        }
    }

    private void sendEmail(String email, Purpose purpose, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (StringUtils.hasText(mailFrom)) {
                helper.setFrom(mailFrom);
            }
            helper.setTo(email);
            helper.setSubject("Nexacord 邮箱验证码");
            helper.setText(buildEmailHtml(purpose, code), true);
            mailSender.send(message);
        } catch (MessagingException | RuntimeException ex) {
            log.warn("邮箱验证码发送失败：email={}, purpose={}", email, purpose, ex);
            throw new BadRequestException("验证码邮件发送失败，请稍后再试。");
        }
    }

    private void storeCode(String key, String code) {
        try {
            redisTemplate.opsForValue().set(key, code, CODE_TTL);
        } catch (RuntimeException ex) {
            log.warn("Redis 暂不可用，验证码临时写入内存：{}", key, ex);
            fallbackCodes.put(key, new LocalCode(code, Instant.now().plus(CODE_TTL)));
        }
    }

    private String readCode(String key) {
        try {
            String redisCode = redisTemplate.opsForValue().get(key);
            if (redisCode != null) {
                return redisCode;
            }
        } catch (RuntimeException ex) {
            log.warn("Redis 暂不可用，尝试读取内存验证码：{}", key, ex);
        }

        LocalCode localCode = fallbackCodes.get(key);
        if (localCode == null) {
            return null;
        }
        if (localCode.expiresAt().isBefore(Instant.now())) {
            fallbackCodes.remove(key);
            return null;
        }

        return localCode.code();
    }

    private void deleteCode(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis 暂不可用，跳过验证码删除：{}", key, ex);
        }
        fallbackCodes.remove(key);
    }

    private String buildKey(String email, Purpose purpose) {
        return "nexacord:email-code:%s:%s".formatted(purpose.name(), email);
    }

    private String generateCode() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new BadRequestException("邮箱不能为空。");
        }
        return normalizedEmail;
    }

    private String buildEmailHtml(Purpose purpose, String code) {
        return """
                <div style="margin:0;padding:32px;background:#f2f3f5;font-family:'Noto Sans SC','PingFang SC','Microsoft YaHei',Arial,sans-serif;color:#232428;">
                  <div style="max-width:560px;margin:0 auto;border-radius:18px;overflow:hidden;background:#ffffff;border:1px solid #dfe1e5;box-shadow:0 18px 48px rgba(30,31,34,.12);">
                    <div style="padding:30px;background:linear-gradient(135deg,#eef2ff,#e7f7ee);border-bottom:1px solid #dfe1e5;">
                      <h1 style="margin:0;font-size:28px;line-height:1.2;color:#232428;">%s</h1>
                    </div>
                    <div style="padding:30px;">
                      <p style="margin:0 0 18px;color:#4e5058;font-size:15px;line-height:1.7;">请在 Nexacord 页面中输入下面的 6 位验证码。验证码 10 分钟内有效，请勿转发给他人。</p>
                      <div style="padding:18px 20px;border-radius:14px;background:#f6f7f9;border:1px solid #e3e5e8;color:#232428;font-size:32px;font-weight:900;letter-spacing:.24em;text-align:center;">%s</div>
                      <p style="margin:18px 0 0;color:#6d6f78;font-size:13px;line-height:1.6;">如果这不是你的操作，可以直接忽略这封邮件。</p>
                    </div>
                  </div>
                </div>
                """.formatted(purpose.label, code);
    }

    private record LocalCode(String code, Instant expiresAt) {
    }
}
