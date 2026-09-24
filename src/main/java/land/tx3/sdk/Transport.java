package land.tx3.sdk;

import java.util.concurrent.CompletableFuture;

/** Injectable asynchronous transport used by the low-level TRP client. */
@FunctionalInterface
public interface Transport {
  /**
   * Sends one immutable transport request.
   *
   * <p>Cancelling the returned future must cancel any underlying I/O.
   *
   * @param request request metadata and body
   * @return the eventual HTTP response
   */
  CompletableFuture<TransportResponse> send(TransportRequest request);
}
