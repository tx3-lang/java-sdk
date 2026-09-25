package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtocolTest {
  private static final Path TRANSFER = Path.of("src/test/resources/fixtures/transfer.tii");
  private static final Path COMPLEX = Path.of("src/test/resources/fixtures/complex.tii");
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void loadsEquivalentValuesFromEveryInputForm() throws Exception {
    var bytes = java.nio.file.Files.readAllBytes(TRANSFER);
    var fromFile = Protocol.fromFile(TRANSFER);
    var fromString = Protocol.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    var fromBytes = Protocol.fromJson(bytes);
    var fromTree = Protocol.fromJson(mapper.readTree(bytes));

    assertEquals(fromFile.json(), fromString.json());
    assertEquals(fromFile.json(), fromBytes.json());
    assertEquals(fromFile.json(), fromTree.json());
    assertEquals("v1beta0", fromFile.tiiVersion());
    assertEquals(List.of("transfer"), List.copyOf(fromFile.transactions().keySet()));
    assertEquals(
        List.of("middleman", "receiver", "sender"), List.copyOf(fromFile.parties().keySet()));
    assertInstanceOf(
        ParamType.Integer.class,
        fromFile.transactions().get("transfer").parameters().get("quantity"));
    assertEquals("hex", fromFile.transactions().get("transfer").tir().encoding());
  }

  @Test
  void interpretsCompoundTypesAndPreservesDeclaredOrdering() {
    var transaction = Protocol.fromFile(COMPLEX).transactions().get("complex");
    var params = transaction.parameters();

    assertInstanceOf(ParamType.Integer.class, params.get("quantity"));
    assertInstanceOf(ParamType.Boolean.class, params.get("flag"));
    assertInstanceOf(ParamType.Unit.class, params.get("nothing"));
    assertInstanceOf(ParamType.Address.class, params.get("recipient"));
    assertInstanceOf(ParamType.UtxoRef.class, params.get("source"));
    assertInstanceOf(ParamType.AnyAsset.class, params.get("bag"));
    assertInstanceOf(ParamType.List.class, params.get("amounts"));
    assertEquals(2, ((ParamType.Tuple) params.get("pair")).elements().size());
    assertInstanceOf(ParamType.Map.class, params.get("labels"));

    var asset = assertInstanceOf(ParamType.NamedReference.class, params.get("asset"));
    assertEquals("AssetClass", asset.name());
    var record = assertInstanceOf(ParamType.Record.class, asset.target().orElseThrow());
    assertEquals(
        List.of("policy", "name"), record.fields().stream().map(ParamType.Field::name).toList());

    var side = assertInstanceOf(ParamType.NamedReference.class, params.get("side"));
    var variant = assertInstanceOf(ParamType.Variant.class, side.target().orElseThrow());
    assertEquals(
        List.of("Buy", "Sell"), variant.cases().stream().map(ParamType.Case::name).toList());
  }

  @Test
  void retainsUnknownAndRecursiveSchemasWithoutThrowing() throws Exception {
    var unknownSchema = mapper.readTree("{\"type\":\"string\",\"format\":\"future\"}");
    var unknown =
        assertInstanceOf(
            ParamType.Unknown.class, ParamType.fromJsonSchema(unknownSchema, java.util.Map.of()));
    assertEquals(unknownSchema, unknown.schema());

    var recursive = mapper.readTree("{\"$ref\":\"#/components/schemas/Node\"}");
    var node =
        mapper.readTree(
            "{\"type\":\"object\",\"properties\":{\"next\":{\"$ref\":\"#/components/schemas/Node\"}},\"required\":[\"next\"]}");
    var outer =
        assertInstanceOf(
            ParamType.NamedReference.class,
            ParamType.fromJsonSchema(recursive, java.util.Map.of("Node", node)));
    var record = assertInstanceOf(ParamType.Record.class, outer.target().orElseThrow());
    var inner = assertInstanceOf(ParamType.NamedReference.class, record.fields().getFirst().type());
    assertEquals("Node", inner.name());
    assertTrue(inner.target().isEmpty());
  }

  @Test
  void matchesCanonicalAndLegacyScalarReferencesByTrailingName() throws Exception {
    var canonical =
        mapper.readTree("{\"$ref\":\"https://tx3.land/specs/v1beta0/tii#/$defs/Bytes\"}");
    var legacy = mapper.readTree("{\"$ref\":\"https://tx3.land/specs/v1beta0/core#Bytes\"}");

    assertInstanceOf(
        ParamType.Bytes.class, ParamType.fromJsonSchema(canonical, java.util.Map.of()));
    assertInstanceOf(ParamType.Bytes.class, ParamType.fromJsonSchema(legacy, java.util.Map.of()));
  }

  @Test
  void discriminatesReadJsonAndStructureFailures() {
    assertEquals(
        ProtocolException.Kind.READ,
        assertThrows(ProtocolException.class, () -> Protocol.fromFile(Path.of("missing.tii")))
            .kind());
    assertEquals(
        ProtocolException.Kind.INVALID_JSON,
        assertThrows(ProtocolException.class, () -> Protocol.fromJson("{")).kind());
    assertEquals(
        ProtocolException.Kind.INVALID_PROTOCOL,
        assertThrows(ProtocolException.class, () -> Protocol.fromJson("{}")).kind());
  }

  @Test
  void fixturesMatchTheFounderBoundCanonicalHashes() throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    assertEquals(
        "8d5d715f300e618373b588af96f0cfb9b0a3904b3a34fc0038f72ebb1695da31",
        HexFormat.of().formatHex(digest.digest(java.nio.file.Files.readAllBytes(TRANSFER))));
    assertEquals(
        "0a7195b22fe3262a87110a89ee83f75d1295257c193d8859165842aeeea2ae83",
        HexFormat.of().formatHex(digest.digest(java.nio.file.Files.readAllBytes(COMPLEX))));
  }
}
