package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** Chain location where a transaction was first confirmed. */
public record ChainPoint(
    @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) long slot, String blockHash) {
  /** Validates the chain point. */
  public ChainPoint {
    if (slot < 0 || blockHash == null) {
      throw new ValidationException(
          "chainPoint", "slot must be non-negative and blockHash present");
    }
  }
}
