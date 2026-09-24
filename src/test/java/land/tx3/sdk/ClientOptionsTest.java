package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientOptionsTest {
  @Test
  void appliesDefaults() {
    var options = new ClientOptions(URI.create("https://trp.example"), null, null);

    assertEquals(List.of(), List.copyOf(options.headers().keySet()));
    assertEquals(ClientOptions.DEFAULT_TIMEOUT, options.timeout());
  }

  @Test
  void copiesHeadersAndPreservesInsertionOrder() {
    var headers = new LinkedHashMap<String, String>();
    headers.put("X-First", "one");
    headers.put("X-Second", "two");

    var options =
        new ClientOptions(URI.create("https://trp.example"), headers, Duration.ofSeconds(3));
    headers.put("X-Third", "three");

    assertEquals(List.of("X-First", "X-Second"), List.copyOf(options.headers().keySet()));
    assertThrows(UnsupportedOperationException.class, () -> options.headers().put("X", "value"));
  }

  @Test
  void rejectsInvalidSettings() {
    assertEquals(
        "endpoint",
        assertThrows(ValidationException.class, () -> new ClientOptions(null, null, null)).field());
    assertEquals(
        "timeout",
        assertThrows(
                ValidationException.class,
                () -> new ClientOptions(URI.create("https://trp.example"), null, Duration.ZERO))
            .field());

    var nullHeader = new LinkedHashMap<String, String>();
    nullHeader.put("X-Null", null);
    assertEquals(
        "headers",
        assertThrows(
                ValidationException.class,
                () -> new ClientOptions(URI.create("https://trp.example"), nullHeader, null))
            .field());
  }
}
