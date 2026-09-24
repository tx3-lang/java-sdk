package land.tx3.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/**
 * A parameter type interpreted from a TII JSON Schema node.
 *
 * <p>Interpretation is total: unsupported shapes produce {@link Unknown} and retain their source
 * schema. Component references produce {@link NamedReference}; recursive references stop at an
 * unresolved named-reference leaf rather than recursing forever.
 */
public sealed interface ParamType
    permits ParamType.Unit,
        ParamType.Boolean,
        ParamType.Integer,
        ParamType.Bytes,
        ParamType.String,
        ParamType.Address,
        ParamType.UtxoRef,
        ParamType.Utxo,
        ParamType.AnyAsset,
        ParamType.List,
        ParamType.Tuple,
        ParamType.Map,
        ParamType.Record,
        ParamType.Variant,
        ParamType.NamedReference,
        ParamType.Unknown {

  /** The JSON Schema null type. */
  record Unit() implements ParamType {}

  /** A boolean value. */
  record Boolean() implements ParamType {}

  /** An arbitrary-precision integer. */
  record Integer() implements ParamType {}

  /** A byte string. */
  record Bytes() implements ParamType {}

  /** An explicitly referenced string scalar. Bare JSON Schema strings remain unknown. */
  record String() implements ParamType {}

  /** A Cardano address. */
  record Address() implements ParamType {}

  /** A transaction-output reference. */
  record UtxoRef() implements ParamType {}

  /** A resolved transaction output. */
  record Utxo() implements ParamType {}

  /** An asset whose policy and name are supplied at runtime. */
  record AnyAsset() implements ParamType {}

  /** A homogeneous sequence. */
  record List(ParamType element) implements ParamType {
    public List {
      Objects.requireNonNull(element, "element");
    }
  }

  /** A fixed-length positional sequence. */
  record Tuple(java.util.List<ParamType> elements) implements ParamType {
    public Tuple {
      elements = java.util.List.copyOf(elements);
    }
  }

  /** A string-keyed map with homogeneous values. */
  record Map(ParamType value) implements ParamType {
    public Map {
      Objects.requireNonNull(value, "value");
    }
  }

  /** A field in a record, retained in declared order. */
  record Field(java.lang.String name, ParamType type) {
    public Field {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
    }
  }

  /** A record whose fields follow the schema's required-array order. */
  record Record(java.util.List<Field> fields) implements ParamType {
    public Record {
      fields = java.util.List.copyOf(fields);
    }

    /** Returns a field by name without discarding the record's positional ordering. */
    public Optional<ParamType> field(java.lang.String name) {
      return fields.stream()
          .filter(field -> field.name().equals(name))
          .map(Field::type)
          .findFirst();
    }
  }

  /** One externally tagged variant case. */
  record Case(java.lang.String name, ParamType fields) {
    public Case {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(fields, "fields");
    }
  }

  /** An externally tagged union whose cases follow oneOf order. */
  record Variant(java.util.List<Case> cases) implements ParamType {
    public Variant {
      cases = java.util.List.copyOf(cases);
    }
  }

  /**
   * A reference into {@code components.schemas}.
   *
   * <p>{@code target} is empty only when resolving the reference would form a recursion cycle or
   * when the named component is absent.
   */
  record NamedReference(java.lang.String name, Optional<ParamType> target) implements ParamType {
    public NamedReference {
      Objects.requireNonNull(name, "name");
      target = Objects.requireNonNull(target, "target");
    }
  }

  /** An unsupported shape retaining the original schema. */
  record Unknown(JsonNode schema) implements ParamType {
    public Unknown {
      schema = Objects.requireNonNull(schema, "schema").deepCopy();
    }

    @Override
    public JsonNode schema() {
      return schema.deepCopy();
    }
  }

  /**
   * Interprets one JSON Schema node, resolving named types through {@code components.schemas}. This
   * method never throws for an unsupported schema shape.
   */
  static ParamType fromJsonSchema(
      JsonNode schema, java.util.Map<java.lang.String, JsonNode> components) {
    var safeSchema =
        schema == null ? com.fasterxml.jackson.databind.node.NullNode.instance : schema;
    var safeComponents =
        components == null ? java.util.Map.<java.lang.String, JsonNode>of() : components;
    return interpret(safeSchema, safeComponents, new HashSet<>());
  }

  private static ParamType interpret(
      JsonNode schema,
      java.util.Map<java.lang.String, JsonNode> components,
      HashSet<java.lang.String> resolving) {
    if (!schema.isObject()) {
      return new Unknown(schema);
    }

    var reference = schema.path("$ref");
    if (reference.isTextual()) {
      var value = reference.textValue();
      var prefix = "#/components/schemas/";
      if (value.startsWith(prefix)) {
        var name = value.substring(prefix.length());
        var target = components.get(name);
        if (target == null || !resolving.add(name)) {
          return new NamedReference(name, Optional.empty());
        }
        try {
          return new NamedReference(name, Optional.of(interpret(target, components, resolving)));
        } finally {
          resolving.remove(name);
        }
      }
      var separator = Math.max(value.lastIndexOf('#'), value.lastIndexOf('/'));
      var name = value.substring(separator + 1);
      return switch (name) {
        case "Bytes" -> new Bytes();
        case "String" -> new String();
        case "Address" -> new Address();
        case "UtxoRef" -> new UtxoRef();
        case "Utxo" -> new Utxo();
        case "AnyAsset" -> new AnyAsset();
        default -> new Unknown(schema);
      };
    }

    var oneOf = schema.path("oneOf");
    if (oneOf.isArray()) {
      var cases = new ArrayList<Case>(oneOf.size());
      oneOf.forEach(branch -> cases.add(variantCase(branch, components, resolving)));
      return new Variant(cases);
    }

    return switch (schema.path("type").asText("")) {
      case "null" -> new Unit();
      case "boolean" -> new Boolean();
      case "integer" -> new Integer();
      case "array" -> array(schema, components, resolving);
      case "object" -> object(schema, components, resolving);
      default -> new Unknown(schema);
    };
  }

  private static ParamType array(
      JsonNode schema,
      java.util.Map<java.lang.String, JsonNode> components,
      HashSet<java.lang.String> resolving) {
    var prefixItems = schema.path("prefixItems");
    if (prefixItems.isArray()) {
      var elements = new ArrayList<ParamType>(prefixItems.size());
      prefixItems.forEach(item -> elements.add(interpret(item, components, resolving)));
      return new Tuple(elements);
    }
    var items = schema.path("items");
    return items.isObject()
        ? new List(interpret(items, components, resolving))
        : new Unknown(schema);
  }

  private static ParamType object(
      JsonNode schema,
      java.util.Map<java.lang.String, JsonNode> components,
      HashSet<java.lang.String> resolving) {
    var additional = schema.path("additionalProperties");
    if (additional.isObject()) {
      return new Map(interpret(additional, components, resolving));
    }
    var properties = schema.path("properties");
    return properties.isObject()
        ? new Record(recordFields(schema, properties, components, resolving))
        : new Unknown(schema);
  }

  private static java.util.List<Field> recordFields(
      JsonNode schema,
      JsonNode properties,
      java.util.Map<java.lang.String, JsonNode> components,
      HashSet<java.lang.String> resolving) {
    var fields = new ArrayList<Field>();
    var seen = new HashSet<java.lang.String>();
    var required = schema.path("required");
    if (required.isArray()) {
      required.forEach(
          name -> {
            if (name.isTextual() && properties.has(name.textValue())) {
              var key = name.textValue();
              fields.add(new Field(key, interpret(properties.get(key), components, resolving)));
              seen.add(key);
            }
          });
    }
    properties
        .fields()
        .forEachRemaining(
            entry -> {
              if (seen.add(entry.getKey())) {
                fields.add(
                    new Field(entry.getKey(), interpret(entry.getValue(), components, resolving)));
              }
            });
    return fields;
  }

  private static Case variantCase(
      JsonNode branch,
      java.util.Map<java.lang.String, JsonNode> components,
      HashSet<java.lang.String> resolving) {
    var required = branch.path("required");
    var name =
        required.isArray() && !required.isEmpty() && required.get(0).isTextual()
            ? required.get(0).textValue()
            : "";
    var fields = branch.path("properties").path(name);
    return new Case(
        name,
        fields.isMissingNode() ? new Unknown(branch) : interpret(fields, components, resolving));
  }

  /** Returns an insertion-ordered map for a record's fields. */
  static java.util.Map<java.lang.String, ParamType> fieldsByName(Record record) {
    var result = new LinkedHashMap<java.lang.String, ParamType>();
    record.fields().forEach(field -> result.put(field.name(), field.type()));
    return java.util.Collections.unmodifiableMap(result);
  }
}
