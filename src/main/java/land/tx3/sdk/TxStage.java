package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Transaction lifecycle stage reported by TRP. */
public enum TxStage {
  PENDING("pending"),
  PROPAGATED("propagated"),
  ACKNOWLEDGED("acknowledged"),
  CONFIRMED("confirmed"),
  FINALIZED("finalized"),
  DROPPED("dropped"),
  ROLLED_BACK("rolled_back"),
  UNKNOWN("unknown");

  private final String wireValue;

  TxStage(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the pinned TRP wire spelling. */
  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  /** Decodes the pinned TRP wire spelling. */
  @JsonCreator
  public static TxStage fromWireValue(String value) {
    return Arrays.stream(values())
        .filter(stage -> stage.wireValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown transaction stage: " + value));
  }
}
