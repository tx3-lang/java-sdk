package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class ContractSerializationTest {
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serializesSignerContractsDeterministically() throws Exception {
    assertEquals(
        "{\"value\":\"addr_test1\"}", mapper.writeValueAsString(new Address("addr_test1")));
    assertEquals(
        "{\"txHashHex\":\"aabb\",\"txCborHex\":\"ccdd\"}",
        mapper.writeValueAsString(new SignRequest("aabb", "ccdd")));
    assertEquals(
        "{\"publicKeyHex\":\"0011\",\"signatureHex\":\"2233\",\"type\":\"vkey\"}",
        mapper.writeValueAsString(new Witness("0011", "2233", WitnessType.VKEY)));
  }

  @Test
  void roundTripsUtxoRefWithDefensiveBytes() throws Exception {
    var txId = new byte[32];
    txId[0] = 1;
    var reference = new UtxoRef(txId, UtxoRef.MAX_INDEX);

    var json = mapper.writeValueAsString(reference);
    var decoded = mapper.readValue(json, UtxoRef.class);

    assertEquals(
        "{\"txId\":\"AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\",\"index\":4294967295}", json);
    assertEquals(reference, decoded);
  }

  @Test
  void serializesClientOptionsWithHeaderOrder() throws Exception {
    var headers = new LinkedHashMap<String, String>();
    headers.put("X-First", "one");
    headers.put("X-Second", "two");
    var options =
        new ClientOptions(URI.create("https://trp.example"), headers, Duration.ofSeconds(10));

    assertEquals(
        "{\"endpoint\":\"https://trp.example\",\"headers\":{\"X-First\":\"one\","
            + "\"X-Second\":\"two\"},\"timeout\":10.000000000}",
        mapper.writeValueAsString(options));
  }

  @Test
  void reportsInvalidSignerContractsThroughTheTypedHierarchy() {
    assertEquals(
        "txHashHex",
        assertThrows(ValidationException.class, () -> new SignRequest(null, "ccdd")).field());
    assertEquals(
        "signatureHex",
        assertThrows(ValidationException.class, () -> new Witness("0011", null, WitnessType.VKEY))
            .field());
  }
}
