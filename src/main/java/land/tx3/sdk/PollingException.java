package land.tx3.sdk;

import java.util.Objects;

/** Reports terminal-stage failure or timeout while polling a submitted transaction. */
public final class PollingException extends Tx3Exception {
  /** Stable polling failure categories. */
  public enum Kind {
    TERMINAL_STAGE,
    TIMEOUT
  }

  private final Kind kind;
  private final String transactionHashHex;
  private final String targetStage;

  /** Creates a terminal-stage polling error. */
  public PollingException(String transactionHashHex, String targetStage, String message) {
    this(Kind.TERMINAL_STAGE, transactionHashHex, targetStage, message);
  }

  /** Creates a polling error with stable transaction and target-stage context. */
  public PollingException(
      Kind kind, String transactionHashHex, String targetStage, String message) {
    super(message);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.transactionHashHex = Objects.requireNonNull(transactionHashHex, "transactionHashHex");
    this.targetStage = Objects.requireNonNull(targetStage, "targetStage");
  }

  /** Returns whether polling failed at a terminal stage or exhausted its attempts. */
  public Kind kind() {
    return kind;
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
