package Utils;

import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
public class SignatureUtils {

  //TODO: Check if the method is correct, it was written before task 2 it's based on the protocol description
  public static boolean verifySignature(byte[] data, String signature, String publicKey) {
    try {
      byte[] publicKeyBytes = Hex.decode(publicKey);
      byte[] signatureBytes = Hex.decode(signature);

      Ed25519PublicKeyParameters ed25519PublicKeyParameters = new Ed25519PublicKeyParameters(publicKeyBytes, 0);
      Ed25519Signer ed25519Signer = new Ed25519Signer();

      ed25519Signer.init(false, ed25519PublicKeyParameters);
      ed25519Signer.update(data, 0, data.length);

      return ed25519Signer.verifySignature(signatureBytes);
    } catch (Exception e) {
      return false;
    }
  }
}