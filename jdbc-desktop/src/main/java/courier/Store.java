package courier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** All database access uses JDBC. Each operation owns and closes its connection. */
public final class Store {
  public record User(long id, String name, String role) {}

  public record Registration(
      String sender,
      String senderContact,
      String senderAddress,
      String recipient,
      String recipientContact,
      String recipientAddress,
      BigDecimal weight,
      String service,
      LocalDate expected) {}

  private final String url, username, password;
  private static final Map<String, List<String>> NEXT =
      Map.of(
          "REGISTERED",
          List.of("PICKED_UP", "CANCELLED"),
          "PICKED_UP",
          List.of("IN_TRANSIT", "RETURNED"),
          "IN_TRANSIT",
          List.of("OUT_FOR_DELIVERY", "RETURNED"),
          "OUT_FOR_DELIVERY",
          List.of("DELIVERED", "DELIVERY_FAILED"),
          "DELIVERY_FAILED",
          List.of("OUT_FOR_DELIVERY", "RETURNED"));

  public static List<String> next(String status) {
    return NEXT.getOrDefault(status, List.of());
  }

  public Store(String url, String username, String password) {
    this.url = url;
    this.username = username;
    this.password = password;
  }

  Connection connect() throws SQLException {
    return DriverManager.getConnection(url, username, password);
  }

  interface Work<T> {
    T run(Connection c) throws Exception;
  }

  private <T> T transaction(Work<T> work) throws Exception {
    try (Connection c = connect()) {
      c.setAutoCommit(false);
      try {
        T result = work.run(c);
        c.commit();
        return result;
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  static List<Map<String, Object>> query(Connection c, String sql, Object... args)
      throws SQLException {
    try (PreparedStatement s = c.prepareStatement(sql)) {
      bind(s, args);
      try (ResultSet r = s.executeQuery()) {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (r.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
            row.put(r.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), r.getObject(i));
          rows.add(row);
        }
        return rows;
      }
    }
  }

  static void bind(PreparedStatement s, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
  }

  static int execute(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement s = c.prepareStatement(sql)) {
      bind(s, args);
      return s.executeUpdate();
    }
  }

  static long insert(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      bind(s, args);
      s.executeUpdate();
      try (ResultSet r = s.getGeneratedKeys()) {
        if (!r.next()) throw new SQLException("Missing generated key");
        return r.getLong(1);
      }
    }
  }

  public void initialize(String adminEmail, char[] adminPassword) throws Exception {
    try (Connection c = connect()) {
      String schema;
      try (var in = Store.class.getResourceAsStream("/schema.sql")) {
        schema = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
      }
      for (String sql : schema.split(";")) if (!sql.isBlank()) execute(c, sql);
      if (query(c, "SELECT id FROM users LIMIT 1").isEmpty()) {
        if (adminEmail == null || !adminEmail.contains("@") || adminPassword.length < 12)
          throw new IllegalArgumentException(
              "Empty database: set ADMIN_EMAIL and ADMIN_PASSWORD (12+ characters).");
        execute(
            c,
            "INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,?)",
            "Administrator",
            adminEmail.toLowerCase(Locale.ROOT),
            Passwords.hash(adminPassword),
            "ADMIN");
      }
    }
  }

  public User login(String email, char[] password) throws Exception {
    try (Connection c = connect()) {
      var rows =
          query(
              c,
              "SELECT * FROM users WHERE email=? AND active=TRUE",
              email.trim().toLowerCase(Locale.ROOT));
      if (rows.isEmpty()
          || !Passwords.verify(password, rows.get(0).get("password_hash").toString()))
        throw new IllegalArgumentException("Invalid email or password");
      var r = rows.get(0);
      return new User(
          ((Number) r.get("id")).longValue(), r.get("name").toString(), r.get("role").toString());
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  private void authorize(Connection c, User user, boolean admin) throws Exception {
    if (user == null) throw new IllegalArgumentException("Sign in first");
    var rows = query(c, "SELECT role FROM users WHERE id=? AND active=TRUE", user.id());
    if (rows.isEmpty()
        || !rows.get(0).get("role").equals(user.role())
        || (admin && !user.role().equals("ADMIN")))
      throw new IllegalArgumentException("Permission denied");
  }

  static String required(String value, int max) {
    if (value == null || value.isBlank() || value.trim().length() > max)
      throw new IllegalArgumentException(
          "Required field is empty or too long (maximum " + max + ")");
    return value.trim();
  }

  public void createAgent(User user, String name, String email, char[] pass) throws Exception {
    try {
      transaction(
          c -> {
            authorize(c, user, true);
            if (pass.length < 12)
              throw new IllegalArgumentException("Password needs 12+ characters");
            if (!email.contains("@")) throw new IllegalArgumentException("Invalid email");
            execute(
                c,
                "INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,?)",
                required(name, 100),
                required(email, 254).toLowerCase(Locale.ROOT),
                Passwords.hash(pass),
                "AGENT");
            return null;
          });
    } finally {
      Arrays.fill(pass, '\0');
    }
  }

  public String register(User user, Registration r) throws Exception {
    return transaction(
        c -> {
          authorize(c, user, true);
          if (r.weight() == null
              || r.weight().signum() <= 0
              || r.weight().compareTo(new BigDecimal("99999999.99")) > 0
              || r.weight().scale() > 2)
            throw new IllegalArgumentException(
                "Weight must be positive, with at most two decimals");
          if (r.expected() == null
              || r.expected().isBefore(LocalDate.now(java.time.ZoneOffset.UTC)))
            throw new IllegalArgumentException("Expected date cannot be in the past");
          if (!List.of("STANDARD", "EXPRESS").contains(r.service()))
            throw new IllegalArgumentException("Invalid service");
          String tracking = "CT-" + UUID.randomUUID();
          long id =
              insert(
                  c,
                  "INSERT INTO"
                      + " shipments(tracking,sender,sender_contact,sender_address,recipient,recipient_contact,recipient_address,weight,service,expected,status)"
                      + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                  tracking,
                  required(r.sender(), 100),
                  required(r.senderContact(), 254),
                  required(r.senderAddress(), 300),
                  required(r.recipient(), 100),
                  required(r.recipientContact(), 254),
                  required(r.recipientAddress(), 300),
                  r.weight(),
                  r.service(),
                  r.expected(),
                  "REGISTERED");
          event(c, id, "REGISTERED", "Origin", "Parcel registered", user);
          return tracking;
        });
  }

  private void event(Connection c, long id, String status, String location, String note, User user)
      throws Exception {
    execute(
        c,
        "INSERT INTO events(parcel_id,status,location,note,actor_id) VALUES(?,?,?,?,?)",
        id,
        status,
        required(location, 100),
        note == null ? "" : note,
        user.id());
  }

  public List<Map<String, Object>> agents(User user) throws Exception {
    try (Connection c = connect()) {
      authorize(c, user, true);
      return query(
          c, "SELECT id,name,email FROM users WHERE role='AGENT' AND active=TRUE ORDER BY name");
    }
  }

  public List<Map<String, Object>> parcels(User user, String search, String status, int page)
      throws Exception {
    try (Connection c = connect()) {
      authorize(c, user, false);
      return query(
          c,
          "SELECT id,tracking,recipient,service,weight,status,expected,agent_id,version FROM"
              + " shipments WHERE (?='ADMIN' OR agent_id=?) AND (tracking LIKE ? OR recipient LIKE"
              + " ?) AND (?='' OR status=?) ORDER BY id DESC LIMIT 25 OFFSET ?",
          user.role(),
          user.id(),
          "%" + search + "%",
          "%" + search + "%",
          status,
          status,
          Math.max(0, page) * 25);
    }
  }

  public void change(User user, long id, Long agent, String to, String location, String note)
      throws Exception {
    transaction(
        c -> {
          authorize(c, user, agent != null);
          var rows = query(c, "SELECT * FROM shipments WHERE id=? FOR UPDATE", id);
          if (rows.isEmpty()) throw new IllegalArgumentException("Parcel not found");
          var r = rows.get(0);
          String status = r.get("status").toString();
          if (next(status).isEmpty()) throw new IllegalArgumentException("Parcel is complete");
          Object assigned = r.get("agent_id");
          if (!user.role().equals("ADMIN")
              && (assigned == null || ((Number) assigned).longValue() != user.id()))
            throw new IllegalArgumentException("Parcel belongs to another agent");
          if (agent != null) {
            if (query(c, "SELECT id FROM users WHERE id=? AND role='AGENT' AND active=TRUE", agent)
                .isEmpty()) throw new IllegalArgumentException("Active agent required");
            execute(c, "UPDATE shipments SET agent_id=?,version=version+1 WHERE id=?", agent, id);
            event(c, id, status, "Dispatch", "Assigned to agent #" + agent, user);
            execute(
                c,
                "INSERT INTO alerts(user_id,parcel_id,message) VALUES(?,?,?)",
                agent,
                id,
                "Assigned: " + r.get("tracking"));
          } else {
            if (!next(status).contains(to))
              throw new IllegalArgumentException("Invalid status transition");
            if (to.equals("CANCELLED")) {
              if (!user.role().equals("ADMIN"))
                throw new IllegalArgumentException("Only administrators can cancel");
            } else if (assigned == null)
              throw new IllegalArgumentException("Assign an agent first");
            if (note != null && note.length() > 500)
              throw new IllegalArgumentException("Note maximum is 500 characters");
            int changed =
                execute(
                    c,
                    "UPDATE shipments SET status=?,version=version+1 WHERE id=? AND version=?",
                    to,
                    id,
                    r.get("version"));
            if (changed != 1)
              throw new IllegalArgumentException("Parcel changed; refresh and retry");
            event(c, id, to, location, note, user);
            if (assigned != null)
              execute(
                  c,
                  "INSERT INTO alerts(user_id,parcel_id,message) VALUES(?,?,?)",
                  assigned,
                  id,
                  r.get("tracking") + ": " + to);
          }
          return null;
        });
  }

  public List<Map<String, Object>> track(String tracking) throws Exception {
    try (Connection c = connect()) {
      return query(
          c,
          "SELECT s.tracking,e.status,e.location,e.occurred_at FROM shipments s JOIN events e ON"
              + " e.parcel_id=s.id WHERE s.tracking=? ORDER BY e.occurred_at,e.id",
          tracking.trim());
    }
  }

  public List<Map<String, Object>> history(User user, long id) throws Exception {
    try (Connection c = connect()) {
      authorize(c, user, false);
      if (query(
              c,
              "SELECT id FROM shipments WHERE id=? AND (?='ADMIN' OR agent_id=?)",
              id,
              user.role(),
              user.id())
          .isEmpty()) throw new IllegalArgumentException("Permission denied");
      return query(
          c,
          "SELECT status,location,note,actor_id,occurred_at FROM events WHERE parcel_id=? ORDER BY"
              + " occurred_at,id",
          id);
    }
  }

  public List<Map<String, Object>> alerts(User user) throws Exception {
    try (Connection c = connect()) {
      authorize(c, user, false);
      return query(
          c,
          "SELECT id,parcel_id,message,is_read,created_at FROM alerts WHERE user_id=? ORDER BY id"
              + " DESC LIMIT 100",
          user.id());
    }
  }

  public void read(User user, long id) throws Exception {
    try (Connection c = connect()) {
      authorize(c, user, false);
      execute(c, "UPDATE alerts SET is_read=TRUE WHERE id=? AND user_id=?", id, user.id());
    }
  }

  public List<Map<String, Object>> dashboard(User user) throws Exception {
    try (Connection c = connect()) {
      authorize(c, user, false);
      return query(
          c,
          "SELECT COUNT(*) AS total,COALESCE(SUM(CASE WHEN status='DELIVERED' THEN 1 ELSE 0 END),0)"
              + " AS delivered,COALESCE(SUM(CASE WHEN status='DELIVERY_FAILED' THEN 1 ELSE 0"
              + " END),0) AS failed,COALESCE(SUM(CASE WHEN status NOT IN"
              + " ('DELIVERED','CANCELLED','RETURNED') THEN 1 ELSE 0 END),0) AS"
              + " active,COALESCE(SUM(CASE WHEN agent_id IS NULL AND status NOT IN"
              + " ('DELIVERED','CANCELLED','RETURNED') THEN 1 ELSE 0 END),0) AS"
              + " unassigned,COALESCE(SUM(CASE WHEN expected<CURRENT_DATE AND status NOT IN"
              + " ('DELIVERED','CANCELLED','RETURNED') THEN 1 ELSE 0 END),0) AS overdue FROM"
              + " shipments WHERE (?='ADMIN' OR agent_id=?)",
          user.role(),
          user.id());
    }
  }
}
