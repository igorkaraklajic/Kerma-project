
import Validator.PeerAddressValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PeerAddressValidatorTest {

  @Test
  void testValidPeers() {
    assertTrue(PeerAddressValidator.isValid("128.130.122.101:18018"));
    assertTrue(PeerAddressValidator.isValid("138.197.191.170:18018"));
    assertTrue(PeerAddressValidator.isValid("kermanode.net:18018"));
    assertTrue(PeerAddressValidator.isValid("node.example.com:8080"));
  }

  @Test
  void testInvalidIpAddresses() {
    assertFalse(PeerAddressValidator.isValid("256.2.3.4:18018"));
    assertFalse(PeerAddressValidator.isValid("1.2.3.4.5:678"));
    assertFalse(PeerAddressValidator.isValid("192.168.0.2:18018"));
    assertFalse(PeerAddressValidator.isValid("127.0.0.1:18018"));
    assertFalse(PeerAddressValidator.isValid("10.0.0.1:18018"));
  }

  @Test
  void testInvalidPorts() {
    assertFalse(PeerAddressValidator.isValid("kermanode.net:0"));
    assertFalse(PeerAddressValidator.isValid("kermanode.net:65536"));
    assertFalse(PeerAddressValidator.isValid("kermanode.net:-1"));
    assertFalse(PeerAddressValidator.isValid("kermanode.net:abc"));
  }

  @Test
  void testValidPortBoundaries() {
    assertTrue(PeerAddressValidator.isValid("kermanode.net:1"));
    assertTrue(PeerAddressValidator.isValid("kermanode.net:65535"));
  }

  @Test
  void testInvalidDnsNames() {
    assertFalse(PeerAddressValidator.isValid("localhost:18018"));
    assertFalse(PeerAddressValidator.isValid("nodotindomain:1234"));
    assertFalse(PeerAddressValidator.isValid(".node:18018"));
    assertFalse(PeerAddressValidator.isValid("node.:18018"));
    assertFalse(PeerAddressValidator.isValid("node@example.com:18018"));
  }

  @Test
  void testInvalidFormats() {
    assertFalse(PeerAddressValidator.isValid("kermanode.net"));
    assertFalse(PeerAddressValidator.isValid(":18018"));
    assertFalse(PeerAddressValidator.isValid("kermanode.net:"));
    assertFalse(PeerAddressValidator.isValid(""));
    assertFalse(PeerAddressValidator.isValid(null));
  }

  @Test
  void testParseAddress() {
    PeerAddressValidator.Address address =
        PeerAddressValidator.parse("128.130.122.101:18018");

    assertEquals("128.130.122.101", address.host());
    assertEquals(18018, address.port());
  }
}
