package land.tx3.sdk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

/** An immutable transaction-output reference consisting of a 32-byte transaction id and index. */
public final class UtxoRef {
  /** Largest output index representable by the protocol's unsigned 32-bit integer. */
  public static final long MAX_INDEX = 0xffff_ffffL;

  private final byte[] txId;
  private final long index;

  /**
   * Creates a transaction-output reference.
   *
   * @param txId exactly 32 bytes identifying the transaction
   * @param index output index in the unsigned 32-bit range
   * @throws ValidationException if either value is outside its protocol range
   */
  @JsonCreator
  public UtxoRef(@JsonProperty("txId") byte[] txId, @JsonProperty("index") long index) {
    if (txId == null || txId.length != 32) {
      throw new ValidationException("txId", "transaction id must contain exactly 32 bytes");
    }
    if (index < 0 || index > MAX_INDEX) {
      throw new ValidationException("index", "output index must be an unsigned 32-bit integer");
    }
    this.txId = txId.clone();
    this.index = index;
  }

  /** Returns a defensive copy of the 32-byte transaction id. */
  @JsonProperty("txId")
  public byte[] txId() {
    return txId.clone();
  }

  /** Returns the unsigned 32-bit output index as a Java {@code long}. */
  @JsonProperty("index")
  public long index() {
    return index;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof UtxoRef that && index == that.index && Arrays.equals(txId, that.txId));
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(txId) + Long.hashCode(index);
  }

  @Override
  public String toString() {
    return "UtxoRef{txId=" + Arrays.toString(txId) + ", index=" + index + '}';
  }
}
