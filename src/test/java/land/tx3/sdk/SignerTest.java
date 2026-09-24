package land.tx3.sdk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SignerTest {
  private static final HexFormat HEX = HexFormat.of();
  private static JsonNode vectors;

  @BeforeAll
  static void loadVectors() throws IOException {
    try (var input = SignerTest.class.getResourceAsStream("/fixtures/signer-vectors.json")) {
      if (input == null) {
        throw new IOException("missing signer vector fixture");
      }
      vectors = new ObjectMapper().readTree(input);
    }
  }

  @Test
  void rawSignerMatchesIndependentPublicKeyAndSignatureVector() {
    JsonNode vector = vectors.get("rawEd25519");
    byte[] seed = HEX.parseHex(vector.get("privateKeySeedHex").textValue());
    String publicKeyHex = vector.at("/expected/publicKeyHex").textValue();
    var signer = new Ed25519Signer(seed, enterpriseAddress(publicKeyHex));

    seed[0] ^= (byte) 0xff;
    Witness witness = signer.sign(new SignRequest(vector.get("messageHex").textValue(), "80"));

    assertEquals(publicKeyHex, witness.publicKeyHex());
    assertEquals(vector.at("/expected/signatureHex").textValue(), witness.signatureHex());
    assertEquals(WitnessType.VKEY, witness.type());
  }

  @Test
  void cardanoSignerMatchesIndependentCip1852VectorAndSigns() {
    JsonNode vector = vectors.get("cardanoCip1852");
    var address = new Address(vector.at("/expected/address").textValue());
    var signer = new CardanoSigner(vector.get("mnemonic").textValue(), address);
    var request =
        new SignRequest(
            vectors.at("/rawEd25519/messageHex").textValue(),
            "84a400818258200000000000000000000000000000000000000000000000000000000000000000");

    Witness witness = signer.sign(request);

    assertEquals(address, signer.address());
    assertEquals(vector.at("/expected/publicKeyHex").textValue(), witness.publicKeyHex());
    assertEquals(
        vector.at("/expected/chainCodeHex").textValue(), HEX.formatHex(signer.chainCode()));
    assertEquals(WitnessType.VKEY, witness.type());
    assertTrue(
        CryptoConfiguration.INSTANCE
            .getSigningProvider()
            .verify(
                HEX.parseHex(witness.signatureHex()),
                HEX.parseHex(request.txHashHex()),
                HEX.parseHex(witness.publicKeyHex())));
  }

  @Test
  void customSignerCanImplementThePublicContract() {
    Signer signer =
        new Signer() {
          private final Address address = new Address("custom-address");

          @Override
          public Address address() {
            return address;
          }

          @Override
          public Witness sign(SignRequest request) {
            return new Witness("00", "11", WitnessType.VKEY);
          }
        };

    assertEquals("custom-address", signer.address().value());
    assertEquals(
        WitnessType.VKEY,
        signer
            .sign(
                new SignRequest(
                    "0000000000000000000000000000000000000000000000000000000000000000", "80"))
            .type());
  }

  @Test
  void rejectsInvalidKeysHashesAndAddressBindingsWithTypedErrors() {
    JsonNode raw = vectors.get("rawEd25519");
    byte[] seed = HEX.parseHex(raw.get("privateKeySeedHex").textValue());
    Address address = enterpriseAddress(raw.at("/expected/publicKeyHex").textValue());

    assertEquals(
        "privateKey",
        assertThrows(ValidationException.class, () -> new Ed25519Signer(new byte[31], address))
            .field());
    assertEquals(
        "txHashHex",
        assertThrows(ValidationException.class, () -> new SignRequest("00", "80")).field());
    assertEquals(
        "txCborHex",
        assertThrows(
                ValidationException.class,
                () ->
                    new SignRequest(
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        "not-hex"))
            .field());
    assertThrows(SigningException.class, () -> new Ed25519Signer(seed, new Address("bad")));
    assertThrows(
        SigningException.class,
        () -> new CardanoSigner(vectors.at("/cardanoCip1852/mnemonic").textValue(), address));
    assertEquals(
        "request",
        assertThrows(ValidationException.class, () -> new Ed25519Signer(seed, address).sign(null))
            .field());
    assertEquals(
        "mnemonic",
        assertThrows(ValidationException.class, () -> new CardanoSigner(" ", address)).field());
  }

  @Test
  void rawSignerExposesNoMnemonicConstructor() {
    assertFalse(
        java.util.Arrays.stream(Ed25519Signer.class.getDeclaredConstructors())
            .anyMatch(
                constructor ->
                    java.util.Arrays.asList(constructor.getParameterTypes())
                        .contains(String.class)));
  }

  @Test
  void cardanoChainCodeIsDefensivelyCopied() {
    JsonNode vector = vectors.get("cardanoCip1852");
    var signer =
        new CardanoSigner(
            vector.get("mnemonic").textValue(),
            new Address(vector.at("/expected/address").textValue()));

    byte[] first = signer.chainCode();
    byte[] expected = first.clone();
    first[0] ^= (byte) 0xff;

    assertArrayEquals(expected, signer.chainCode());
  }

  private static Address enterpriseAddress(String publicKeyHex) {
    byte[] keyHash = Blake2bUtil.blake2bHash224(HEX.parseHex(publicKeyHex));
    String address =
        AddressProvider.getEntAddress(Credential.fromKey(keyHash), new Network(0, 1)).toBech32();
    return new Address(address);
  }
}
