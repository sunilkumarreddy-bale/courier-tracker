package com.courier.jdbc;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class Checks {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 interface Operation{void run()throws Exception;}
 static void rejected(Operation op)throws Exception{try{op.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection");}
 public static void main(String[] args)throws Exception {
  Workflow w=new Workflow(Path.of("models/parcel-workflow.jff"));
  for(String s:new String[]{"ptod","ptofod","c","pr","ptr","ptofr"})check(w.accepts(s),"Should accept "+s);
  for(String s:new String[]{"","pto","d","ptodd","pc","ptofd","ptof"})check(!w.accepts(s),"Should reject "+s);
  char[] password="TestingSecure!2026".toCharArray();String hash=Passwords.hash(password);
  check(Passwords.verify(password,hash),"Correct password");check(!Passwords.verify("WrongPassword!".toCharArray(),hash),"Wrong password");check(!hash.equals(Passwords.hash(password)),"Random salt");
  if(args.length>0&&args[0].equals("--database")){
   try(Store s=new Store(w)){
    s.initialize();s.bootstrap("Admin","admin@test.local",password);var admin=s.login("admin@test.local",password);
    rejected(()->s.bootstrap("Other","other@test.local",password));
    s.addAgent(admin,"Agent A","a@test.local",password);s.addAgent(admin,"Agent B","b@test.local",password);
    var a=s.login("a@test.local",password);var b=s.login("b@test.local",password);
    String id=s.register(admin,new String[]{"Sender","Street 1","Pune","Recipient","Street 2","Delhi"},new BigDecimal("1.25"),"STANDARD",LocalDate.now(java.time.ZoneOffset.UTC).plusDays(3));
    rejected(()->s.update(admin,id,"p","Pune","No agent"));s.assign(admin,id,a.id());
    rejected(()->s.update(b,id,"p","Pune","Wrong agent"));rejected(()->s.update(a,id,"c","Pune","Agent cancel"));
    s.update(a,id,"p","Pune","");s.update(a,id,"t","Hub","");s.update(a,id,"o","Delhi","");s.update(a,id,"f","Delhi","");s.update(a,id,"o","Delhi","Retry");s.update(a,id,"d","Delhi","");
    rejected(()->s.assign(admin,id,b.id()));rejected(()->s.update(a,id,"r","Delhi","Terminal"));
    s.track(id);s.dashboard(admin);s.inbox(a);
   }
  }
  System.out.println("Checks passed");
 }
}
