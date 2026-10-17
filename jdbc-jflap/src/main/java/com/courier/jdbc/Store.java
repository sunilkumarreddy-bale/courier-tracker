package com.courier.jdbc;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

final class Store implements AutoCloseable {
 record User(long id,String name,String role) {}
 private final Connection db;
 private final Workflow workflow;
 java.util.function.Consumer<String> output=System.out::print;
 Store(Workflow w)throws Exception {
  workflow=w;
  String url=System.getenv().getOrDefault("DB_URL","jdbc:mysql://localhost:3306/courier_jflap?connectionTimeZone=UTC");
  db=DriverManager.getConnection(url,required("DB_USER"),required("DB_PASSWORD"));
  try(var s=db.createStatement()){s.execute("SET time_zone = '+00:00'");}
 }
 static String required(String key){String v=System.getenv(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Set "+key);return v;}
 void initialize()throws Exception {
  try(var in=Store.class.getResourceAsStream("/schema.sql")){
   if(in==null)throw new IllegalStateException("Missing schema.sql");
   for(String sql:new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())try(var s=db.createStatement()){s.execute(sql);}
  }
  System.out.println("Schema ready. Use --bootstrap once to create the first admin.");
 }
 void bootstrap(String name,String email,char[] password)throws Exception {
  // Unique email + named database lock serialize concurrent first-admin setup.
  try(var q=db.prepareStatement("SELECT GET_LOCK('courier_jflap_bootstrap',10)")){try(var r=q.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Bootstrap busy");}}
  try {try(var q=db.createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM users")){r.next();if(r.getLong(1)>0)throw new IllegalStateException("Users already exist");}
   insertUser(name,email,password,"ADMIN");
  }finally{try(var q=db.createStatement()){q.execute("DO RELEASE_LOCK('courier_jflap_bootstrap')");}}
 }
 User login(String email,char[] password)throws Exception {
  try(var q=db.prepareStatement("SELECT * FROM users WHERE email=? AND active=TRUE")) {
   q.setString(1,email.toLowerCase(Locale.ROOT));try(var r=q.executeQuery()) {
    if(r.next()&&Passwords.verify(password,r.getString("password_hash")))return new User(r.getLong("id"),r.getString("name"),r.getString("role"));
   }
  }
  throw new IllegalArgumentException("Invalid credentials");
 }
 void addAgent(User u,String name,String email,char[] password)throws Exception {admin(u);insertUser(name,email,password,"AGENT");}
 private void insertUser(String name,String email,char[] password,String role)throws Exception {
  if(name.isBlank()||name.length()>100||!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")||email.length()>254)throw new IllegalArgumentException("Invalid name/email");
  execute("INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,?)",name,email.toLowerCase(Locale.ROOT),Passwords.hash(password),role);
 }
 void agents(User u)throws Exception {admin(u);print("SELECT id,name,email FROM users WHERE role='AGENT' AND active=TRUE ORDER BY name");}
 private static void admin(User u){if(!u.role().equals("ADMIN"))throw new IllegalArgumentException("Administrator required");}
 private long insert(String sql,Object...args)throws SQLException {
  try(var q=db.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bind(q,args);q.executeUpdate();try(var r=q.getGeneratedKeys()){r.next();return r.getLong(1);}}
 }
 private void execute(String sql,Object...args)throws SQLException {try(var q=db.prepareStatement(sql)){bind(q,args);q.executeUpdate();}}
 private static void bind(PreparedStatement q,Object...args)throws SQLException{for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);}
 interface Action {void run()throws Exception;}
 private void transaction(Action action)throws Exception {
  db.setAutoCommit(false);try{action.run();db.commit();}catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
 }
 String register(User u,String[] fields,BigDecimal weight,String service,LocalDate date)throws Exception {
  admin(u);
  if(fields.length!=6)throw new IllegalArgumentException("Six contact fields required");
  for(int i=0;i<fields.length;i++)if(fields[i].isBlank()||fields[i].length()>(i==1||i==4?300:100))throw new IllegalArgumentException("Invalid contact field");
  if(weight.signum()<=0||weight.compareTo(new BigDecimal("99999999.99"))>0||weight.scale()>2||date.isBefore(LocalDate.now(java.time.ZoneOffset.UTC))||!Set.of("STANDARD","EXPRESS").contains(service))throw new IllegalArgumentException("Invalid weight, service or date");
  String tracking="CT-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
  transaction(()->{
   long id=insert("INSERT INTO parcels(tracking_id,sender_name,sender_address,sender_city,recipient_name,recipient_address,recipient_city,weight_kg,service_type,expected_delivery,status,creator_id) VALUES(?,?,?,?,?,?,?,?,?,?,'REGISTERED',?)",tracking,fields[0],fields[1],fields[2],fields[3],fields[4],fields[5],weight,service,date,u.id());
   event(id,"REGISTERED",fields[2],"Parcel registered",u);
   notify(u.id(),id,tracking+" registered");
  });return tracking;
 }
 private void event(long id,String status,String location,String note,User u)throws SQLException{execute("INSERT INTO events(parcel_id,status,location,note,actor_id) VALUES(?,?,?,?,?)",id,status,location,note,u.id());}
 private void notify(long user,long parcel,String message)throws SQLException{execute("INSERT INTO notifications(user_id,parcel_id,message) VALUES(?,?,?)",user,parcel,message);}
 private record Parcel(long id,String status,Long agent,long creator){}
 private Parcel lock(String tracking,User u)throws SQLException {
  try(var q=db.prepareStatement("SELECT * FROM parcels WHERE tracking_id=? FOR UPDATE")){
   q.setString(1,tracking);try(var r=q.executeQuery()){
    if(!r.next())throw new IllegalArgumentException("Parcel not found");
    Long agent=r.getObject("agent_id",Long.class);
    if(!u.role().equals("ADMIN")&&!Objects.equals(agent,u.id()))throw new IllegalArgumentException("Parcel not found");
    return new Parcel(r.getLong("id"),r.getString("status"),agent,r.getLong("creator_id"));
   }
  }
 }
 void assign(User u,String tracking,long agent)throws Exception {
  admin(u);transaction(()->{
   Parcel p=lock(tracking,u);if(workflow.terminal(p.status()))throw new IllegalArgumentException("Terminal parcel cannot be assigned");
   if(Objects.equals(p.agent(),agent))throw new IllegalArgumentException("Agent already assigned");
   try(var q=db.prepareStatement("SELECT id FROM users WHERE id=? AND role='AGENT' AND active=TRUE FOR UPDATE")){q.setLong(1,agent);try(var r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Active agent required");}}
   execute("UPDATE parcels SET agent_id=? WHERE id=?",agent,p.id());event(p.id(),p.status(),"Dispatch","Assigned to agent "+agent,u);notify(agent,p.id(),tracking+" assigned to you");
  });
 }
 void update(User u,String tracking,String symbol,String location,String note)throws Exception {
  if(location.isBlank()||location.length()>100||note.length()>500)throw new IllegalArgumentException("Invalid location/note");
  transaction(()->{
   Parcel p=lock(tracking,u);String next=workflow.move(p.status(),symbol);
   if(next.equals("CANCELLED"))admin(u);else if(p.agent()==null)throw new IllegalArgumentException("Assign agent first");
   execute("UPDATE parcels SET status=? WHERE id=?",next,p.id());event(p.id(),next,location,note,u);notify(p.creator(),p.id(),tracking+" is now "+next);
  });
 }
 void track(String tracking)throws SQLException {
  // Public projection deliberately excludes names, addresses, notes and actors.
  print("SELECT tracking_id,sender_city,recipient_city,status,service_type,expected_delivery FROM parcels WHERE tracking_id=?",tracking);
  print("SELECT e.status,e.location,e.occurred_at FROM events e JOIN parcels p ON p.id=e.parcel_id WHERE p.tracking_id=? ORDER BY e.id",tracking);
 }
 void list(User u,String search,String status,int page)throws SQLException {
  if(page<0)throw new IllegalArgumentException("Page must be >=0");
  String scope=u.role().equals("ADMIN")?"1=1":"agent_id="+u.id();
  print("SELECT tracking_id,recipient_name,recipient_city,status,agent_id,expected_delivery FROM parcels WHERE "+scope+" AND LOCATE(?,CONCAT(tracking_id,' ',recipient_name,' ',recipient_city))>0 AND (?='' OR status=?) ORDER BY id DESC LIMIT 20 OFFSET ?",search,status,status,Math.multiplyExact(page,20));
 }
 void dashboard(User u)throws SQLException {
  print("SELECT status,COUNT(*) AS total FROM parcels WHERE "+(u.role().equals("ADMIN")?"1=1":"agent_id="+u.id())+" GROUP BY status");
 }
 void inbox(User u)throws SQLException{print("SELECT id,message,is_read FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT 100",u.id());}
 void read(User u,long id)throws SQLException{execute("UPDATE notifications SET is_read=TRUE WHERE id=? AND user_id=?",id,u.id());}
 private void print(String sql,Object...args)throws SQLException {
  try(var q=db.prepareStatement(sql)){bind(q,args);try(var r=q.executeQuery()){
   var m=r.getMetaData();for(int i=1;i<=m.getColumnCount();i++)output.accept(m.getColumnLabel(i)+" | ");output.accept("\n");
   int count=0;while(r.next()){count++;for(int i=1;i<=m.getColumnCount();i++)output.accept(r.getString(i)+" | ");output.accept("\n");}if(count==0)output.accept("No records.\n");
  }}
 }
 public void close()throws SQLException{db.close();}
}
