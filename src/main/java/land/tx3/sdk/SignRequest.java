package land.tx3.sdk;

/** The bound transaction hash and full transaction CBOR supplied to a {@link Signer}. */
public record SignRequest(String txHashHex, String txCborHex) {
  /**
   * Creates an immutable signing request containing both hexadecimal envelopes.
   *
   * @param txHashHex hexadecimal bound transaction hash
   * @param txCborHex hexadecimal full transaction CBOR
   * @throws ValidationException if the hash is not exactly 32 bytes of hexadecimal or the CBOR is
   *     empty or malformed hexadecimal
   */
  public SignRequest {
    SignerSupport.decodeHex(txHashHex, "txHashHex", 32);
    SignerSupport.decodeHex(txCborHex, "txCborHex", -1);
  }
}
