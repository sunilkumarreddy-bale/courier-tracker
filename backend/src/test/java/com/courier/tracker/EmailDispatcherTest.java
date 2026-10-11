package com.courier.tracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;

class EmailDispatcherTest {
  @Test
  void sendsPendingEmailAndRecordsSuccess() {
    var repo = mock(NotificationRepository.class);
    var mail = mock(JavaMailSender.class);
    var n = new Notification();
    n.recipientEmail = "recipient@example.com";
    n.title = "Delivery update";
    n.message = "Delivered";
    n.emailStatus = "PENDING";
    when(repo.findTop20ByEmailStatusInAndAttemptsLessThanOrderByCreatedAtAsc(anyList(), eq(5)))
        .thenReturn(List.of(n));
    new EmailDispatcher(repo, mail, true, "courier@example.com").dispatch();
    verify(mail).send(any(SimpleMailMessage.class));
    assertEquals("SENT", n.emailStatus);
    assertEquals(1, n.attempts);
  }

  @Test
  void failedSmtpIsRecordedForRetryWithoutLosingInAppNotification() {
    var repo = mock(NotificationRepository.class);
    var mail = mock(JavaMailSender.class);
    var n = new Notification();
    n.recipientEmail = "recipient@example.com";
    n.emailStatus = "PENDING";
    when(repo.findTop20ByEmailStatusInAndAttemptsLessThanOrderByCreatedAtAsc(anyList(), eq(5)))
        .thenReturn(List.of(n));
    doThrow(new MailSendException("unavailable")).when(mail).send(any(SimpleMailMessage.class));
    new EmailDispatcher(repo, mail, true, "courier@example.com").dispatch();
    assertEquals("FAILED", n.emailStatus);
    assertEquals(1, n.attempts);
  }

  @Test
  void disabledEmailDoesNotAttemptSending() {
    var repo = mock(NotificationRepository.class);
    var mail = mock(JavaMailSender.class);
    new EmailDispatcher(repo, mail, false, "courier@example.com").dispatch();
    verifyNoInteractions(repo, mail);
  }
}
