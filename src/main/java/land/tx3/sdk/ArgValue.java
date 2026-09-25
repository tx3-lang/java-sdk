package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A canonical, explicitly tagged transaction argument.
 *
 * <p>Aggregate values recursively contain tagged children so generated and dynamic clients can
 * produce the same TRP representation without embedding a protocol schema.
 */
public sealed interface ArgValue
    permits ArgValue.Int,
        ArgValue.Bool,
        ArgValue.Text,
        ArgValue.Bytes,
        ArgValue.AddressValue,
        ArgValue.UtxoReference,
        ArgValue.Sequence,
        ArgValue.Tuple,
        ArgValue.MapValue,
        ArgValue.Struct,
        ArgValue.Raw {
  BigInteger MIN_I128 = BigInteger.ONE.shiftLeft(127).negate();
  BigInteger MAX_I128 = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);
  BigInteger MIN_SAFE_JSON_INTEGER = BigInteger.valueOf(-9_007_199_254_740_991L);
  BigInteger MAX_SAFE_JSON_INTEGER = BigInteger.valueOf(9_007_199_254_740_991L);

  /** Returns the JSON-compatible TRP wire representation. */
  @JsonValue
  Object toWireValue();

  /** Creates a signed-i128 integer argument. */
  static ArgValue integer(BigInteger value) {
    return new Int(value);
  }

  /** Promotes a Java {@code long} losslessly. */
  static ArgValue integer(long value) {
    return integer(BigInteger.valueOf(value));
  }

  /** Promotes a Java {@code int} losslessly. */
  static ArgValue integer(int value) {
    return integer(BigInteger.valueOf(value));
  }

  /** Creates a Boolean argument. */
  static ArgValue bool(boolean value) {
    return new Bool(value);
  }

  /** Creates a string argument, including a tagged map key. */
  static ArgValue string(String value) {
    return new Text(value);
  }

  /** Creates a byte-string argument by defensively copying {@code value}. */
  static ArgValue bytes(byte[] value) {
    return new Bytes(value);
  }

  /** Creates a validated address argument. */
  static ArgValue address(Address value) {
    return new AddressValue(value);
  }

  /** Creates a validated transaction-output-reference argument. */
  static ArgValue utxoRef(UtxoRef value) {
    return new UtxoReference(value);
  }

  /** Creates a homogeneous tagged list. */
  static ArgValue list(List<? extends ArgValue> values) {
    return new Sequence(List.copyOf(values));
  }

  /** Creates a fixed-arity tagged tuple. */
  static ArgValue tuple(List<? extends ArgValue> values) {
    return new Tuple(List.copyOf(values));
  }

  /** Creates a deterministically ordered tagged map. */
  static ArgValue map(List<MapEntry> entries) {
    return new MapValue(entries);
  }

  /** Creates a record or variant value using its constructor index and positional fields. */
  static ArgValue struct(int constructor, List<? extends ArgValue> fields) {
    return new Struct(constructor, List.copyOf(fields));
  }

  /** Creates a JSON passthrough value for an untyped protocol parameter. */
  static ArgValue raw(JsonNode value) {
    return new Raw(value);
  }

  /** A signed-i128 value encoded without losing precision. */
  record Int(BigInteger value) implements ArgValue {
    public Int {
      value = Objects.requireNonNull(value, "value");
      if (value.compareTo(MIN_I128) < 0 || value.compareTo(MAX_I128) > 0) {
        throw new ArgumentEncodingException(
            ArgumentEncodingException.Kind.RANGE, "$", "signed i128 integer");
      }
    }

    @Override
    public Object toWireValue() {
      Object wire =
          value.compareTo(MIN_SAFE_JSON_INTEGER) >= 0 && value.compareTo(MAX_SAFE_JSON_INTEGER) <= 0
              ? value.longValue()
              : signedI128Hex(value);
      return tagged("int", wire);
    }
  }

  /** A Boolean value. */
  record Bool(boolean value) implements ArgValue {
    @Override
    public Object toWireValue() {
      return tagged("bool", value);
    }
  }

  /** A string value, including map keys. */
  record Text(String value) implements ArgValue {
    public Text {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public Object toWireValue() {
      return tagged("string", value);
    }
  }

  /** A byte string. */
  final class Bytes implements ArgValue {
    private final byte[] value;

    public Bytes(byte[] value) {
      this.value = Objects.requireNonNull(value, "value").clone();
    }

    /** Returns a defensive copy of the bytes. */
    public byte[] value() {
      return value.clone();
    }

    @Override
    public Object toWireValue() {
      return tagged("bytes", "0x" + java.util.HexFormat.of().formatHex(value));
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof Bytes that && Arrays.equals(value, that.value));
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(value);
    }
  }

  /** A chain address. */
  record AddressValue(Address value) implements ArgValue {
    public AddressValue {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public Object toWireValue() {
      return tagged("address", value.value());
    }
  }

  /** A transaction-output reference. */
  record UtxoReference(UtxoRef value) implements ArgValue {
    public UtxoReference {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public Object toWireValue() {
      var txId = java.util.HexFormat.of().formatHex(value.txId());
      return tagged("utxoRef", txId + "#" + value.index());
    }
  }

  /** A homogeneous list. */
  record Sequence(List<ArgValue> values) implements ArgValue {
    public Sequence {
      values = List.copyOf(values);
    }

    @Override
    public Object toWireValue() {
      return tagged("list", values);
    }
  }

  /** A fixed-length positional sequence. */
  record Tuple(List<ArgValue> values) implements ArgValue {
    public Tuple {
      values = List.copyOf(values);
    }

    @Override
    public Object toWireValue() {
      return tagged("tuple", values);
    }
  }

  /** One key/value pair in a tagged map. */
  record MapEntry(ArgValue key, ArgValue value) {
    public MapEntry {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(value, "value");
    }
  }

  /** An ordered sequence of tagged map entries. */
  record MapValue(List<MapEntry> entries) implements ArgValue {
    public MapValue {
      entries = List.copyOf(entries);
    }

    @Override
    public Object toWireValue() {
      var pairs = new ArrayList<List<ArgValue>>(entries.size());
      entries.forEach(entry -> pairs.add(List.of(entry.key(), entry.value())));
      return tagged("map", pairs);
    }
  }

  /** A record or resolved variant case. */
  record Struct(int constructor, List<ArgValue> fields) implements ArgValue {
    public Struct {
      if (constructor < 0) {
        throw new ArgumentEncodingException(
            ArgumentEncodingException.Kind.RANGE, "constructor", "non-negative integer");
      }
      fields = List.copyOf(fields);
    }

    @Override
    public Object toWireValue() {
      var body = new LinkedHashMap<String, Object>();
      body.put("constructor", constructor);
      body.put("fields", fields);
      return tagged("struct", body);
    }
  }

  /** A passthrough JSON value for unknown, UTxO, and any-asset parameters. */
  record Raw(JsonNode value) implements ArgValue {
    public Raw {
      value = Objects.requireNonNull(value, "value").deepCopy();
    }

    @Override
    public JsonNode value() {
      return value.deepCopy();
    }

    @Override
    public Object toWireValue() {
      return value();
    }
  }

  private static Map<String, Object> tagged(String tag, Object value) {
    var result = new LinkedHashMap<String, Object>(1);
    result.put(tag, value);
    return result;
  }

  private static String signedI128Hex(BigInteger value) {
    var bytes = value.toByteArray();
    var fill = value.signum() < 0 ? (byte) 0xff : 0;
    var fixed = new byte[16];
    Arrays.fill(fixed, fill);
    var source = Math.max(0, bytes.length - fixed.length);
    var length = Math.min(bytes.length, fixed.length);
    System.arraycopy(bytes, source, fixed, fixed.length - length, length);
    return "0x" + java.util.HexFormat.of().formatHex(fixed);
  }
}
