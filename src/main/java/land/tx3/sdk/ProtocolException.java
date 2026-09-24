package land.tx3.sdk;

import java.util.Objects;

/** Reports loading, JSON parsing, or schema validation of a TII protocol artifact. */
public final class ProtocolException extends Tx3Exception {
  /** Identifies the protocol-loading phase that failed. */
  public enum Kind {
    READ,
    INVALID_JSON,
    INVALID_PROTOCOL
  }

  private final String source;
  private final Kind kind;

  /** Creates a protocol error with an attributable, non-secret source description. */
  public ProtocolException(String source, String message) {
    this(Kind.INVALID_PROTOCOL, source, message);
  }

  /** Creates a protocol error retaining its underlying cause. */
  public ProtocolException(String source, String message, Throwable cause) {
    this(Kind.INVALID_PROTOCOL, source, message, cause);
  }

  /** Creates a protocol error with a discriminable failure kind. */
  public ProtocolException(Kind kind, String source, String message) {
    super(message);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.source = Objects.requireNonNull(source, "source");
  }

  /** Creates a discriminable protocol error retaining its underlying cause. */
  public ProtocolException(Kind kind, String source, String message, Throwable cause) {
    super(message, cause);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.source = Objects.requireNonNull(source, "source");
  }

  /** Returns the loading phase that failed. */
  public Kind kind() {
    return kind;
  }

  /** Returns the path or logical source of the invalid protocol. */
  public String source() {
    return source;
  }
}
