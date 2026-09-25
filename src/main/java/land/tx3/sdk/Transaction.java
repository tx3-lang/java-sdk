package land.tx3.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A TII transaction declaration with its TIR envelope and interpreted parameter metadata. */
public final class Transaction {
  private final TirEnvelope tir;
  private final JsonNode paramsSchema;
  private final Map<String, ParamType> parameters;

  Transaction(TirEnvelope tir, JsonNode paramsSchema, Map<String, ParamType> parameters) {
    this.tir = Objects.requireNonNull(tir, "tir");
    this.paramsSchema = Objects.requireNonNull(paramsSchema, "paramsSchema").deepCopy();
    this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
  }

  /** Returns the transaction's encoded TIR envelope. */
  public TirEnvelope tir() {
    return tir;
  }

  /** Returns a defensive copy of the transaction's parameter JSON Schema. */
  public JsonNode paramsSchema() {
    return paramsSchema.deepCopy();
  }

  /** Returns parameter types in the source schema's property order. */
  public Map<String, ParamType> parameters() {
    return parameters;
  }

  /** Alias for {@link #parameters()} using the TII field's terminology. */
  public Map<String, ParamType> params() {
    return parameters;
  }
}
