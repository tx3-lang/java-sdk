package land.tx3.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Collects and resolves one transaction invocation. */
public final class TxBuilder {
  private final String transaction;
  private final TirEnvelope tir;
  private final TrpClient trp;
  private final Map<String, ParamType> parameters;
  private final Set<String> requiredParameters;
  private final Map<String, Object> environment;
  private final Map<String, Party> parties;
  private final LinkedHashMap<String, ArgValue> taggedArguments = new LinkedHashMap<>();

  TxBuilder() {
    this("", null, null, Map.of(), Set.of(), Map.of(), Map.of());
  }

  TxBuilder(Map<String, ParamType> parameters) {
    this("", null, null, parameters, Set.of(), Map.of(), Map.of());
  }

  TxBuilder(
      String transaction,
      TirEnvelope tir,
      TrpClient trp,
      Map<String, ParamType> parameters,
      Set<String> requiredParameters,
      Map<String, Object> environment,
      Map<String, Party> parties) {
    this.transaction = Objects.requireNonNull(transaction, "transaction");
    this.tir = tir;
    this.trp = trp;
    this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    this.requiredParameters = Collections.unmodifiableSet(new LinkedHashSet<>(requiredParameters));
    this.environment = Collections.unmodifiableMap(new LinkedHashMap<>(environment));
    this.parties = Collections.unmodifiableMap(new LinkedHashMap<>(parties));
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

  /**
   * Resolves this invocation through the configured TRP client.
   *
   * <p>Profile environment and party addresses are applied first. Explicit transaction arguments
   * win over injected party addresses, and every dynamic argument follows its resolved parameter
   * type. Values supplied through {@link #argTagged} are sent without another encoding pass.
   *
   * @throws ResolutionException if a required transaction parameter has no explicit or injected
   *     value
   */
  public CompletableFuture<ResolveResponse> resolve() {
    if (trp == null || tir == null) {
      throw new ValidationException("txBuilder", "this transaction builder cannot resolve");
    }

    var merged = new LinkedHashMap<String, Object>();
    parties.forEach((name, party) -> merged.put(normalize(name), party.address().value()));
    taggedArguments.forEach((name, value) -> merged.put(normalize(name), value));

    for (var required : requiredParameters) {
      if (!merged.containsKey(normalize(required))) {
        throw new ResolutionException(
            transaction, required, "missing required transaction argument: " + required);
      }
    }

    var env = environment.isEmpty() ? null : new LinkedHashMap<String, Object>(environment);
    return trp.resolve(new ResolveParams(tir, merged, env));
  }

  private static String normalize(String name) {
    return name.toLowerCase(java.util.Locale.ROOT);
  }
}
