package consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import land.tx3.sdk.Address;
import land.tx3.sdk.ClientOptions;
import org.junit.jupiter.api.Test;

/** Compile-time smoke test from outside the library package. */
class ExternalConsumerTest {
  @Test
  void importsPublicEntryPoint() {
    var address = new Address("addr_test1qconsumer");
    var options = ClientOptions.forEndpoint(URI.create("http://localhost:8164"));

    assertEquals("addr_test1qconsumer", address.value());
    assertEquals(URI.create("http://localhost:8164"), options.endpoint());
  }
}
