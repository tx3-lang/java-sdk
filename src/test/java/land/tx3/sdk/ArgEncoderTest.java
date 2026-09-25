package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArgEncoderTest {
  private static final String ORACLE = "/fixtures/wire-vectors.json";
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void encodesEverySharedAcceptVector() throws Exception {
    var oracle = oracle();
    var components = components(oracle.path("components"));

    for (var vector : oracle.withArray("accept")) {
      var type = ParamType.fromJsonSchema(vector.path("schema"), components);
      var nativeValue = nativeValue(vector.path("value"), type);
      var actual = mapper.valueToTree(ArgEncoder.encode(type, nativeValue));
      var expected = canonicalBytes(vector.path("tagged").deepCopy());

      assertEquals(expected.toString(), actual.toString(), vector.path("name").asText());
    }
  }

  @Test
  void rejectsEverySharedRejectVector() throws Exception {
    var oracle = oracle();
    var components = components(oracle.path("components"));

    for (var vector : oracle.withArray("reject")) {
      var type = ParamType.fromJsonSchema(vector.path("schema"), components);
      var nativeValue = nativeValue(vector.path("value"), type);
      assertThrows(
          ArgumentEncodingException.class,
          () -> ArgEncoder.encode(type, nativeValue),
          vector.path("name").asText());
    }
  }

  @Test
  void preservesNativeScalarContractsAndStructuredErrors() throws Exception {
    var maximum = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);
    var encoded = ArgEncoder.encode(new ParamType.Integer(), maximum);
    assertEquals(
        "{\"int\":\"0x7fffffffffffffffffffffffffffffff\"}", mapper.writeValueAsString(encoded));

    var source = new byte[] {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef};
    var bytes =
        assertInstanceOf(ArgValue.Bytes.class, ArgEncoder.encode(new ParamType.Bytes(), source));
    source[0] = 0;
    assertArrayEquals(
        new byte[] {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef}, bytes.value());
    assertEquals("{\"bytes\":\"0xdeadbeef\"}", mapper.writeValueAsString(bytes));

    var range =
        assertThrows(
            ArgumentEncodingException.class,
            () -> ArgEncoder.encode(new ParamType.Integer(), BigInteger.ONE.shiftLeft(127)));
    assertEquals(ArgumentEncodingException.Kind.RANGE, range.kind());
    assertEquals("$", range.path());
    assertEquals("signed i128 integer", range.expected());

    var shape =
        assertThrows(
            ArgumentEncodingException.class,
            () -> ArgEncoder.encode(new ParamType.Integer(), 1.0d));
    assertEquals(ArgumentEncodingException.Kind.SHAPE, shape.kind());
    assertEquals("BigInteger, int, or long", shape.expected());
  }

  @Test
  void encodesAddressAndUtxoReferenceUsingTheirNativeTypes() throws Exception {
    var address = ArgEncoder.encode(new ParamType.Address(), new Address("addr_test1qexample"));
    assertEquals("{\"address\":\"addr_test1qexample\"}", mapper.writeValueAsString(address));

    var txId = new byte[32];
    txId[31] = (byte) 0xaa;
    var reference = ArgEncoder.encode(new ParamType.UtxoRef(), new UtxoRef(txId, 4_294_967_295L));
    assertEquals(
        "{\"utxoRef\":\"00000000000000000000000000000000000000000000000000000000000000aa#4294967295\"}",
        mapper.writeValueAsString(reference));
  }

  @Test
  void reportsNestedPathsAndUsesCaseInsensitiveBuilderSeams() {
    var type =
        new ParamType.Record(
            List.of(new ParamType.Field("items", new ParamType.List(new ParamType.Integer()))));
    var value = new LinkedHashMap<String, Object>();
    value.put("items", List.of(BigInteger.ONE.shiftLeft(127)));

    var error = assertThrows(ArgumentEncodingException.class, () -> ArgEncoder.encode(type, value));
    assertEquals(ArgumentEncodingException.Kind.RANGE, error.kind());
    assertEquals("$.items[0]", error.path());

    var builder = new TxBuilder(Map.of("Quantity", new ParamType.Integer()));
    builder.arg("quantity", 7).argTagged("QUANTITY", ArgValue.integer(8));
    assertEquals(Map.of("quantity", ArgValue.integer(8)), builder.taggedArguments());
  }

  @Test
  void passesUnknownUtxoAndAnyAssetJsonThroughWithoutTypeDirectedRewriting() {
    var value = new LinkedHashMap<String, Object>();
    value.put("items", List.of(1, "raw"));
    value.put("ok", true);

    var expected = mapper.valueToTree(value);
    assertEquals(
        expected, mapper.valueToTree(ArgEncoder.encode(new ParamType.Unknown(expected), value)));
    assertEquals(expected, mapper.valueToTree(ArgEncoder.encode(new ParamType.Utxo(), value)));
    assertEquals(expected, mapper.valueToTree(ArgEncoder.encode(new ParamType.AnyAsset(), value)));
  }

  @Test
  void copiedOracleHasTheFounderBoundHash() throws Exception {
    byte[] bytes;
    try (var stream = ArgEncoderTest.class.getResourceAsStream(ORACLE)) {
      if (stream == null) throw new AssertionError("missing wire-vector fixture");
      bytes = stream.readAllBytes();
    }
    assertEquals(
        "9d16d23a06a36a7ec1eb07af14f0b404b54f1438bb88692dc13ed9c078422a2b",
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
  }

  private JsonNode oracle() throws Exception {
    try (var stream = ArgEncoderTest.class.getResourceAsStream(ORACLE)) {
      if (stream == null) throw new AssertionError("missing wire-vector fixture");
      return mapper.readTree(stream);
    }
  }

  private Map<String, JsonNode> components(JsonNode node) {
    var result = new LinkedHashMap<String, JsonNode>();
    node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
    return result;
  }

  private Object nativeValue(JsonNode value, ParamType type) {
    if (type instanceof ParamType.NamedReference reference && reference.target().isPresent()) {
      return nativeValue(value, reference.target().orElseThrow());
    }
    if (type instanceof ParamType.Integer && value.isIntegralNumber()) {
      return value.bigIntegerValue();
    }
    if (type instanceof ParamType.Boolean && value.isBoolean()) {
      return value.booleanValue();
    }
    if (type instanceof ParamType.Bytes && value.isTextual()) {
      var hex = value.textValue().replaceFirst("^0x", "");
      return HexFormat.of().parseHex(hex);
    }
    if (type instanceof ParamType.List list && value.isArray()) {
      var result = new ArrayList<>();
      value.forEach(item -> result.add(nativeValue(item, list.element())));
      return List.copyOf(result);
    }
    if (type instanceof ParamType.Tuple tuple && value.isArray()) {
      var result = new ArrayList<>();
      for (int index = 0; index < value.size(); index++) {
        var elementType =
            index < tuple.elements().size()
                ? tuple.elements().get(index)
                : new ParamType.Unknown(value.get(index));
        result.add(nativeValue(value.get(index), elementType));
      }
      return List.copyOf(result);
    }
    if (type instanceof ParamType.Map map && value.isObject()) {
      return nativeObject(value, Map.of(), map.value());
    }
    if (type instanceof ParamType.Record record && value.isObject()) {
      var fieldTypes = new LinkedHashMap<String, ParamType>();
      record.fields().forEach(field -> fieldTypes.put(field.name(), field.type()));
      return nativeObject(value, fieldTypes, null);
    }
    if (type instanceof ParamType.Variant variant && value.isObject()) {
      var result = new LinkedHashMap<String, Object>();
      value
          .fields()
          .forEachRemaining(
              entry -> {
                var caseType =
                    variant.cases().stream()
                        .filter(candidate -> candidate.name().equals(entry.getKey()))
                        .map(ParamType.Case::fields)
                        .findFirst()
                        .orElse(new ParamType.Unknown(entry.getValue()));
                result.put(entry.getKey(), nativeValue(entry.getValue(), caseType));
              });
      return result;
    }
    return value.deepCopy();
  }

  private Map<String, Object> nativeObject(
      JsonNode value, Map<String, ParamType> fieldTypes, ParamType defaultType) {
    var result = new LinkedHashMap<String, Object>();
    value
        .fields()
        .forEachRemaining(
            entry -> {
              var type = fieldTypes.getOrDefault(entry.getKey(), defaultType);
              result.put(
                  entry.getKey(),
                  type == null ? entry.getValue().deepCopy() : nativeValue(entry.getValue(), type));
            });
    return result;
  }

  private JsonNode canonicalBytes(JsonNode node) {
    if (node.isObject()) {
      if (node.size() == 1 && node.has("bytes") && node.path("bytes").isTextual()) {
        var value = node.path("bytes").textValue();
        if (!value.startsWith("0x")) {
          ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("bytes", "0x" + value);
        }
      } else {
        node.fields().forEachRemaining(entry -> canonicalBytes(entry.getValue()));
      }
    } else if (node.isArray()) {
      node.forEach(this::canonicalBytes);
    }
    return node;
  }
}
