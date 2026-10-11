package com.courier.tracker;

import java.util.List;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class EmailDispatcher {
  private final NotificationRepository notifications;
  private final JavaMailSender mail;
  private final boolean enabled;
  private final String from;
  private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

  EmailDispatcher(
      NotificationRepository notifications,
      JavaMailSender mail,
      @Value("${app.email-enabled}") boolean enabled,
      @Value("${app.mail-from}") String from) {
    this.notifications = notifications;
    this.mail = mail;
    this.enabled = enabled;
    this.from = from;
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 15000)
  @Transactional
  public void dispatch() {
    if (!enabled) return;
    for (Notification n :
        notifications.findTop20ByEmailStatusInAndAttemptsLessThanOrderByCreatedAtAsc(
            List.of("PENDING", "FAILED"), 5)) {
      n.attempts++;
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(n.recipientEmail);
      message.setSubject("Courier Tracker — " + n.title);
      message.setText(n.message);
      try {
        mail.send(message);
        n.emailStatus = "SENT";
      } catch (MailException e) {
        n.emailStatus = "FAILED";
        log.warn("Email attempt failed for notification {}", n.id);
      }
    }
  }
}
