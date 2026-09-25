package land.tx3.sdk;

/** Bytes plus their content type, as represented by the TRP wire contract. */
public record BytesEnvelope(String content, String contentType) {
  /** Validates required wire fields. */
  public BytesEnvelope {
    if (content == null || contentType == null) {
      throw new ValidationException("bytesEnvelope", "bytes envelope fields must not be null");
    }
  }
}
