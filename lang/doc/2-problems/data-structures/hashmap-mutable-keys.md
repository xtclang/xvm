# HashMap with Mutable Keys - The Silent Data Corruption

## The Core Problem

When you use an object as a key in a `HashMap`, that object's `hashCode()` and `equals()` methods 
**must remain stable**. If they change after the object is inserted as a key, the HashMap becomes corrupted:

- Lookups return `null` for keys that exist
- The same key can be inserted multiple times
- `contains()` returns `false` for keys that are in the map
- Iteration may skip or duplicate entries

**The XVM codebase uses mutable objects as HashMap keys throughout**, and the `hashCode()` implementation explicitly 
returns different values as objects mutate.

## The HashMap Contract (From Java Documentation)

> **Warning**: great care must be exercised if mutable objects are used as map keys. The behavior of a map is not specified if the value of an object is changed in a manner that affects equals comparisons while the object is a key in the map.

This isn't a suggestion. It's a **requirement**. Violating it causes silent data corruption.

## Real Example: Constant as HashMap Key

### The Problem Code

```java
// Constant.java line 560-568
@Override
public int hashCode() {
    int iHash = m_iHash;
    return iHash == 0 ? computeHashCodeInternal() : iHash;
}

protected int computeHashCodeInternal() {
    if (containsUnresolved()) {
        return 0;  // Returns ZERO when unresolved!
    }
    int iHash = Hash.of(getClass().getName(), computeHashCode());
    return m_iHash = iHash == 0 ? 7654211 : iHash;  // Caches and returns different value!
}
```

### What This Means

1. **Before resolution**: `hashCode()` returns `0`
2. **After resolution**: `hashCode()` returns a computed non-zero value
3. **The value changes**: The same object returns different hash codes at different times

### How HashMap Uses hashCode

```java
// Simplified HashMap.put():
int bucket = key.hashCode() % buckets.length;  // Bucket determined by hashCode
buckets[bucket].add(entry);

// Simplified HashMap.get():
int bucket = key.hashCode() % buckets.length;  // MUST find same bucket!
return buckets[bucket].find(key);
```

### The Corruption Scenario

```java
// 1. Create an unresolved constant
TypeConstant unresolvedType = createUnresolvedType();
// hashCode() returns 0

// 2. Use it as a HashMap key
Map<TypeConstant, SomeInfo> map = new HashMap<>();
map.put(unresolvedType, info);
// Entry goes into bucket 0 (because hashCode was 0)

// 3. Constant gets resolved (during compilation)
unresolvedType.resolve();
// Now hashCode() returns, say, 12345

// 4. Try to retrieve it
SomeInfo result = map.get(unresolvedType);
// HashMap looks in bucket 12345 % size
// Entry is in bucket 0
// Returns NULL even though the key IS in the map!

// 5. Check if it's there
map.containsKey(unresolvedType);  // Returns FALSE!

// 6. Put it again (thinking it's not there)
map.put(unresolvedType, newInfo);
// Now the map has TWO entries for the "same" key!
```

### Real Usage in Codebase

```java
// ConstantPool.java - uses Constants as map keys
private volatile EnumMap<Format, Map<Object, Constant>> m_mapLocators = new EnumMap<>(Format.class);

// TypeConstant.java - uses TypeConstants as map keys
Map<Object, ParamInfo> mapTypeParams = new HashMap<>();
Map<Object, MethodInfo> mapVirtMethods = new HashMap<>();

// TypeInfo.java - stores type information keyed by potentially mutable keys
private final Map<Object, MethodInfo> f_cacheByNid = new ConcurrentHashMap<>(mapVirtMethods);
```

## The equals() Problem

It's not just `hashCode()` - `equals()` also uses mutable state:

```java
// Constant.java line 598-607
@Override
public boolean equals(Object obj) {
    if (!(obj instanceof Constant that)) {
        return false;
    }

    Constant constThis = this.resolve();  // MUTATION! resolve() can change state
    Constant constThat = that.resolve();  // MUTATION!
    return constThis == constThat || (constThis.getFormat() == constThat.getFormat()
            && constThis.compareDetails(constThat) == 0);
}
```

**The `equals()` method calls `resolve()`**, which can mutate the object and change its `hashCode()`. This means:

```java
map.get(key);  // This call to equals() might CHANGE the key's hashCode!
```

## Real Bug Scenarios

### Scenario 1: Lost Entries

```java
// During compilation
Map<TypeConstant, TypeInfo> typeCache = new HashMap<>();
typeCache.put(unresolvedType, info);

// ... later, after resolution happens elsewhere ...
TypeInfo cached = typeCache.get(unresolvedType);
// Returns null! Entry is lost.

// Code falls back to recomputing, wasting time
// Or worse, proceeds with null and crashes later
```

### Scenario 2: Duplicate Keys

```java
Map<Constant, Integer> refCounts = new HashMap<>();
refCounts.put(const1, 1);

// const1 gets resolved
const1.resolve();

// Check and increment
Integer count = refCounts.get(const1);
if (count == null) {
    refCounts.put(const1, 1);  // DUPLICATE ENTRY!
} else {
    refCounts.put(const1, count + 1);
}

// Now map has two entries for const1, with counts 1 and 1
// Total should be 2, but you'll only see 1 when you look it up
```

### Scenario 3: ConcurrentHashMap Deadlock

`ConcurrentHashMap` is even more sensitive:

```java
// ConcurrentHashMap.computeIfAbsent():
// 1. Computes bucket from hashCode()
// 2. Locks that bucket
// 3. Calls the computation function
// 4. If function changes hashCode, chaos ensues

Map<TypeConstant, TypeInfo> cache = new ConcurrentHashMap<>();
cache.computeIfAbsent(type, t -> {
    // This computation might cause resolution!
    return buildTypeInfo(t);  // Can trigger t.resolve()!
});
// If buildTypeInfo triggers resolution, the key's hashCode changes
// while the entry is being inserted. Corruption guaranteed.
```

## Why This Is Hard to Debug

1. **Non-deterministic**: Depends on order of operations
2. **Silent corruption**: No exception thrown
3. **Delayed failure**: Bug manifests far from cause
4. **Works in testing**: Tests may not trigger resolution timing issues
5. **Concurrency-dependent**: May only happen under load

## The Fix: Immutable Keys

### Option 1: Compute Hash Once, Never Change

```java
public final class Constant {
    private final int hashCode;  // Computed at construction, never changes

    public Constant(...) {
        // Compute hash in constructor, from immutable identity
        this.hashCode = computeIdentityHash();
    }

    @Override
    public int hashCode() {
        return hashCode;  // Always returns same value
    }
}
```

### Option 2: Use Immutable Key Objects

```java
// Instead of using TypeConstant as key, use an immutable identity
public record TypeId(String moduleName, String typePath) {
    // Records have stable hashCode/equals by default
}

Map<TypeId, TypeInfo> cache = new HashMap<>();
cache.put(type.getId(), info);  // getId() returns immutable TypeId
```

### Option 3: Identity-Based Maps

If you need object identity (not equality):

```java
Map<TypeConstant, TypeInfo> cache = new IdentityHashMap<>();
// Uses System.identityHashCode() and == instead of hashCode()/equals()
// Stable as long as you don't use different object instances for same logical type
```

### Option 4: Separate Resolution from Identity

```java
// Unresolved and resolved are DIFFERENT objects
public sealed interface TypeRef permits UnresolvedTypeRef, ResolvedTypeRef {
    // Common interface
}

public final class UnresolvedTypeRef implements TypeRef {
    // hashCode/equals based on unresolved identity
}

public final class ResolvedTypeRef implements TypeRef {
    // hashCode/equals based on resolved identity
}

// Resolution creates a NEW object, doesn't mutate
public ResolvedTypeRef resolve(UnresolvedTypeRef unresolved) {
    return new ResolvedTypeRef(/* resolved data */);
}
```

## Checking for This Problem

### Code Review Red Flags

1. `hashCode()` that reads non-final fields
2. `hashCode()` that returns 0 in some cases
3. `hashCode()` that caches in a mutable field
4. `equals()` that calls methods that might mutate state
5. Mutable objects used as `Map` keys or `Set` elements

### Static Analysis

SpotBugs/FindBugs can detect some of these patterns:
- `HE_HASHCODE_USE_OBJECT_EQUALS`
- `HE_EQUALS_USE_HASHCODE`
- `HE_HASHCODE_NO_EQUALS`

### Runtime Detection

```java
// Debug wrapper to detect hashCode changes
public class HashCodeTracker<K, V> extends HashMap<K, V> {
    private final Map<K, Integer> originalHashes = new IdentityHashMap<>();

    @Override
    public V put(K key, V value) {
        originalHashes.put(key, key.hashCode());
        return super.put(key, value);
    }

    @Override
    public V get(Object key) {
        Integer original = originalHashes.get(key);
        if (original != null && original != key.hashCode()) {
            throw new IllegalStateException(
                "Key hashCode changed! Was " + original + ", now " + key.hashCode());
        }
        return super.get(key);
    }
}
```

## Summary

The XVM codebase:

1. **Uses mutable `Constant` objects as HashMap keys**
2. **`hashCode()` changes from 0 to a computed value during resolution**
3. **`equals()` calls `resolve()` which can mutate the object**
4. **This violates the fundamental HashMap contract**

The result:
- Silent data corruption
- Lost cache entries
- Duplicate keys
- Non-deterministic behavior
- Impossible-to-debug production issues

**Fix**: Make key objects immutable, or use identity-based collections, or separate mutable state from key identity.

This is not an obscure edge case. This is **HashMap 101** - the most basic data structure contract in Java. 
Every CS curriculum covers this. Every Java certification tests for this. There is no excuse for getting this wrong.
