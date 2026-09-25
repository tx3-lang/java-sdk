package land.tx3.sdk;

/** Hash returned after a successful TRP submission. */
public record SubmitResponse(String hash) {
  /** Validates the required response hash. */
  public SubmitResponse {
    if (hash == null) {
      throw new ValidationException("hash", "submitted transaction hash must not be null");
    }
  }
}
