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

    @Value("${notification.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${notification.fallback-email:security@fingaurd.com}")
    private String fallbackEmail;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.of("UTC"));

    public void handleFraudAlert(FraudAlertEvent event) {
        logAlert(event);

        if (!notificationsEnabled) {
            log.info("Notifications disabled — skipping email for tx {}", event.getTransactionId());
            return;
        }

        sendEmail(event);
    }

    private void sendEmail(FraudAlertEvent e) {
        // FIX 3: Send to the user's own email, not a fixed admin address.
        // Fall back to fallbackEmail if userEmail is missing (should not happen in practice).
        String recipient = (e.getUserEmail() != null && !e.getUserEmail().isBlank())
                ? e.getUserEmail()
                : fallbackEmail;

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(recipient);
            msg.setSubject(buildSubject(e));
            msg.setText(buildBody(e));
            mailSender.send(msg);
            log.info("Fraud alert email sent to {} for tx {}", recipient, e.getTransactionId());
        } catch (Exception ex) {
            log.warn("Failed to send email to {} for tx {}: {}", recipient, e.getTransactionId(), ex.getMessage());
        }
    }

    private String buildSubject(FraudAlertEvent e) {
        return String.format("[FinGuard Alert] %s transaction detected — $%s",
                e.getStatus(), e.getAmount());
    }

    private String buildBody(FraudAlertEvent e) {
        return """
                FinGuard Security Alert
                =======================

                Dear FinGuard User,

                We have detected a suspicious transaction on your account.

                Status        : %s
                Transaction ID: %s
                Amount        : $%s
                Fraud Score   : %.2f / 1.00
                Rule Triggered: %s
                Detected At   : %s

                Why we flagged this:
                %s

                If this was you, no action is needed.
                If you did not make this transaction, please contact support immediately
                or log in to your FinGuard dashboard to dispute it.

                — The FinGuard Security Team
                """.formatted(
                e.getStatus(),
                e.getTransactionId(),
                e.getAmount(),
                e.getFraudScore(),
                e.getRuleTriggered(),
                e.getDetectedAt() != null ? FORMATTER.format(e.getDetectedAt()) : "unknown",
                e.getExplanation() != null ? e.getExplanation() : "Suspicious activity pattern detected."
        );
    }

    private void logAlert(FraudAlertEvent e) {
        log.warn("FRAUD ALERT | tx={} account={} email={} amount={} score={} status={} rule={}",
                e.getTransactionId(), e.getAccountId(), e.getUserEmail(),
                e.getAmount(), e.getFraudScore(), e.getStatus(), e.getRuleTriggered());
    }
}
