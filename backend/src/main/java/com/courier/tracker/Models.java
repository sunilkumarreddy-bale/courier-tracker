package com.courier.tracker;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

enum Role {
  ADMIN,
  AGENT
}

enum Status {
  REGISTERED,
  PICKED_UP,
  IN_TRANSIT,
  OUT_FOR_DELIVERY,
  DELIVERED,
  DELIVERY_FAILED,
  RETURNED,
  CANCELLED
}

enum ServiceType {
  STANDARD,
  EXPRESS
}

@Entity
@Table(name = "app_users")
class AppUser {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 100)
  String name;

  @Column(nullable = false, unique = true, length = 254)
  String email;

  @Column(nullable = false, length = 100)
  String passwordHash;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  Role role;

  @Column(nullable = false)
  boolean active = true;
}

@Entity
@Table(name = "parcels")
class Parcel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true, length = 40)
  String trackingId;

  @Column(nullable = false, length = 100)
  String senderName;

  @Column(nullable = false, length = 254)
  String senderEmail;

  @Column(nullable = false, length = 20)
  String senderPhone;

  @Column(nullable = false, length = 300)
  String senderAddress;

  @Column(nullable = false, length = 100)
  String senderCity;

  @Column(nullable = false, length = 100)
  String recipientName;

  @Column(nullable = false, length = 254)
  String recipientEmail;

  @Column(nullable = false, length = 20)
  String recipientPhone;

  @Column(nullable = false, length = 300)
  String recipientAddress;

  @Column(nullable = false, length = 100)
  String recipientCity;

  @Column(nullable = false, length = 300)
  String description;

  @Column(nullable = false, precision = 10, scale = 2)
  BigDecimal weightKg;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  ServiceType serviceType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 30)
  Status status;

  @Column(nullable = false)
  LocalDate expectedDelivery;

  @Column(nullable = false)
  LocalDateTime createdAt;

  @Column(nullable = false)
  LocalDateTime updatedAt;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "created_by_id")
  AppUser createdBy;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "agent_id")
  AppUser agent;

  @Version Long version;
}

@Entity
@Table(name = "parcel_events")
class ParcelEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "parcel_id")
  Parcel parcel;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 30)
  Status status;

  @Column(nullable = false, length = 100)
  String location;

  @Column(nullable = false, length = 500)
  String note;

  @Column(nullable = false, length = 100)
  String actorName;

  @Column(nullable = false)
  LocalDateTime occurredAt;
}

@Entity
@Table(name = "notifications")
class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "user_id")
  AppUser user;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "parcel_id")
  Parcel parcel;

  @Column(nullable = false, length = 200)
  String title;

  @Column(nullable = false, length = 700)
  String message;

  @Column(length = 254)
  String recipientEmail;

  @Column(nullable = false, length = 30)
  String emailStatus;

  @Column(nullable = false)
  int attempts;

  @Column(name = "is_read", nullable = false)
  boolean read;

  @Column(nullable = false)
  LocalDateTime createdAt;
}
