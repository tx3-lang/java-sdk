package land.tx3.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/** Asynchronous low-level JSON-RPC client for the Transaction Resolver Protocol. */
public final class TrpClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ClientOptions options;
  private final Transport transport;
  private final Supplier<String> requestIds;

  /** Creates a client using Java 21 {@link java.net.http.HttpClient}. */
  public TrpClient(ClientOptions options) {
    this(options, new HttpTransport());
  }

  /** Creates a client using an injected transport. */
  public TrpClient(ClientOptions options, Transport transport) {
    this(options, transport, () -> UUID.randomUUID().toString());
  }

  TrpClient(ClientOptions options, Transport transport, Supplier<String> requestIds) {
    if (options == null || transport == null || requestIds == null) {
      throw new ValidationException(
          "trpClient", "options, transport, and request IDs are required");
    }
    this.options = options;
    this.transport = transport;
    this.requestIds = requestIds;
  }

  /**
   * Resolves a TIR transaction.
   *
   * @throws TransportException asynchronously for network, HTTP, JSON-RPC, timeout, cancellation,
   *     or malformed-response failures
   */
  public CompletableFuture<ResolveResponse> resolve(ResolveParams params) {
    return call("trp.resolve", params, ResolveResponse.class);
  }

  /**
   * Submits transaction bytes and witnesses.
   *
   * @throws TransportException asynchronously for network, HTTP, JSON-RPC, timeout, cancellation,
   *     or malformed-response failures
   */
  public CompletableFuture<SubmitResponse> submit(SubmitParams params) {
    return call("trp.submit", params, SubmitResponse.class);
  }

  /**
   * Queries current lifecycle status for transaction hashes.
   *
   * @throws TransportException asynchronously for network, HTTP, JSON-RPC, timeout, cancellation,
   *     or malformed-response failures
   */
  public CompletableFuture<StatusResponse> checkStatus(List<String> hashes) {
    if (hashes == null || hashes.stream().anyMatch(java.util.Objects::isNull)) {
      throw new ValidationException("hashes", "transaction hashes must not be null");
    }
    return call(
        "trp.checkStatus", new CheckStatusParams(List.copyOf(hashes)), StatusResponse.class);
  }

  private <T> CompletableFuture<T> call(String method, Object params, Class<T> resultType) {
    var id = requestIds.get();
    if (id == null) {
      throw new ValidationException("requestId", "request ID must not be null");
    }

    String body;
    try {
      var request = new LinkedHashMap<String, Object>();
      request.put("jsonrpc", "2.0");
      request.put("method", method);
      request.put("params", params);
      request.put("id", id);
      body = MAPPER.writeValueAsString(request);
    } catch (JsonProcessingException failure) {
      return CompletableFuture.failedFuture(
          malformed("request parameters could not be serialized", failure, null));
    }

    var headers = new LinkedHashMap<String, String>();
    headers.put("Content-Type", "application/json");
    headers.putAll(options.headers());
    var request =
        new TransportRequest(options.endpoint(), "POST", headers, body, options.timeout());

    final CompletableFuture<TransportResponse> transportFuture;
    try {
      transportFuture = transport.send(request);
    } catch (RuntimeException failure) {
      return CompletableFuture.failedFuture(mapTransportFailure(failure));
    }
    if (transportFuture == null) {
      return CompletableFuture.failedFuture(
          new TransportException(TransportFailure.NETWORK, "transport returned no future"));
    }

    var result = new CompletableFuture<T>();
    transportFuture.whenComplete(
        (response, failure) -> {
          if (failure != null) {
            result.completeExceptionally(mapTransportFailure(failure));
            return;
          }
          if (response == null) {
            result.completeExceptionally(malformed("transport returned no response", null, null));
            return;
          }
          try {
            result.complete(decode(response, id, resultType));
          } catch (TransportException mapped) {
            result.completeExceptionally(mapped);
          } catch (RuntimeException unexpected) {
            result.completeExceptionally(
                malformed("TRP response could not be processed", unexpected, response.body()));
          }
        });
    result.whenComplete(
        (ignored, failure) -> {
          if (result.isCancelled()) {
            transportFuture.cancel(true);
          }
        });
    return result;
  }

  private static <T> T decode(TransportResponse response, String requestId, Class<T> resultType) {
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new TransportException(
          TransportFailure.HTTP_STATUS,
          "TRP returned HTTP status " + response.statusCode(),
          null,
          response.statusCode(),
          null,
          response.body());
    }

    final JsonNode root;
    try {
      root = MAPPER.readTree(response.body());
    } catch (JsonProcessingException failure) {
      throw malformed("TRP response is not valid JSON", failure, response.body());
    }
    if (root == null || !root.isObject()) {
      throw malformed("TRP response must be a JSON object", null, response.body());
    }
    if (!root.has("jsonrpc") || !"2.0".equals(root.path("jsonrpc").textValue())) {
      throw malformed("TRP response has an invalid jsonrpc version", null, response.body());
    }
    if (!root.has("id") || !requestId.equals(root.path("id").textValue())) {
      throw malformed("TRP response ID does not match the request", null, response.body());
    }

    var hasResult = root.has("result");
    var hasError = root.has("error");
    if (hasResult == hasError) {
      throw malformed(
          "TRP response must contain exactly one of result or error", null, response.body());
    }
    if (hasError) {
      var error = root.get("error");
      if (!error.isObject()
          || !error.path("code").canConvertToInt()
          || !error.path("message").isTextual()) {
        throw malformed("TRP response contains a malformed JSON-RPC error", null, response.body());
      }
      var data = error.get("data");
      throw new TransportException(
          TransportFailure.JSON_RPC,
          "TRP JSON-RPC error "
              + error.path("code").intValue()
              + ": "
              + error.path("message").textValue(),
          null,
          null,
          error.path("code").intValue(),
          data == null ? null : data.toString());
    }

    try {
      var decoded = MAPPER.treeToValue(root.get("result"), resultType);
      if (decoded == null) {
        throw malformed("TRP result must not be null", null, root.get("result").toString());
      }
      return decoded;
    } catch (JsonProcessingException | RuntimeException failure) {
      throw malformed(
          "TRP result does not match the expected response",
          failure,
          root.get("result").toString());
    }
  }

  private static TransportException mapTransportFailure(Throwable raw) {
    var failure = unwrap(raw);
    if (failure instanceof HttpTimeoutException) {
      return new TransportException(TransportFailure.TIMEOUT, "TRP request timed out", failure);
    }
    if (failure instanceof CancellationException) {
      return new TransportException(
          TransportFailure.CANCELLED, "TRP request was cancelled", failure);
    }
    return new TransportException(TransportFailure.NETWORK, "TRP network request failed", failure);
  }

  private static Throwable unwrap(Throwable raw) {
    var current = raw;
    while ((current instanceof CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static TransportException malformed(String message, Throwable cause, String payload) {
    return new TransportException(
        TransportFailure.MALFORMED_RESPONSE, message, cause, null, null, payload);
  }

  private record CheckStatusParams(List<String> hashes) {}
}
