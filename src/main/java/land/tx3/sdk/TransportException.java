package land.tx3.sdk;

import java.util.Objects;

/** Reports a typed network, HTTP, JSON-RPC, response, timeout, or cancellation failure. */
public final class TransportException extends Tx3Exception {
  private final TransportFailure failure;

  /** Creates a transport error. */
  public TransportException(TransportFailure failure, String message) {
    super(message);
    this.failure = Objects.requireNonNull(failure, "failure");
  }

  /** Creates a transport error retaining its underlying cause. */
  public TransportException(TransportFailure failure, String message, Throwable cause) {
    super(message, cause);
    this.failure = Objects.requireNonNull(failure, "failure");
  }

  /** Returns the stable transport failure category. */
  public TransportFailure failure() {
    return failure;
  }
}
