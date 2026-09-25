package land.tx3.sdk;

import java.util.Objects;
import java.util.Optional;

/** A named protocol participant backed by a read-only address or a signer. */
public final class Party {
  private final Address address;
  private final Signer signer;

  private Party(Address address, Signer signer) {
    this.address = Objects.requireNonNull(address, "address");
    this.signer = signer;
  }

  /** Creates a read-only party from an address. */
  public static Party address(Address address) {
    return new Party(address, null);
  }

  /** Creates a signing party, reading its address from the signer. */
  public static Party signer(Signer signer) {
    Objects.requireNonNull(signer, "signer");
    return new Party(Objects.requireNonNull(signer.address(), "signer address"), signer);
  }

  /** Returns the address injected into resolve arguments. */
  public Address address() {
    return address;
  }

  /** Returns the signer when this is a signing party. */
  public Optional<Signer> signer() {
    return Optional.ofNullable(signer);
  }
}
