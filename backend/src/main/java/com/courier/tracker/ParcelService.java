package com.courier.tracker;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ParcelService {
  final ParcelRepository parcels;
  final EventRepository events;
  final UserRepository users;
  final NotificationRepository notifications;
  final PasswordEncoder encoder;
  final boolean emailEnabled;

  ParcelService(
      ParcelRepository p,
      EventRepository e,
      UserRepository u,
      NotificationRepository n,
      PasswordEncoder encoder,
      @Value("${app.email-enabled}") boolean emailEnabled) {
    parcels = p;
    events = e;
    users = u;
    notifications = n;
    this.encoder = encoder;
    this.emailEnabled = emailEnabled;
  }

  AppUser user(String email) {
    return users.findByEmail(email).orElseThrow(() -> new ApiException(401, "Please sign in."));
  }

  static LocalDateTime now() {
    return LocalDateTime.now(ZoneOffset.UTC);
  }

  Specification<Parcel> visible(AppUser u) {
    return (root, q, cb) ->
        u.role == Role.ADMIN ? cb.conjunction() : cb.equal(root.get("agent").get("id"), u.id);
  }

  Parcel accessible(Long id, AppUser u, boolean lock) {
    Parcel p =
        (lock ? parcels.lockById(id) : parcels.findById(id))
            .orElseThrow(() -> new ApiException(404, "Parcel not found."));
    if (u.role != Role.ADMIN && (p.agent == null || !p.agent.id.equals(u.id)))
      throw new ApiException(404, "Parcel not found.");
    return p;
  }

  void admin(AppUser u) {
    if (u.role != Role.ADMIN) throw new ApiException(403, "Administrator access required.");
  }

  Page<Dtos.ParcelView> list(AppUser u, String search, Status status, int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new ApiException(400, "Invalid pagination.");
    Specification<Parcel> spec = visible(u);
    if (status != null) spec = spec.and((r, q, c) -> c.equal(r.get("status"), status));
    if (search != null && !search.isBlank()) {
      String value =
          "%"
              + search.trim().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_")
              + "%";
      spec =
          spec.and(
              (r, q, c) ->
                  c.or(
                      c.like(c.lower(r.get("trackingId")), value, '!'),
                      c.like(c.lower(r.get("recipientName")), value, '!'),
                      c.like(c.lower(r.get("recipientCity")), value, '!')));
    }
    return parcels
        .findAll(
            spec,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
        .map(Dtos.ParcelView::of);
  }

  Dtos.ParcelDetail detail(Long id, AppUser u) {
    Parcel p = accessible(id, u, false);
    return new Dtos.ParcelDetail(
        Dtos.ParcelView.of(p),
        events.findByParcelIdOrderByOccurredAtAscIdAsc(id).stream()
            .map(Dtos.EventView::of)
            .toList());
  }

  Dtos.PublicTracking track(String id) {
    Parcel p =
        parcels
            .findByTrackingId(id.trim().toUpperCase())
            .orElseThrow(() -> new ApiException(404, "No parcel found. Check your tracking ID."));
    return new Dtos.PublicTracking(
        p.trackingId,
        p.senderCity,
        p.recipientCity,
        p.status,
        p.serviceType,
        p.expectedDelivery,
        events.findByParcelIdOrderByOccurredAtAscIdAsc(p.id).stream()
            .map(
                e ->
                    new Dtos.PublicEvent(
                        e.status, e.location, e.occurredAt.atOffset(ZoneOffset.UTC)))
            .toList());
  }

  Dtos.ParcelView create(Dtos.CreateParcel d, AppUser u) {
    admin(u);
    Parcel p = new Parcel();
    p.trackingId = "CT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    p.senderName = d.senderName().trim();
    p.senderEmail = d.senderEmail().trim();
    p.senderPhone = d.senderPhone().trim();
    p.senderAddress = d.senderAddress().trim();
    p.senderCity = d.senderCity().trim();
    p.recipientName = d.recipientName().trim();
    p.recipientEmail = d.recipientEmail().trim();
    p.recipientPhone = d.recipientPhone().trim();
    p.recipientAddress = d.recipientAddress().trim();
    p.recipientCity = d.recipientCity().trim();
    p.description = d.description().trim();
    p.weightKg = d.weightKg();
    p.serviceType = d.serviceType();
    p.expectedDelivery = d.expectedDelivery();
    p.status = Status.REGISTERED;
    p.createdAt = now();
    p.updatedAt = p.createdAt;
    p.createdBy = u;
    parcels.save(p);
    event(p, u, p.senderCity, "Parcel registered.");
    notify(p, u, "Parcel registered", p.trackingId + " has been registered.", p.recipientEmail);
    return Dtos.ParcelView.of(p);
  }

  Dtos.ParcelView assign(Long id, Long agentId, AppUser u) {
    admin(u);
    Parcel p = accessible(id, u, true);
    if (Workflow.terminal(p.status))
      throw new ApiException(409, "Completed parcels cannot be reassigned.");
    AppUser agent =
        users
            .findById(agentId)
            .filter(a -> a.active && a.role == Role.AGENT)
            .orElseThrow(() -> new ApiException(400, "Choose an active delivery agent."));
    if (p.agent != null && p.agent.id.equals(agent.id))
      throw new ApiException(409, "This agent is already assigned.");
    p.agent = agent;
    p.updatedAt = now();
    event(p, u, p.senderCity, "Assigned to " + agent.name + ".");
    notify(
        p,
        agent,
        "New delivery assignment",
        p.trackingId + " • " + p.senderCity + " to " + p.recipientCity,
        null);
    return Dtos.ParcelView.of(p);
  }

  Dtos.ParcelView update(Long id, Dtos.StatusChange d, AppUser u) {
    Parcel p = accessible(id, u, true);
    Workflow.validate(p.status, d.status());
    if (u.role == Role.AGENT && d.status() == Status.CANCELLED)
      throw new ApiException(403, "Only an administrator can cancel a parcel.");
    if (p.agent == null && d.status() != Status.CANCELLED)
      throw new ApiException(409, "Assign a delivery agent before updating delivery status.");
    p.status = d.status();
    p.updatedAt = now();
    event(p, u, d.location().trim(), d.note().trim());
    notify(
        p,
        p.createdBy,
        "Delivery update",
        p.trackingId + " is now " + p.status.name().replace('_', ' ') + ".",
        p.recipientEmail);
    return Dtos.ParcelView.of(p);
  }

  void event(Parcel p, AppUser u, String location, String note) {
    ParcelEvent e = new ParcelEvent();
    e.parcel = p;
    e.status = p.status;
    e.location = location;
    e.note = note;
    e.actorName = u.name;
    e.occurredAt = now();
    events.save(e);
  }

  void notify(Parcel p, AppUser u, String title, String message, String email) {
    Notification n = new Notification();
    n.parcel = p;
    n.user = u;
    n.title = title;
    n.message = message;
    n.recipientEmail = email;
    n.emailStatus = email == null ? "IN_APP_ONLY" : emailEnabled ? "PENDING" : "NOT_CONFIGURED";
    n.createdAt = now();
    notifications.save(n);
  }

  List<Dtos.UserView> agents(AppUser u) {
    admin(u);
    return users.findByRoleAndActiveTrueOrderByName(Role.AGENT).stream()
        .map(Dtos.UserView::of)
        .toList();
  }

  Dtos.UserView createAgent(Dtos.CreateAgent d, AppUser u) {
    admin(u);
    if (d.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ApiException(400, "Password must be at most 72 UTF-8 bytes.");
    String email = d.email().trim().toLowerCase();
    if (users.findByEmail(email).isPresent())
      throw new ApiException(409, "Email already registered.");
    AppUser a = new AppUser();
    a.name = d.name().trim();
    a.email = email;
    a.passwordHash = encoder.encode(d.password());
    a.role = Role.AGENT;
    return Dtos.UserView.of(users.save(a));
  }

  List<Dtos.NotificationView> inbox(AppUser u) {
    return notifications.findTop100ByUserIdOrderByCreatedAtDescIdDesc(u.id).stream()
        .map(Dtos.NotificationView::of)
        .toList();
  }

  void read(Long id, AppUser u) {
    Notification n =
        notifications
            .findById(id)
            .filter(x -> x.user.id.equals(u.id))
            .orElseThrow(() -> new ApiException(404, "Notification not found."));
    n.read = true;
  }

  Dtos.Dashboard dashboard(AppUser u) {
    Specification<Parcel> scope = visible(u);
    Map<Status, Long> counts = new LinkedHashMap<>();
    for (Status s : Status.values())
      counts.put(s, parcels.count(scope.and((r, q, c) -> c.equal(r.get("status"), s))));
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    long delivered = counts.get(Status.DELIVERED);
    long active = total - delivered - counts.get(Status.RETURNED) - counts.get(Status.CANCELLED);
    Specification<Parcel> nonterminal =
        (r, q, c) -> c.not(r.get("status").in(Status.DELIVERED, Status.RETURNED, Status.CANCELLED));
    long unassigned =
        parcels.count(scope.and(nonterminal).and((r, q, c) -> c.isNull(r.get("agent"))));
    long overdue =
        parcels.count(
            scope
                .and(nonterminal)
                .and(
                    (r, q, c) ->
                        c.lessThan(r.get("expectedDelivery"), LocalDate.now(ZoneOffset.UTC))));
    return new Dtos.Dashboard(
        total,
        active,
        delivered,
        counts.get(Status.DELIVERY_FAILED),
        unassigned,
        overdue,
        counts,
        parcels
            .findAll(
                scope,
                PageRequest.of(0, 5, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
            .map(Dtos.ParcelView::of)
            .getContent());
  }
}
