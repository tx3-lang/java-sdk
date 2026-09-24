package land.tx3.sdk;

/** A non-empty chain address used by a Tx3 party or signer. */
public record Address(String value) {
  /**
   * Creates an address.
   *
   * @param value the textual chain address
   * @throws ValidationException if {@code value} is null or blank
   */
  public Address {
    if (value == null || value.isBlank()) {
      throw new ValidationException("value", "address must not be null or blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
