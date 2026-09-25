package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** Current TRP lifecycle status for one transaction. */
public record TxStatus(
    TxStage stage,
    @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) long confirmations,
    @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) long nonConfirmations,
    @JsonInclude(JsonInclude.Include.NON_NULL) ChainPoint confirmedAt) {
  /** Validates the status fields. */
  public TxStatus {
    if (stage == null || confirmations < 0 || nonConfirmations < 0) {
      throw new ValidationException("txStatus", "stage and non-negative counters are required");
    }
  }
}
