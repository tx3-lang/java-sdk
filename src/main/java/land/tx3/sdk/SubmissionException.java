package land.tx3.sdk;

import java.util.Objects;

/** Reports a submit-hash mismatch or server rejection. */
public final class SubmissionException extends Tx3Exception {
  private final String transactionHashHex;
  private final String receivedHashHex;

  /** Creates a submission error with the public transaction hash as stable context. */
  public SubmissionException(String transactionHashHex, String message) {
    this(transactionHashHex, null, message);
  }

  /** Creates a submit-hash mismatch retaining both public hashes. */
  public SubmissionException(String transactionHashHex, String receivedHashHex, String message) {
    super(message);
    this.transactionHashHex = Objects.requireNonNull(transactionHashHex, "transactionHashHex");
    this.receivedHashHex = receivedHashHex;
  }

  /** Returns the submitted transaction hash. */
  public String transactionHashHex() {
    return transactionHashHex;
  }

  /** Returns the hash returned by TRP, or {@code null} for failures without a response hash. */
  public String receivedHashHex() {
    return receivedHashHex;
  }
}
