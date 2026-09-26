package land.tx3.sdk;

/** Public, non-secret metadata for one witness carried by a signed transaction. */
public record WitnessInfo(
    String party, Address address, Witness witness, String signedHashHex, boolean external) {
  /** Validates required witness metadata. */
  public WitnessInfo {
    if (party == null || witness == null || signedHashHex == null) {
      throw new ValidationException("witnessInfo", "witness metadata must not be null");
    }
    if (!external && address == null) {
      throw new ValidationException("witnessInfo.address", "signer address must not be null");
    }
  }
}
