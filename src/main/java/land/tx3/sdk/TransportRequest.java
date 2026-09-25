package land.tx3.sdk;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable HTTP request passed to a {@link Transport}. */
public record TransportRequest(
    URI endpoint, String method, Map<String, String> headers, String body, Duration timeout) {
  /** Validates and defensively copies request data. */
  public TransportRequest {
    if (endpoint == null || method == null || headers == null || body == null || timeout == null) {
      throw new ValidationException(
          "transportRequest", "transport request fields must not be null");
    }
    headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
  }
}
