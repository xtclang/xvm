# Beyond the Command Line - Distributed Architecture Requirements

## The Short Version

The XTC compiler is designed for one use case: a developer running `xtc compile MyApp.x` on their local machine. But this is the **least common** way XTC will actually be used. Real-world usage includes:

- IDE integration via LSP (most common developer interaction)
- Cloud-based compilation services (CI/CD, app stores)
- Distributed build systems (Gradle, Bazel with remote caching)
- Container-based build environments
- The XTC App Store infrastructure itself

The current architecture assumes "single command line on a single machine" and provides no support for wire protocols, serialization, or distributed operation.

## The Reality of Modern Development

### How Developers Actually Interact with Compilers

| Method | Frequency | Current Support |
|--------|-----------|-----------------|
| IDE hover/completion | Thousands/day | None |
| IDE diagnostics | Hundreds/day | None |
| Local command line | Few times/day | Yes |
| CI/CD pipeline | Per commit | Partial |
| Remote build cache | Every build | None |
| Cloud compilation | Increasing | None |

The command line is the **fallback**, not the primary interface. Yet 100% of the design effort went into it.

### The XTC App Store Model

XTC's business model centers on an **App Store**. Apps are compiled, packaged, and distributed through a centralized service. This means:

- Compilation happens on **servers**, not developer machines
- Results need to be **cached** and **distributed**
- Build metadata needs to be **serialized** for storage
- Multiple services need to **coordinate**

None of this is possible with the current architecture.

## What's Missing

### 1. Wire Protocols

The compiler has no concept of communicating over a network:

```java
// Current: Everything is in-process Java objects
TypeInfo info = type.ensureTypeInfo();
// Can't send TypeInfo over a network
// Can't cache TypeInfo to disk
// Can't share TypeInfo between processes
```

What's needed:
```java
// Serializable representation
TypeInfoProto info = type.ensureTypeInfo().toProto();
byte[] bytes = info.toByteArray();  // Send over network
TypeInfoProto restored = TypeInfoProto.parseFrom(bytes);  // Receive
```

### 2. Serialization Support

The codebase has no serialization story:

- No JSON serialization for web APIs
- No Protocol Buffers for efficient binary
- No Avro/Thrift for schema evolution
- Java Serialization is present but broken (uses Cloneable, transient misuse)

### 3. Stateless Operations

Every operation assumes long-lived in-memory state:

```java
// Current: Must have entire ConstantPool in memory
ConstantPool pool = module.getConstantPool();
TypeConstant type = pool.ensureType(signature);

// Needed: Stateless operations
TypeConstant type = TypeService.resolveType(moduleId, signature);
// Service can be remote, load-balanced, cached
```

### 4. Incremental/Cacheable Results

No support for build caching:

```java
// Current: Full recompilation every time
compiler.compile(sources);  // All or nothing

// Needed: Cacheable compilation units
CompileResult result = compiler.compile(source);
cache.store(source.hash(), result.toBytes());

// Later, from another machine
CompileResult cached = cache.get(source.hash());
```

### 5. Request/Response Model

The compiler is a monolithic process, not a service:

```java
// Current: One shot
public static void main(String[] args) {
    Compiler compiler = new Compiler();
    compiler.compile(args);
    System.exit(0);
}

// Needed: Service model
public class CompilerService {
    @POST("/compile")
    public CompileResponse compile(CompileRequest request) {
        // Stateless, can handle concurrent requests
        // Can run in container, scale horizontally
    }
}
```

## Wire Protocol Options

### Option 1: JSON (Simple, Universal)

Pros:
- Human readable
- Universal support
- Easy debugging
- Works with any HTTP client

Cons:
- Verbose (large payloads)
- Slow parsing
- No schema enforcement

Best for:
- Web APIs
- Debugging interfaces
- Configuration

```java
// Example
public record CompileRequest(
    String sourcePath,
    String content,
    List<String> options
) {}

// Jackson serialization
String json = objectMapper.writeValueAsString(request);
```

### Option 2: Protocol Buffers (Efficient, Typed)

Pros:
- Compact binary format
- Strong typing with schemas
- Fast serialization/deserialization
- Schema evolution support
- gRPC integration

Cons:
- Requires .proto files
- Not human readable
- Additional tooling

Best for:
- High-volume communication
- Service-to-service
- Build caching

```protobuf
// compiler.proto
message CompileRequest {
    string source_path = 1;
    bytes content = 2;
    repeated string options = 3;
}

message CompileResponse {
    bool success = 1;
    repeated Diagnostic diagnostics = 2;
    bytes compiled_module = 3;
}

message TypeInfo {
    string qualified_name = 1;
    TypeKind kind = 2;
    repeated MethodInfo methods = 3;
    repeated PropertyInfo properties = 4;
}
```

### Option 3: FlatBuffers (Zero-Copy)

Pros:
- Zero-copy access
- Even faster than protobuf
- Good for large data structures

Cons:
- More complex API
- Less tooling support

Best for:
- Very large ASTs
- Memory-mapped caches

### Option 4: Language Server Protocol (Standard for IDEs)

LSP is already a JSON-RPC wire protocol:

```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "textDocument/hover",
    "params": {
        "textDocument": {"uri": "file:///path/to/file.x"},
        "position": {"line": 10, "character": 5}
    }
}
```

The [LSP implementation](./implementation.md) already handles this for IDE features.

## Distributed Architecture Design

### Build Service Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Developer IDE                            │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐                   │
│  │  VS Code  │  │  IntelliJ │  │   Helix   │                   │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘                   │
│        │              │              │                          │
│        └──────────────┴──────────────┘                          │
│                       │ LSP (JSON-RPC)                          │
├───────────────────────┼─────────────────────────────────────────┤
│                 ┌─────▼─────┐                                   │
│                 │ LSP Server│  (Local or Remote)                │
│                 └─────┬─────┘                                   │
│                       │ gRPC/Protobuf                           │
├───────────────────────┼─────────────────────────────────────────┤
│               ┌───────▼───────┐                                 │
│               │ Build Service │                                 │
│               │   (Stateless) │                                 │
│               └───────┬───────┘                                 │
│          ┌────────────┼────────────┐                            │
│          ▼            ▼            ▼                            │
│   ┌───────────┐ ┌───────────┐ ┌───────────┐                    │
│   │TypeService│ │  Compile  │ │  Cache    │                    │
│   │  (gRPC)   │ │  Service  │ │  Service  │                    │
│   └───────────┘ └───────────┘ └───────────┘                    │
│                       │                                         │
│                       ▼                                         │
│               ┌───────────────┐                                 │
│               │  Distributed  │                                 │
│               │     Cache     │                                 │
│               │  (Redis/etc)  │                                 │
│               └───────────────┘                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

**LSP Server** (see [implementation](./implementation.md))
- Handles IDE communication
- Maintains local state
- Delegates to build service

**Build Service**
- Stateless compilation
- Horizontal scaling
- Container-friendly

**Type Service**
- Type resolution
- Caches TypeInfo
- Serves type queries

**Cache Service**
- Compilation artifact caching
- Content-addressable storage
- Distributed across regions

### Data Flow Example

```
1. Developer types in IDE
2. IDE sends LSP request (JSON-RPC over stdio/socket)
3. LSP server extracts request
4. LSP server checks local cache
5. If miss, calls Build Service (gRPC)
6. Build Service checks distributed cache (Redis)
7. If miss, compiles (using cached dependencies)
8. Result stored in cache (protobuf serialized)
9. Response flows back to IDE
10. IDE displays result
```

## Serialization Requirements

Every data structure that needs to cross a boundary (process, network, cache) needs:

### 1. Deterministic Serialization

```java
// Same input → same bytes → same hash
byte[] bytes1 = serialize(typeInfo);
byte[] bytes2 = serialize(typeInfo);
assert Arrays.equals(bytes1, bytes2);  // Must be true for caching
```

### 2. Schema Evolution

```protobuf
// Version 1
message TypeInfo {
    string name = 1;
}

// Version 2 - backwards compatible
message TypeInfo {
    string name = 1;
    repeated string type_params = 2;  // New field
}
```

### 3. Efficient Representation

```java
// Current - wasteful
public class TypeInfo {
    private String qualifiedName;  // "org.example.MyClass<T, U>"
    // Repeats package, class name everywhere
}

// Better - intern common strings
public class TypeInfoProto {
    int package_id;   // Index into string table
    int class_id;     // Index into string table
    List<int> params; // Indexes into type table
}
```

## What Needs to Change

### 1. Define Wire Types

Create protobuf schemas for all serializable types:

```protobuf
// lsp.proto
message Location {
    string file = 1;
    int32 line = 2;
    int32 column = 3;
}

message TypeSymbol {
    string name = 1;
    string qualified_name = 2;
    Location definition = 3;
    repeated MethodSymbol methods = 4;
    repeated PropertySymbol properties = 5;
}

message CompilationUnit {
    string source_hash = 1;
    bytes compiled_module = 2;
    repeated TypeSymbol types = 3;
    repeated Diagnostic diagnostics = 4;
}
```

### 2. Add Serialization Layer

```java
public interface Serializable<T extends Message> {
    T toProto();
    static <T> T fromProto(Message proto);
}

public record LspTypeInfo(...) implements Serializable<TypeSymbolProto> {
    @Override
    public TypeSymbolProto toProto() {
        return TypeSymbolProto.newBuilder()
            .setName(name)
            .setQualifiedName(qualifiedName)
            .setDefinition(definition.toProto())
            .addAllMethods(methods.stream().map(MethodSymbol::toProto).toList())
            .build();
    }
}
```

### 3. Build Stateless Services

```java
@Service
public class CompilerService {
    private final DistributedCache cache;

    public CompileResponse compile(CompileRequest request) {
        // Check cache
        String hash = computeHash(request);
        Optional<CompileResponse> cached = cache.get(hash);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Compile
        CompileResponse response = doCompile(request);

        // Cache result
        cache.put(hash, response);
        return response;
    }
}
```

### 4. Container-Ready Design

```dockerfile
FROM eclipse-temurin:21-jre
COPY xtc-compiler-service.jar /app/
EXPOSE 8080
ENV XTC_CACHE_URL=redis://cache:6379
CMD ["java", "-jar", "/app/xtc-compiler-service.jar"]
```

```yaml
# kubernetes deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: xtc-compiler
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: compiler
        image: xtc/compiler-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: XTC_CACHE_URL
          valueFrom:
            secretKeyRef:
              name: xtc-secrets
              key: cache-url
```

## The App Store Perspective

The XTC App Store needs:

1. **Remote Compilation**
   - Accept source code via API
   - Compile on server
   - Return compiled module

2. **Build Verification**
   - Reproducible builds
   - Content-addressed artifacts
   - Deterministic hashing

3. **Artifact Storage**
   - Efficient binary storage
   - Content deduplication
   - Global distribution (CDN)

4. **Dependency Resolution**
   - Remote dependency fetching
   - Version resolution
   - Security scanning

None of this works without:
- Wire protocols for communication
- Serialization for storage
- Stateless services for scaling
- Caching for performance

## Implementation Priority

### Phase 1: LSP Foundation (Now)
- JSON-RPC for IDE communication (standard LSP)
- Local compilation only
- [Adapter layer](./adapter-layer-design.md) and [implementation](./implementation.md)

### Phase 2: Protobuf Schemas (2-4 weeks)
- Define schemas for all LSP model types
- Add protobuf serialization
- Enable build caching

### Phase 3: Service Architecture (1-2 months)
- Extract stateless compiler service
- Add gRPC endpoints
- Implement distributed caching

### Phase 4: App Store Integration (Ongoing)
- Remote compilation API
- Artifact storage
- Dependency management

## Summary

The XTC tools are designed for a world that doesn't exist anymore:
- Single developer on single machine
- Command-line-only interaction
- No caching, no distribution
- No wire protocols

The real world needs:
- IDE integration as primary interface
- Distributed compilation services
- Cloud-native, container-ready design
- Wire protocols for every boundary

**The [adapter layer](./adapter-layer-design.md) is the starting point.** It provides the clean data model that can be serialized for any wire protocol. Build on that foundation to create a modern, distributed compiler infrastructure.
