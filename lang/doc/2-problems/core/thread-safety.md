# Thread Safety Anti-Patterns

## The Short Version

The XTC compiler was written for single-threaded batch compilation. Its data structures and access patterns assume one thread, one compilation, then exit. An LSP server is the opposite: long-running, handling concurrent requests, with shared state. Almost every design decision in the codebase violates thread safety principles, making concurrent operation impossible without massive rework.

## What Is the Problem?

Thread safety requires one of three things:
1. **Immutability**: Data that never changes is always safe to share
2. **Isolation**: Each thread has its own copy of mutable data
3. **Synchronization**: Controlled access to shared mutable data

The XTC codebase provides none of these consistently:
- Most data is mutable
- Data is shared globally (constant pools, type caches)
- Synchronization is coarse-grained and inconsistent

## Where Does XTC Do This?

### Anti-Pattern 1: Lazy Initialization Without Synchronization

```java
// Common pattern throughout codebase
private TypeInfo m_typeInfo;  // Shared, mutable, not volatile

public TypeInfo getTypeInfo() {
    if (m_typeInfo == null) {
        m_typeInfo = computeTypeInfo();  // Race condition!
    }
    return m_typeInfo;
}
```

**The bug**: Two threads can both see `m_typeInfo == null`, both compute it, and one's result is lost. 
Worse, without `volatile`, one thread might see a partially-constructed object.

**Files affected**: TypeConstant.java, Constant.java, Component.java, many others

### Anti-Pattern 2: Check-Then-Act Without Locks

```java
// ConstantPool.java - typical pattern
public <T extends Constant> T register(T constant) {
    T existing = findConstant(constant);
    if (existing != null) {
        return existing;  // Check
    }
    addConstant(constant);  // Act - but another thread might have added it!
    return constant;
}
```

**The bug**: Between `findConstant` and `addConstant`, another thread can add the same constant, resulting in duplicates.

### Anti-Pattern 3: Mutable Shared State

```java
// Static shared maps
private static final Map<TypeConstant, TypeInfo> s_typeinfo = new ConcurrentHashMap<>();

// But the TypeInfo itself is mutable!
TypeInfo info = s_typeinfo.get(type);
info.someField = newValue;  // Corrupts shared data
```

Using `ConcurrentHashMap` doesn't help if the values in the map are mutable.

### Anti-Pattern 4: Coarse-Grained Locking

```java
// TypeConstant.java
private synchronized TypeInfo ensureTypeInfo(TypeInfo info, ErrorListener errs) {
    // Hundreds of lines of code under one lock
    // Recursive calls to other ensureTypeInfo
    // Other threads completely blocked
}
```

**The problem**: One thread computing TypeInfo for any type blocks ALL other TypeInfo requests.

### Anti-Pattern 5: Lock Ordering Violations (Potential Deadlocks)

```java
// Thread 1
synchronized (typeA) {
    synchronized (typeB) {
        // ...
    }
}

// Thread 2
synchronized (typeB) {
    synchronized (typeA) {  // DEADLOCK!
        // ...
    }
}
```

The recursive nature of TypeInfo computation creates complex lock acquisition patterns that can deadlock.

### Anti-Pattern 6: Non-Atomic Field Updates

```java
// Constant.java
private transient int m_cRefs;

public void incrementRefs() {
    m_cRefs++;  // NOT atomic! Read-modify-write race condition
}
```

**The bug**: Two threads incrementing simultaneously can lose an increment.

### Anti-Pattern 7: HashMap with Mutable Keys

See: [HashMap Mutable Keys](../data-structures/hashmap-mutable-keys.md)

```java
// Keys whose hashCode changes after insertion
Map<Constant, Something> map = new HashMap<>();
map.put(constant, value);
constant.resolve();  // Changes hashCode!
map.get(constant);   // Returns null - key is "lost"
```

## The Specific Thread Safety Violations

### In ConstantPool

```java
// ConstantPool.java
private final List<Constant> m_listConst = new ArrayList<>();  // Not thread-safe
private final Map<Object, Constant> m_mapConstants = new HashMap<>();  // Not thread-safe

// Synchronized inconsistently
public synchronized void ensureConstant(Constant constant) { ... }
// But other methods accessing these collections aren't synchronized
```

### In TypeConstant

```java
// TypeConstant.java
private static final Map<TypeConstant, TypeInfo> s_typeinfo = new ConcurrentHashMap<>();
// ConcurrentHashMap - good!

// But individual TypeConstants are mutable
private transient Object m_oStructure;  // Cached structure - races
private transient Boolean m_fNullable;  // Cached flag - races
```

### In Component

```java
// Component.java
private Map<String, Component> m_mapChildren;  // Not thread-safe
private Access m_access;                       // Mutable after construction
private List<Contribution> m_listContribs;     // Not thread-safe
```

## Why Does This Block LSP?

### Scenario 1: Concurrent Hover and Completion

```
User hovers over symbol in file A (Thread 1)
  → Needs TypeInfo for ClassX
    → Starts computing TypeInfo

User requests completion in file B (Thread 2)
  → Also needs TypeInfo for ClassX
    → BLOCKED waiting for Thread 1
    → Completion is slow/unresponsive
```

### Scenario 2: Background Compilation Race

```
Background compilation starts (Thread 1)
  → Modifies ConstantPool
  → Updates TypeInfo cache

User requests go-to-definition (Thread 2)
  → Reads from ConstantPool
  → Gets partially-updated data
  → Returns wrong location or crashes
```

### Scenario 3: Edit During Query

```
User types character (Thread 1)
  → Re-parses file
  → Updates AST in place

Hover handler still running (Thread 2)
  → Walking AST from before edit
  → AST mutates under it
  → ConcurrentModificationException or wrong results
```

## The Numbers

| Issue | Count | Files |
|-------|-------|-------|
| `synchronized` methods | 23 | 11 files |
| Mutable shared fields | 200+ | Most files |
| `volatile` fields | 9 | 4 files |
| `AtomicReference` | 6 | 4 files |
| `ConcurrentHashMap` | 5 | 4 files |
| Thread-safe collections | ~10 | Scattered |
| Potential race conditions | 100+ | Pervasive |

## What Should Be Done Instead?

### Solution 1: Immutable Core Data

```java
// Before
public class TypeInfo {
    private Map<String, PropertyInfo> properties;  // Mutable
}

// After
public record TypeInfo(
    Map<String, PropertyInfo> properties  // Immutable - Map.copyOf() in constructor
) {
    public TypeInfo {
        properties = Map.copyOf(properties);
    }
}
```

### Solution 2: Copy-on-Write for Caches

```java
public class TypeInfoCache {
    private volatile Map<TypeConstant, TypeInfo> cache = Map.of();

    public TypeInfo get(TypeConstant type) {
        return cache.get(type);  // No synchronization needed
    }

    public void put(TypeConstant type, TypeInfo info) {
        // Copy-on-write - expensive but thread-safe
        synchronized (this) {
            var newCache = new HashMap<>(cache);
            newCache.put(type, info);
            cache = Map.copyOf(newCache);
        }
    }
}
```

### Solution 3: Thread-Local State

```java
public class CompilationContext {
    private static final ThreadLocal<CompilationContext> CURRENT = new ThreadLocal<>();

    private final ConstantPool localPool;  // Thread's own pool
    private final ErrorListener localErrors;  // Thread's own errors

    public static CompilationContext current() {
        return CURRENT.get();
    }
}
```

### Solution 4: Actor Model for Shared State

```java
public class ConstantPoolActor {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CompletableFuture<Constant> register(Constant c) {
        return CompletableFuture.supplyAsync(() -> {
            // All access serialized through single thread
            return doRegister(c);
        }, executor);
    }
}
```

### Solution 5: Proper Double-Checked Locking

```java
public class TypeConstant {
    private volatile TypeInfo cachedTypeInfo;  // MUST be volatile

    public TypeInfo getTypeInfo() {
        TypeInfo info = cachedTypeInfo;  // Single read
        if (info != null) {
            return info;
        }

        synchronized (this) {
            info = cachedTypeInfo;  // Check again under lock
            if (info != null) {
                return info;
            }
            info = computeTypeInfo();
            cachedTypeInfo = info;  // Volatile write
            return info;
        }
    }
}
```

### Solution 6: Snapshot Architecture for LSP

```java
public class LspSnapshot {
    // Complete immutable view of the world at a point in time
    private final Map<String, SourceFile> files;
    private final Map<String, LspTypeInfo> types;
    private final Map<String, List<LspDiagnostic>> diagnostics;

    // All queries go against the snapshot - never against live compiler
    public Optional<LspTypeInfo> getType(String name) {
        return Optional.ofNullable(types.get(name));
    }
}

public class LspService {
    private volatile LspSnapshot currentSnapshot;

    public void onFileChanged(String file, String content) {
        // Recompile and create new snapshot (can run in background)
        CompletableFuture.runAsync(() -> {
            final LspSnapshot newSnapshot = compile(file, content);
            currentSnapshot = newSnapshot;  // Atomic update
        });
    }

    public LspSnapshot getSnapshot() {
        return currentSnapshot;  // Always consistent
    }
}
```

## Migration Path

### Phase 1: Add Volatile to Caches (Day 1)

```java
// Every lazily-initialized cache field needs volatile
private volatile TypeInfo m_cachedTypeInfo;
private volatile TypeConstant m_resolved;
```

### Phase 2: Fix Double-Checked Locking (Week 1)

Audit all lazy initialization patterns and fix race conditions.

### Phase 3: Use ConcurrentHashMap Correctly (Week 1)

```java
// computeIfAbsent is atomic
cache.computeIfAbsent(key, k -> computeValue(k));
```

### Phase 4: Immutable Data Structures (Weeks 2-3)

Convert core data structures to records with immutable collections.

### Phase 5: LSP Snapshot Layer (Week 4)

Build the snapshot architecture that provides thread-safe queries.

## Thread Safety Checklist

For every field, answer:
- [ ] Is it final? (Immutable)
- [ ] Is it volatile? (Simple thread-safe reads/writes)
- [ ] Is access synchronized? (Complex thread-safe operations)
- [ ] Is the type itself thread-safe? (Using concurrent collections)
- [ ] Is the field thread-local? (Each thread has its own copy)

For every method, answer:
- [ ] Does it read shared mutable state? (Needs synchronization)
- [ ] Does it write shared mutable state? (Needs synchronization)
- [ ] Does it check-then-act? (Needs atomic operation or lock)
- [ ] Does it call other synchronized methods? (Watch for deadlocks)

## Summary

The XTC codebase has pervasive thread safety violations:

| Anti-Pattern | Impact |
|--------------|--------|
| Lazy init without volatile | Partially-constructed objects |
| Check-then-act races | Duplicate entries, lost updates |
| Mutable shared state | Unpredictable corruption |
| Coarse-grained locks | Serialized operations, blocking |
| Non-atomic field updates | Lost increments/decrements |
| Mutable HashMap keys | Lost entries, lookup failures |

**For LSP to work**, the codebase needs:
1. Immutable core data structures (records, List.of(), Map.copyOf())
2. Proper synchronization for remaining mutable state
3. Snapshot architecture for consistent querying
4. Thread-local or actor-isolated mutable state

This is fundamental. You cannot add thread safety as an afterthought to code designed for single-threaded use. It requires architectural change.
