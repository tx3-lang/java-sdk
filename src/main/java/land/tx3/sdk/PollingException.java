package land.tx3.sdk;

import java.util.Objects;

/** Reports terminal-stage failure or timeout while polling a submitted transaction. */
public final class PollingException extends Tx3Exception {
  private final String transactionHashHex;
  private final String targetStage;

  /** Creates a polling error with stable transaction and target-stage context. */
  public PollingException(String transactionHashHex, String targetStage, String message) {
    super(message);
    this.transactionHashHex = Objects.requireNonNull(transactionHashHex, "transactionHashHex");
    this.targetStage = Objects.requireNonNull(targetStage, "targetStage");
  }

  /** Returns the transaction hash being polled. */
  public String transactionHashHex() {
    return transactionHashHex;
  }

  /** Returns the requested terminal stage. */
  public String targetStage() {
    return targetStage;
  }
}
