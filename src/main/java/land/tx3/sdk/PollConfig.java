package land.tx3.sdk;

import java.time.Duration;

/** Immutable attempt and delay settings for transaction status polling. */
public record PollConfig(int attempts, Duration delay) {
  /** Default number of status requests. */
  public static final int DEFAULT_ATTEMPTS = 20;

  /** Default delay between status requests. */
  public static final Duration DEFAULT_DELAY = Duration.ofSeconds(5);

  /** Validates polling settings. Zero delay is supported for deterministic callers and tests. */
  public PollConfig {
    if (attempts <= 0) {
      throw new ValidationException("attempts", "poll attempts must be positive");
    }
    if (delay == null || delay.isNegative()) {
      throw new ValidationException("delay", "poll delay must not be null or negative");
    }
  }

  /** Returns the standard configuration of 20 attempts spaced five seconds apart. */
  public static PollConfig defaults() {
    return new PollConfig(DEFAULT_ATTEMPTS, DEFAULT_DELAY);
  }
}
