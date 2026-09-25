package land.tx3.sdk;

import java.util.Objects;

/** Reports a typed network, HTTP, JSON-RPC, response, timeout, or cancellation failure. */
public final class TransportException extends Tx3Exception {
  private final TransportFailure failure;
  private final Integer httpStatus;
  private final Integer rpcCode;
  private final String diagnosticPayload;

  /** Creates a transport error. */
  public TransportException(TransportFailure failure, String message) {
    this(failure, message, null, null, null, null);
  }

  /** Creates a transport error retaining its underlying cause. */
  public TransportException(TransportFailure failure, String message, Throwable cause) {
    this(failure, message, cause, null, null, null);
  }

  /** Creates a transport error retaining structured HTTP or JSON-RPC diagnostics. */
  public TransportException(
      TransportFailure failure,
      String message,
      Throwable cause,
      Integer httpStatus,
      Integer rpcCode,
      String diagnosticPayload) {
    super(message, cause);
    this.failure = Objects.requireNonNull(failure, "failure");
    this.httpStatus = httpStatus;
    this.rpcCode = rpcCode;
    this.diagnosticPayload = diagnosticPayload;
  }

  /** Returns the stable transport failure category. */
  public TransportFailure failure() {
    return failure;
  }

  /** Returns the HTTP status when the failure occurred at the HTTP layer. */
  public Integer httpStatus() {
    return httpStatus;
  }

  /** Returns the JSON-RPC error code when supplied by the server. */
  public Integer rpcCode() {
    return rpcCode;
  }

  /** Returns the non-header response or JSON-RPC diagnostic payload, when available. */
  public String diagnosticPayload() {
    return diagnosticPayload;
  }
}
