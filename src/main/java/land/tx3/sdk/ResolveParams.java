package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parameters for resolving a TIR transaction through TRP. */
public record ResolveParams(
    TirEnvelope tir,
    Map<String, Object> args,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> env) {
  /** Validates and defensively copies resolution parameters. */
  public ResolveParams {
    if (tir == null || args == null) {
      throw new ValidationException("resolveParams", "tir and args must not be null");
    }
    args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
    env = env == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(env));
  }

  /** Creates resolution parameters without an environment map. */
  public ResolveParams(TirEnvelope tir, Map<String, Object> args) {
    this(tir, args, null);
  }
}
