package land.tx3.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Statuses keyed by transaction hash. */
public record StatusResponse(Map<String, TxStatus> statuses) {
  /** Validates and defensively copies the status map. */
  public StatusResponse {
    if (statuses == null
        || statuses.entrySet().stream()
            .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
      throw new ValidationException("statuses", "status keys and values must not be null");
    }
    statuses = Collections.unmodifiableMap(new LinkedHashMap<>(statuses));
  }
}
