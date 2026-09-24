package land.tx3.sdk;

import java.util.Objects;

/** Reports a missing parameter or argument coercion failure during transaction resolution. */
public final class ResolutionException extends Tx3Exception {
  private final String transaction;
  private final String parameter;

  /** Creates a resolution error with stable transaction and parameter context. */
  public ResolutionException(String transaction, String parameter, String message) {
    super(message);
    this.transaction = Objects.requireNonNull(transaction, "transaction");
    this.parameter = Objects.requireNonNull(parameter, "parameter");
  }

  /** Returns the transaction being resolved. */
  public String transaction() {
    return transaction;
  }

  /** Returns the parameter whose value could not be supplied or coerced. */
  public String parameter() {
    return parameter;
  }
}
