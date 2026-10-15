package com.courier.jdbc;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** The JFLAP DFA is the application's transition source of truth. */
public final class Workflow {
 private final Map<String,Map<String,String>> transitions=new LinkedHashMap<>();
 private final Set<String> finals=new HashSet<>();
 private String initial;
 public Workflow(Path file) throws Exception {
  var f=DocumentBuilderFactory.newInstance();
  f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  f.setFeature("http://xml.org/sax/features/external-general-entities",false);
  f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  var doc=f.newDocumentBuilder().parse(file.toFile());
  if (!doc.getElementsByTagName("type").item(0).getTextContent().equals("fa")) throw new IllegalArgumentException("Expected JFLAP FA");
  Map<String,String> ids=new HashMap<>();
  var ns=doc.getElementsByTagName("state");
  for(int i=0;i<ns.getLength();i++) {
   var n=(Element)ns.item(i); String name=n.getAttribute("name");
   if(transitions.putIfAbsent(name,new LinkedHashMap<>())!=null || ids.put(n.getAttribute("id"),name)!=null) throw new IllegalArgumentException("Duplicate state");
   if(n.getElementsByTagName("initial").getLength()>0) {if(initial!=null)throw new IllegalArgumentException("Multiple initial states");initial=name;}
   if(n.getElementsByTagName("final").getLength()>0) finals.add(name);
  }
  ns=doc.getElementsByTagName("transition");
  for(int i=0;i<ns.getLength();i++) {
   var n=(Element)ns.item(i); String from=ids.get(value(n,"from")), to=ids.get(value(n,"to")), symbol=value(n,"read");
   if(from==null || to==null || symbol.length()!=1 || transitions.get(from).putIfAbsent(symbol,to)!=null) throw new IllegalArgumentException("Expected deterministic single-character transitions");
  }
  if(!"REGISTERED".equals(initial) || !finals.equals(Set.of("DELIVERED","RETURNED","CANCELLED")))throw new IllegalArgumentException("Unexpected workflow states");
  for(String s:finals)if(!transitions.get(s).isEmpty())throw new IllegalArgumentException("Terminal state has outgoing edges");
 }
 private static String value(Element n,String tag){return n.getElementsByTagName(tag).item(0).getTextContent();}
 public Map<String,String> next(String state){return Collections.unmodifiableMap(transitions.getOrDefault(state,Map.of()));}
 public String move(String state,String symbol){String to=next(state).get(symbol);if(to==null)throw new IllegalArgumentException("Invalid event for "+state+": "+symbol);return to;}
 public boolean terminal(String state){return finals.contains(state);}
 public boolean accepts(String input){try{String s=initial;for(char c:input.toCharArray())s=move(s,String.valueOf(c));return terminal(s);}catch(IllegalArgumentException e){return false;}}
}
