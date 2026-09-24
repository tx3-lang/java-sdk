package land.tx3.sdk;

/** Reports that a client cannot be built until TRP connection settings are supplied. */
public final class MissingTrpEndpointException extends Tx3Exception {
  /** Creates the missing-endpoint error. */
  public MissingTrpEndpointException() {
    super("a TRP endpoint is required");
  }
}
