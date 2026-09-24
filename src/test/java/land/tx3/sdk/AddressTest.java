package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AddressTest {
  @Test
  void acceptsAndDisplaysNonBlankAddress() {
    var address = new Address("addr_test1qexample");

    assertEquals("addr_test1qexample", address.value());
    assertEquals("addr_test1qexample", address.toString());
  }

  @Test
  void rejectsNullOrBlankAddress() {
    assertEquals("value", assertThrows(ValidationException.class, () -> new Address(null)).field());
    assertEquals(
        "value", assertThrows(ValidationException.class, () -> new Address(" \t")).field());
  }
}
