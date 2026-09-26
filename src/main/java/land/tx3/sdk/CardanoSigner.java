package land.tx3.sdk;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import java.util.Arrays;

/** A Cardano BIP32-Ed25519 signer derived at {@code m/1852'/1815'/0'/0/0}. */
public final class CardanoSigner implements Signer {
  private final Address address;
  private final byte[] privateKey;
  private final byte[] publicKey;
  private final byte[] chainCode;

  /**
   * Creates a Cardano signer from an Icarus/CIP-1852 mnemonic and its payment address.
   *
   * <p>Derived key material is defensively copied and is never included in errors or logs.
   *
   * @param mnemonic BIP-39 mnemonic phrase
   * @param address Cardano address controlled by the derived payment key
   * @throws ValidationException if the mnemonic is null or blank
   * @throws SigningException if derivation fails or the address is not controlled by the key
   */
  public CardanoSigner(String mnemonic, Address address) {
    if (address == null) {
      throw new ValidationException("address", "address must not be null");
    }
    if (mnemonic == null || mnemonic.isBlank()) {
      throw new ValidationException("mnemonic", "mnemonic must not be null or blank");
    }
    this.address = address;

    final HdKeyPair keyPair;
    try {
      keyPair =
          new CIP1852()
              .getKeyPairFromMnemonic(
                  mnemonic, DerivationPath.createExternalAddressDerivationPath());
    } catch (RuntimeException exception) {
      throw new SigningException(address, "CIP-1852 key derivation failed", exception);
    }

    this.privateKey = keyPair.getPrivateKey().getKeyData().clone();
    this.publicKey = keyPair.getPublicKey().getKeyData().clone();
    this.chainCode = keyPair.getPublicKey().getChainCode().clone();
    SignerSupport.verifyAddressBinding(address, this.publicKey);
  }

  @Override
  public Address address() {
    return address;
  }

  /**
   * Signs the request's 32-byte transaction hash with the derived BIP32-Ed25519 key.
   *
   * @throws ValidationException if the request is null
   * @throws SigningException if signing fails
   */
  @Override
  public Witness sign(SignRequest request) {
    if (request == null) {
      throw new ValidationException("request", "sign request must not be null");
    }
    byte[] hash = SignerSupport.decodeHex(request.txHashHex(), "txHashHex", 32);
    try {
      byte[] signature =
          CryptoConfiguration.INSTANCE.getSigningProvider().signExtended(hash, privateKey);
      return new Witness(
          SignerSupport.encodeHex(publicKey), SignerSupport.encodeHex(signature), WitnessType.VKEY);
    } catch (CryptoException exception) {
      throw new SigningException(address, "Cardano signing failed", exception);
    } finally {
      Arrays.fill(hash, (byte) 0);
    }
  }

  byte[] chainCode() {
    return chainCode.clone();
  }
}
