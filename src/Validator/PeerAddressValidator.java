package Validator;

import java.util.regex.Pattern;

public final class PeerAddressValidator {
  private static final Pattern DNS_CHARACTERS = Pattern.compile("[a-zA-Z0-9._-]{3,50}");
  private static final Pattern LETTER = Pattern.compile("[a-zA-Z]");
  private static final Pattern DECIMAL = Pattern.compile("[0-9]+");

  private PeerAddressValidator() { }

  public record Address(String host, int port) { }

  public static boolean isValid(String address) {
    try {
      parse(address);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  public static Address parse(String address) {
    if (address == null) {
      throw new IllegalArgumentException("Peer address is missing.");
    }
    int colon = address.indexOf(':');
    if (colon <= 0 || colon != address.lastIndexOf(':') || colon == address.length() - 1) {
      throw new IllegalArgumentException("Expected host:port (IPv4 or course-format DNS).");
    }
    String host = address.substring(0, colon);
    String portString = address.substring(colon + 1);
    if (!DECIMAL.matcher(portString).matches()) {
      throw new IllegalArgumentException("Port must be a decimal integer.");
    }
    int port = 0;
    for (int i = 0; i < portString.length(); i++) {
      port = port * 10 + portString.charAt(i) - '0';
      if (port > 65535) {
        throw new IllegalArgumentException("Port must be in [1, 65535].");
      }
    }
    if (port == 0) {
      throw new IllegalArgumentException("Port must be in [1, 65535].");
    }
    if (!validCourseDns(host) && !publicIpv4(host)) {
      throw new IllegalArgumentException("Invalid DNS name or non-public/invalid IPv4 address.");
    }
    return new Address(host, port);
  }

  private static boolean validCourseDns(String host) {
    return DNS_CHARACTERS.matcher(host).matches() && LETTER.matcher(host).find() && host.substring(1, host.length() - 1).contains(".");
  }

  private static boolean publicIpv4(String host) {
    String[] parts = host.split("\\.", -1);
    if (parts.length != 4) return false;
    int[] o = new int[4];
    for (int i = 0; i < 4; i++) {
      if (parts[i].isEmpty() || parts[i].length() > 3 || !DECIMAL.matcher(parts[i]).matches()) {
        return false;
      }
      o[i] = Integer.parseInt(parts[i], 10);
      if (o[i] > 255) {
        return false;
      }
    }
    int a = o[0], b = o[1], c = o[2], d = o[3];
    if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
    if (a == 100 && b >= 64 && b <= 127) return false;
    if (a == 169 && b == 254) return false;
    if (a == 172 && b >= 16 && b <= 31) return false;
    if (a == 192 && b == 168) return false;
    if (a == 192 && b == 0 && c == 0 && d != 9 && d != 10) return false;
    if (a == 192 && b == 0 && c == 2) return false;
    if (a == 192 && b == 88 && c == 99) return false;
    if (a == 198 && (b == 18 || b == 19)) return false;
    if (a == 198 && b == 51 && c == 100) return false;
    if (a == 203 && b == 0 && c == 113) return false;
    return true;
  }
}
