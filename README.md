# Tx3 SDK for Java

The Java SDK is the Java 21 client library for Tx3 protocols. It includes extensible signer
contracts, a raw-key Java Ed25519 signer, and a Cardano mnemonic signer derived at
`m/1852'/1815'/0'/0/0`.

## Requirements

- Java 21
- The checked-in Maven Wrapper (no system Maven installation is required)

## Use from Maven

The package coordinates are `land.tx3:tx3-sdk:0.15.0`. Until the first Maven Central release,
install the package from this checkout before resolving it from a local consumer:

```shell
./mvnw -B -ntp install
```

```xml
<dependency>
  <groupId>land.tx3</groupId>
  <artifactId>tx3-sdk</artifactId>
  <version>0.15.0</version>
</dependency>
```

Public declarations are in `land.tx3.sdk`; the automatic module name is also `land.tx3.sdk`.

```java
import land.tx3.sdk.Address;
import land.tx3.sdk.ClientOptions;

var address = new Address("addr_test1...");
var options = ClientOptions.forEndpoint(java.net.URI.create("http://localhost:8164"));
```

## Sign transactions

`Ed25519Signer` accepts only a 32-byte private-key seed and an address controlled by that key.
Mnemonic derivation belongs to `CardanoSigner`; both implementations sign the 32-byte
`txHashHex` from `SignRequest` and return a `VKEY` witness.

```java
import land.tx3.sdk.Address;
import land.tx3.sdk.CardanoSigner;
import land.tx3.sdk.SignRequest;

var signer = new CardanoSigner(mnemonic, new Address("addr_test1..."));
var witness = signer.sign(new SignRequest(txHashHex, txCborHex));
```

Key inputs and derived key material are kept in defensive copies and are never written to logs or
error messages. Invalid keys and malformed hashes use the SDK's typed `ValidationException`;
derivation, address-binding, and cryptographic failures use `SigningException`.

## Development

These are the canonical foundation checks:

```shell
./mvnw -B -ntp spotless:check
./mvnw -B -ntp test
./mvnw -B -ntp verify
./mvnw -B -ntp install
./mvnw -B -ntp -f examples/consumer/pom.xml package
```

To record the resolved dependency tree exactly as CI does:

```shell
./mvnw -B -ntp dependency:tree -DoutputFile=target/dependency-tree.txt
```

The test suite is deterministic and needs no TRP endpoint or credentials.

## Platform scope

The supported baseline is Java SE 21 on macOS, Linux, and Windows, on x64 and ARM64 where hosted
runners are available. Android and GraalVM native-image are not supported by this release.

Licensed under Apache-2.0.
