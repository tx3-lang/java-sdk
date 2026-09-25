package land.tx3.sdk;

import java.util.List;

/** Signed transaction bytes and witnesses submitted through TRP. */
public record SubmitParams(BytesEnvelope tx, List<TxWitness> witnesses) {
  /** Validates and defensively copies submission parameters. */
  public SubmitParams {
    if (tx == null || witnesses == null || witnesses.stream().anyMatch(java.util.Objects::isNull)) {
      throw new ValidationException("submitParams", "tx and witnesses must not be null");
    }
    witnesses = List.copyOf(witnesses);
  }
}
