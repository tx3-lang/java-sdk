package land.tx3.sdk;

import com.bloxbean.cardano.client.address.AddressProvider;
import java.util.HexFormat;

final class SignerSupport {
  private static final HexFormat HEX = HexFormat.of();

  private SignerSupport() {}

  static byte[] decodeHex(String value, String field, int expectedBytes) {
    if (value == null || value.isEmpty()) {
      throw new ValidationException(field, field + " must not be null or empty");
    }

    final byte[] decoded;
    try {
      decoded = HEX.parseHex(value);
    } catch (IllegalArgumentException exception) {
      throw new ValidationException(field, field + " must contain only complete hexadecimal bytes");
    }

    if (expectedBytes >= 0 && decoded.length != expectedBytes) {
      throw new ValidationException(field, field + " must be exactly " + expectedBytes + " bytes");
    }
    return decoded;
  }

  static String encodeHex(byte[] value) {
    return HEX.formatHex(value);
  }

  static void verifyAddressBinding(Address address, byte[] publicKey) {
    final com.bloxbean.cardano.client.address.Address parsed;
    try {
      parsed = new com.bloxbean.cardano.client.address.Address(address.value());
    } catch (RuntimeException exception) {
      throw new SigningException(
          address, "signer address is not a valid Cardano address", exception);
    }

    try {
      if (!parsed.isPubKeyHashInPaymentPart()) {
        throw new SigningException(address, "signer address must contain a payment key credential");
      }
      if (!AddressProvider.verifyAddress(parsed, publicKey)) {
        throw new SigningException(address, "signer key does not control the supplied address");
      }
    } catch (SigningException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new SigningException(address, "signer address cannot be bound to the key", exception);
    }
  }
}
