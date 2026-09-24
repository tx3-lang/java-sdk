package land.tx3.sdk;

/** Chain location where a transaction was first confirmed. */
public record ChainPoint(long slot, String blockHash) {
  /** Validates the chain point. */
  public ChainPoint {
    if (slot < 0 || blockHash == null) {
      throw new ValidationException(
          "chainPoint", "slot must be non-negative and blockHash present");
    }
  }
}
