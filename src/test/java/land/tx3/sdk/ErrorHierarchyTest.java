package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorHierarchyTest {
  @Test
  void exposesEveryErrorAsAStableTx3ExceptionSubtype() {
    var address = new Address("addr_test1");
    List<Tx3Exception> errors =
        List.of(
            new ProtocolException("protocol.tii", "invalid protocol"),
            new UnknownTransactionException("transfer"),
            new UnknownProfileException("preview"),
            new UnknownPartyException("sender"),
            new MissingTrpEndpointException(),
            new ValidationException("field", "invalid field"),
            new ArgumentEncodingException(
                ArgumentEncodingException.Kind.SHAPE, "$.amount", "BigInteger"),
            new TransportException(TransportFailure.TIMEOUT, "timed out"),
            new ResolutionException("transfer", "amount", "missing amount"),
            new SigningException(address, "hash mismatch"),
            new SubmissionException("aabb", "server rejected transaction"),
            new PollingException("aabb", "finalized", "poll timed out"));

    errors.forEach(error -> assertInstanceOf(Tx3Exception.class, error));
    assertEquals(TransportFailure.TIMEOUT, ((TransportException) errors.get(7)).failure());
    assertEquals("transfer", ((ResolutionException) errors.get(8)).transaction());
    assertEquals("amount", ((ResolutionException) errors.get(8)).parameter());
    assertEquals(address, ((SigningException) errors.get(9)).signerAddress());
    assertEquals("aabb", ((SubmissionException) errors.get(10)).transactionHashHex());
    assertEquals("finalized", ((PollingException) errors.get(11)).targetStage());
  }
}
