package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UtxoRefTest {
  @Test
  void defensivelyCopiesTransactionId() {
    var txId = new byte[32];
    txId[0] = 7;
    var reference = new UtxoRef(txId, UtxoRef.MAX_INDEX);

    txId[0] = 8;
    var returned = reference.txId();
    returned[0] = 9;

    assertEquals(7, reference.txId()[0]);
    assertEquals(UtxoRef.MAX_INDEX, reference.index());
  }

  @Test
  void hasValueEqualityForByteArrays() {
    var left = new UtxoRef(new byte[32], 4);
    var right = new UtxoRef(new byte[32], 4);

    assertEquals(left, right);
    assertEquals(left.hashCode(), right.hashCode());
    assertNotEquals(left, new UtxoRef(new byte[32], 5));
  }

  @Test
  void validatesProtocolRanges() {
    assertEquals(
        "txId",
        assertThrows(ValidationException.class, () -> new UtxoRef(new byte[31], 0)).field());
    assertEquals(
        "index",
        assertThrows(ValidationException.class, () -> new UtxoRef(new byte[32], -1)).field());
    assertEquals(
        "index",
        assertThrows(
                ValidationException.class, () -> new UtxoRef(new byte[32], UtxoRef.MAX_INDEX + 1))
            .field());
  }
}
