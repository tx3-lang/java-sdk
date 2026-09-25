package land.tx3.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Type-directed conversion from native Java values to canonical transaction arguments. */
public final class ArgEncoder {
  private static final ObjectMapper JSON = new ObjectMapper();

  private ArgEncoder() {}

  /**
   * Encodes {@code value} according to a resolved protocol parameter type.
   *
   * <p>Integers accept {@link BigInteger}, {@code int}, and {@code long}; floating-point values are
   * rejected. Bytes accept {@code byte[]}, addresses accept {@link Address}, UTxO references accept
   * {@link UtxoRef}, and compound values accept {@link List} or string-keyed {@link Map} values.
   * Unknown, UTxO, and any-asset parameters pass JSON-compatible values through unchanged.
   *
   * @throws ArgumentEncodingException when a value has the wrong shape, exceeds signed i128, or
   *     cannot be represented as JSON without inspecting arbitrary application objects
   */
  public static ArgValue encode(ParamType type, Object value) {
    if (type == null) {
      throw failure(ArgumentEncodingException.Kind.SHAPE, "$", "resolved parameter type");
    }
    return encode(type, value, "$");
  }

  /**
   * Looks up a transaction parameter case-insensitively and encodes its native value.
   *
   * @throws ArgumentEncodingException if the parameter is undeclared or its value is invalid
   */
  public static ArgValue encode(Transaction transaction, String parameter, Object value) {
    if (transaction == null || parameter == null) {
      throw failure(ArgumentEncodingException.Kind.SHAPE, "$", "transaction and parameter name");
    }
    var type = transaction.parameterType(parameter);
    if (type.isEmpty()) {
      throw failure(
          ArgumentEncodingException.Kind.SHAPE, parameter, "declared transaction parameter");
    }
    return encode(type.get(), value, parameter);
  }

  private static ArgValue encode(ParamType type, Object value, String path) {
    if (type instanceof ParamType.NamedReference reference) {
      return reference
          .target()
          .map(target -> encode(target, value, path))
          .orElseGet(() -> raw(value, path));
    }
    if (type instanceof ParamType.Unit) {
      if (value != null && !(value instanceof NullNode)) {
        throw mismatch(path, "null");
      }
      return ArgValue.struct(0, List.of());
    }
    if (type instanceof ParamType.Boolean) {
      if (!(value instanceof java.lang.Boolean bool)) {
        throw mismatch(path, "Boolean");
      }
      return ArgValue.bool(bool);
    }
    if (type instanceof ParamType.Integer) {
      return integer(value, path);
    }
    if (type instanceof ParamType.Bytes) {
      if (!(value instanceof byte[] bytes)) {
        throw mismatch(path, "byte[]");
      }
      return ArgValue.bytes(bytes);
    }
    if (type instanceof ParamType.String) {
      if (!(value instanceof java.lang.String text)) {
        throw mismatch(path, "String");
      }
      return ArgValue.string(text);
    }
    if (type instanceof ParamType.Address) {
      if (!(value instanceof land.tx3.sdk.Address address)) {
        throw mismatch(path, "Address");
      }
      return ArgValue.address(address);
    }
    if (type instanceof ParamType.UtxoRef) {
      if (!(value instanceof land.tx3.sdk.UtxoRef reference)) {
        throw mismatch(path, "UtxoRef");
      }
      return ArgValue.utxoRef(reference);
    }
    if (type instanceof ParamType.List list) {
      var values = list(value, path, "List");
      var encoded = new ArrayList<ArgValue>(values.size());
      for (int index = 0; index < values.size(); index++) {
        encoded.add(encode(list.element(), values.get(index), index(path, index)));
      }
      return ArgValue.list(encoded);
    }
    if (type instanceof ParamType.Tuple tuple) {
      var values = list(value, path, "List with " + tuple.elements().size() + " elements");
      if (values.size() != tuple.elements().size()) {
        throw mismatch(path, "List with " + tuple.elements().size() + " elements");
      }
      var encoded = new ArrayList<ArgValue>(values.size());
      for (int index = 0; index < values.size(); index++) {
        encoded.add(encode(tuple.elements().get(index), values.get(index), index(path, index)));
      }
      return ArgValue.tuple(encoded);
    }
    if (type instanceof ParamType.Map map) {
      var values = object(value, path, "insertion-ordered Map<String, ?>");
      var keys = new ArrayList<>(values.keySet());
      keys.sort(java.lang.String::compareTo);
      var entries = new ArrayList<ArgValue.MapEntry>(keys.size());
      for (var key : keys) {
        entries.add(
            new ArgValue.MapEntry(
                ArgValue.string(key), encode(map.value(), values.get(key), field(path, key))));
      }
      return ArgValue.map(entries);
    }
    if (type instanceof ParamType.Record record) {
      return ArgValue.struct(0, recordFields(record.fields(), value, path));
    }
    if (type instanceof ParamType.Variant variant) {
      return variant(variant, value, path);
    }
    if (type instanceof ParamType.Utxo
        || type instanceof ParamType.AnyAsset
        || type instanceof ParamType.Unknown) {
      return raw(value, path);
    }
    throw failure(ArgumentEncodingException.Kind.ENCODING, path, "supported parameter type");
  }

  private static ArgValue integer(Object value, String path) {
    final BigInteger integer;
    if (value instanceof BigInteger big) {
      integer = big;
    } else if (value instanceof java.lang.Integer number) {
      integer = BigInteger.valueOf(number.longValue());
    } else if (value instanceof java.lang.Long number) {
      integer = BigInteger.valueOf(number);
    } else {
      throw mismatch(path, "BigInteger, int, or long");
    }
    if (integer.compareTo(ArgValue.MIN_I128) < 0 || integer.compareTo(ArgValue.MAX_I128) > 0) {
      throw failure(ArgumentEncodingException.Kind.RANGE, path, "signed i128 integer");
    }
    return ArgValue.integer(integer);
  }

  private static ArgValue variant(ParamType.Variant variant, Object value, String path) {
    var object = object(value, path, "single declared variant case");
    if (object.size() != 1) {
      throw mismatch(path, "single declared variant case");
    }
    var entry = object.entrySet().iterator().next();
    for (int index = 0; index < variant.cases().size(); index++) {
      var candidate = variant.cases().get(index);
      if (candidate.name().equals(entry.getKey())) {
        var casePath = field(path, entry.getKey());
        var fields =
            candidate.fields() instanceof ParamType.Record record
                ? recordFields(record.fields(), entry.getValue(), casePath)
                : List.of(encode(candidate.fields(), entry.getValue(), casePath));
        return ArgValue.struct(index, fields);
      }
    }
    throw mismatch(path, "declared variant case");
  }

  private static List<ArgValue> recordFields(
      List<ParamType.Field> fields, Object value, String path) {
    var object = object(value, path, "record Map<String, ?>");
    for (var name : object.keySet()) {
      if (fields.stream().noneMatch(field -> field.name().equals(name))) {
        throw mismatch(field(path, name), "declared record field");
      }
    }
    var result = new ArrayList<ArgValue>(fields.size());
    for (var field : fields) {
      if (!object.containsKey(field.name())) {
        throw mismatch(field(path, field.name()), expected(field.type()));
      }
      result.add(encode(field.type(), object.get(field.name()), field(path, field.name())));
    }
    return result;
  }

  private static List<?> list(Object value, String path, String expected) {
    if (!(value instanceof List<?> values)) {
      throw mismatch(path, expected);
    }
    return values;
  }

  private static Map<String, ?> object(Object value, String path, String expected) {
    if (!(value instanceof Map<?, ?> map)) {
      throw mismatch(path, expected);
    }
    var result = new LinkedHashMap<String, Object>();
    for (var entry : map.entrySet()) {
      if (!(entry.getKey() instanceof java.lang.String key)) {
        throw mismatch(path, expected);
      }
      result.put(key, entry.getValue());
    }
    return result;
  }

  private static ArgValue raw(Object value, String path) {
    return ArgValue.raw(json(value, path));
  }

  private static JsonNode json(Object value, String path) {
    if (value == null) {
      return NullNode.instance;
    }
    if (value instanceof JsonNode node) {
      return node.deepCopy();
    }
    if (value instanceof java.lang.String text) {
      return JSON.getNodeFactory().textNode(text);
    }
    if (value instanceof java.lang.Boolean bool) {
      return JSON.getNodeFactory().booleanNode(bool);
    }
    if (value instanceof BigInteger integer) {
      return JSON.getNodeFactory().numberNode(integer);
    }
    if (value instanceof java.lang.Integer integer) {
      return JSON.getNodeFactory().numberNode(integer);
    }
    if (value instanceof java.lang.Long integer) {
      return JSON.getNodeFactory().numberNode(integer);
    }
    if (value instanceof java.lang.Double number && java.lang.Double.isFinite(number)) {
      return JSON.getNodeFactory().numberNode(number);
    }
    if (value instanceof java.lang.Float number && java.lang.Float.isFinite(number)) {
      return JSON.getNodeFactory().numberNode(number);
    }
    if (value instanceof List<?> values) {
      ArrayNode result = JSON.createArrayNode();
      for (int index = 0; index < values.size(); index++) {
        result.add(json(values.get(index), index(path, index)));
      }
      return result;
    }
    if (value instanceof Map<?, ?> values) {
      ObjectNode result = JSON.createObjectNode();
      for (var entry : values.entrySet()) {
        if (!(entry.getKey() instanceof java.lang.String key)) {
          throw failure(
              ArgumentEncodingException.Kind.ENCODING, path, "JSON object with string keys");
        }
        result.set(key, json(entry.getValue(), field(path, key)));
      }
      return result;
    }
    throw failure(ArgumentEncodingException.Kind.ENCODING, path, "JSON-compatible value");
  }

  private static String expected(ParamType type) {
    if (type instanceof ParamType.Integer) return "BigInteger, int, or long";
    if (type instanceof ParamType.Boolean) return "Boolean";
    if (type instanceof ParamType.Bytes) return "byte[]";
    if (type instanceof ParamType.String) return "String";
    if (type instanceof ParamType.Address) return "Address";
    if (type instanceof ParamType.UtxoRef) return "UtxoRef";
    if (type instanceof ParamType.List) return "List";
    if (type instanceof ParamType.Tuple tuple)
      return "List with " + tuple.elements().size() + " elements";
    if (type instanceof ParamType.Map) return "insertion-ordered Map<String, ?>";
    if (type instanceof ParamType.Record) return "record Map<String, ?>";
    if (type instanceof ParamType.Variant) return "single declared variant case";
    return "JSON-compatible value";
  }

  private static ArgumentEncodingException mismatch(String path, String expected) {
    return failure(ArgumentEncodingException.Kind.SHAPE, path, expected);
  }

  private static ArgumentEncodingException failure(
      ArgumentEncodingException.Kind kind, String path, String expected) {
    return new ArgumentEncodingException(kind, path, expected);
  }

  private static String field(String path, String name) {
    return path + "." + name;
  }

  private static String index(String path, int index) {
    return path + "[" + index + "]";
  }
}
