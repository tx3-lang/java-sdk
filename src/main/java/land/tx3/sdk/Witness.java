package land.tx3.sdk;

/** A signature payload containing hexadecimal public-key and signature envelopes. */
public record Witness(String publicKeyHex, String signatureHex, WitnessType type) {
  /**
   * Creates an immutable witness.
   *
   * @param publicKeyHex hexadecimal public-key envelope
   * @param signatureHex hexadecimal signature envelope
   * @param type stable witness discriminator
   * @throws ValidationException if any component is null
   */
  public Witness {
    if (publicKeyHex == null) {
      throw new ValidationException("publicKeyHex", "public key must not be null");
    }
    if (signatureHex == null) {
      throw new ValidationException("signatureHex", "signature must not be null");
    }
    if (type == null) {
      throw new ValidationException("type", "witness type must not be null");
    }
  }
}
