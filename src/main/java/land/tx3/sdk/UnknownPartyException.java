package land.tx3.sdk;

import java.util.Objects;

/** Reports a party name that is not declared by the loaded protocol. */
public final class UnknownPartyException extends Tx3Exception {
  private final String party;

  /** Creates an unknown-party error. */
  public UnknownPartyException(String party) {
    super("unknown party: " + party);
    this.party = Objects.requireNonNull(party, "party");
  }

  /** Returns the requested party name. */
  public String party() {
    return party;
  }
}
