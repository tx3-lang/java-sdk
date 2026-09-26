package land.tx3.sdk;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Scheduler and clock pair used by lifecycle polling. */
final class PollingRuntime {
  private static final ScheduledExecutorService DEFAULT_SCHEDULER =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            var thread = new Thread(runnable, "tx3-status-poller");
            thread.setDaemon(true);
            return thread;
          });
  private static final PollingRuntime SYSTEM =
      new PollingRuntime(DEFAULT_SCHEDULER, Clock.systemUTC());

  private final ScheduledExecutorService scheduler;
  private final Clock clock;

  PollingRuntime(ScheduledExecutorService scheduler, Clock clock) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  static PollingRuntime system() {
    return SYSTEM;
  }

  Clock clock() {
    return clock;
  }

  CompletableFuture<Void> delay(Duration duration) {
    var result = new CompletableFuture<Void>();
    var scheduled =
        scheduler.schedule(() -> result.complete(null), duration.toNanos(), TimeUnit.NANOSECONDS);
    result.whenComplete(
        (ignored, failure) -> {
          if (result.isCancelled()) scheduled.cancel(true);
        });
    return result;
  }
}
