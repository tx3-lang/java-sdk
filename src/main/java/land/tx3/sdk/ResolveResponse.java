package land.tx3.sdk;

/** Resolved transaction hash and hexadecimal transaction bytes. */
public record ResolveResponse(String tx, String hash) {
  /** Validates required response fields. */
  public ResolveResponse {
    if (tx == null || hash == null) {
      throw new ValidationException(
          "resolveResponse", "resolved transaction fields must not be null");
    }
  }
}
