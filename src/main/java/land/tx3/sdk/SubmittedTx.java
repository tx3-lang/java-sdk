package land.tx3.sdk;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/** A submitted transaction whose confirmed and finalized stages can be awaited. */
public final class SubmittedTx {
  private final TrpClient trp;
  private final String hash;
  private final PollingRuntime polling;

  SubmittedTx(TrpClient trp, String hash, PollingRuntime polling) {
    this.trp = Objects.requireNonNull(trp, "trp");
    this.hash = Objects.requireNonNull(hash, "hash");
    this.polling = Objects.requireNonNull(polling, "polling");
  }

  /** Returns the submitted transaction hash. */
  public String hash() {
    return hash;
  }

  /** Resolves at Confirmed or Finalized and fails on terminal failure or timeout. */
  public CompletableFuture<TxStatus> waitForConfirmed(PollConfig config) {
    return waitFor(config, TxStage.CONFIRMED);
  }

  /** Resolves only at Finalized and fails on terminal failure or timeout. */
  public CompletableFuture<TxStatus> waitForFinalized(PollConfig config) {
    return waitFor(config, TxStage.FINALIZED);
  }

  private CompletableFuture<TxStatus> waitFor(PollConfig config, TxStage target) {
    Objects.requireNonNull(config, "config");
    var result = new CompletableFuture<TxStatus>();
    var active = new AtomicReference<CompletableFuture<?>>();
    var startedAt = polling.clock().instant();
    poll(config, target, 1, startedAt, result, active);
    result.whenComplete(
        (ignored, failure) -> {
          if (result.isCancelled()) {
            var pending = active.get();
            if (pending != null) pending.cancel(true);
          }
        });
    return result;
  }

  private void poll(
      PollConfig config,
      TxStage target,
      int attempt,
      Instant startedAt,
      CompletableFuture<TxStatus> result,
      AtomicReference<CompletableFuture<?>> active) {
    if (result.isDone()) return;

    final CompletableFuture<StatusResponse> statusFuture;
    try {
      statusFuture = trp.checkStatus(java.util.List.of(hash));
    } catch (RuntimeException failure) {
      result.completeExceptionally(failure);
      return;
    }
    active.set(statusFuture);
    if (result.isCancelled()) {
      statusFuture.cancel(true);
      return;
    }

    statusFuture.whenComplete(
        (response, rawFailure) -> {
          if (result.isDone()) return;
          if (rawFailure != null) {
            result.completeExceptionally(unwrap(rawFailure));
            return;
          }

          var status = response.statuses().get(hash);
          if (status != null) {
            if (status.stage() == TxStage.FINALIZED
                || (target == TxStage.CONFIRMED && status.stage() == TxStage.CONFIRMED)) {
              result.complete(status);
              return;
            }
            if (status.stage() == TxStage.DROPPED || status.stage() == TxStage.ROLLED_BACK) {
              result.completeExceptionally(
                  new PollingException(
                      PollingException.Kind.TERMINAL_STAGE,
                      hash,
                      target.wireValue(),
                      "transaction reached terminal stage " + status.stage().wireValue()));
              return;
            }
          }

          if (attempt >= config.attempts()) {
            Duration elapsed = Duration.between(startedAt, polling.clock().instant());
            result.completeExceptionally(
                new PollingException(
                    PollingException.Kind.TIMEOUT,
                    hash,
                    target.wireValue(),
                    "polling timed out after "
                        + config.attempts()
                        + " attempts (elapsed "
                        + elapsed
                        + ")"));
            return;
          }

          final CompletableFuture<Void> delayFuture;
          try {
            delayFuture = polling.delay(config.delay());
          } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
            return;
          }
          active.set(delayFuture);
          if (result.isCancelled()) {
            delayFuture.cancel(true);
            return;
          }
          delayFuture.whenComplete(
              (ignored, delayFailure) -> {
                if (result.isDone()) return;
                if (delayFailure != null) {
                  var unwrapped = unwrap(delayFailure);
                  if (!(unwrapped instanceof CancellationException)) {
                    result.completeExceptionally(unwrapped);
                  }
                  return;
                }
                poll(config, target, attempt + 1, startedAt, result, active);
              });
        });
  }

  private static Throwable unwrap(Throwable raw) {
    var current = raw;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
