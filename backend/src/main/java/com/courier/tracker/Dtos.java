package com.courier.tracker;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class Dtos {
  record Login(@NotBlank @Email String email, @NotBlank String password) {}

  record UserView(Long id, String name, String email, Role role) {
    static UserView of(AppUser u) {
      return new UserView(u.id, u.name, u.email, u.role);
    }
  }

  record CreateAgent(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  record CreateParcel(
      @NotBlank @Size(max = 100) String senderName,
      @NotBlank @Email @Size(max = 254) String senderEmail,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}") String senderPhone,
      @NotBlank @Size(max = 300) String senderAddress,
      @NotBlank @Size(max = 100) String senderCity,
      @NotBlank @Size(max = 100) String recipientName,
      @NotBlank @Email @Size(max = 254) String recipientEmail,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}") String recipientPhone,
      @NotBlank @Size(max = 300) String recipientAddress,
      @NotBlank @Size(max = 100) String recipientCity,
      @NotBlank @Size(max = 300) String description,
      @NotNull @DecimalMin("0.01") @DecimalMax("1000.00") @Digits(integer = 4, fraction = 2)
          BigDecimal weightKg,
      @NotNull ServiceType serviceType,
      @NotNull @FutureOrPresent LocalDate expectedDelivery) {}

  record Assignment(@NotNull Long agentId) {}

  record StatusChange(
      @NotNull Status status,
      @NotBlank @Size(max = 100) String location,
      @NotBlank @Size(max = 500) String note) {}

  record EventView(
      Long id,
      Status status,
      String location,
      String note,
      String actorName,
      OffsetDateTime occurredAt) {
    static EventView of(ParcelEvent e) {
      return new EventView(
          e.id, e.status, e.location, e.note, e.actorName, e.occurredAt.atOffset(ZoneOffset.UTC));
    }
  }

  record PublicEvent(Status status, String location, OffsetDateTime occurredAt) {}

  record ParcelView(
      Long id,
      String trackingId,
      String senderName,
      String senderEmail,
      String senderPhone,
      String senderAddress,
      String senderCity,
      String recipientName,
      String recipientEmail,
      String recipientPhone,
      String recipientAddress,
      String recipientCity,
      String description,
      BigDecimal weightKg,
      ServiceType serviceType,
      Status status,
      LocalDate expectedDelivery,
      OffsetDateTime createdAt,
      UserView agent,
      List<Status> nextStatuses) {
    static ParcelView of(Parcel p) {
      return new ParcelView(
          p.id,
          p.trackingId,
          p.senderName,
          p.senderEmail,
          p.senderPhone,
          p.senderAddress,
          p.senderCity,
          p.recipientName,
          p.recipientEmail,
          p.recipientPhone,
          p.recipientAddress,
          p.recipientCity,
          p.description,
          p.weightKg,
          p.serviceType,
          p.status,
          p.expectedDelivery,
          p.createdAt.atOffset(ZoneOffset.UTC),
          p.agent == null ? null : UserView.of(p.agent),
          Workflow.next(p.status));
    }
  }

  record ParcelDetail(ParcelView parcel, List<EventView> history) {}

  record PublicTracking(
      String trackingId,
      String origin,
      String destination,
      Status status,
      ServiceType serviceType,
      LocalDate expectedDelivery,
      List<PublicEvent> history) {}

  record NotificationView(
      Long id,
      String trackingId,
      String title,
      String message,
      String emailStatus,
      boolean read,
      OffsetDateTime createdAt) {
    static NotificationView of(Notification n) {
      return new NotificationView(
          n.id,
          n.parcel.trackingId,
          n.title,
          n.message,
          n.emailStatus,
          n.read,
          n.createdAt.atOffset(ZoneOffset.UTC));
    }
  }

  record Dashboard(
      long total,
      long active,
      long delivered,
      long exceptions,
      long unassigned,
      long overdue,
      Map<Status, Long> byStatus,
      List<ParcelView> recent) {}
}
