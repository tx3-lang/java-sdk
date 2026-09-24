package land.tx3.sdk;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/** Java 21 {@link HttpClient}-backed TRP transport. */
public final class HttpTransport implements Transport {
  private final HttpClient client;

  /** Creates a transport backed by a new default HTTP client. */
  public HttpTransport() {
    this(HttpClient.newHttpClient());
  }

  /** Creates a transport backed by the supplied HTTP client. */
  public HttpTransport(HttpClient client) {
    if (client == null) {
      throw new ValidationException("client", "HTTP client must not be null");
    }
    this.client = client;
  }

  @Override
  public CompletableFuture<TransportResponse> send(TransportRequest request) {
    var builder =
        HttpRequest.newBuilder(request.endpoint())
            .timeout(request.timeout())
            .method(request.method(), HttpRequest.BodyPublishers.ofString(request.body()));
    request.headers().forEach(builder::header);

    var httpFuture = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    var result = new CompletableFuture<TransportResponse>();
    httpFuture.whenComplete(
        (response, failure) -> {
          if (failure != null) {
            result.completeExceptionally(failure);
          } else {
            result.complete(new TransportResponse(response.statusCode(), response.body()));
          }
        });
    result.whenComplete(
        (ignored, failure) -> {
          if (result.isCancelled()) {
            httpFuture.cancel(true);
          }
        });
    return result;
  }
}
