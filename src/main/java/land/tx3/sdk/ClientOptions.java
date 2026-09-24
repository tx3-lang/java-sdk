package land.tx3.sdk;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable connection settings shared by low-level and facade TRP clients. */
public record ClientOptions(URI endpoint, Map<String, String> headers, Duration timeout) {
  /** Default timeout used when a caller does not provide one. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Creates client settings, preserving header insertion order and applying empty defaults.
   *
   * @param endpoint TRP endpoint
   * @param headers optional request headers; {@code null} is treated as empty
   * @param timeout optional positive request timeout; {@code null} selects {@link #DEFAULT_TIMEOUT}
   * @throws ValidationException if the endpoint, headers, or timeout are invalid
   */
  public ClientOptions {
    if (endpoint == null) {
      throw new ValidationException("endpoint", "TRP endpoint must not be null");
    }
    var copiedHeaders = new LinkedHashMap<String, String>();
    if (headers != null) {
      for (var header : headers.entrySet()) {
        if (header.getKey() == null || header.getValue() == null) {
          throw new ValidationException("headers", "header names and values must not be null");
        }
        copiedHeaders.put(header.getKey(), header.getValue());
      }
    }
    headers = Collections.unmodifiableMap(copiedHeaders);
    timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    if (timeout.isZero() || timeout.isNegative()) {
      throw new ValidationException("timeout", "timeout must be positive");
    }
  }

  /** Creates options for an endpoint with default headers and timeout. */
  public static ClientOptions forEndpoint(URI endpoint) {
    return new ClientOptions(endpoint, Map.of(), null);
  }
}
