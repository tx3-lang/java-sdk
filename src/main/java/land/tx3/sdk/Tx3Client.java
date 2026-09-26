package land.tx3.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable high-level client for resolving transactions from a deconstructed Tx3 protocol.
 *
 * <p>Instances are created only by {@link Tx3ClientBuilder#build()}. Profile selection is fixed at
 * build time; late party binding returns a new client.
 */
public final class Tx3Client {
  private final Map<String, TirEnvelope> transactions;
  private final Map<String, Map<String, ParamType>> transactionParameters;
  private final Map<String, Set<String>> requiredParameters;
  private final Set<String> knownParties;
  private final TrpClient trp;
  private final Map<String, Party> parties;
  private final Map<String, Object> environment;
  private final PollingRuntime polling;

  Tx3Client(
      Map<String, TirEnvelope> transactions,
      Map<String, Map<String, ParamType>> transactionParameters,
      Map<String, Set<String>> requiredParameters,
      Set<String> knownParties,
      TrpClient trp,
      Map<String, Party> parties,
      Map<String, Object> environment,
      PollingRuntime polling) {
    this.transactions = Collections.unmodifiableMap(new LinkedHashMap<>(transactions));
    this.transactionParameters = transactionParameters;
    this.requiredParameters = requiredParameters;
    this.knownParties = Collections.unmodifiableSet(new LinkedHashSet<>(knownParties));
    this.trp = Objects.requireNonNull(trp, "trp");
    this.parties = Collections.unmodifiableMap(new LinkedHashMap<>(parties));
    this.environment = Collections.unmodifiableMap(new LinkedHashMap<>(environment));
    this.polling = Objects.requireNonNull(polling, "polling");
  }

  /**
   * Starts a declared transaction invocation.
   *
   * @throws UnknownTransactionException if the name is not declared
   */
  public TxBuilder tx(String name) {
    Objects.requireNonNull(name, "name");
    var found =
        transactions.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new UnknownTransactionException(name));
    var canonicalName = found.getKey();
    return new TxBuilder(
        canonicalName,
        found.getValue(),
        trp,
        transactionParameters.getOrDefault(canonicalName, Map.of()),
        requiredParameters.getOrDefault(canonicalName, Set.of()),
        environment,
        parties,
        polling);
  }

  /**
   * Late-binds a declared party and returns a new client.
   *
   * @throws UnknownPartyException if the name is not declared
   */
  public Tx3Client withParty(String name, Party party) {
    var normalized = normalize(name);
    if (!knownParties.contains(normalized)) throw new UnknownPartyException(normalized);
    return withPartyValue(normalized, party);
  }

  /** Late-binds several declared parties in iteration order; later writes win. */
  public Tx3Client withParties(Map<String, Party> values) {
    var updated = this;
    for (var entry : Objects.requireNonNull(values, "values").entrySet()) {
      updated = updated.withParty(entry.getKey(), entry.getValue());
    }
    return updated;
  }

  /**
   * Late-binds a party without declared-name validation.
   *
   * <p>This is intended for generated wrappers with names fixed by generated methods.
   */
  public Tx3Client withPartyUnchecked(String name, Party party) {
    return withPartyValue(normalize(name), party);
  }

  private Tx3Client withPartyValue(String name, Party party) {
    var updated = new LinkedHashMap<>(parties);
    updated.put(name, Objects.requireNonNull(party, "party"));
    return new Tx3Client(
        transactions,
        transactionParameters,
        requiredParameters,
        knownParties,
        trp,
        updated,
        environment,
        polling);
  }

  private static String normalize(String name) {
    return Objects.requireNonNull(name, "name").toLowerCase(java.util.Locale.ROOT);
  }
}
