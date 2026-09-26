package land.tx3.sdk;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** A signed transaction ready for submission. */
public final class SignedTx {
  private final TrpClient trp;
  private final String hash;
  private final SubmitParams submitParams;
  private final List<WitnessInfo> witnesses;
  private final PollingRuntime polling;

  SignedTx(
      TrpClient trp,
      String hash,
      SubmitParams submitParams,
      List<WitnessInfo> witnesses,
      PollingRuntime polling) {
    this.trp = Objects.requireNonNull(trp, "trp");
    this.hash = Objects.requireNonNull(hash, "hash");
    this.submitParams = Objects.requireNonNull(submitParams, "submitParams");
    this.witnesses = List.copyOf(witnesses);
    this.polling = Objects.requireNonNull(polling, "polling");
  }

  /** Returns the signed transaction hash. */
  public String hash() {
    return hash;
  }

  /** Returns the immutable TRP submission payload. */
  public SubmitParams submitParams() {
    return submitParams;
  }

  /** Returns immutable metadata in the exact witness submission order. */
  public List<WitnessInfo> witnesses() {
    return witnesses;
  }

  /**
   * Submits this transaction and verifies that TRP returns the signed hash.
   *
   * @throws SubmissionException asynchronously when the returned hash differs
   */
  public CompletableFuture<SubmittedTx> submit() {
    var pending = trp.submit(submitParams);
    var result = new CompletableFuture<SubmittedTx>();
    pending.whenComplete(
        (response, failure) -> {
          if (failure != null) {
            result.completeExceptionally(failure);
          } else if (!hash.equals(response.hash())) {
            result.completeExceptionally(
                new SubmissionException(
                    hash,
                    response.hash(),
                    "submitted transaction hash mismatch: expected "
                        + hash
                        + ", received "
                        + response.hash()));
          } else {
            result.complete(new SubmittedTx(trp, hash, polling));
          }
        });
    result.whenComplete(
        (ignored, failure) -> {
          if (result.isCancelled()) pending.cancel(true);
        });
    return result;
  }
}
