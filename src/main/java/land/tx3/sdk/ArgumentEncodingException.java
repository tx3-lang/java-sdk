package land.tx3.sdk;

import java.util.Objects;

/** Reports a pre-transport argument shape, range, or JSON-encoding failure. */
public final class ArgumentEncodingException extends Tx3Exception {
  /** Identifies the validation category without requiring message parsing. */
  public enum Kind {
    SHAPE,
    RANGE,
    ENCODING
  }

  private final Kind kind;
  private final String path;
  private final String expected;

  /** Creates an argument error with non-secret structural context. */
  public ArgumentEncodingException(Kind kind, String path, String expected) {
    super("Invalid argument at '" + path + "': expected " + expected);
    this.kind = Objects.requireNonNull(kind, "kind");
    this.path = Objects.requireNonNull(path, "path");
    this.expected = Objects.requireNonNull(expected, "expected");
  }

  /** Returns the validation category. */
  public Kind kind() {
    return kind;
  }

  /** Returns the structural path to the invalid value. */
  public String path() {
    return path;
  }

  /** Returns a non-secret description of the required representation. */
  public String expected() {
    return expected;
  }
}
