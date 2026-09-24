package land.tx3.sdk;

import java.util.Objects;

/** Reports a transaction name that is not declared by the loaded protocol. */
public final class UnknownTransactionException extends Tx3Exception {
  private final String transaction;

  /** Creates an unknown-transaction error. */
  public UnknownTransactionException(String transaction) {
    super("unknown transaction: " + transaction);
    this.transaction = Objects.requireNonNull(transaction, "transaction");
  }

  /** Returns the requested transaction name. */
  public String transaction() {
    return transaction;
  }
}
