package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Verification key and signature envelopes submitted with a transaction. */
public record TxWitness(
    BytesEnvelope key, BytesEnvelope signature, @JsonProperty("type") WitnessType type) {
  /** Validates required witness fields. */
  public TxWitness {
    if (key == null || signature == null || type == null) {
      throw new ValidationException("witness", "witness fields must not be null");
    }
  }
}
