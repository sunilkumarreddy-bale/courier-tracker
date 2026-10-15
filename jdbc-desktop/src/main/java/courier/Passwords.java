package courier;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Passwords {
  static String hash(char[] password) throws Exception {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return "210000:"
        + Base64.getEncoder().encodeToString(salt)
        + ":"
        + Base64.getEncoder().encodeToString(derive(password, salt, 210000));
  }

  static byte[] derive(char[] password, byte[] salt, int rounds) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } finally {
      spec.clearPassword();
    }
  }

  static boolean verify(char[] password, String stored) throws Exception {
    String[] v = stored.split(":");
    return MessageDigest.isEqual(
        Base64.getDecoder().decode(v[2]),
        derive(password, Base64.getDecoder().decode(v[1]), Integer.parseInt(v[0])));
  }
}
