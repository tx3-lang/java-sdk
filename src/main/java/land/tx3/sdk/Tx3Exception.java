package land.tx3.sdk;

/** Root of the SDK's typed, unchecked, recoverable error hierarchy. */
public abstract sealed class Tx3Exception extends RuntimeException
    permits ProtocolException,
        UnknownTransactionException,
        UnknownProfileException,
        UnknownPartyException,
        MissingTrpEndpointException,
        ValidationException,
        ArgumentEncodingException,
        TransportException,
        ResolutionException,
        SigningException,
        SubmissionException,
        PollingException {
  protected Tx3Exception(String message) {
    super(message);
  }

  protected Tx3Exception(String message, Throwable cause) {
    super(message, cause);
  }
}
