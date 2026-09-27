package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Live tests for the canonical transfer protocol against preprod TRP. */
@Tag("e2e")
class TransactionE2eTest {
  private static final List<String> REQUIRED_ENV =
      List.of(
          "TRP_ENDPOINT_PREPROD",
          "TRP_API_KEY_PREPROD",
          "TEST_PARTY_A_ADDRESS",
          "TEST_PARTY_A_MNEMONIC",
          "TEST_PARTY_B_ADDRESS",
          "TEST_PARTY_B_MNEMONIC");

  @Test
  void transferCompletesResolveSignSubmitConfirmAndFinalize() {
    var env = environment();
    var client = liveClient(env);

    var resolved = client.tx("transfer").arg("quantity", 10_000_000).resolve().join();
    var signed = resolved.sign();
    assertEquals(resolved.hash(), signed.hash());

    var submitted = signed.submit().join();
    var confirmed = submitted.waitForConfirmed(PollConfig.defaults()).join();
    assertTrue(confirmed.stage() == TxStage.CONFIRMED || confirmed.stage() == TxStage.FINALIZED);

    var finalized = submitted.waitForFinalized(new PollConfig(120, Duration.ofSeconds(5))).join();
    assertEquals(TxStage.FINALIZED, finalized.stage());
  }

  @Test
  void missingArgumentIsReportedBeforeTransport() {
    var env = environment();
    var failure =
        assertThrows(ResolutionException.class, () -> liveClient(env).tx("transfer").resolve());
    assertEquals("quantity", failure.parameter());
  }

  @Test
  void unreachableEndpointProducesTypedTransportFailure() {
    environment();
    var protocol = Protocol.fromFile(fixture());
    var client =
        protocol
            .client()
            .trp(
                new ClientOptions(
                    URI.create("http://127.0.0.1:1"), java.util.Map.of(), Duration.ofSeconds(2)))
            .withProfile("preprod")
            .withParty("sender", Party.address(new Address("addr_test1unreachable")))
            .withParty("receiver", Party.address(new Address("addr_test1unreachable")))
            .withParty("middleman", Party.address(new Address("addr_test1unreachable")))
            .build();

    var failure =
        assertThrows(
            CompletionException.class,
            () -> client.tx("transfer").arg("quantity", 10_000_000).resolve().join());
    assertInstanceOf(TransportException.class, failure.getCause());
  }

  private static Tx3Client liveClient(E2eEnvironment env) {
    var protocol = Protocol.fromFile(fixture());
    var signer = new CardanoSigner(env.partyAMnemonic(), new Address(env.partyAAddress()));
    return protocol
        .client()
        .trpEndpoint(URI.create(env.endpoint()))
        .withHeader("dmtr-api-key", env.apiKey())
        .withProfile("preprod")
        .withParty("sender", Party.signer(signer))
        .withParty("receiver", Party.address(new Address(env.partyBAddress())))
        .withParty("middleman", Party.address(new Address(env.partyBAddress())))
        .build();
  }

  private static Path fixture() {
    return Path.of("src/test/resources/fixtures/transfer.tii");
  }

  private static E2eEnvironment environment() {
    var missing = new ArrayList<String>();
    for (var name : REQUIRED_ENV) {
      if (System.getenv(name) == null || System.getenv(name).isBlank()) missing.add(name);
    }
    if (!missing.isEmpty()) {
      var message = "Missing e2e configuration: " + String.join(", ", missing);
      if (System.getenv("CI") != null) throw new AssertionError(message);
      Assumptions.abort(message);
    }
    return new E2eEnvironment(
        System.getenv("TRP_ENDPOINT_PREPROD"),
        System.getenv("TRP_API_KEY_PREPROD"),
        System.getenv("TEST_PARTY_A_ADDRESS"),
        System.getenv("TEST_PARTY_A_MNEMONIC"),
        System.getenv("TEST_PARTY_B_ADDRESS"),
        System.getenv("TEST_PARTY_B_MNEMONIC"));
  }

  private record E2eEnvironment(
      String endpoint,
      String apiKey,
      String partyAAddress,
      String partyAMnemonic,
      String partyBAddress,
      String partyBMnemonic) {}
}
