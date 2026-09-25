package land.tx3.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Collects native or already-tagged arguments for one transaction invocation. */
public final class TxBuilder {
  private final Map<String, ParamType> parameters;
  private final LinkedHashMap<String, ArgValue> taggedArguments = new LinkedHashMap<>();

  TxBuilder() {
    this(Map.of());
  }

  TxBuilder(Map<String, ParamType> parameters) {
    this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
  }

  /**
   * Encodes and adds one native value using the transaction's resolved parameter type.
   *
   * @throws ArgumentEncodingException if the name is undeclared or the native value does not match
   *     its declared type
   */
  public TxBuilder arg(String name, Object value) {
    Objects.requireNonNull(name, "name");
    var declared =
        parameters.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(
                () ->
                    new ArgumentEncodingException(
                        ArgumentEncodingException.Kind.SHAPE,
                        name,
                        "declared transaction parameter"));
    return argTagged(declared.getKey(), ArgEncoder.encode(declared.getValue(), value));
  }

  /** Encodes and adds native values in iteration order; later case-insensitive writes win. */
  public TxBuilder args(Map<String, ?> values) {
    Objects.requireNonNull(values, "values");
    values.forEach(this::arg);
    return this;
  }

  /** Adds an already canonical tagged value without another schema-directed encoding pass. */
  public TxBuilder argTagged(String name, ArgValue value) {
    if (name == null || name.isBlank()) {
      throw new ValidationException("name", "argument name must not be null or blank");
    }
    taggedArguments.put(name.toLowerCase(java.util.Locale.ROOT), Objects.requireNonNull(value));
    return this;
  }

  Map<String, ArgValue> taggedArguments() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(taggedArguments));
  }
}
