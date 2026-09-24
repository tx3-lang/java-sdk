package land.tx3.sdk;

import java.util.Objects;

/** Reports invalid caller-controlled data without embedding the rejected value. */
public final class ValidationException extends Tx3Exception {
  private final String field;

  /** Creates a validation error for a stable field name. */
  public ValidationException(String field, String message) {
    super(message);
    this.field = Objects.requireNonNull(field, "field");
  }

  /** Returns the stable name of the invalid field. */
  public String field() {
    return field;
  }
}
