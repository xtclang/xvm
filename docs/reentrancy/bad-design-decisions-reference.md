# Bad Design Decisions Reference

This is the single reference list of the bad state/ownership design decisions
found while making branch `lagergren/lazy-instance` reentrant enough for
same-JVM and multi-container runtime work.

The important theme is that these were not advanced parallel-runtime features
that were omitted for schedule reasons. Most of them were ordinary Java design
choices that would have made the single-threaded code easier to reason about
from day one: explicit owners, final fields, immutable globals, no constructor
publication, typed APIs, and one synchronization model for mutable lifecycle
state.

## Summary Table

| Design decision | Why it was bad even single-threaded | Why it becomes must-fix | Branch status |
| --- | --- | --- | --- |
| Mutable process-global native template `INSTANCE` fields | API pretended container-owned templates were JVM globals; constructor assignment also published incomplete objects. | Last container wins, wrong template owner, wrong pool, and startup races. | Fixed by container-owned `NativeTemplates` table. |
| Static runtime metadata caches | Values derived from `Container`, `ConstantPool`, templates, handles, or enum forms were cached as JVM-global state. | Runtime/container B can reuse metadata from A. | Fixed for scanned runtime-template/Utils category. |
| Raw enum singleton handle paths | Construction structs could escape where initialized enum values were expected. | Parallel enum initialization returned wrong/partial enum handles. | Fixed public paths with initialized enum helpers and owner-local factories. |
| Constructor `this` escape | Constructors registered or cached `this` before subclass fields and invariants were complete. | Reentrant lookup can observe a partially constructed owner/handle/template. | Fixed for native containers, runtime handles, and several ASM/compiler groups; remaining warnings tracked separately. |
| Ambient `ConstantPool.getCurrentPool()` | Methods had hidden owner preconditions; nested calls could change or clear the current pool. | Constants, metadata, diagnostics, and type helpers could be created in or reported to the wrong pool. | Semantic main-code callers removed; bridge remains transitional. |
| Split lifecycle state across several mutable fields | Readers could observe state combinations that were never a real lifecycle state. | Fibers can see mixed initialization/waiter/owner state. | Fixed known `SingletonConstant` case with atomic immutable state. |
| Shallow `clone()`/`adoptedBy(...)` for owner state | Copying object bits also copied helper locks, lazy cells, runtime handles, and owner-derived caches. | Constants adopted into pool B could retain pool A runtime/helper state. | Several constants hardened; base clone/adoption remains must-audit. |
| ConstantPool registration before recursive completion | A constant can become discoverable before all child constants and owner-sensitive fields are stable. | Parallel readers can observe partial registration or mutable hash/equality state. | Documented must-audit; guarded in diagnostics. |
| Manual lazy null caches | `if (field == null) field = ...` has no happens-before edge and hides owner/lifecycle rules. | Shared runtime/compiler paths can duplicate work, publish stale values, or mix owners. | Many runtime startup caches fixed; broad audit remains. |
| Public/protected mutable fields and arrays | Callers can mutate state without preserving invariants; final-looking arrays still have mutable contents. | Cross-owner mutation and stale cached state become very hard to localize. | Documented should-fix/must-audit category. |
| Raw or weakly typed APIs | Caller-side casts hide owner and payload expectations. | Wrong-owner values fail late as casts or state-machine errors. | New `generics-api-audit.md`; typed helpers used where practical. |
| Thread-local hidden context | Dependencies are not visible in signatures and depend on cleanup discipline. | Reused workers, callbacks, nested scopes, and parallel containers can observe stale context. | Semantic current-pool use removed; other thread-local contexts remain audited. |

## Examples And Replacements

### Process-Global Native Template Instances

Bad shape:

```java
public static xBoolean INSTANCE;

public xBoolean(Container container, ClassStructure structure) {
    super(container, structure);
    INSTANCE = this;
}
```

Why it was bad in a single-threaded world:

- The code said "there is one `xBoolean`", but the object contains a
  `Container`, `ConstantPool`, native metadata, and handle/template caches.
- The constructor published `this` before `initNative()` and subclass
  construction completed.
- Reviewers had to remember that a public static field was not really a
  constant.

Replacement:

```java
xBoolean template = NativeTemplates.get(container).booleanTemplate();
```

The cache lives under the `Container` owner. It preserves the old hot lookup
behavior without a mutable process-global pointer.

### Ambient Current Pool

Bad shape:

```java
SignatureConstant resolved = sig.resolveGenericTypes(
        ConstantPool.getCurrentPool(), resolver);
```

Why it was bad in a single-threaded world:

- A nested helper can temporarily bind another pool.
- A callback or diagnostic path can run with no current pool.
- The method signature does not tell the caller that pool ownership matters.

Replacement:

```java
SignatureConstant resolved = sig.resolveGenericTypes(pool, resolver);
```

Where the receiver already has an owner:

```java
return getConstantPool().ensureRangeConstant(this, that);
```

Branch fixes in this category:

- `TypeConstant` covariance/contravariance helpers require an explicit pool.
- `ByteConstant` and `IntConstant` range folding use the receiver pool.
- `ConstantPool.checkFunctionCompatibility(...)` uses the receiver pool.
- `IdentityConstant` resolver-backed nested identities carry the explicit
  output pool.
- `MethodBody`, `MethodInfo`, and `PropertyInfo` metadata helpers derive from
  receiver owner state.
- `FileStructure` diagnostics no longer redirect through ambient state.

### Constructor Publication

Bad shape:

```java
RefHandle ref = new RefHandle(clazz, frame, iVar); // constructor stores this in frame cache
```

Why it was bad in a single-threaded world:

- The constructor both built an object and published it to owner-visible state.
- Any reentrant lookup from a field initializer, overridden method, diagnostic,
  or callback could observe the object before construction completed.

Replacement:

```java
var ref = new RefHandle(clazz, frame, iVar);
info.setRef(ref);
return ref;
```

Factory-owned post-construction publication is not slower. It just makes the
lifecycle visible.

### Split Mutable Lifecycle State

Bad shape:

```java
m_handle            = handle;
m_fiberInitializing = null;
m_cfInitialized     = null;
```

Why it was bad in a single-threaded world:

- The code relied on readers observing several fields in the intended order.
- Any exception, callback, or nested read could see a half-transitioned state.

Replacement:

```java
state.compareAndSet(oldState, new Initialized(handle));
```

One immutable state object makes every observed state a real state.

### Shallow Clone Adoption

Bad shape:

```java
Constant adoptedBy(ConstantPool pool) {
    Constant that = (Constant) super.clone();
    that.m_pool = pool;
    return that;
}
```

Why it was bad in a single-threaded world:

- Adding any new helper field to a constant silently made it part of adoption.
- Final locks, lazy cells, JIT caches, thread-local reentrancy markers, and
  runtime handles were copied unless every subclass remembered to opt out.

Replacement:

- subclasses must define which logical constant fields are copied;
- owner-local helper/runtime fields must be fresh, cleared, or rejected;
- diagnostics must assert that adopted constants do not carry source-owner
  runtime state.

The same rule applies to method/parameter copies. This branch fixed a
single-threaded bug where `Parameter.cloneBody()` mutated the source parameter
while copying it, and a separate owner bug where `MethodStructure.cloneBody()`
attached copied parameters back to the source method. `Parameter` now uses an
owner-explicit `copyFor(MethodStructure)` helper instead of `Object.clone()`,
and method copies pass the cloned method as the target owner.

### Manual Lazy Null Caches

Bad shape:

```java
if (m_type == null) {
    m_type = computeType();
}
return m_type;
```

Why it was bad in a single-threaded world:

- It hides whether the value is immutable, resettable, owner-local, or
  lifecycle state.
- It makes later reentrancy depend on every caller knowing the original
  confinement assumption.

Replacement:

```java
private final Lazy.Owner<MyOwner, TypeConstant> type =
        Lazy.ofOwner(MyOwner::computeType);
```

or, for keyed caches:

```java
return cache.computeIfAbsent(key, this::computeValue);
```

### Raw Types And Scattered Casts

Bad shape:

```java
ArrayHandle array = (ArrayHandle) container.getConstHeap().getConstHandle(container, constant);
```

Why it was bad in a single-threaded world:

- The API hides the expected payload type.
- The failure appears at the cast, not at the owner boundary.

Replacement:

```java
ArrayHandle array = container.getConstHeap()
        .getConstHandle(container, constant, ArrayHandle.class);
```

Typed owner-boundary helpers do not remove all runtime checks, but they put the
check in one place that can attach owner diagnostics.

## Tracked Work

Must-fix details and branch fixes are tracked in:

- [must-fix-races.md](must-fix-races.md)
- [fixed-in-this-branch.md](fixed-in-this-branch.md)
- [must-audit-backlog.md](must-audit-backlog.md)
- [plans/xvm-memory-model-hygiene.md](plans/xvm-memory-model-hygiene.md)

Supporting audits:

- [ambient-context-audit.md](ambient-context-audit.md)
- [constant-pool-hostile-state-audit.md](constant-pool-hostile-state-audit.md)
- [constant-adoption-clone-audit.md](constant-adoption-clone-audit.md)
- [manual-lazy-cache-audit.md](manual-lazy-cache-audit.md)
- [this-escape-tally.md](this-escape-tally.md)
- [generics-api-audit.md](generics-api-audit.md)
