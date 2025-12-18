# The TypeInfo Bottleneck

## The Short Version

`TypeInfo` is the central data structure that holds "flattened" type information in the XTC compiler. Every type lookup, 
method resolution, and property access flows through it. But `TypeInfo` is computed lazily, cached globally, 
and accessed under a `synchronized` lock. This creates a massive bottleneck that prevents any concurrent type 
queries - exactly what an LSP needs to do constantly.

## What Is the Problem?

When you need to know what methods a type has, or whether one type is assignable to another, you call 
`ensureTypeInfo()`. This method:

1. Checks a global cache for existing TypeInfo
2. If not found, **acquires a lock** and computes the full TypeInfo
3. TypeInfo computation recursively calls `ensureTypeInfo()` on related types
4. The result is cached globally

The critical issue: **TypeInfo computation is synchronized at the method level**, meaning only one thread can 
compute TypeInfo for a given type at a time, and the computation can trigger recursive calls that compute other types.

## Where Does XTC Do This?

### The Synchronized Bottleneck (TypeConstant.java:1632)

```java
private synchronized TypeInfo ensureTypeInfo(TypeInfo info, ErrorListener errs) {
    // ... hundreds of lines of computation
    // This lock is held for the ENTIRE computation
}
```

This `synchronized` keyword means:
- Thread A starts computing TypeInfo for `MyClass`
- Thread B wants TypeInfo for `MyClass` - **blocked**
- Thread C wants TypeInfo for `MyInterface` (extends something that needs `MyClass`) - **potentially blocked**

### Recursive TypeInfo Dependencies

The TypeInfo computation calls `ensureTypeInfo()` on many related types:

```java
// TypeConstant.java:2140 - computing extends
TypeInfo infoExtend = contrib.getTypeConstant().ensureTypeInfo();

// TypeConstant.java:2181 - computing annotations
TypeInfo infoAnno = typeAnnoPrivate.ensureTypeInfoInternal(errs);

// TypeConstant.java:2224 - computing access variants
TypeInfo infoPri = pool.ensureAccessTypeConstant(getUnderlyingType(), Access.PRIVATE)
                   .ensureTypeInfoInternal(errs);

// TypeConstant.java:3009 - computing mixin contributions
TypeInfo infoContrib = typeContrib.adjustAccess(constId).ensureTypeInfoInternal(errs);
```

A single `ensureTypeInfo()` call can trigger dozens of recursive TypeInfo computations.

### The Global Cache (TypeConstant.java:1814)

```java
protected TypeInfo getTypeInfo() {
    return s_typeinfo.get(this);  // Static global cache
}
```

The cache `s_typeinfo` is a static map shared across all threads. While the map itself might be concurrent, the population of entries is synchronized per-type.

### Monster Constructor (TypeInfo.java:69-138)

TypeInfo has a constructor with **20 parameters**:

```java
public TypeInfo(
    TypeConstant type,
    int cInvalidations,
    ClassStructure struct,
    int cDepth,
    boolean fSynthetic,
    Map<Object, ParamInfo> mapTypeParams,
    Annotation[] aannoClass,
    Annotation[] aannoMixin,
    TypeConstant typeExtends,
    TypeConstant typeRebases,
    TypeConstant typeInto,
    List<Contribution> listProcess,
    ListMap<IdentityConstant, Origin> listmapClassChain,
    ListMap<IdentityConstant, Origin> listmapDefaultChain,
    Map<PropertyConstant, PropertyInfo> mapProps,
    Map<MethodConstant, MethodInfo> mapMethods,
    Map<Object, PropertyInfo> mapVirtProps,
    Map<Object, MethodInfo> mapVirtMethods,
    ListMap<String, ChildInfo> mapChildren,
    Set<TypeConstant> setDepends,
    Progress progress
)
```

This is a "god object" that holds everything about a type. Computing all of this takes significant time.

## Why Does This Block LSP?

### Problem 1: Hover Information Blocks

When user hovers over a type, LSP needs TypeInfo:

```java
// LSP hover handler
TypeInfo info = type.ensureTypeInfo();  // BLOCKS if another thread is computing
String hoverText = formatTypeInfo(info);
```

If compilation is running in the background (also calling `ensureTypeInfo`), hover becomes unresponsive.

### Problem 2: Completion Serialization

Auto-completion needs TypeInfo for all visible types:

```java
// LSP completion handler
final var items = new ArrayList<CompletionItem>();
for (final TypeConstant type : visibleTypes) {
    final TypeInfo info = type.ensureTypeInfo();  // Each one potentially BLOCKS
    items.addAll(getMethodCompletions(info));
}
```

With hundreds of types in scope, this can take seconds if locks are contended.

### Problem 3: Cache Invalidation Chaos

The code explicitly tracks invalidation counts:

```java
// TypeConstant.java:1636
int cInvalidations = pool.getInvalidationCount();

// TypeInfo.java:136
assert cInvalidations == 0
    || cInvalidations <= type.getConstantPool().getInvalidationCount();
```

When types change, cached TypeInfo becomes stale. But invalidation doesn't happen atomically - there are windows where:
- TypeInfo A depends on TypeInfo B
- B is invalidated but A is not yet
- Queries against A return stale data

### Problem 4: Incomplete TypeInfo States

The system has a complex state machine for partially-computed TypeInfo:

```java
// TypeConstant.java:1656-1667 (comment block)
// this is where things get very, very complicated. this method is responsible for returning
// a "completed" TypeInfo, but there are (theoretically) lots of threads trying to do the
// same or similar thing at the same time, and any thread can end up in a recursive
// situation in which to complete the TypeInfo for type X, it has to get the TypeInfo for
// type Y, and to build that, it has to get the TypeInfo for type X. this is a catch-22!
```

The code acknowledges the threading problem but "solves" it with coarse-grained locking and incomplete placeholders - not suitable for LSP.

## The Numbers

```java
// From grep analysis
ensureTypeInfo() calls:    50+ locations
synchronized blocks:       23 files
TypeInfo constructor:      20 parameters
TypeInfo fields:          ~25 fields
ConcurrentHashMap usage:   2 (for method caches)
```

## What Should Be Done Instead?

### Solution 1: Read-Write Lock

Replace method-level synchronization with read-write lock:

```java
public class TypeConstant {
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    public TypeInfo getTypeInfo() {
        lock.readLock().lock();
        try {
            return cache.get(this);
        } finally {
            lock.readLock().unlock();
        }
    }

    public TypeInfo ensureTypeInfo(ErrorListener errs) {
        // Try read first
        TypeInfo cached = getTypeInfo();
        if (cached != null && isValid(cached)) {
            return cached;
        }

        // Need to compute - acquire write lock
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            cached = cache.get(this);
            if (cached != null && isValid(cached)) {
                return cached;
            }
            return computeTypeInfo(errs);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

### Solution 2: Per-Type Fine-Grained Locking

Lock at the individual type level, not globally:

```java
public class TypeConstant {
    private final Object typeInfoLock = new Object();
    private volatile TypeInfo cachedInfo;

    public TypeInfo ensureTypeInfo(ErrorListener errs) {
        TypeInfo info = cachedInfo;
        if (info != null && isValid(info)) {
            return info;
        }

        synchronized (typeInfoLock) {  // Only locks THIS type
            info = cachedInfo;
            if (info != null && isValid(info)) {
                return info;
            }
            info = computeTypeInfo(errs);
            cachedInfo = info;
            return info;
        }
    }
}
```

### Solution 3: Separate Type Model

For LSP, build a separate type model that doesn't share state with compilation:

```java
// LSP type model - completely separate from compiler's TypeInfo
public record LspTypeInfo(
    String qualifiedName,
    List<LspMethodInfo> methods,
    List<LspPropertyInfo> properties,
    List<String> typeParameters,
    @Nullable String superType,
    Set<String> interfaces
) {
    // Immutable, safe to share across threads

    public static LspTypeInfo fromCompilerTypeInfo(TypeInfo info) {
        // Snapshot compiler's TypeInfo into our immutable structure
        TypeConstant extendType = info.getExtends();
        return new LspTypeInfo(
            info.getType().getValueString(),
            extractMethods(info),
            extractProperties(info),
            extractTypeParams(info),
            extendType == null ? null : extendType.getValueString(),
            extractInterfaces(info)
        );
    }
}
```

### Solution 4: Async TypeInfo Loading

Make TypeInfo computation non-blocking:

```java
public class TypeInfoService {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<TypeConstant, CompletableFuture<TypeInfo>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<TypeInfo> getTypeInfoAsync(final TypeConstant type) {
        return pending.computeIfAbsent(type, t ->
            CompletableFuture.supplyAsync(() -> t.ensureTypeInfo(), executor)
                .whenComplete((info, err) -> pending.remove(t))
        );
    }

    // For LSP hover - returns cached if available, otherwise starts async load
    public @Nullable TypeInfo getTypeInfoIfCached(final TypeConstant type) {
        final CompletableFuture<TypeInfo> future = pending.get(type);
        if (future != null && future.isDone()) {
            return future.getNow(null);
        }
        return null;
    }
}
```

### Solution 5: Break Up TypeInfo

TypeInfo is too big. Split into lazy-loaded components:

```java
// Core type identity - always loaded
public record TypeIdentity(
    String qualifiedName,
    List<String> typeParameters,
    TypeKind kind
) {}

// Methods - loaded on demand
public record TypeMethods(
    Map<String, MethodInfo> byName,
    Map<SignatureConstant, MethodInfo> bySignature
) {}

// Properties - loaded on demand
public record TypeProperties(
    Map<String, PropertyInfo> byName
) {}

// Full type info composed lazily
public class LazyTypeInfo {
    private final TypeIdentity identity;
    private volatile TypeMethods methods;
    private volatile TypeProperties properties;

    public TypeMethods getMethods() {
        TypeMethods m = methods;
        if (m == null) {
            synchronized (this) {
                m = methods;
                if (m == null) {
                    m = methods = loadMethods();
                }
            }
        }
        return m;
    }
}
```

## Migration Path

### Phase 1: Add Volatile (Day 1)

Make TypeInfo caching use proper memory visibility:

```java
// TypeConstant.java
private volatile TypeInfo m_cachedTypeInfo;  // Instance-level cache
```

### Phase 2: Per-Instance Locking (Week 1)

Move from class-level synchronized to instance-level:

```java
private final Object m_typeInfoLock = new Object();

public TypeInfo ensureTypeInfo(final ErrorListener errs) {
    synchronized (m_typeInfoLock) {  // Instance lock, not class lock
        // ...
    }
}
```

### Phase 3: Read-Write Lock (Week 2)

Implement proper read-write separation for the global cache.

### Phase 4: LSP-Specific Model (Week 3-4)

Build `LspTypeInfo` that snapshots compiler TypeInfo into immutable structures.

## Summary

TypeInfo is the choke point of the entire type system:

| Problem | Impact on LSP |
|---------|---------------|
| synchronized method | All type queries serialize |
| Global cache | No isolation between requests |
| Recursive computation | Lock held during dependent type loading |
| 20-param constructor | All-or-nothing computation |
| Invalidation tracking | Stale data windows |

**The fix requires:**
1. Finer-grained locking (per-type, not global)
2. Read-write separation (most ops are reads)
3. Separate LSP model (don't share mutable state with compiler)
4. Async loading (don't block UI thread on type computation)
5. Lazy decomposition (don't compute everything upfront)

Without these changes, any LSP feature that needs type information will be slow and unresponsive during compilation.
