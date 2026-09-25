package consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.net.URI;
import land.tx3.sdk.Address;
import land.tx3.sdk.ArgEncoder;
import land.tx3.sdk.ArgValue;
import land.tx3.sdk.ClientOptions;
import land.tx3.sdk.ParamType;
import land.tx3.sdk.SignRequest;
import land.tx3.sdk.Signer;
import land.tx3.sdk.Witness;
import land.tx3.sdk.WitnessType;
import org.junit.jupiter.api.Test;

/** Compile-time smoke test from outside the library package. */
class ExternalConsumerTest {
  @Test
  void importsPublicEntryPoint() {
    var address = new Address("addr_test1qconsumer");
    var options = ClientOptions.forEndpoint(URI.create("http://localhost:8164"));

    assertEquals("addr_test1qconsumer", address.value());
    assertEquals(URI.create("http://localhost:8164"), options.endpoint());
    assertEquals(
        ArgValue.integer(BigInteger.valueOf(42)),
        ArgEncoder.encode(new ParamType.Integer(), BigInteger.valueOf(42)));
  }

  @Test
  void implementsSignerOutsideTheLibraryPackage() {
    Signer signer =
        new Signer() {
          @Override
          public Address address() {
            return new Address("external-consumer-address");
          }

          @Override
          public Witness sign(SignRequest request) {
            return new Witness("00", "11", WitnessType.VKEY);
          }
        };

    var request =
        new SignRequest("0000000000000000000000000000000000000000000000000000000000000000", "80");
    assertEquals("external-consumer-address", signer.address().value());
    assertEquals(WitnessType.VKEY, signer.sign(request).type());
  }
}
