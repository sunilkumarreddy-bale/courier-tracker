package com.courier.jdbc;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;

/** Swing desktop UI; --preview renders the actual panel with labelled sample data. */
public final class Gui {
 private final JPanel root=new JPanel(new BorderLayout(0,0));
 private final JPanel content=new JPanel(new BorderLayout(16,16));
 private final JLabel heading=new JLabel("Parcel operations"), identity=new JLabel("Public access");
 private final JTextArea results=new JTextArea();
 private final DefaultTableModel rows=new DefaultTableModel(new String[]{"Tracking ID","Recipient","City","Status","Agent","Expected"},0){public boolean isCellEditable(int r,int c){return false;}};
 private final JTable table=new JTable(rows);
 private final JTextField search=new JTextField(20);
 private final JComboBox<String> status=new JComboBox<>(new String[]{"","REGISTERED","PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","DELIVERY_FAILED","RETURNED","CANCELLED"});
 private final JSpinner page=new JSpinner(new SpinnerNumberModel(0,0,100000,1));
 private final java.util.List<JButton> actions=new ArrayList<>();
 private Store store; private Store.User user; private boolean busy; private final boolean preview;
 private final Color navy=new Color(19,34,57), teal=new Color(0,122,117), bg=new Color(242,246,250);
 private Gui(boolean preview){
  this.preview=preview;root.setBackground(bg);content.setBackground(bg);content.setBorder(new EmptyBorder(28,28,24,28));
  JPanel sidebar=new JPanel();sidebar.setLayout(new BoxLayout(sidebar,BoxLayout.Y_AXIS));sidebar.setBackground(navy);sidebar.setBorder(new EmptyBorder(30,22,26,22));sidebar.setPreferredSize(new Dimension(210,700));
  JLabel brand=new JLabel("COURIER FLOW");brand.setForeground(Color.WHITE);brand.setFont(new Font("SansSerif",Font.BOLD,18));sidebar.add(brand);sidebar.add(Box.createVerticalStrut(10));
  JLabel sub=new JLabel("JFLAP · JDBC · MySQL");sub.setForeground(new Color(157,183,210));sidebar.add(sub);sidebar.add(Box.createVerticalStrut(38));
  nav(sidebar,"Parcels",this::load);nav(sidebar,"Register parcel",this::register);nav(sidebar,"Assign delivery",this::assign);nav(sidebar,"Update status",this::update);nav(sidebar,"Delivery agents",()->run(()->store.agents(requireUser())));nav(sidebar,"Add agent",this::addAgent);nav(sidebar,"Dashboard",()->run(()->store.dashboard(requireUser())));nav(sidebar,"Notifications",()->run(()->store.inbox(requireUser())));nav(sidebar,"Mark alert read",()->{String v=prompt("Notification ID");if(v!=null)run(()->store.read(requireUser(),Long.parseLong(v)));});
  sidebar.add(Box.createVerticalGlue());nav(sidebar,"Login / Logout",this::login);nav(sidebar,"Public tracking",this::track);
  root.add(sidebar,BorderLayout.WEST);root.add(content,BorderLayout.CENTER);
  JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);heading.setFont(new Font("SansSerif",Font.BOLD,28));top.add(heading,BorderLayout.WEST);identity.setForeground(teal);top.add(identity,BorderLayout.EAST);
  JPanel north=new JPanel(new BorderLayout(0,22));north.setOpaque(false);north.add(top,BorderLayout.NORTH);
  JPanel tools=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));tools.setOpaque(false);tools.add(new JLabel("Search"));tools.add(search);tools.add(status);tools.add(new JLabel("Page"));tools.add(page);JButton refresh=new JButton("Refresh");refresh.addActionListener(e->{try{if(!preview)load();}catch(Exception ex){error(ex);}});tools.add(refresh);actions.add(refresh);north.add(tools,BorderLayout.SOUTH);content.add(north,BorderLayout.NORTH);
  table.setRowHeight(42);table.setFont(new Font("SansSerif",Font.PLAIN,13));table.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,12));table.getTableHeader().setPreferredSize(new Dimension(0,38));table.setSelectionBackground(new Color(209,237,235));table.setFillsViewportHeight(true);table.setShowVerticalLines(false);table.setGridColor(new Color(230,236,242));
  JPanel center=new JPanel(new BorderLayout(0,18));center.setOpaque(false);JScrollPane grid=new JScrollPane(table);grid.setColumnHeaderView(table.getTableHeader());center.add(grid,BorderLayout.CENTER);
  results.setEditable(false);results.setFont(new Font("Monospaced",Font.PLAIN,12));results.setBorder(new EmptyBorder(14,14,14,14));results.setText("Welcome. Sign in to manage deliveries, or use Public tracking.\nDatabase credentials are read from your environment.");
  JScrollPane output=new JScrollPane(results);output.setPreferredSize(new Dimension(800,175));output.setBorder(BorderFactory.createTitledBorder("Activity & query results"));center.add(output,BorderLayout.SOUTH);content.add(center,BorderLayout.CENTER);
  JLabel footer=new JLabel("Delivery rules loaded from models/parcel-workflow.jff  •  Changes saved with JDBC transactions");footer.setForeground(new Color(97,113,132));content.add(footer,BorderLayout.SOUTH);
  if(preview){identity.setText("Administrator · SAMPLE DATA");rows.addRow(new Object[]{"CT-DEMO-1001","Aarav Sharma","Bengaluru","IN_TRANSIT","2","2026-10-18"});rows.addRow(new Object[]{"CT-DEMO-1002","Priya Rao","Hyderabad","OUT_FOR_DELIVERY","3","2026-10-16"});rows.addRow(new Object[]{"CT-DEMO-1003","Meera Singh","Pune","DELIVERED","2","2026-10-15"});rows.addRow(new Object[]{"CT-DEMO-1004","Rohan Patel","Mumbai","REGISTERED","—","2026-10-20"});rows.addRow(new Object[]{"CT-DEMO-1005","Ananya Das","Delhi","DELIVERY_FAILED","3","2026-10-16"});results.setText("VISUAL PREVIEW — sample records, no database connection\n\nWorkflow: REGISTERED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED\nFailed deliveries can be retried or returned. Invalid transitions are rejected.\nChoose an operation on the left to open its form in the live application.");}
 }
 private void nav(JPanel p,String text,Runnable action){JButton b=new JButton(text);b.setAlignmentX(Component.LEFT_ALIGNMENT);b.setMaximumSize(new Dimension(175,36));b.setBackground(navy);b.setForeground(Color.WHITE);b.setHorizontalAlignment(SwingConstants.LEFT);b.setBorderPainted(false);b.setFocusPainted(false);b.addActionListener(e->{try{if(!preview)action.run();}catch(Exception ex){error(ex);}});p.add(b);p.add(Box.createVerticalStrut(6));actions.add(b);}
 private String prompt(String text){return JOptionPane.showInputDialog(root,text);}
 private String[] form(String title,String...labels){JPanel p=new JPanel(new GridLayout(0,2,10,12));JTextField[] fields=new JTextField[labels.length];for(int i=0;i<labels.length;i++){p.add(new JLabel(labels[i]));fields[i]=new JTextField(24);p.add(fields[i]);}if(JOptionPane.showConfirmDialog(root,p,title,JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;return Arrays.stream(fields).map(f->f.getText().trim()).toArray(String[]::new);}
 private char[] password(){JPasswordField p=new JPasswordField(24);return JOptionPane.showConfirmDialog(root,p,"Password",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION?p.getPassword():null;}
 private Store.User requireUser(){if(user==null)throw new IllegalArgumentException("Please sign in first");return user;}
 private void error(Exception e){JOptionPane.showMessageDialog(root,e.getMessage(),"Operation failed",JOptionPane.ERROR_MESSAGE);}
 private void run(Store.Action action){if(busy)return;busy=true;actions.forEach(b->b.setEnabled(false));results.setText("");new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{if(store==null){store=new Store(new Workflow(Path.of("models/parcel-workflow.jff")));store.output=s->SwingUtilities.invokeLater(()->results.append(s));}action.run();return null;}protected void done(){try{get();results.append("\nOperation completed.\n");}catch(Exception e){error(e.getCause() instanceof Exception cause?cause:e);}finally{busy=false;actions.forEach(b->b.setEnabled(true));identity.setText(user==null?"Public access":user.name()+" · "+user.role());}}}.execute();}
 private void login(){if(user!=null){user=null;rows.setRowCount(0);identity.setText("Public access");results.setText("Signed out.");return;}String email=prompt("Email");if(email==null)return;char[] p=password();if(p==null)return;run(()->{try{user=store.login(email.trim(),p);}finally{Arrays.fill(p,'\0');}});}
 private void load(){requireUser();String term=search.getText().trim(),filter=(String)status.getSelectedItem();int n=(Integer)page.getValue();run(()->{StringBuilder captured=new StringBuilder();var out=store.output;store.output=captured::append;try{store.list(user,term,filter,n);}finally{store.output=out;}String text=captured.toString();SwingUtilities.invokeLater(()->{rows.setRowCount(0);String[] lines=text.split("\\R");for(int i=1;i<lines.length;i++)if(lines[i].contains(" | "))rows.addRow(lines[i].split(" \\| ",-1));});});}
 private void register(){requireUser();String[] v=form("Register parcel","Sender name","Sender address","Sender city","Recipient name","Recipient address","Recipient city","Weight kg","Service STANDARD/EXPRESS","Expected YYYY-MM-DD");if(v!=null)run(()->{String id=store.register(user,Arrays.copyOf(v,6),new BigDecimal(v[6]),v[7].toUpperCase(Locale.ROOT),LocalDate.parse(v[8]));SwingUtilities.invokeLater(()->results.append("Created tracking ID: "+id+"\n"));});}
 private void assign(){requireUser();String[] v=form("Assign delivery","Tracking ID","Agent ID");if(v!=null)run(()->store.assign(user,v[0].toUpperCase(Locale.ROOT),Long.parseLong(v[1])));}
 private void update(){requireUser();String[] v=form("Update: p=pickup, t=transit, o=out/retry, d=delivered, f=failed, r=return, c=cancel","Tracking ID","Event symbol","Location","Note");if(v!=null)run(()->store.update(user,v[0].toUpperCase(Locale.ROOT),v[1],v[2],v[3]));}
 private void addAgent(){requireUser();String[] v=form("Add delivery agent","Name","Email");if(v==null)return;char[] p=password();if(p!=null)run(()->{try{store.addAgent(user,v[0],v[1],p);}finally{Arrays.fill(p,'\0');}});}
 private void track(){String v=prompt("Tracking ID");if(v!=null)run(()->store.track(v.trim().toUpperCase(Locale.ROOT)));}
 private static void layout(Component c){if(c instanceof Container p){p.doLayout();for(Component child:p.getComponents())layout(child);}}
 public static void main(String[] args)throws Exception {
  boolean preview=Arrays.asList(args).contains("--preview");
  if(preview){System.setProperty("java.awt.headless","true");SwingUtilities.invokeAndWait(()->{try{Gui g=new Gui(true);g.root.setSize(1280,800);layout(g.root);BufferedImage image=new BufferedImage(1280,800,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.root.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",Path.of(args.length>1?args[1]:"gui-preview.png").toFile());}catch(Exception e){throw new RuntimeException(e);}});return;}
  SwingUtilities.invokeLater(()->{Gui g=new Gui(false);JFrame frame=new JFrame("Courier Flow | JFLAP + JDBC + MySQL");frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);frame.setContentPane(g.root);frame.setSize(1280,800);frame.setMinimumSize(new Dimension(1100,700));frame.setLocationRelativeTo(null);frame.setVisible(true);});
 }
}
