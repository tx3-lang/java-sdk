package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stable discriminator for a transaction witness. */
public enum WitnessType {
  /** A verification-key witness. */
  VKEY;

  /** Returns the TRP wire spelling. */
  @JsonValue
  public String wireValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  /** Decodes the TRP wire spelling. */
  @JsonCreator
  public static WitnessType fromWireValue(String value) {
    if ("vkey".equals(value)) {
      return VKEY;
    }
    throw new IllegalArgumentException("unknown witness type: " + value);
  }
}
