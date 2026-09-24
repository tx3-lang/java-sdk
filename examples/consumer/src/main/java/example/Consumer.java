package example;

import java.net.URI;
import land.tx3.sdk.Address;
import land.tx3.sdk.ClientOptions;

/** Minimal consumer proving that the locally installed package resolves and its public API imports. */
public final class Consumer {
  private Consumer() {}

  /** Creates representative SDK values from another Maven project. */
  public static void main(String[] args) {
    var address = new Address("addr_test1qconsumer");
    var options = ClientOptions.forEndpoint(URI.create("http://localhost:8164"));
    System.out.printf("%s via %s%n", address, options.endpoint());
  }
}
