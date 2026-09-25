package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class FacadeTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final URI ENDPOINT = URI.create("https://trp.example/rpc");

  @Test
  void builderDefersNamedValidationUntilBuild() {
    var protocol = Protocol.fromJson(protocolJson());

    assertInstanceOf(Tx3ClientBuilder.class, protocol.client());
    assertThrows(MissingTrpEndpointException.class, () -> protocol.client().build());
    assertThrows(
        UnknownProfileException.class,
        () -> protocol.client().trpEndpoint(ENDPOINT).withProfile("missing").build());
    assertThrows(
        UnknownPartyException.class,
        () ->
            protocol
                .client()
                .trpEndpoint(ENDPOINT)
                .withParty("missing", Party.address(new Address("addr_missing")))
                .build());

    assertThrows(
        UnknownPartyException.class,
        () ->
            protocol
                .client()
                .trpEndpoint(ENDPOINT)
                .withPartyUnchecked("missing", Party.address(new Address("addr_first")))
                .withParty("missing", Party.address(new Address("addr_last")))
                .build());
  }

  @Test
  void dynamicAndPartsClientsProduceEquivalentResolveRequests() throws Exception {
    var protocol = Protocol.fromJson(protocolJson());
    var dynamicTransport = new CapturingTransport();
    var generatedTransport = new CapturingTransport();

    var dynamic =
        protocol
            .client()
            .trpEndpoint(ENDPOINT)
            .withHeader("x-api-key", "secret")
            .withProfile("local")
            .withParty("sender", Party.address(new Address("addr_explicit")))
            .withEnvValue("fee", 9)
            .transport(dynamicTransport)
            .build();

    dynamic
        .tx("init")
        .arg("participants", List.of(new byte[] {1, 2}, new byte[] {3, 4}))
        .arg("sender", new Address("addr_argument"))
        .resolve()
        .join();

    var transaction = protocol.transactions().get("init");
    var profiles = Map.of("local", protocol.profiles().get("local"));
    var generated =
        Tx3ClientBuilder.fromParts(Map.of("init", transaction.tir()), profiles, Set.of())
            .trpEndpoint(ENDPOINT)
            .withHeader("x-api-key", "secret")
            .withProfile("local")
            .withPartyUnchecked("sender", Party.address(new Address("addr_explicit")))
            .withEnvValue("fee", 9)
            .transport(generatedTransport)
            .build();

    generated
        .tx("init")
        .argTagged(
            "participants",
            ArgValue.list(
                List.of(ArgValue.bytes(new byte[] {1, 2}), ArgValue.bytes(new byte[] {3, 4}))))
        .argTagged("sender", ArgValue.address(new Address("addr_argument")))
        .resolve()
        .join();

    var dynamicParams = dynamicTransport.params();
    var generatedParams = generatedTransport.params();
    assertEquals(dynamicParams, generatedParams);
    assertEquals(9, dynamicParams.path("env").path("fee").intValue());
    assertEquals(
        "addr_argument", dynamicParams.path("args").path("sender").path("address").textValue());
    assertEquals(
        "0x0102",
        dynamicParams
            .path("args")
            .path("participants")
            .path("list")
            .get(0)
            .path("bytes")
            .textValue());
    assertEquals("secret", dynamicTransport.request.headers().get("x-api-key"));
  }

  @Test
  void recordOrderAndVariantConstructorReachCapturedRequest() throws Exception {
    var transport = new CapturingTransport();
    var client =
        Protocol.fromJson(complexProtocolJson())
            .client()
            .trpEndpoint(ENDPOINT)
            .transport(transport)
            .build();

    client
        .tx("complex")
        .arg("asset", Map.of("name", new byte[] {0, 17}, "policy", new byte[] {-86, -69}))
        .arg("side", Map.of("Sell", Map.of("price", 5)))
        .resolve()
        .join();

    var args = transport.params().path("args");
    assertEquals(
        "0xaabb",
        args.path("asset").path("struct").path("fields").get(0).path("bytes").textValue());
    assertEquals(
        "0x0011",
        args.path("asset").path("struct").path("fields").get(1).path("bytes").textValue());
    assertEquals(1, args.path("side").path("struct").path("constructor").intValue());
    assertEquals(5, args.path("side").path("struct").path("fields").get(0).path("int").intValue());
  }

  @Test
  void explicitArgsOverridePartiesAndLateBindingIsValidated() throws Exception {
    var transport = new CapturingTransport();
    var client =
        Protocol.fromJson(protocolJson())
            .client()
            .trpEndpoint(ENDPOINT)
            .withParty("sender", Party.address(new Address("addr_builder")))
            .transport(transport)
            .build();

    assertThrows(
        UnknownPartyException.class,
        () -> client.withParty("missing", Party.address(new Address("addr_missing"))));
    assertNotNull(
        client.withPartyUnchecked("generated", Party.address(new Address("addr_generated"))));
    assertThrows(UnknownTransactionException.class, () -> client.tx("missing"));

    client
        .tx("init")
        .arg("participants", List.of(new byte[] {1}))
        .arg("sender", new Address("addr_argument"))
        .resolve()
        .join();
    assertEquals(
        "addr_argument",
        transport.params().path("args").path("sender").path("address").textValue());

    client.tx("init").arg("participants", List.of(new byte[] {1})).resolve().join();
    assertEquals(
        "addr_builder", transport.params().path("args").path("sender").path("address").textValue());
  }

  @Test
  void resolveRejectsMissingRequiredArgumentsBeforeTransport() {
    var transport = new CapturingTransport();
    var client =
        Protocol.fromJson(protocolJson())
            .client()
            .trpEndpoint(ENDPOINT)
            .transport(transport)
            .build();

    var failure = assertThrows(ResolutionException.class, () -> client.tx("init").resolve());
    assertEquals("init", failure.transaction());
    assertEquals("participants", failure.parameter());
    assertEquals(null, transport.request);
  }

  private static String protocolJson() {
    return """
        {
          "tii": {"version": "v1beta0"},
          "protocol": {"name": "facade", "scope": "test", "version": "1.0.0"},
          "environment": {
            "type": "object",
            "properties": {"fee": {"type": "integer"}},
            "required": ["fee"]
          },
          "parties": {"sender": {}},
          "profiles": {
            "local": {
              "environment": {"fee": 5},
              "parties": {"sender": "addr_profile"}
            }
          },
          "transactions": {
            "init": {
              "params": {
                "type": "object",
                "properties": {
                  "participants": {
                    "type": "array",
                    "items": {"$ref": "https://tx3.land/specs/v1beta0/tii#/$defs/Bytes"}
                  },
                  "sender": {"$ref": "https://tx3.land/specs/v1beta0/tii#/$defs/Address"}
                },
                "required": ["participants", "sender"]
              },
              "tir": {"content": "00", "encoding": "hex", "version": "v1beta0"}
            }
          }
        }
        """;
  }

  private static String complexProtocolJson() {
    return """
        {
          "tii": {"version": "v1beta0"},
          "protocol": {"name": "complex", "scope": "test", "version": "1.0.0"},
          "components": {
            "schemas": {
              "AssetClass": {
                "type": "object",
                "properties": {
                  "name": {"$ref": "https://tx3.land/specs/v1beta0/tii#/$defs/Bytes"},
                  "policy": {"$ref": "https://tx3.land/specs/v1beta0/tii#/$defs/Bytes"}
                },
                "required": ["policy", "name"]
              },
              "Side": {
                "oneOf": [
                  {
                    "type": "object",
                    "required": ["Buy"],
                    "properties": {
                      "Buy": {"type": "object", "properties": {}, "required": []}
                    }
                  },
                  {
                    "type": "object",
                    "required": ["Sell"],
                    "properties": {
                      "Sell": {
                        "type": "object",
                        "properties": {"price": {"type": "integer"}},
                        "required": ["price"]
                      }
                    }
                  }
                ]
              }
            }
          },
          "transactions": {
            "complex": {
              "params": {
                "type": "object",
                "properties": {
                  "asset": {"$ref": "#/components/schemas/AssetClass"},
                  "side": {"$ref": "#/components/schemas/Side"}
                },
                "required": ["asset", "side"]
              },
              "tir": {"content": "00", "encoding": "hex", "version": "v1beta0"}
            }
          }
        }
        """;
  }

  private static final class CapturingTransport implements Transport {
    private TransportRequest request;

    @Override
    public CompletableFuture<TransportResponse> send(TransportRequest request) {
      this.request = request;
      try {
        var id = JSON.readTree(request.body()).path("id").textValue();
        return CompletableFuture.completedFuture(
            new TransportResponse(
                200,
                "{\"jsonrpc\":\"2.0\",\"result\":{\"tx\":\"00\",\"hash\":\"hash\"},\"id\":\""
                    + id
                    + "\"}"));
      } catch (Exception failure) {
        return CompletableFuture.failedFuture(failure);
      }
    }

    private JsonNode params() throws Exception {
      return JSON.readTree(request.body()).path("params");
    }
  }
}
