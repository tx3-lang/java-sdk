package land.tx3.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Fluent builder for the single high-level {@link Tx3Client} facade.
 *
 * <p>Use {@link Protocol#client()} for dynamically loaded TII or {@link #fromParts} for generated
 * clients. Name validation is intentionally deferred until {@link #build()}.
 */
public final class Tx3ClientBuilder {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final Map<String, TirEnvelope> transactions;
  private final Map<String, Map<String, ParamType>> transactionParameters;
  private final Map<String, Set<String>> requiredParameters;
  private final Map<String, Profile> profiles;
  private final Set<String> knownParties;
  private final Map<String, Party> parties = new LinkedHashMap<>();
  private final Set<String> uncheckedPartyNames = new LinkedHashSet<>();
  private final Map<String, String> headers = new LinkedHashMap<>();
  private final Map<String, Object> environmentOverrides = new LinkedHashMap<>();

  private ClientOptions trpOptions;
  private String profile;
  private Transport transport;
  private PollingRuntime polling = PollingRuntime.system();

  private Tx3ClientBuilder(
      Map<String, TirEnvelope> transactions,
      Map<String, Map<String, ParamType>> transactionParameters,
      Map<String, Set<String>> requiredParameters,
      Map<String, Profile> profiles,
      Set<String> knownParties) {
    this.transactions = immutableMap(transactions, "transactions");
    this.transactionParameters = immutableNestedMap(transactionParameters);
    this.requiredParameters = immutableNestedSet(requiredParameters);
    this.profiles = immutableMap(profiles, "profiles");
    this.knownParties = Collections.unmodifiableSet(new LinkedHashSet<>(knownParties));
  }

  /**
   * Seeds a builder with code-generated runtime fragments.
   *
   * <p>Generated clients supply already-tagged values through {@link TxBuilder#argTagged}; this
   * path intentionally carries no raw TII, parameter schemas, or {@link ParamType} models.
   */
  public static Tx3ClientBuilder fromParts(
      Map<String, TirEnvelope> transactions,
      Map<String, Profile> profiles,
      Iterable<String> knownParties) {
    Objects.requireNonNull(knownParties, "knownParties");
    var normalizedParties = new LinkedHashSet<String>();
    knownParties.forEach(name -> normalizedParties.add(normalize(name)));
    return new Tx3ClientBuilder(transactions, Map.of(), Map.of(), profiles, normalizedParties);
  }

  static Tx3ClientBuilder fromProtocol(Protocol protocol) {
    var tirs = new LinkedHashMap<String, TirEnvelope>();
    var parameters = new LinkedHashMap<String, Map<String, ParamType>>();
    var required = new LinkedHashMap<String, Set<String>>();
    protocol
        .transactions()
        .forEach(
            (name, transaction) -> {
              tirs.put(name, transaction.tir());
              parameters.put(name, transaction.parameters());
              var requiredForTransaction = new LinkedHashSet<String>();
              var requiredNode = transaction.paramsSchema().path("required");
              if (requiredNode.isArray()) {
                requiredNode.forEach(
                    value -> {
                      if (value.isTextual()) requiredForTransaction.add(value.textValue());
                    });
              }
              required.put(name, requiredForTransaction);
            });
    var partyNames = new LinkedHashSet<String>();
    protocol.parties().keySet().forEach(name -> partyNames.add(normalize(name)));
    return new Tx3ClientBuilder(tirs, parameters, required, protocol.profiles(), partyNames);
  }

  /** Sets complete TRP connection options. */
  public Tx3ClientBuilder trp(ClientOptions options) {
    trpOptions = Objects.requireNonNull(options, "options");
    return this;
  }

  /** Sets the TRP endpoint with default connection options. */
  public Tx3ClientBuilder trpEndpoint(URI endpoint) {
    trpOptions = ClientOptions.forEndpoint(endpoint);
    return this;
  }

  /** Adds or replaces one request header; the last write wins. */
  public Tx3ClientBuilder withHeader(String key, String value) {
    headers.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    return this;
  }

  /** Selects a protocol profile. The name is validated by {@link #build()}. */
  public Tx3ClientBuilder withProfile(String name) {
    profile = name;
    return this;
  }

  /** Binds a declared party. The name is validated by {@link #build()}. */
  public Tx3ClientBuilder withParty(String name, Party party) {
    var normalized = normalize(name);
    parties.put(normalized, Objects.requireNonNull(party, "party"));
    uncheckedPartyNames.remove(normalized);
    return this;
  }

  /** Binds several declared parties in iteration order; later writes win. */
  public Tx3ClientBuilder withParties(Map<String, Party> values) {
    Objects.requireNonNull(values, "values").forEach(this::withParty);
    return this;
  }

  /**
   * Binds a party without a declared-name lookup.
   *
   * <p>This is the generated-client seam; hand-written dynamic clients should use {@link
   * #withParty}.
   */
  public Tx3ClientBuilder withPartyUnchecked(String name, Party party) {
    var normalized = normalize(name);
    parties.put(normalized, Objects.requireNonNull(party, "party"));
    uncheckedPartyNames.add(normalized);
    return this;
  }

  /** Sets one environment override; explicit values win over the selected profile. */
  public Tx3ClientBuilder withEnvValue(String key, Object value) {
    environmentOverrides.put(Objects.requireNonNull(key, "key"), value);
    return this;
  }

  Tx3ClientBuilder transport(Transport value) {
    transport = Objects.requireNonNull(value, "transport");
    return this;
  }

  Tx3ClientBuilder polling(ScheduledExecutorService scheduler, Clock clock) {
    polling = new PollingRuntime(scheduler, clock);
    return this;
  }

  /**
   * Validates configuration and creates the immutable high-level client.
   *
   * @throws MissingTrpEndpointException if no TRP options were supplied
   * @throws UnknownProfileException if the selected profile is undeclared
   * @throws UnknownPartyException if a checked party name is undeclared
   */
  public Tx3Client build() {
    if (trpOptions == null || trpOptions.endpoint() == null) {
      throw new MissingTrpEndpointException();
    }
    var selectedProfile = profile == null ? null : profiles.get(profile);
    if (profile != null && selectedProfile == null) {
      throw new UnknownProfileException(profile);
    }
    for (var name : parties.keySet()) {
      if (!uncheckedPartyNames.contains(name) && !knownParties.contains(name)) {
        throw new UnknownPartyException(name);
      }
    }

    var mergedHeaders = new LinkedHashMap<>(trpOptions.headers());
    mergedHeaders.putAll(headers);
    var options = new ClientOptions(trpOptions.endpoint(), mergedHeaders, trpOptions.timeout());
    var trpClient = transport == null ? new TrpClient(options) : new TrpClient(options, transport);

    var boundParties = new LinkedHashMap<String, Party>();
    if (selectedProfile != null) {
      selectedProfile
          .parties()
          .forEach(
              (name, value) ->
                  boundParties.put(normalize(name), Party.address(profileAddress(name, value))));
    }
    boundParties.putAll(parties);

    var environment = new LinkedHashMap<String, Object>();
    if (selectedProfile != null) {
      selectedProfile
          .environment()
          .forEach((name, value) -> environment.put(name, jsonValue(value)));
    }
    environment.putAll(environmentOverrides);

    return new Tx3Client(
        transactions,
        transactionParameters,
        requiredParameters,
        knownParties,
        trpClient,
        boundParties,
        environment,
        polling);
  }

  private static Address profileAddress(String name, JsonNode value) {
    if (!value.isTextual()) {
      throw new ValidationException(
          "profile.parties." + name, "profile party address must be a string");
    }
    return new Address(value.textValue());
  }

  private static Object jsonValue(JsonNode value) {
    return JSON.convertValue(value, Object.class);
  }

  private static String normalize(String name) {
    return Objects.requireNonNull(name, "name").toLowerCase(java.util.Locale.ROOT);
  }

  private static <K, V> Map<K, V> immutableMap(Map<K, V> source, String name) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, name)));
  }

  private static Map<String, Map<String, ParamType>> immutableNestedMap(
      Map<String, Map<String, ParamType>> source) {
    var result = new LinkedHashMap<String, Map<String, ParamType>>();
    source.forEach(
        (name, values) ->
            result.put(name, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, Set<String>> immutableNestedSet(Map<String, Set<String>> source) {
    var result = new LinkedHashMap<String, Set<String>>();
    source.forEach(
        (name, values) ->
            result.put(name, Collections.unmodifiableSet(new LinkedHashSet<>(values))));
    return Collections.unmodifiableMap(result);
  }
}
