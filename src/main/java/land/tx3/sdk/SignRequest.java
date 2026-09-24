package land.tx3.sdk;

/** The bound transaction hash and full transaction CBOR supplied to a {@link Signer}. */
public record SignRequest(String txHashHex, String txCborHex) {
  /**
   * Creates an immutable signing request containing both hexadecimal envelopes.
   *
   * @param txHashHex hexadecimal bound transaction hash
   * @param txCborHex hexadecimal full transaction CBOR
   * @throws ValidationException if either envelope is null
   */
  public SignRequest {
    if (txHashHex == null) {
      throw new ValidationException("txHashHex", "transaction hash must not be null");
    }
    if (txCborHex == null) {
      throw new ValidationException("txCborHex", "transaction CBOR must not be null");
    }
  }
}
