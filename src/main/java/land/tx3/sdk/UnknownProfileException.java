package land.tx3.sdk;

import java.util.Objects;

/** Reports a profile name that is not declared by the loaded protocol. */
public final class UnknownProfileException extends Tx3Exception {
  private final String profile;

  /** Creates an unknown-profile error. */
  public UnknownProfileException(String profile) {
    super("unknown profile: " + profile);
    this.profile = Objects.requireNonNull(profile, "profile");
  }

  /** Returns the requested profile name. */
  public String profile() {
    return profile;
  }
}
