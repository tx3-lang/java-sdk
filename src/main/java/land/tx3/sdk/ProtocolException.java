package land.tx3.sdk;

import java.util.Objects;

/** Reports loading, JSON parsing, or schema validation of a TII protocol artifact. */
public final class ProtocolException extends Tx3Exception {
  private final String source;

  /** Creates a protocol error with an attributable, non-secret source description. */
  public ProtocolException(String source, String message) {
    super(message);
    this.source = Objects.requireNonNull(source, "source");
  }

  /** Creates a protocol error retaining its underlying cause. */
  public ProtocolException(String source, String message, Throwable cause) {
    super(message, cause);
    this.source = Objects.requireNonNull(source, "source");
  }

  /** Returns the path or logical source of the invalid protocol. */
  public String source() {
    return source;
  }
}
