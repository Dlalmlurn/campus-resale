// 文件功能：提供密码找回令牌投递边界，支持配置 SMTP 邮件发送。
package com.campusresale.identity.application;

import com.campusresale.identity.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 密码找回投递服务。配置 spring.mail.host 后会发送邮件，未配置时保留安全日志提示。
 */
@Service
public class PasswordResetDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetDeliveryService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String frontendBaseUrl;
    private final String from;

    public PasswordResetDeliveryService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${campus-resale.auth.password-reset.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            @Value("${campus-resale.auth.password-reset.from:}") String from
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.frontendBaseUrl = frontendBaseUrl;
        this.from = from;
    }

    /**
     * 投递密码找回令牌；SMTP 未配置时不把 token 暴露给匿名响应，只记录待配置提示。
     */
    public void deliver(UserAccount userAccount, String email, String rawToken) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            // 不在匿名 API 响应中返回 token，避免公网环境下通过邮箱枚举直接接管账号。
            LOGGER.info("Password reset token generated for user={} email={}; spring.mail.host is not configured.",
                    userAccount.username(), email);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from.trim());
        }
        message.setTo(email);
        message.setSubject("校园二手交易平台密码重置");
        message.setText("""
                你好，%s：

                你正在重置校园二手交易平台账号密码。请在 30 分钟内使用以下链接完成重置：
                %s

                如果不是你本人操作，请忽略这封邮件。
                """.formatted(userAccount.nickname(), resetLink(rawToken)));
        mailSender.send(message);
    }

    private String resetLink(String rawToken) {
        String baseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:3000"
                : frontendBaseUrl.trim();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "resetToken=" + rawToken;
    }
}
