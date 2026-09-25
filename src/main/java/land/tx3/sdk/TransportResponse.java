package land.tx3.sdk;

/** Immutable response returned by a {@link Transport}. */
public record TransportResponse(int statusCode, String body) {
  /** Validates response data. */
  public TransportResponse {
    if (statusCode < 100 || statusCode > 599) {
      throw new ValidationException("statusCode", "HTTP status must be between 100 and 599");
    }
    if (body == null) {
      throw new ValidationException("body", "response body must not be null");
    }
  }
}
