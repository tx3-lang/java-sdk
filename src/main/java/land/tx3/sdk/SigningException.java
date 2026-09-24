package land.tx3.sdk;

import java.util.Objects;

/** Reports invalid keys, hash mismatches, or signer/address binding failures. */
public final class SigningException extends Tx3Exception {
  private final Address signerAddress;

  /** Creates a signing error for the signer address. */
  public SigningException(Address signerAddress, String message) {
    super(message);
    this.signerAddress = Objects.requireNonNull(signerAddress, "signerAddress");
  }

  /** Creates a signing error retaining its underlying cause. */
  public SigningException(Address signerAddress, String message, Throwable cause) {
    super(message, cause);
    this.signerAddress = Objects.requireNonNull(signerAddress, "signerAddress");
  }

  /** Returns the non-secret signer address associated with the failure. */
  public Address signerAddress() {
    return signerAddress;
  }
}
