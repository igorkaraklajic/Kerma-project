package Utils;

import java.security.MessageDigest;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

public class HashUtils {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  public static String computeBlake2sHash(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("BLAKE2S-256", "BC");
      byte[] digest = md.digest(bytes);
      return Hex.toHexString(digest);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}