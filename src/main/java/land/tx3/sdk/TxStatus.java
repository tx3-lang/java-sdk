package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Current TRP lifecycle status for one transaction. */
public record TxStatus(
    TxStage stage,
    long confirmations,
    long nonConfirmations,
    @JsonInclude(JsonInclude.Include.NON_NULL) ChainPoint confirmedAt) {
  /** Validates the status fields. */
  public TxStatus {
    if (stage == null || confirmations < 0 || nonConfirmations < 0) {
      throw new ValidationException("txStatus", "stage and non-negative counters are required");
    }
  }
}
