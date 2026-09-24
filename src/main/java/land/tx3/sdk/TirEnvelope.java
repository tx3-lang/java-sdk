package land.tx3.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** The encoded TIR payload embedded in a TII transaction declaration. */
public record TirEnvelope(String content, String encoding, String version, JsonNode json) {
  public TirEnvelope {
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(encoding, "encoding");
    Objects.requireNonNull(version, "version");
    json = Objects.requireNonNull(json, "json").deepCopy();
  }

  @Override
  public JsonNode json() {
    return json.deepCopy();
  }
}
