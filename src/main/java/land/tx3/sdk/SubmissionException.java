package land.tx3.sdk;

import java.util.Objects;

/** Reports a submit-hash mismatch or server rejection. */
public final class SubmissionException extends Tx3Exception {
  private final String transactionHashHex;

  /** Creates a submission error with the public transaction hash as stable context. */
  public SubmissionException(String transactionHashHex, String message) {
    super(message);
    this.transactionHashHex = Objects.requireNonNull(transactionHashHex, "transactionHashHex");
  }

  /** Returns the submitted transaction hash. */
  public String transactionHashHex() {
    return transactionHashHex;
  }
}
