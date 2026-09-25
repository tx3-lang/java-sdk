# Tx3 SDK for Java

The Java SDK is the Java 21 client library for Tx3 protocols. It contains public contract values,
the signer interface, the typed error hierarchy, TII protocol loading and introspection, and an
asynchronous low-level TRP client.

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
var trp = new land.tx3.sdk.TrpClient(options);
```

The low-level client exposes `resolve`, `submit`, and `checkStatus`; each returns a
`CompletableFuture`. Cancelling that future cancels the underlying HTTP operation. Transport
failures are reported as `TransportException`, whose `failure()` discriminator separates network,
HTTP status, JSON-RPC, malformed response, timeout, and cancellation cases without message parsing.

Load a canonical TII document from a path, JSON text, bytes, or a Jackson `JsonNode`:

```java
var protocol = land.tx3.sdk.Protocol.fromFile(java.nio.file.Path.of("transfer.tii"));
var transfer = protocol.transactions().get("transfer");
var quantityType = transfer.parameters().get("quantity");
```

Loading failures throw `ProtocolException`; its `kind()` distinguishes file reads, malformed JSON,
and invalid TII structure without including the document contents in the error.

Native transaction values are encoded with the resolved `ParamType` before transport. Integers use
`BigInteger` (or lossless `int`/`long`) and are checked against signed i128; bytes use defensively
copied `byte[]`; addresses and output references use `Address` and `UtxoRef`. Lists and tuples use
immutable `List` values, while maps, records, and variants use string-keyed insertion-ordered maps.

```java
var encoded = land.tx3.sdk.ArgEncoder.encode(
    new land.tx3.sdk.ParamType.List(new land.tx3.sdk.ParamType.Integer()),
    java.util.List.of(1, 2, 3));
// Jackson wire JSON: {"list":[{"int":1},{"int":2},{"int":3}]}
```

The public sealed `ArgValue` hierarchy also supports generated clients that construct canonical
tagged values directly. `TxBuilder.argTagged(name, value)` stores such a value without repeating
schema-directed encoding. Shape, range, and JSON-encoding failures throw
`ArgumentEncodingException`, whose `kind()`, `path()`, and `expected()` fields contain structural
context without including rejected values.

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
