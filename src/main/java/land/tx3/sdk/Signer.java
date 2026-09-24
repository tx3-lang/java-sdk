package land.tx3.sdk;

/** A user-extensible synchronous signer that produces a witness for a Tx3 transaction. */
public interface Signer {
  /** Returns the chain address controlled by this signer. */
  Address address();

  /**
   * Signs the transaction described by {@code request}.
   *
   * @throws SigningException if the request cannot be signed
   */
  Witness sign(SignRequest request);
}
