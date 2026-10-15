package com.courier.jdbc;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
final class Passwords {
 static String hash(char[] password) throws Exception {
  if(password.length<12)throw new IllegalArgumentException("Use at least 12 characters");
  byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
  return "210000$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(password,salt,210000));
 }
 static boolean verify(char[] password,String stored) throws Exception {
  String[] p=stored.split("\\$");
  return MessageDigest.isEqual(Base64.getDecoder().decode(p[2]),derive(password,Base64.getDecoder().decode(p[1]),Integer.parseInt(p[0])));
 }
 private static byte[] derive(char[] p,byte[] salt,int iterations)throws Exception {
  var spec=new PBEKeySpec(p,salt,iterations,256);
  try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
 }
}
