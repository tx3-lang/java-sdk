package land.tx3.sdk;

import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

/** A raw-key Ed25519 signer backed by the Java 21 Ed25519 provider. */
public final class Ed25519Signer implements Signer {
  private static final byte[] PKCS8_SEED_PREFIX =
      new byte[] {
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04,
        0x20
      };

  private final Address address;
  private final byte[] privateKey;
  private final byte[] publicKey;

  /**
   * Creates a signer from a 32-byte Ed25519 private-key seed and its Cardano address.
   *
   * <p>The key is defensively copied and is never included in errors or logs. Mnemonic input is
   * deliberately unsupported; use {@link CardanoSigner} for CIP-1852 mnemonic derivation.
   *
   * @param privateKey 32-byte Ed25519 private-key seed
   * @param address Cardano address controlled by the key
   * @throws ValidationException if the key is null or not 32 bytes
   * @throws SigningException if the address is malformed or is not controlled by the key
   */
  public Ed25519Signer(byte[] privateKey, Address address) {
    if (address == null) {
      throw new ValidationException("address", "address must not be null");
    }
    if (privateKey == null || privateKey.length != 32) {
      throw new ValidationException("privateKey", "private key must be exactly 32 bytes");
    }
    this.privateKey = privateKey.clone();
    this.address = address;
    try {
      this.publicKey = KeyGenUtil.getPublicKeyFromPrivateKey(this.privateKey).clone();
    } catch (RuntimeException exception) {
      throw new SigningException(address, "Ed25519 public-key derivation failed", exception);
    }
    SignerSupport.verifyAddressBinding(address, this.publicKey);
  }

  @Override
  public Address address() {
    return address;
  }

  /**
   * Signs the request's 32-byte transaction hash with Ed25519.
   *
   * @throws ValidationException if the request is null
   * @throws SigningException if the Java Ed25519 provider cannot sign the hash
   */
  @Override
  public Witness sign(SignRequest request) {
    if (request == null) {
      throw new ValidationException("request", "sign request must not be null");
    }
    byte[] hash = SignerSupport.decodeHex(request.txHashHex(), "txHashHex", 32);
    try {
      Signature signer = Signature.getInstance("Ed25519");
      signer.initSign(toJavaPrivateKey());
      signer.update(hash);
      return new Witness(
          SignerSupport.encodeHex(publicKey),
          SignerSupport.encodeHex(signer.sign()),
          WitnessType.VKEY);
    } catch (GeneralSecurityException exception) {
      throw new SigningException(address, "Ed25519 signing failed", exception);
    } finally {
      Arrays.fill(hash, (byte) 0);
    }
  }

  private PrivateKey toJavaPrivateKey() throws GeneralSecurityException {
    byte[] encoded = new byte[PKCS8_SEED_PREFIX.length + privateKey.length];
    System.arraycopy(PKCS8_SEED_PREFIX, 0, encoded, 0, PKCS8_SEED_PREFIX.length);
    System.arraycopy(privateKey, 0, encoded, PKCS8_SEED_PREFIX.length, privateKey.length);
    try {
      return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    } finally {
      Arrays.fill(encoded, (byte) 0);
    }
  }
}
