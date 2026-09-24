package land.tx3.sdk;

/** Encoded Transaction Intermediate Representation sent to TRP. */
public record TirEnvelope(String encoding, String content, String version) {
  /** Validates required wire fields. */
  public TirEnvelope {
    if (encoding == null || content == null || version == null) {
      throw new ValidationException("tir", "TIR envelope fields must not be null");
    }
    if (!encoding.equals("hex") && !encoding.equals("base64")) {
      throw new ValidationException("encoding", "TIR encoding must be hex or base64");
    }
  }
}
