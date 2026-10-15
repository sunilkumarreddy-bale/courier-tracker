package com.courier.jdbc;
import java.nio.file.Path;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

public final class Main {
 private static final Scanner input=new Scanner(System.in);
 private static String ask(String label){System.out.print(label+": ");if(!input.hasNextLine())throw new NoSuchElementException();return input.nextLine().trim();}
 private static char[] password(){var c=System.console();if(c!=null)return c.readPassword("Password: ");System.out.println("Console unavailable: password input will be visible.");return ask("Password").toCharArray();}
 public static void main(String[] args)throws Exception {
  var workflow=new Workflow(Path.of("models/parcel-workflow.jff"));
  try(var store=new Store(workflow)){
   if(Arrays.asList(args).contains("--init")){store.initialize();return;}
   if(Arrays.asList(args).contains("--bootstrap")){String name=ask("Admin name"),email=ask("Admin email");char[] p=password();try{store.bootstrap(name,email,p);}finally{Arrays.fill(p,'\0');}return;}
   Store.User user=null;
   while(true){
    try{
     System.out.println(user==null?"\n1 Login | 2 Public tracking | 0 Exit":"\n1 Parcels | 2 Register | 3 Agents | 4 Add agent | 5 Assign | 6 Update status | 7 Dashboard | 8 Inbox | 9 Mark read | 10 Public tracking | 11 Logout | 0 Exit");
     String choice=ask("Choice");if(choice.equals("0"))return;
     if(user==null){
      switch(choice){case "1"->{String email=ask("Email");char[] p=password();try{user=store.login(email,p);}finally{Arrays.fill(p,'\0');}}case "2"->store.track(ask("Tracking ID").toUpperCase(Locale.ROOT));default->System.out.println("Unknown choice");}continue;
     }
     switch(choice){
      case "1"->store.list(user,ask("Search (blank for all)"),ask("Status (blank for all)").toUpperCase(Locale.ROOT),Integer.parseInt(ask("Page (0 first)")));
      case "2"->{String[] fields={ask("Sender name"),ask("Sender address"),ask("Sender city"),ask("Recipient name"),ask("Recipient address"),ask("Recipient city")};System.out.println(store.register(user,fields,new BigDecimal(ask("Weight kg (up to 2 decimals)")),ask("Service STANDARD/EXPRESS").toUpperCase(Locale.ROOT),LocalDate.parse(ask("Expected date YYYY-MM-DD"))));}
      case "3"->store.agents(user);
      case "4"->{String name=ask("Agent name"),email=ask("Email");char[] p=password();try{store.addAgent(user,name,email,p);}finally{Arrays.fill(p,'\0');}}
      case "5"->store.assign(user,ask("Tracking ID").toUpperCase(Locale.ROOT),Long.parseLong(ask("Agent ID")));
      case "6"->{System.out.println("p=pick up, t=in transit, o=out for delivery/retry, d=delivered, f=failed, r=returned, c=cancel");store.update(user,ask("Tracking ID").toUpperCase(Locale.ROOT),ask("Event symbol"),ask("Location"),ask("Note"));}
      case "7"->store.dashboard(user);case "8"->store.inbox(user);case "9"->store.read(user,Long.parseLong(ask("Notification ID")));
      case "10"->store.track(ask("Tracking ID").toUpperCase(Locale.ROOT));case "11"->user=null;default->System.out.println("Unknown choice");
     }
    }catch(NoSuchElementException e){return;}catch(Exception e){System.out.println("Operation failed: "+e.getMessage());}
   }
  }
 }
}
