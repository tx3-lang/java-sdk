package land.tx3.sdk;

/** Stable discriminator for failures while communicating with TRP. */
public enum TransportFailure {
  /** The server could not be reached. */
  NETWORK,
  /** The server returned an unsuccessful HTTP status. */
  HTTP_STATUS,
  /** The server returned a JSON-RPC error response. */
  JSON_RPC,
  /** The server response could not be decoded or validated. */
  MALFORMED_RESPONSE,
  /** The request exceeded its configured timeout. */
  TIMEOUT,
  /** The caller cancelled the request. */
  CANCELLED
}
