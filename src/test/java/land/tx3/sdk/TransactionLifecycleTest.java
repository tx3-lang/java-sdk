package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TransactionLifecycleTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String HASH = "00".repeat(32);
  private static final String OTHER_HASH = "11".repeat(32);
  private static final String CBOR = "84a40081";
  private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

  TransactionLifecycleTest() {
    scheduler.setRemoveOnCancelPolicy(true);
  }

  @AfterEach
  void stopScheduler() {
    scheduler.shutdownNow();
  }

  @Test
  void fullChainPopulatesSignerRequestOrdersWitnessesAndAcceptsConfirmed() throws Exception {
    var transport = new LifecycleTransport();
    transport.resolve(HASH, CBOR);
    transport.submit(HASH);
    transport.status(HASH, TxStage.PENDING);
    transport.status(HASH, TxStage.CONFIRMED);
    var signer = new CapturingSigner();

    var client =
        Tx3ClientBuilder.fromParts(
                Map.of("transfer", new TirEnvelope("hex", "00", "v1beta0")),
                Map.of(),
                Set.of("sender"))
            .trpEndpoint(URI.create("https://trp.example/rpc"))
            .withParty("sender", Party.signer(signer))
            .transport(transport)
            .polling(scheduler, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
            .build();
    var manualOne = new Witness("22", "33", WitnessType.VKEY);
    var manualTwo = new Witness("44", "55", WitnessType.VKEY);

    var resolved = client.tx("transfer").resolve().join();
    assertEquals(HASH, resolved.hash());
    assertEquals(HASH, resolved.signingHash());
    assertEquals(CBOR, resolved.txHex());
    assertEquals(CBOR, resolved.txCborHex());

    var signed = resolved.addWitness(manualOne).addWitness(manualTwo).sign();
    assertEquals(new SignRequest(HASH, CBOR), signer.request);
    assertEquals(
        List.of("aa", "22", "44"),
        signed.submitParams().witnesses().stream().map(w -> w.key().content()).toList());
    assertEquals(
        List.of(false, true, true),
        signed.witnesses().stream().map(WitnessInfo::external).toList());

    var submitted = signed.submit().join();
    assertEquals(HASH, submitted.hash());
    var status = submitted.waitForConfirmed(new PollConfig(2, Duration.ZERO)).join();
    assertEquals(TxStage.CONFIRMED, status.stage());
    assertEquals(
        List.of("trp.resolve", "trp.submit", "trp.checkStatus", "trp.checkStatus"),
        transport.methods);
  }

  @Test
  void externalWitnessOnlySigningWorksAndSubmitHashMismatchIsTyped() {
    var transport = new LifecycleTransport();
    transport.resolve(HASH, CBOR);
    transport.submit(OTHER_HASH);
    var client = client(transport);

    var signed =
        client
            .tx("transfer")
            .resolve()
            .join()
            .addWitness(new Witness("aa", "bb", WitnessType.VKEY))
            .sign();
    assertEquals(1, signed.witnesses().size());
    assertTrue(signed.witnesses().getFirst().external());

    var failure = assertThrows(CompletionException.class, () -> signed.submit().join());
    var mismatch = assertInstanceOf(SubmissionException.class, failure.getCause());
    assertEquals(HASH, mismatch.transactionHashHex());
    assertEquals(OTHER_HASH, mismatch.receivedHashHex());
  }

  @Test
  void finalizedWaitIgnoresConfirmedAndReportsTerminalAndTimeoutKinds() {
    var finalizedTransport = new LifecycleTransport();
    finalizedTransport.status(HASH, TxStage.CONFIRMED);
    finalizedTransport.status(HASH, TxStage.FINALIZED);
    var finalized = submitted(finalizedTransport);
    assertEquals(
        TxStage.FINALIZED,
        finalized.waitForFinalized(new PollConfig(2, Duration.ZERO)).join().stage());

    var droppedTransport = new LifecycleTransport();
    droppedTransport.status(HASH, TxStage.DROPPED);
    var dropped =
        assertThrows(
            CompletionException.class,
            () -> submitted(droppedTransport).waitForConfirmed(PollConfig.defaults()).join());
    assertEquals(
        PollingException.Kind.TERMINAL_STAGE,
        assertInstanceOf(PollingException.class, dropped.getCause()).kind());

    var timeoutTransport = new LifecycleTransport();
    timeoutTransport.status(HASH, TxStage.PENDING);
    timeoutTransport.status(HASH, TxStage.ROLLED_BACK);
    var timeout =
        assertThrows(
            CompletionException.class,
            () ->
                submitted(timeoutTransport)
                    .waitForFinalized(new PollConfig(1, Duration.ZERO))
                    .join());
    assertEquals(
        PollingException.Kind.TIMEOUT,
        assertInstanceOf(PollingException.class, timeout.getCause()).kind());
  }

  @Test
  void cancellationStopsInFlightStatusRequestAndScheduledDelay() {
    var pendingTransport = new LifecycleTransport();
    pendingTransport.pendingStatus();
    var inFlightWait = submitted(pendingTransport).waitForConfirmed(PollConfig.defaults());
    assertTrue(inFlightWait.cancel(true));
    assertTrue(pendingTransport.lastSent.isCancelled());

    var delayedTransport = new LifecycleTransport();
    delayedTransport.status(HASH, TxStage.PENDING);
    var delayedWait =
        submitted(delayedTransport).waitForConfirmed(new PollConfig(2, Duration.ofDays(1)));
    assertEquals(1, scheduler.getQueue().size());
    assertTrue(delayedWait.cancel(true));
    assertTrue(scheduler.getQueue().isEmpty());
  }

  @Test
  void pollDefaultsAndValidationAreStable() {
    assertEquals(20, PollConfig.defaults().attempts());
    assertEquals(Duration.ofSeconds(5), PollConfig.defaults().delay());
    assertEquals(
        "attempts",
        assertThrows(ValidationException.class, () -> new PollConfig(0, Duration.ZERO)).field());
    assertEquals(
        "delay",
        assertThrows(ValidationException.class, () -> new PollConfig(1, Duration.ofSeconds(-1)))
            .field());
  }

  private Tx3Client client(LifecycleTransport transport) {
    return Tx3ClientBuilder.fromParts(
            Map.of("transfer", new TirEnvelope("hex", "00", "v1beta0")), Map.of(), Set.of())
        .trpEndpoint(URI.create("https://trp.example/rpc"))
        .transport(transport)
        .polling(scheduler, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        .build();
  }

  private SubmittedTx submitted(LifecycleTransport transport) {
    var options = ClientOptions.forEndpoint(URI.create("https://trp.example/rpc"));
    return new SubmittedTx(
        new TrpClient(options, transport, () -> "request-id"),
        HASH,
        new PollingRuntime(scheduler, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
  }

  private static final class CapturingSigner implements Signer {
    private SignRequest request;

    @Override
    public Address address() {
      return new Address("addr_test1signer");
    }

    @Override
    public Witness sign(SignRequest request) {
      this.request = request;
      return new Witness("aa", "bb", WitnessType.VKEY);
    }
  }

  private static final class LifecycleTransport implements Transport {
    private final ArrayDeque<CompletableFuture<TransportResponse>> responses = new ArrayDeque<>();
    private final List<String> methods = new java.util.ArrayList<>();
    private CompletableFuture<TransportResponse> lastSent;

    void resolve(String hash, String tx) {
      responses.add(completed("{\"tx\":\"" + tx + "\",\"hash\":\"" + hash + "\"}"));
    }

    void submit(String hash) {
      responses.add(completed("{\"hash\":\"" + hash + "\"}"));
    }

    void status(String hash, TxStage stage) {
      responses.add(
          completed(
              "{\"statuses\":{\""
                  + hash
                  + "\":{\"stage\":\""
                  + stage.wireValue()
                  + "\",\"confirmations\":0,\"nonConfirmations\":0}}}"));
    }

    CompletableFuture<TransportResponse> pendingStatus() {
      var pending = new CompletableFuture<TransportResponse>();
      responses.add(pending);
      return pending;
    }

    @Override
    public CompletableFuture<TransportResponse> send(TransportRequest request) {
      try {
        JsonNode body = JSON.readTree(request.body());
        methods.add(body.path("method").textValue());
        var response = responses.removeFirst();
        var id = body.path("id").textValue();
        lastSent =
            response.thenApply(
                raw ->
                    new TransportResponse(
                        raw.statusCode(),
                        "{\"jsonrpc\":\"2.0\",\"result\":"
                            + raw.body()
                            + ",\"id\":\""
                            + id
                            + "\"}"));
        return lastSent;
      } catch (Exception failure) {
        return CompletableFuture.failedFuture(failure);
      }
    }

    private static CompletableFuture<TransportResponse> completed(String result) {
      return CompletableFuture.completedFuture(new TransportResponse(200, result));
    }
  }
}
