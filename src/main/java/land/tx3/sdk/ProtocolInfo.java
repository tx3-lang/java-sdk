package land.tx3.sdk;

import java.util.Objects;
import java.util.Optional;

/** Descriptive metadata from a TII document's protocol section. */
public record ProtocolInfo(
    String name, String version, String scope, Optional<String> description) {
  public ProtocolInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(scope, "scope");
    description = Objects.requireNonNull(description, "description");
  }
}
