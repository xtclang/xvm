# Protocol Buffers for Ecstasy

The `protobuf.xtclang.org` module provides a complete Protocol Buffers implementation for the
Ecstasy language. It includes runtime libraries for binary serialization, a `protoc` plugin for
generating Ecstasy source from `.proto` files, and a standalone parser for working with proto
definitions directly.

## Quick Start

### Generating Code with `protoc`

The Ecstasy protoc plugin is named `protoc-gen-xtc` and is installed alongside the `xtc`
executable, so it should already be on your path.

To generate Ecstasy source files from a `.proto` file:

```bash
protoc --xtc_out=build/generated test.proto
```

This reads `test.proto` and writes generated `.x` files into `build/generated`, organized by
package path. For example, given:

```proto
syntax = "proto3";
package com.example;

message Person {
  string name = 1;
  int32 id = 2;
  bool active = 3;
}
```

The plugin produces `build/generated/com/example/Person.x`.

### Plugin Options

Options are passed to the plugin via the `--xtc_opt` flag as comma-delimited `key=value` pairs:

```bash
protoc --xtc_out=build/generated \
       --xtc_opt=packages=google.protobuf=protobuf.wellknown \
       test.proto
```

#### Package Relocations (`packages`)

The `packages` option remaps protobuf package names to different Ecstasy package paths. This is
useful when generated code should live under a different module namespace than the original proto
package.

Each value takes the form `original.package=new.package`. The option can be specified multiple
times (comma-separated) for multiple relocations:

```bash
--xtc_opt=packages=google.protobuf=protobuf.wellknown,packages=mycompany.api=app.api
```

When a package is relocated, generated classes are annotated with `@WellKnownLocation` to record
the original package name:

```ecstasy
@protobuf.WellKnownLocation("google.protobuf")
class Timestamp
        extends protobuf.AbstractMessage {
    // ...
}
```

### Supported Proto Syntaxes

The plugin supports:

- **proto3** — The default. Scalar fields have implicit presence (no `has*()` method) unless
  declared with the `optional` keyword.
- **proto2** — All singular fields have explicit presence with `has*()` methods.
- **Editions (2023, 2024)** — Field presence is controlled by `FeatureSet.fieldPresence`. The
  plugin reads resolved features from each field's options.

## Type Mapping

### Scalar Types

| Proto Type | Ecstasy Type | Default Value |
|------------|-------------|---------------|
| `double` | `Float64` | `0.0` |
| `float` | `Float32` | `0.0` |
| `int32`, `sint32`, `sfixed32` | `Int32` | `0` |
| `int64`, `sint64`, `sfixed64` | `Int64` | `0` |
| `uint32`, `fixed32` | `UInt32` | `0` |
| `uint64`, `fixed64` | `UInt64` | `0` |
| `bool` | `Boolean` | `False` |
| `string` | `String` | `""` |
| `bytes` | `Byte[]` | `[]` |

### Composite Types

| Proto Construct | Ecstasy Type |
|----------------|-------------|
| `message Foo` | `class Foo extends AbstractMessage` |
| `enum Bar` | `enum Bar implements ProtoEnum` |
| `repeated T` | `T[]` (e.g. `String[]`, `Int32[]`) |
| `map<K, V>` | `Map<K, V>` (e.g. `Map<String, Int32>`) |
| `oneof name` | Typedef union type + nullable property |
| Message field | Nullable reference (e.g. `Foo?`) |
| Enum field | Nullable reference (e.g. `Bar?`) |

## Generated Code Structure

### Messages

Each proto message produces an Ecstasy class extending `AbstractMessage`. For this proto:

```proto
syntax = "proto3";

message Person {
  string name = 1;
  int32 id = 2;
  bool active = 3;
  optional string email = 4;
}
```

The generated class looks like:

```ecstasy
class Person
        extends protobuf.AbstractMessage {

    construct(
            String name = "",
            Int32 id = 0,
            Boolean active = False,
            String? email = Null) {
        construct protobuf.AbstractMessage();
        this.name   = name;
        this.id     = id;
        this.active = active;
        if (email != Null) {
            this.email = email;
            presentBits_0 |= 0x1;
        }
    }

    @Override
    construct(Person other) {
        construct protobuf.AbstractMessage(other);
        // ... copies all fields
    }

    // proto3 implicit presence — plain fields with defaults
    String  name   = "";
    Int32   id     = 0;
    Boolean active = False;

    // proto3 explicit presence (optional keyword) — has backing bit
    private Int presentBits_0 = 0;
    private String _email = "";
    String email {
        @Override String get() = _email;
        void set(String value) { _email = value; presentBits_0 |= 0x1; }
    }
    Boolean hasEmail() = presentBits_0 & 0x1 != 0;

    // ... parseField, writeKnownFields, knownFieldsSize, mergeFrom, freeze
}
```

Key points:

- **Proto3 implicit presence**: Fields without `optional` are plain properties. They are
  serialized when their value differs from the default (e.g. `name.size != 0`, `id != 0`).
  No `has*()` method is generated.

- **Explicit presence** (proto2 fields, proto3 `optional`, editions `EXPLICIT`): Fields use
  presence-tracking bitmasks and virtual properties. A `has*()` method is generated.

- **Copy constructor**: A `construct(Person other)` that duplicates all fields, producing an
  independent mutable copy.

### Enums

Each proto enum produces an Ecstasy enum implementing `ProtoEnum`:

```proto
enum Status {
  UNKNOWN = 0;
  ACTIVE = 1;
  INACTIVE = 2;
}
```

Generates:

```ecstasy
enum Status
        implements protobuf.ProtoEnum {

    Unknown(0),
    Active(1),
    Inactive(2);

    construct(Int protoValue) {
        this.protoValue = protoValue;
    }

    @Override
    Int protoValue;
}
```

Proto enum value names are converted from `UPPER_SNAKE_CASE` to `PascalCase`. The `protoValue`
property holds the wire-format integer, which may differ from the Ecstasy ordinal.

To look up an enum constant by its proto value:

```ecstasy
if (Status status := ProtoEnum.byProtoValue(Status.values, 1)) {
    // status == Active
}
```

### Oneof Fields

A proto `oneof` generates a typedef union and a nullable property:

```proto
message Event {
  oneof payload {
    string text = 1;
    int32 code = 2;
    Error error = 3;
  }
}
```

Generates a typedef like `typedef String|Int32|Error as Payload` and a nullable property
`Payload? payload`. Only one field in the oneof can be set at a time; setting one clears the
others.

### Map Fields

Map fields generate `Map<K, V>` properties:

```proto
message Config {
  map<string, string> settings = 1;
}
```

Generates:

```ecstasy
Map<String, String> settings = Map:[];
```

Map entries are serialized as repeated key-value pair messages on the wire, but the generated API
exposes them as a native Ecstasy `Map`.

### Repeated Fields

Repeated fields generate array properties:

```proto
message NumberList {
  repeated int32 values = 1;
  repeated string names = 2;
}
```

Generates:

```ecstasy
Int32[]  values = [];
String[] names  = [];
```

Numeric repeated fields use packed encoding on the wire for efficiency.

### Nested Messages and Enums

Nested proto types become `static class` or inner enum declarations within the enclosing message
class:

```proto
message Outer {
  message Inner {
    int32 value = 1;
  }
  Inner child = 1;
}
```

Generates `Outer.Inner` as a static inner class.

### Services

Proto service definitions generate Ecstasy interfaces:

```proto
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloResponse);
}
```

Generates:

```ecstasy
interface Greeter {
    HelloResponse sayHello(HelloRequest request);
}
```

Streaming RPCs generate a placeholder `TODO` method with a comment indicating that streaming is
not yet supported.

## Runtime API

### Serialization and Deserialization

All generated message classes inherit serialization methods from `MessageLite`:

```ecstasy
// Serialize to bytes
Person person = new Person(name="Alice", id=42, active=True);
immutable Byte[] bytes = person.toByteArray();

// Deserialize from bytes
Person restored = new Person();
restored.mergeFromBytes(bytes);

// Serialize to a stream
person.writeTo(outputStream);

// Deserialize from a stream
Person fromStream = new Person();
fromStream.mergeFrom(inputStream);
```

### Merging

The `mergeFrom` method follows standard protobuf merge semantics:

- **Scalar fields**: Last value wins.
- **Message fields**: Recursively merged.
- **Repeated fields**: Appended.
- **Map fields**: Entries merged (last value wins per key).

```ecstasy
Person base = new Person(name="Alice", id=1);
Person update = new Person(id=2, active=True);
base.mergeFrom(update);
// base.name == "Alice", base.id == 2, base.active == True
```

### Immutability

Messages implement `Freezable`. Call `freeze()` to produce an immutable snapshot:

```ecstasy
immutable Person frozen = person.freeze();
```

Once frozen, a message cannot be modified. To create a mutable copy from a frozen message, use
the copy constructor:

```ecstasy
Person mutable = new Person(frozen);
mutable.name = "Bob";  // OK — this is a new mutable copy
```

### Unknown Fields

Generated messages extend `AbstractMessage`, which automatically preserves unknown fields
encountered during deserialization. This enables faithful round-tripping: if a message is
deserialized from a newer schema that has fields the current code doesn't know about, those
fields are preserved when the message is re-serialized.

### GenericMessage

`GenericMessage` provides schema-free access to protobuf binary data. It stores all fields by
field number without interpreting them according to a schema:

```ecstasy
GenericMessage msg = new GenericMessage();
msg.mergeFromBytes(bytes);

// Access fields by number
if (msg.hasField(1)) {
    String name = msg.getString(1);
}
Int64 id = msg.getVarint(2);

// Build a message dynamically
GenericMessage out = new GenericMessage();
out.setString(1, "hello");
out.setVarint(2, 42);
immutable Byte[] encoded = out.toByteArray();
```

This is useful for middleware, proxies, or tools that need to inspect or forward protobuf
messages without compiled schemas.

## Parsing Proto Files

The `ProtoParser` class can parse `.proto` file text directly into a `FileDescriptorProto`,
without needing `protoc`:

```ecstasy
import protobuf.ProtoParser;
import protobuf.wellknown.FileDescriptorProto;

String protoSource = $|syntax = "proto3";
                      |message Person \{
                      |  string name = 1;
                      |  int32 id = 2;
                      |}
                     ;

ProtoParser parser = new ProtoParser(protoSource);
FileDescriptorProto descriptor = parser.parseFile("person.proto");
```

This is useful for testing, tooling, or scenarios where invoking `protoc` is not practical.

### Programmatic Code Generation

You can combine `ProtoParser` with `ProtoCodeGen` to generate Ecstasy source without `protoc`:

```ecstasy
import protobuf.ProtoParser;
import protobuf.ProtoCodeGen;
import protobuf.wellknown.FileDescriptorProto;

ProtoParser parser = new ProtoParser(protoSource);
FileDescriptorProto descriptor = parser.parseFile("test.proto");

ProtoCodeGen codeGen = new ProtoCodeGen();
Map<String, String> files = codeGen.generate(descriptor);
// files maps file paths (e.g. "Person.x") to generated source text
```

To pass options (e.g. package relocations) programmatically:

```ecstasy
Map<String, String[]> options = new HashMap();
options.put("packages", ["google.protobuf=protobuf.wellknown"]);

ProtoCodeGen codeGen = new ProtoCodeGen(options);
```

## Low-Level Wire Format API

For advanced use cases, `CodedInput` and `CodedOutput` provide direct access to the protobuf
binary wire format.

### Writing

```ecstasy
import protobuf.CodedOutput;

ByteArrayOutputStream buf = new ByteArrayOutputStream();
CodedOutput out = new CodedOutput(buf);

out.writeString(1, "hello");
out.writeInt32(2, 42);
out.writeBool(3, True);
out.writeMessage(4, someMessage);

immutable Byte[] bytes = buf.bytes.freeze(inPlace=True);
```

### Reading

```ecstasy
import protobuf.CodedInput;
import protobuf.WireType;

CodedInput input = new CodedInput(new ByteArrayInputStream(bytes));

while (!input.isAtEnd()) {
    Int tag = input.readTag();
    Int fieldNumber = WireType.getFieldNumber(tag);
    WireType wireType = WireType.getWireType(tag);

    switch (fieldNumber) {
    case 1:
        String name = input.readString();
        break;
    case 2:
        Int32 id = input.readInt32();
        break;
    case 3:
        Boolean active = input.readBool();
        break;
    default:
        input.skipField(wireType);
        break;
    }
}
```

### Size Computation

`CodedOutput` provides static methods to compute the serialized size of fields without actually
writing them. This is used internally by `serializedSize()` and is necessary when writing
embedded messages (the length must be known before writing):

```ecstasy
Int size = 0;
size += CodedOutput.computeStringSize(1, "hello");   // tag + length + UTF-8 bytes
size += CodedOutput.computeInt32Size(2, 42);          // tag + varint
size += CodedOutput.computeBoolSize(3);               // tag + 1 byte
size += CodedOutput.computeMessageSize(4, someMsg);   // tag + length + message bytes
```

## Well-Known Types

The module includes pre-built descriptors for the standard Google well-known types under
`protobuf.wellknown`. References to `google.protobuf.*` types in `.proto` files are
automatically rewritten to `protobuf.wellknown.*` during code generation.

These include the descriptor types themselves (`FileDescriptorProto`, `DescriptorProto`,
`FieldDescriptorProto`, etc.), the compiler types (`CodeGeneratorRequest`,
`CodeGeneratorResponse`), and common types like `FeatureSet`, `SourceCodeInfo`, and `Edition`.

## Wire Format Reference

Protobuf uses a tag-length-value (TLV) binary encoding. Each field is preceded by a tag that
encodes the field number and wire type:

| Wire Type | Name | Used For |
|-----------|------|----------|
| 0 | VARINT | `int32`, `int64`, `uint32`, `uint64`, `sint32`, `sint64`, `bool`, `enum` |
| 1 | I64 | `fixed64`, `sfixed64`, `double` |
| 2 | LEN | `string`, `bytes`, embedded messages, packed repeated fields |
| 5 | I32 | `fixed32`, `sfixed32`, `float` |

The tag is computed as `(field_number << 3) | wire_type`. For example, field 2 with wire type
VARINT is tag `0x10` (16).

### Encoding Details

- **Varint**: Variable-length encoding where each byte uses 7 data bits and 1 continuation bit.
  Smaller values use fewer bytes.
- **ZigZag** (`sint32`, `sint64`): Maps signed integers to unsigned before varint encoding, so
  small negative numbers use fewer bytes: `(n << 1) ^ (n >> 31)`.
- **Fixed**: Always uses exactly 4 or 8 bytes in little-endian order.
- **Length-delimited**: A varint length prefix followed by that many bytes of payload.
- **Packed repeated**: Multiple values of the same field concatenated into a single
  length-delimited payload (used for numeric types by default in proto3).
