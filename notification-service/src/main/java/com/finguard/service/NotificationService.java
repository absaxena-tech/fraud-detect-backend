package com.finguard.service;

import com.finguard.dto.FraudAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${notification.alert-email}")
    private String alertEmail;

    @Value("${notification.enabled:true}")
    private boolean notificationsEnabled;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.of("UTC"));

    public void handleFraudAlert(FraudAlertEvent event) {
        // Always log
        logAlert(event);

        if (!notificationsEnabled) {
            log.info("Notifications disabled — skipping outbound channels for tx {}", event.getTransactionId());
            return;
        }

        // Email
        sendEmail(event);

        // Extend here: sendSlack(event), sendSms(event), sendPagerDuty(event)
    }

    private void logAlert(FraudAlertEvent e) {
        log.warn("""
                ╔══════════════════════════════════════════════════
                ║  FINGUARD FRAUD ALERT
                ║  Status      : {}
                ║  Transaction : {}
                ║  Account     : {}
                ║  Amount      : {} USD
                ║  Fraud Score : {:.2f}
                ║  Rule        : {}
                ║  Detected    : {}
                ╚══════════════════════════════════════════════════
                """,
                e.getStatus(), e.getTransactionId(), e.getAccountId(),
                e.getAmount(), e.getFraudScore(), e.getRuleTriggered(),
                e.getDetectedAt() != null ? FORMATTER.format(e.getDetectedAt()) : "unknown");
    }

    private void sendEmail(FraudAlertEvent e) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(alertEmail);
            msg.setSubject(String.format("[FinGuard %s] Fraud Alert | Account: %s | Score: %.2f",
                    e.getStatus(), e.getAccountId(), e.getFraudScore()));
            msg.setText(buildEmailBody(e));
            mailSender.send(msg);
            log.info("Email alert dispatched for tx {}", e.getTransactionId());
        } catch (Exception ex) {
            log.warn("Failed to send email alert for tx {}: {}", e.getTransactionId(), ex.getMessage());
        }
    }

    private String buildEmailBody(FraudAlertEvent e) {
        return """
                FinGuard — Automated Fraud Detection Alert
                ==========================================

                Status        : %s
                Transaction ID: %s
                Account ID    : %s
                Amount        : %s USD
                Fraud Score   : %.4f  (threshold: 0.7 = FLAGGED, 0.9 = BLOCKED)
                Rule Triggered: %s
                Detected At   : %s

                --- AI Explanation ---
                %s

                ---
                This is an automated alert from the FinGuard Fraud Detection System.
                Please log into the dashboard to review and action this alert.
                """.formatted(
                e.getStatus(),
                e.getTransactionId(),
                e.getAccountId(),
                e.getAmount(),
                e.getFraudScore(),
                e.getRuleTriggered(),
                e.getDetectedAt() != null ? FORMATTER.format(e.getDetectedAt()) : "unknown",
                e.getExplanation() != null ? e.getExplanation() : "No explanation available."
        );
    }
}
