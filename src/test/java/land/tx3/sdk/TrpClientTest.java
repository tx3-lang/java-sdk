package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class TrpClientTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final URI ENDPOINT = URI.create("https://trp.example/rpc");

  @Test
  void resolveMatchesPinnedRequestAndResponseFixtures() throws Exception {
    var transport = responding(200, fixture("resolve-response.json"));
    var client = client(transport, "resolve-1");

    var response =
        client
            .resolve(
                new ResolveParams(
                    new TirEnvelope("hex", "aabb", "v1beta0"), Map.of("quantity", 42)))
            .join();

    assertEquals(new ResolveResponse("deadbeef", "resolved-hash"), response);
    assertRequest(transport.request, "resolve-request.json");
  }

  @Test
  void submitMatchesPinnedRequestAndResponseFixtures() throws Exception {
    var transport = responding(200, fixture("submit-response.json"));
    var client = client(transport, "submit-1");
    var params =
        new SubmitParams(
            new BytesEnvelope("deadbeef", "application/cbor"),
            List.of(
                new TxWitness(
                    new BytesEnvelope("0011", "hex"),
                    new BytesEnvelope("2233", "hex"),
                    WitnessType.VKEY)));

    assertEquals(new SubmitResponse("submitted-hash"), client.submit(params).join());
    assertRequest(transport.request, "submit-request.json");
  }

  @Test
  void checkStatusMatchesPinnedRequestAndResponseFixtures() throws Exception {
    var transport = responding(200, fixture("status-response.json"));
    var client = client(transport, "status-1");

    var response = client.checkStatus(List.of("submitted-hash")).join();

    assertEquals(TxStage.CONFIRMED, response.statuses().get("submitted-hash").stage());
    assertEquals(123, response.statuses().get("submitted-hash").confirmedAt().slot());
    assertRequest(transport.request, "status-request.json");
  }

  @Test
  void appliesCustomHeadersWithoutDroppingJsonContentType() {
    var transport = responding(200, success("header-1", "{\"hash\":\"ok\"}"));
    var options = new ClientOptions(ENDPOINT, Map.of("Authorization", "Bearer secret"), null);
    var client = new TrpClient(options, transport, () -> "header-1");

    client.submit(new SubmitParams(new BytesEnvelope("aa", "hex"), List.of())).join();

    assertEquals("POST", transport.request.method());
    assertEquals("application/json", transport.request.headers().get("Content-Type"));
    assertEquals("Bearer secret", transport.request.headers().get("Authorization"));
    assertEquals(options.timeout(), transport.request.timeout());
  }

  @Test
  void mapsHttpRpcAndMalformedFailuresWithDiagnostics() {
    var http =
        failure(
            client(responding(503, "temporarily unavailable"), "http-1").checkStatus(List.of()));
    assertEquals(TransportFailure.HTTP_STATUS, http.failure());
    assertEquals(503, http.httpStatus());
    assertEquals("temporarily unavailable", http.diagnosticPayload());

    var rpcBody =
        "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32001,\"message\":\"missing\","
            + "\"data\":{\"key\":\"quantity\"}},\"id\":\"rpc-1\"}";
    var rpc = failure(client(responding(200, rpcBody), "rpc-1").checkStatus(List.of()));
    assertEquals(TransportFailure.JSON_RPC, rpc.failure());
    assertEquals(-32001, rpc.rpcCode());
    assertEquals("{\"key\":\"quantity\"}", rpc.diagnosticPayload());

    var malformed = failure(client(responding(200, "{not-json"), "bad-1").checkStatus(List.of()));
    assertEquals(TransportFailure.MALFORMED_RESPONSE, malformed.failure());
  }

  @Test
  void mapsNetworkTimeoutAndTransportCancellationDistinctly() {
    var networkTransport = new ControlledTransport();
    var networkCall = client(networkTransport, "network-1").checkStatus(List.of());
    networkTransport.response.completeExceptionally(new IOException("offline"));
    assertEquals(TransportFailure.NETWORK, failure(networkCall).failure());

    var timeoutTransport = new ControlledTransport();
    var timeoutCall = client(timeoutTransport, "timeout-1").checkStatus(List.of());
    timeoutTransport.response.completeExceptionally(new HttpTimeoutException("late"));
    assertEquals(TransportFailure.TIMEOUT, failure(timeoutCall).failure());

    var cancelledTransport = new ControlledTransport();
    var cancelledCall = client(cancelledTransport, "cancelled-1").checkStatus(List.of());
    cancelledTransport.response.cancel(true);
    assertEquals(TransportFailure.CANCELLED, failure(cancelledCall).failure());
  }

  @Test
  void cancellingReturnedFutureCancelsUnderlyingTransportFuture() {
    var transport = new ControlledTransport();
    var call = client(transport, "caller-cancelled-1").checkStatus(List.of("hash"));

    assertTrue(call.cancel(true));

    assertTrue(transport.response.isCancelled());
  }

  @Test
  void rejectsMismatchedIdsAndInvalidResultShapes() {
    var wrongId =
        failure(client(responding(200, success("other", "{}")), "expected").checkStatus(List.of()));
    assertEquals(TransportFailure.MALFORMED_RESPONSE, wrongId.failure());

    var wrongShape =
        failure(
            client(responding(200, success("shape-1", "{}")), "shape-1").checkStatus(List.of()));
    assertEquals(TransportFailure.MALFORMED_RESPONSE, wrongShape.failure());
  }

  private static TrpClient client(Transport transport, String id) {
    return new TrpClient(ClientOptions.forEndpoint(ENDPOINT), transport, () -> id);
  }

  private static ControlledTransport responding(int status, String body) {
    var transport = new ControlledTransport();
    transport.response.complete(new TransportResponse(status, body));
    return transport;
  }

  private static String success(String id, String result) {
    return "{\"jsonrpc\":\"2.0\",\"result\":" + result + ",\"id\":\"" + id + "\"}";
  }

  private static TransportException failure(CompletableFuture<?> future) {
    var completion = assertThrows(CompletionException.class, future::join);
    return assertInstanceOf(TransportException.class, completion.getCause());
  }

  private static String fixture(String name) throws IOException {
    try (var stream = TrpClientTest.class.getResourceAsStream("/fixtures/trp/" + name)) {
      if (stream == null) {
        throw new IOException("missing fixture: " + name);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void assertRequest(TransportRequest request, String fixture) throws IOException {
    assertEquals(ENDPOINT, request.endpoint());
    assertEquals(MAPPER.readTree(fixture(fixture)), MAPPER.readTree(request.body()));
  }

  private static final class ControlledTransport implements Transport {
    private final CompletableFuture<TransportResponse> response = new CompletableFuture<>();
    private TransportRequest request;

    @Override
    public CompletableFuture<TransportResponse> send(TransportRequest request) {
      this.request = request;
      return response;
    }
  }
}
