package courier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Executable integration suite: H2 compatibility or a disposable MySQL database. */
public class StoreSmokeTest {
  static int checks;

  interface Action {
    void run() throws Exception;
  }

  static void check(boolean value) {
    checks++;
    if (!value) throw new AssertionError("Check " + checks + " failed");
  }

  static void denied(Action action) throws Exception {
    try {
      action.run();
      throw new AssertionError("Expected rejection");
    } catch (IllegalArgumentException expected) {
      checks++;
    }
  }

  public static void main(String[] args) throws Exception {
    Store s =
        new Store(
            System.getenv()
                .getOrDefault(
                    "TEST_DB_URL",
                    "jdbc:h2:mem:courier;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"),
            System.getenv().getOrDefault("TEST_DB_USERNAME", "sa"),
            System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    s.initialize("admin@test.local", "TestAdmin!123456".toCharArray());
    var admin = s.login("admin@test.local", "TestAdmin!123456".toCharArray());
    denied(() -> s.login("admin@test.local", "wrong".toCharArray()));
    s.createAgent(admin, "Agent One", "one@test.local", "TestAgent!123456".toCharArray());
    s.createAgent(admin, "Agent Two", "two@test.local", "TestAgent!123456".toCharArray());
    var one = s.login("one@test.local", "TestAgent!123456".toCharArray());
    var two = s.login("two@test.local", "TestAgent!123456".toCharArray());
    var registration =
        new Store.Registration(
            "Sender",
            "sender@test.local",
            "Private sender street",
            "Recipient",
            "recipient@test.local",
            "Private recipient street",
            new BigDecimal("2.50"),
            "EXPRESS",
            LocalDate.now(java.time.ZoneOffset.UTC).plusDays(2));
    denied(() -> s.register(one, registration));
    String tracking = s.register(admin, registration);
    check(tracking.startsWith("CT-"));
    long id = ((Number) s.parcels(admin, "", "", 0).get(0).get("id")).longValue();
    check(s.parcels(one, "", "", 0).isEmpty());
    denied(() -> s.change(admin, id, null, "PICKED_UP", "City", ""));
    s.change(admin, id, one.id(), null, null, null);
    check(s.parcels(one, "", "", 0).size() == 1);
    check(s.parcels(two, "", "", 0).isEmpty());
    denied(() -> s.change(two, id, null, "PICKED_UP", "City", ""));
    denied(() -> s.history(two, id));
    denied(() -> s.change(one, id, null, "CANCELLED", "City", ""));
    denied(() -> s.change(one, id, null, "DELIVERED", "City", ""));
    int history = s.history(admin, id).size();
    denied(() -> s.change(one, id, null, "PICKED_UP", "", ""));
    check(s.history(admin, id).size() == history);
    check(s.parcels(admin, "", "REGISTERED", 0).size() == 1);
    for (String status :
        List.of(
            "PICKED_UP",
            "IN_TRANSIT",
            "OUT_FOR_DELIVERY",
            "DELIVERY_FAILED",
            "OUT_FOR_DELIVERY",
            "DELIVERED")) s.change(one, id, null, status, "City hub", "Private internal note");
    denied(() -> s.change(admin, id, two.id(), null, null, null));
    var publicRows = s.track(tracking);
    check(publicRows.size() == 8);
    check(
        publicRows.get(0).keySet().equals(Set.of("tracking", "status", "location", "occurred_at")));
    check(!publicRows.toString().contains("Private"));
    check(s.track("' OR 1=1 --").isEmpty());
    check(s.parcels(admin, "' OR 1=1 --", "", 0).isEmpty());
    check(((Number) s.dashboard(admin).get(0).get("delivered")).longValue() == 1);
    check(s.alerts(one).size() == 7);
    long alert = ((Number) s.alerts(one).get(0).get("id")).longValue();
    s.read(two, alert);
    check(!((Boolean) s.alerts(one).get(0).get("is_read")));
    s.read(one, alert);
    check((Boolean) s.alerts(one).get(0).get("is_read"));
    String cancel = s.register(admin, registration);
    long cancelId = ((Number) s.parcels(admin, cancel, "", 0).get(0).get("id")).longValue();
    s.change(admin, cancelId, null, "CANCELLED", "Origin", "");
    check(s.parcels(admin, "", "CANCELLED", 0).size() == 1);
    System.out.println("PASS: " + checks + " JDBC integration checks");
  }
}
