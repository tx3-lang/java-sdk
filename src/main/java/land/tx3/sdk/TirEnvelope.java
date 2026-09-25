package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

/** An encoded Transaction Intermediate Representation used by TII and sent to TRP. */
public record TirEnvelope(
    String content, String encoding, String version, @JsonIgnore JsonNode json) {
  /** Validates wire fields and defensively copies the optional source declaration. */
  public TirEnvelope {
    if (encoding == null || content == null || version == null) {
      throw new ValidationException("tir", "TIR envelope fields must not be null");
    }
    if (!encoding.equals("hex") && !encoding.equals("base64")) {
      throw new ValidationException("encoding", "TIR encoding must be hex or base64");
    }
    json = json == null ? null : json.deepCopy();
  }

  /** Creates a TRP wire envelope without a retained TII source declaration. */
  public TirEnvelope(String encoding, String content, String version) {
    this(content, encoding, version, null);
  }

  /** Returns a defensive copy of the TII source declaration, or {@code null} for wire values. */
  @Override
  public JsonNode json() {
    return json == null ? null : json.deepCopy();
  }
}
