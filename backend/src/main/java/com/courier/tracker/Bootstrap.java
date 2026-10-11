package com.courier.tracker;

import java.math.BigDecimal;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class Bootstrap implements CommandLineRunner {
  private final UserRepository users;
  private final ParcelRepository parcels;
  private final ParcelService service;
  private final PasswordEncoder encoder;

  @Value("${app.demo-enabled}")
  boolean demo;

  @Value("${app.admin-email}")
  String email;

  @Value("${app.admin-password}")
  String password;

  Bootstrap(UserRepository u, ParcelRepository p, ParcelService s, PasswordEncoder e) {
    users = u;
    parcels = p;
    service = s;
    encoder = e;
  }

  AppUser createUser(String name, String email, String password, Role role) {
    return users
        .findByEmail(email)
        .orElseGet(
            () -> {
              AppUser u = new AppUser();
              u.name = name;
              u.email = email;
              u.passwordHash = encoder.encode(password);
              u.role = role;
              return users.save(u);
            });
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (!demo) {
      if (users.count() == 0) {
        if (email.isBlank() || password.length() < 12)
          throw new IllegalStateException(
              "Set ADMIN_EMAIL and ADMIN_PASSWORD (at least 12 characters), or DEMO_ENABLED=true"
                  + " for a local demo.");
        createUser("Administrator", email.trim().toLowerCase(), password, Role.ADMIN);
      }
      return;
    }
    AppUser admin = createUser("Alex Morgan", "admin@courier.local", "DemoAdmin!2026", Role.ADMIN);
    AppUser agent = createUser("Sam Rivera", "agent@courier.local", "DemoAgent!2026", Role.AGENT);
    createUser("Priya Shah", "priya@courier.local", "DemoAgent!2026", Role.AGENT);
    if (parcels.count() > 0) return;
    String[] names = {
      "Aarav Mehta", "Nisha Rao", "Daniel Kim", "Sofia Patel", "Rohan Desai", "Maya Chen"
    };
    String[] cities = {"Bengaluru", "Mumbai", "Hyderabad", "Chennai", "Pune", "Delhi"};
    for (int i = 0; i < 6; i++) {
      var d =
          new Dtos.CreateParcel(
              "Demo Store",
              "sender@example.com",
              "+91 9000000000",
              "12 Market Road",
              "Bengaluru",
              names[i],
              "recipient@example.com",
              "+91 9111111111",
              "24 Lake Street",
              cities[i],
              i % 2 == 0 ? "Electronics accessories" : "Books and stationery",
              new BigDecimal("1.50"),
              i % 2 == 0 ? ServiceType.EXPRESS : ServiceType.STANDARD,
              LocalDate.now(ZoneOffset.UTC).plusDays(i % 3 + 1));
      var view = service.create(d, admin);
      Parcel p = parcels.findById(view.id()).orElseThrow();
      p.trackingId = "CT-DEMO-100" + (i + 1);
      if (i > 0) {
        service.assign(p.id, agent.id, admin);
        service.update(
            p.id,
            new Dtos.StatusChange(Status.PICKED_UP, "Bengaluru", "Collected from sender."),
            agent);
        if (i > 1)
          service.update(
              p.id,
              new Dtos.StatusChange(Status.IN_TRANSIT, "Regional hub", "Arrived at regional hub."),
              agent);
        if (i > 2)
          service.update(
              p.id,
              new Dtos.StatusChange(Status.OUT_FOR_DELIVERY, cities[i], "Courier is on the way."),
              agent);
        if (i == 4)
          service.update(
              p.id,
              new Dtos.StatusChange(Status.DELIVERED, cities[i], "Delivered to recipient."),
              agent);
        if (i == 5)
          service.update(
              p.id,
              new Dtos.StatusChange(
                  Status.DELIVERY_FAILED, cities[i], "Recipient unavailable. Retry scheduled."),
              agent);
      }
    }
  }
}
