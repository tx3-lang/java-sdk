package land.tx3.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A resolved transaction ready for signer and external witnesses. */
public final class ResolvedTx {
  private final TrpClient trp;
  private final String hash;
  private final String txCborHex;
  private final List<SignerEntry> signers;
  private final List<Witness> manualWitnesses = new ArrayList<>();
  private final PollingRuntime polling;

  ResolvedTx(
      TrpClient trp,
      String hash,
      String txCborHex,
      List<SignerEntry> signers,
      PollingRuntime polling) {
    this.trp = Objects.requireNonNull(trp, "trp");
    this.hash = Objects.requireNonNull(hash, "hash");
    this.txCborHex = Objects.requireNonNull(txCborHex, "txCborHex");
    this.signers = List.copyOf(signers);
    this.polling = Objects.requireNonNull(polling, "polling");
  }

  /** Returns the resolved transaction hash. */
  public String hash() {
    return hash;
  }

  /** Returns the hash supplied to registered signers. */
  public String signingHash() {
    return hash;
  }

  /** Returns the full hexadecimal transaction CBOR for wallet integrations. */
  public String txHex() {
    return txCborHex;
  }

  /** Returns the full hexadecimal transaction CBOR supplied to registered signers. */
  public String txCborHex() {
    return txCborHex;
  }

  /**
   * Attaches a witness produced outside a registered signer and returns this transaction.
   *
   * <p>External witnesses are appended after automatic signer witnesses in attachment order. The
   * SDK does not verify external witnesses against the transaction hash; TRP enforces that binding.
   */
  public ResolvedTx addWitness(Witness witness) {
    manualWitnesses.add(Objects.requireNonNull(witness, "witness"));
    return this;
  }

  /**
   * Signs with every registered signer and appends all external witnesses.
   *
   * <p>External-witness-only signing is valid. Signer failures retain their typed exception.
   */
  public SignedTx sign() {
    var request = new SignRequest(hash, txCborHex);
    var witnesses = new ArrayList<Witness>(signers.size() + manualWitnesses.size());
    var info = new ArrayList<WitnessInfo>(signers.size() + manualWitnesses.size());

    for (var entry : signers) {
      final Witness witness;
      try {
        witness = Objects.requireNonNull(entry.signer().sign(request), "signer witness");
      } catch (SigningException failure) {
        throw failure;
      } catch (RuntimeException failure) {
        throw new SigningException(entry.address(), "signer failed", failure);
      }
      witnesses.add(witness);
      info.add(new WitnessInfo(entry.name(), entry.address(), witness, hash, false));
    }
    for (var witness : manualWitnesses) {
      witnesses.add(witness);
      info.add(new WitnessInfo("<external>", null, witness, hash, true));
    }

    var wireWitnesses = witnesses.stream().map(ResolvedTx::toWireWitness).toList();
    var submit = new SubmitParams(new BytesEnvelope(txCborHex, "hex"), wireWitnesses);
    return new SignedTx(trp, hash, submit, info, polling);
  }

  private static TxWitness toWireWitness(Witness witness) {
    return new TxWitness(
        new BytesEnvelope(witness.publicKeyHex(), "hex"),
        new BytesEnvelope(witness.signatureHex(), "hex"),
        witness.type());
  }

  record SignerEntry(String name, Address address, Signer signer) {
    SignerEntry {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(address, "address");
      Objects.requireNonNull(signer, "signer");
    }
  }
}
