package courier;

import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Swing presentation; blocking database operations run on a worker thread. */
public final class App {
  private final Store store;
  private final JFrame frame = new JFrame("Courier • JDBC Delivery Manager");
  private Store.User user;
  private JTable parcels;
  private final JTextField search = new JTextField(18);
  private final JComboBox<String> filter =
      new JComboBox<>(
          new String[] {
            "",
            "REGISTERED",
            "PICKED_UP",
            "IN_TRANSIT",
            "OUT_FOR_DELIVERY",
            "DELIVERED",
            "DELIVERY_FAILED",
            "RETURNED",
            "CANCELLED"
          });
  private int page;
  private final JLabel summary = new JLabel(" ");
  private final JLabel pageLabel = new JLabel();

  interface Task {
    Object run() throws Exception;
  }

  private App(Store store) {
    this.store = store;
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(1150, 720);
    frame.setMinimumSize(new Dimension(850, 550));
    frame.setLocationRelativeTo(null);
    login();
    frame.setVisible(true);
  }

  private void work(Task task, java.util.function.Consumer<Object> done) {
    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    new SwingWorker<Object, Void>() {
      protected Object doInBackground() throws Exception {
        return task.run();
      }

      protected void done() {
        frame.setCursor(Cursor.getDefaultCursor());
        try {
          done.accept(get());
        } catch (Exception e) {
          Throwable cause = e.getCause() == null ? e : e.getCause();
          JOptionPane.showMessageDialog(
              frame, cause.getMessage(), "Operation failed", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private JButton button(String title, Runnable action) {
    JButton b = new JButton(title);
    b.addActionListener(e -> action.run());
    return b;
  }

  private JPanel form(String[] labels, JComponent[] fields) {
    JPanel panel = new JPanel(new GridLayout(0, 2, 12, 10));
    panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    for (int i = 0; i < labels.length; i++) {
      JLabel label = new JLabel(labels[i]);
      label.setLabelFor(fields[i]);
      panel.add(label);
      panel.add(fields[i]);
    }
    return panel;
  }

  private boolean confirm(String title, JPanel panel) {
    return JOptionPane.showConfirmDialog(
            frame, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION;
  }

  private void login() {
    user = null;
    JPanel root = new JPanel(new BorderLayout(20, 20));
    JLabel title =
        new JLabel("Courier Parcel Tracking & Delivery Management", SwingConstants.CENTER);
    title.setFont(title.getFont().deriveFont(24f));
    root.add(title, BorderLayout.NORTH);
    JTextField email = new JTextField();
    JPasswordField password = new JPasswordField();
    JPanel center = form(new String[] {"Email", "Password"}, new JComponent[] {email, password});
    JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(center);
    root.add(wrapper);
    JPanel actions = new JPanel();
    actions.add(
        button(
            "Sign in",
            () -> {
              String address = email.getText();
              char[] secret = password.getPassword();
              password.setText("");
              work(
                  () -> store.login(address, secret),
                  result -> {
                    user = (Store.User) result;
                    home();
                  });
            }));
    actions.add(button("Public tracking", this::tracking));
    root.add(actions, BorderLayout.SOUTH);
    frame.setContentPane(root);
    frame.revalidate();
    frame.repaint();
  }

  private void tracking() {
    String id = JOptionPane.showInputDialog(frame, "Tracking ID");
    if (id != null)
      work(() -> store.track(id), result -> showRows("Public journey • UTC", rows(result)));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(Object value) {
    return (List<Map<String, Object>>) value;
  }

  private JTable table(List<Map<String, Object>> rows) {
    String[] cols =
        rows.isEmpty() ? new String[] {"No results"} : rows.get(0).keySet().toArray(String[]::new);
    DefaultTableModel model =
        new DefaultTableModel(cols, 0) {
          public boolean isCellEditable(int r, int c) {
            return false;
          }
        };
    for (var row : rows) model.addRow(row.values().toArray());
    JTable t = new JTable(model);
    t.setRowHeight(30);
    t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    t.setAutoCreateRowSorter(true);
    t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    for (int i = 0; i < t.getColumnCount(); i++)
      t.getColumnModel().getColumn(i).setPreferredWidth(cols[i].equals("tracking") ? 310 : 150);
    return t;
  }

  private void showRows(String title, List<Map<String, Object>> rows) {
    JDialog dialog = new JDialog(frame, title, true);
    dialog.add(new JScrollPane(table(rows)));
    dialog.setSize(900, 420);
    dialog.setLocationRelativeTo(frame);
    dialog.setVisible(true);
  }

  private long selected() {
    int r = parcels.getSelectedRow();
    if (r < 0) throw new IllegalArgumentException("Select a parcel first");
    return ((Number) parcels.getValueAt(r, 0)).longValue();
  }

  private void home() {
    JPanel root = new JPanel(new BorderLayout(10, 12));
    root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
    JPanel top = new JPanel(new BorderLayout());
    JLabel heading = new JLabel("Courier Operations  |  " + user.name() + " • " + user.role());
    heading.setFont(heading.getFont().deriveFont(22f));
    top.add(heading, BorderLayout.NORTH);
    top.add(summary, BorderLayout.SOUTH);
    root.add(top, BorderLayout.NORTH);
    parcels = table(List.of());
    JPanel body = new JPanel(new BorderLayout());
    JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT));
    tools.add(new JLabel("Search"));
    tools.add(search);
    tools.add(filter);
    tools.add(
        button(
            "Search / refresh",
            () -> {
              page = 0;
              refresh();
            }));
    body.add(tools, BorderLayout.NORTH);
    body.add(new JScrollPane(parcels));
    JPanel pages = new JPanel();
    pages.add(
        button(
            "Previous",
            () -> {
              page = Math.max(0, page - 1);
              refresh();
            }));
    pages.add(pageLabel);
    pages.add(
        button(
            "Next",
            () -> {
              if (parcels.getRowCount() == 25) {
                page++;
                refresh();
              }
            }));
    body.add(pages, BorderLayout.SOUTH);
    root.add(body);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    if (user.role().equals("ADMIN")) {
      actions.add(button("Register parcel", this::register));
      actions.add(button("Create agent", this::agent));
      actions.add(button("Assign agent", this::assign));
    }
    actions.add(button("Update status", this::status));
    actions.add(
        button(
            "History",
            () -> {
              try {
                long id = selected();
                work(
                    () -> store.history(user, id),
                    v -> showRows("Internal history • UTC", rows(v)));
              } catch (Exception e) {
                error(e);
              }
            }));
    actions.add(button("Notifications", this::notifications));
    actions.add(button("Track", this::tracking));
    actions.add(button("Sign out", this::login));
    root.add(actions, BorderLayout.SOUTH);
    frame.setContentPane(root);
    frame.revalidate();
    frame.repaint();
    refresh();
  }

  private void refresh() {
    Store.User current = user;
    String term = search.getText(), state = (String) filter.getSelectedItem();
    int currentPage = page;
    work(
        () -> List.of(store.parcels(current, term, state, currentPage), store.dashboard(current)),
        v -> {
          if (user != current) return;
          List<?> result = (List<?>) v;
          JTable fresh = table(rows(result.get(0)));
          parcels.setModel(fresh.getModel());
          parcels.setAutoCreateRowSorter(true);
          for (int i = 0; i < parcels.getColumnCount(); i++)
            parcels.getColumnModel().getColumn(i).setPreferredWidth(i == 1 ? 310 : 150);
          var stats = rows(result.get(1)).get(0);
          long total = ((Number) stats.get("total")).longValue(),
              delivered = ((Number) stats.get("delivered")).longValue();
          summary.setText(
              stats.toString()
                  + String.format(
                      "   Delivery: %.1f%%", total == 0 ? 0 : 100.0 * delivered / total));
          pageLabel.setText("Page " + (currentPage + 1));
        });
  }

  private void error(Exception e) {
    JOptionPane.showMessageDialog(frame, e.getMessage(), "Check input", JOptionPane.ERROR_MESSAGE);
  }

  private void register() {
    String[] labels = {
      "Sender name",
      "Sender email / phone",
      "Sender address",
      "Recipient name",
      "Recipient email / phone",
      "Recipient address",
      "Weight (kg)",
      "Expected date (YYYY-MM-DD)"
    };
    JTextField[] fields = new JTextField[8];
    for (int i = 0; i < 8; i++) fields[i] = new JTextField(25);
    fields[7].setText(LocalDate.now(java.time.ZoneOffset.UTC).plusDays(3).toString());
    JPanel panel = form(labels, fields);
    JComboBox<String> service = new JComboBox<>(new String[] {"STANDARD", "EXPRESS"});
    panel.add(new JLabel("Service"));
    panel.add(service);
    if (confirm("Register parcel", panel)) {
      try {
        var r =
            new Store.Registration(
                fields[0].getText(),
                fields[1].getText(),
                fields[2].getText(),
                fields[3].getText(),
                fields[4].getText(),
                fields[5].getText(),
                new BigDecimal(fields[6].getText()),
                (String) service.getSelectedItem(),
                LocalDate.parse(fields[7].getText()));
        work(
            () -> store.register(user, r),
            id -> {
              JOptionPane.showMessageDialog(frame, "Tracking ID: " + id);
              refresh();
            });
      } catch (Exception e) {
        error(e);
      }
    }
  }

  private void agent() {
    JTextField name = new JTextField(), email = new JTextField();
    JPasswordField password = new JPasswordField();
    if (confirm(
        "Create delivery agent",
        form(
            new String[] {"Name", "Email", "Password (12+ characters)"},
            new JComponent[] {name, email, password}))) {
      String n = name.getText(), mail = email.getText();
      char[] pass = password.getPassword();
      work(
          () -> {
            store.createAgent(user, n, mail, pass);
            return null;
          },
          v -> JOptionPane.showMessageDialog(frame, "Agent created"));
    }
  }

  private void assign() {
    try {
      long id = selected();
      work(
          () -> store.agents(user),
          v -> {
            var agents = rows(v);
            if (agents.isEmpty()) {
              JOptionPane.showMessageDialog(frame, "Create an agent first");
              return;
            }
            String[] names =
                agents.stream()
                    .map(r -> r.get("name") + " <" + r.get("email") + ">")
                    .toArray(String[]::new);
            JComboBox<String> choice = new JComboBox<>(names);
            if (confirm(
                "Assign / reassign", form(new String[] {"Agent"}, new JComponent[] {choice}))) {
              long agent = ((Number) agents.get(choice.getSelectedIndex()).get("id")).longValue();
              work(
                  () -> {
                    store.change(user, id, agent, null, null, null);
                    return null;
                  },
                  x -> refresh());
            }
          });
    } catch (Exception e) {
      error(e);
    }
  }

  private void status() {
    try {
      long id = selected();
      String current = parcels.getValueAt(parcels.getSelectedRow(), 5).toString();
      List<String> next = new ArrayList<>(Store.next(current));
      if (!user.role().equals("ADMIN")) next.remove("CANCELLED");
      if (next.isEmpty()) throw new IllegalArgumentException("No available status updates");
      JComboBox<String> status = new JComboBox<>(next.toArray(String[]::new));
      JTextField location = new JTextField(), note = new JTextField();
      if (confirm(
          "Update parcel",
          form(
              new String[] {"Status", "Public location (city / hub only)", "Internal note"},
              new JComponent[] {status, location, note}))) {
        String to = (String) status.getSelectedItem(), loc = location.getText(), n = note.getText();
        work(
            () -> {
              store.change(user, id, null, to, loc, n);
              return null;
            },
            v -> refresh());
      }
    } catch (Exception e) {
      error(e);
    }
  }

  private void notifications() {
    work(
        () -> store.alerts(user),
        v -> {
          List<Map<String, Object>> alerts = rows(v);
          JTable t = table(alerts);
          JPanel panel = new JPanel(new BorderLayout());
          panel.add(new JScrollPane(t));
          if (confirm("Notifications • select one to mark read", panel)
              && t.getSelectedRow() >= 0) {
            long id = ((Number) t.getValueAt(t.getSelectedRow(), 0)).longValue();
            work(
                () -> {
                  store.read(user, id);
                  return null;
                },
                x -> {});
          }
        });
  }

  public static void main(String[] args) {
    String url =
        System.getenv()
            .getOrDefault(
                "DB_URL",
                "jdbc:mysql://localhost:3306/courier_jdbc?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    Store store =
        new Store(
            url,
            System.getenv().getOrDefault("DB_USERNAME", "courier"),
            System.getenv().getOrDefault("DB_PASSWORD", ""));
    try {
      char[] password = System.getenv().getOrDefault("ADMIN_PASSWORD", "").toCharArray();
      try {
        store.initialize(System.getenv("ADMIN_EMAIL"), password);
      } finally {
        Arrays.fill(password, '\0');
      }
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      SwingUtilities.invokeLater(() -> new App(store));
    } catch (Exception e) {
      System.err.println("Startup failed: " + e.getMessage());
      System.exit(1);
    }
  }
}
