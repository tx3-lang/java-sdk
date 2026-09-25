package land.tx3.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** An immutable, introspectable TII protocol document. */
public final class Protocol {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final JsonNode json;
  private final String tiiVersion;
  private final ProtocolInfo info;
  private final Map<String, Transaction> transactions;
  private final Map<String, JsonNode> parties;
  private final Map<String, Profile> profiles;
  private final JsonNode environment;
  private final Map<String, ParamType> environmentParameters;
  private final Map<String, JsonNode> componentSchemas;

  private Protocol(JsonNode source, String sourceName) {
    json = requireObject(source, sourceName).deepCopy();
    tiiVersion = requiredText(requiredObject(json, "tii", sourceName), "version", sourceName);
    info = readInfo(requiredObject(json, "protocol", sourceName), sourceName);
    componentSchemas = readComponents(json.path("components"));
    transactions = readTransactions(requiredObject(json, "transactions", sourceName), sourceName);
    parties = immutableNodes(optionalObject(json, "parties", sourceName));
    profiles = readProfiles(optionalObject(json, "profiles", sourceName), sourceName);
    environment = optionalObject(json, "environment", sourceName).deepCopy();
    environmentParameters = readParameters(environment);
  }

  /**
   * Loads a UTF-8 TII file.
   *
   * @throws ProtocolException with kind {@link ProtocolException.Kind#READ} when the file cannot be
   *     read, or a parsing/validation kind when its contents are invalid
   */
  public static Protocol fromFile(Path path) {
    Objects.requireNonNull(path, "path");
    try {
      return new Protocol(JSON.readTree(path.toFile()), path.toString());
    } catch (JsonProcessingException exception) {
      throw invalidJson(path.toString(), exception);
    } catch (IOException exception) {
      throw new ProtocolException(
          ProtocolException.Kind.READ, path.toString(), "Failed to read TII file", exception);
    }
  }

  /**
   * Loads a TII document from JSON text.
   *
   * @throws ProtocolException when the text is malformed or the TII structure is invalid
   */
  public static Protocol fromJson(String value) {
    Objects.requireNonNull(value, "value");
    try {
      return new Protocol(JSON.readTree(value), "JSON string");
    } catch (JsonProcessingException exception) {
      throw invalidJson("JSON string", exception);
    }
  }

  /**
   * Loads a TII document from UTF-8 JSON bytes.
   *
   * @throws ProtocolException when the bytes are malformed or the TII structure is invalid
   */
  public static Protocol fromJson(byte[] value) {
    Objects.requireNonNull(value, "value");
    try {
      return new Protocol(JSON.readTree(value), "JSON bytes");
    } catch (JsonProcessingException exception) {
      throw invalidJson("JSON bytes", exception);
    } catch (IOException exception) {
      throw invalidJson("JSON bytes", exception);
    }
  }

  /**
   * Loads a TII document from an already parsed JSON tree. The input is defensively copied.
   *
   * @throws ProtocolException when the TII structure is invalid
   */
  public static Protocol fromJson(JsonNode value) {
    return new Protocol(Objects.requireNonNull(value, "value"), "JSON tree");
  }

  /** Returns the TII schema version. */
  public String tiiVersion() {
    return tiiVersion;
  }

  /** Returns descriptive protocol metadata. */
  public ProtocolInfo info() {
    return info;
  }

  /** Returns transaction declarations in document order. */
  public Map<String, Transaction> transactions() {
    return transactions;
  }

  /** Returns declared parties and their raw declarations in document order. */
  public Map<String, JsonNode> parties() {
    return Collections.unmodifiableMap(copyNodes(parties));
  }

  /** Returns named profiles in document order. */
  public Map<String, Profile> profiles() {
    return profiles;
  }

  /** Returns a defensive copy of the protocol environment schema. */
  public JsonNode environment() {
    return environment.deepCopy();
  }

  /** Returns interpreted protocol environment parameter types. */
  public Map<String, ParamType> environmentParameters() {
    return environmentParameters;
  }

  /** Returns component schemas in document order. */
  public Map<String, JsonNode> componentSchemas() {
    return Collections.unmodifiableMap(copyNodes(componentSchemas));
  }

  /** Returns a defensive copy of the complete source TII document. */
  public JsonNode json() {
    return json.deepCopy();
  }

  /**
   * Starts configuring the high-level client for this protocol.
   *
   * <p>The returned builder owns deconstructed copies of the protocol state; a built client does
   * not retain this {@code Protocol} instance.
   */
  public Tx3ClientBuilder client() {
    return Tx3ClientBuilder.fromProtocol(this);
  }

  private static ProtocolInfo readInfo(JsonNode value, String sourceName) {
    return new ProtocolInfo(
        requiredText(value, "name", sourceName),
        requiredText(value, "version", sourceName),
        requiredText(value, "scope", sourceName),
        Optional.ofNullable(value.path("description").textValue()));
  }

  private Map<String, Transaction> readTransactions(JsonNode value, String sourceName) {
    var result = new LinkedHashMap<String, Transaction>();
    value
        .fields()
        .forEachRemaining(
            entry -> {
              var tx =
                  requireObject(entry.getValue(), sourceName + " transaction " + entry.getKey());
              var params = requiredObject(tx, "params", sourceName);
              var tir = requiredObject(tx, "tir", sourceName);
              result.put(
                  entry.getKey(),
                  new Transaction(
                      new TirEnvelope(
                          requiredText(tir, "content", sourceName),
                          requiredText(tir, "encoding", sourceName),
                          requiredText(tir, "version", sourceName),
                          tir),
                      params,
                      readParameters(params)));
            });
    return Collections.unmodifiableMap(result);
  }

  private Map<String, ParamType> readParameters(JsonNode schema) {
    var result = new LinkedHashMap<String, ParamType>();
    var properties = schema.path("properties");
    if (properties.isObject()) {
      properties
          .fields()
          .forEachRemaining(
              entry ->
                  result.put(
                      entry.getKey(),
                      ParamType.fromJsonSchema(entry.getValue(), componentSchemas)));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, Profile> readProfiles(JsonNode value, String sourceName) {
    var result = new LinkedHashMap<String, Profile>();
    value
        .fields()
        .forEachRemaining(
            entry -> {
              var profile =
                  requireObject(entry.getValue(), sourceName + " profile " + entry.getKey());
              result.put(
                  entry.getKey(),
                  new Profile(
                      nodes(optionalObject(profile, "environment", sourceName)),
                      nodes(optionalObject(profile, "parties", sourceName))));
            });
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, JsonNode> readComponents(JsonNode components) {
    if (!components.isObject() || !components.path("schemas").isObject()) {
      return Map.of();
    }
    return immutableNodes(components.path("schemas"));
  }

  private static JsonNode requiredObject(JsonNode parent, String field, String sourceName) {
    var value = parent.path(field);
    if (!value.isObject()) {
      throw invalidProtocol(sourceName, "TII field '" + field + "' must be an object");
    }
    return value;
  }

  private static JsonNode optionalObject(JsonNode parent, String field, String sourceName) {
    var value = parent.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return JSON.createObjectNode();
    }
    if (!value.isObject()) {
      throw invalidProtocol(sourceName, "TII field '" + field + "' must be an object");
    }
    return value;
  }

  private static JsonNode requireObject(JsonNode value, String sourceName) {
    if (value == null || !value.isObject()) {
      throw invalidProtocol(sourceName, "TII root must be an object");
    }
    return value;
  }

  private static String requiredText(JsonNode parent, String field, String sourceName) {
    var value = parent.path(field);
    if (!value.isTextual()) {
      throw invalidProtocol(sourceName, "TII field '" + field + "' must be a string");
    }
    return value.textValue();
  }

  private static Map<String, JsonNode> nodes(JsonNode object) {
    var result = new LinkedHashMap<String, JsonNode>();
    object
        .fields()
        .forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().deepCopy()));
    return result;
  }

  private static Map<String, JsonNode> immutableNodes(JsonNode object) {
    return Collections.unmodifiableMap(nodes(object));
  }

  private static Map<String, JsonNode> copyNodes(Map<String, JsonNode> source) {
    var result = new LinkedHashMap<String, JsonNode>();
    source.forEach((key, value) -> result.put(key, value.deepCopy()));
    return result;
  }

  private static ProtocolException invalidJson(String source, Exception cause) {
    return new ProtocolException(
        ProtocolException.Kind.INVALID_JSON, source, "Invalid TII JSON", cause);
  }

  private static ProtocolException invalidProtocol(String source, String message) {
    return new ProtocolException(ProtocolException.Kind.INVALID_PROTOCOL, source, message);
  }
}
