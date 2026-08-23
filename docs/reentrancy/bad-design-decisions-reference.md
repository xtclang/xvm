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

The deeper failure is that the implementation repeatedly hides phase and
ownership in mutable object state instead of representing them in the API. A
method that creates a pool-owned constant should say which pool owns it. A
runtime cache should say which container owns it. An object under construction
should not be reachable from owner registries. A logical value copy should not
copy locks, thread locals, runtime handles, or in-progress caches. These rules
are not "parallel runtime extras"; they are normal Java design hygiene.

The Java Memory Model makes this explicit. Without final-field safe publication,
volatile/atomic publication, synchronization, or confinement, there is no
happens-before edge that forces another thread to see a complete object graph or
a coherent group of related field writes. Functional/reentrant code has the same
requirement from a different angle: if a helper depends on hidden global owner
state, mutates a caller-owned object, or caches an owner-derived value in a
shared object, then the result is not a stable function of its explicit inputs.
That makes the code brittle even before multiple Java threads enter it.

## Summary Table

| Design decision | Why it was bad even single-threaded | Why it becomes must-fix | Branch status |
| --- | --- | --- | --- |
| Mutable process-global native template `INSTANCE` fields | API pretended container-owned templates were JVM globals; constructor assignment also published incomplete objects. | Last container wins, wrong template owner, wrong pool, and startup races. | Fixed by container-owned `NativeTemplates` table. |
| Static runtime metadata caches | Values derived from `Container`, `ConstantPool`, templates, handles, or enum forms were cached as JVM-global state. | Runtime/container B can reuse metadata from A. | Fixed for scanned runtime-template/Utils category. |
| Raw enum singleton handle paths | Construction structs could escape where initialized enum values were expected. | Parallel enum initialization returned wrong/partial enum handles. | Fixed public paths with initialized enum helpers and owner-local factories. |
| Constructor `this` escape | Constructors registered or cached `this` before subclass fields and invariants were complete. | Reentrant lookup can observe a partially constructed owner/handle/template. | Fixed for native containers, runtime handles, and several ASM/compiler groups; remaining warnings tracked separately. |
| Ambient `ConstantPool.getCurrentPool()` | Methods had hidden owner preconditions; nested calls could change or clear the current pool. | Constants, metadata, diagnostics, and type helpers could be created in or reported to the wrong pool. | Semantic main-code callers removed; remaining bridge has assertion and `xvm.asm.validateConstantPoolCurrentScope` diagnostics. |
| Split lifecycle state across several mutable fields | Readers could observe state combinations that were never a real lifecycle state. | Fibers can see mixed initialization/waiter/owner state. | Fixed known `SingletonConstant` case with atomic immutable state. |
| Shallow `clone()`/`adoptedBy(...)` for owner state | Copying object bits also copied helper locks, lazy cells, runtime handles, and owner-derived caches. | Constants adopted into pool B could retain pool A runtime/helper state. | Runtime/helper subclasses hardened; base default now fails closed unless a family explicitly opts in. Clone-free adoption remains the target. |
| ConstantPool registration before recursive completion | A constant can become discoverable before all child constants and owner-sensitive fields are stable. | Parallel readers can observe partial registration or mutable hash/equality state. | Guarded in this branch: public readers wait for completion. Transactional registration remains the target. |
| Manual lazy null caches | `if (field == null) field = ...` has no happens-before edge and hides owner/lifecycle rules. | Shared runtime/compiler paths can duplicate work, publish stale values, or mix owners. | Many runtime startup caches fixed; broad audit remains. |
| Public/protected mutable fields and arrays | Callers can mutate state without preserving invariants; final-looking arrays still have mutable contents. | Cross-owner mutation and stale cached state become very hard to localize. | Documented should-fix/must-audit category. |
| Raw or weakly typed APIs | Caller-side casts hide owner and payload expectations. | Wrong-owner values fail late as casts or state-machine errors. | New `generics-api-audit.md`; typed helpers used where practical. |
| Thread-local hidden context | Dependencies are not visible in signatures and depend on cleanup discipline. | Reused workers, callbacks, nested scopes, and parallel containers can observe stale context. | Semantic current-pool use removed; other thread-local contexts remain audited. |
| Non-transactional keep-alive registration | Code incremented owner-visible callback counts before the operation that made the callback live had completed. | Failed scheduling/startup can strand callback counts and make containers look busy forever. | Fixed for LocalClock, NanoTimer, and xRTServer bind failure paths. |
| Message-only exception wrapping | Code threw new failures using only `e.getMessage()`. | Owner, pool, module, and stack evidence disappears before the launcher or stress harness can report it. | Fixed for `MainContainer.invoke0(...)`; broader exception hygiene remains tracked. |
| Print-only worker failures | Worker threads caught runtime defects, printed to stderr, and continued. | Host APIs can observe idle state and report success after a scheduler/service runtime defect. | Fixed for `Container.schedule(...)`, `ServiceContext.drainWork()`, and `InterpreterConnector.join()`. |

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

### Non-Transactional Keep-Alive Registration

Bad shape:

```java
container.registerNativeCallback();
scheduleTimer(trigger, delay);        // may fail
```

Why it was bad in a single-threaded world:

- `registerNativeCallback()` changes container liveness. If the callback is not
  actually scheduled or bound, the container may never become idle again.
- `TimerTask.cancel()` and Java server cleanup are not semantic rollback APIs for
  XVM owner state.
- Weak callbacks can disappear before cleanup. Rediscovering the owner through a
  weak reference is not reliable after the callback count has already been
  claimed.

Replacement:

```java
container.registerNativeCallback();
try {
    scheduleTimer(trigger, delay);
} catch (RuntimeException | Error e) {
    unregisterRegisteredOwner();
    throw e;
}
```

The successful path is unchanged: the callback still keeps its owner alive while
pending. The failure path is now transactional: if native startup cannot publish
a live callback, it releases the exact owner it registered before returning the
failure.

### Message-Only Exception Wrapping

Bad shape:

```java
throw new RuntimeException("failed to run: " + module + ". Cause: " + e.getMessage());
```

Why it was bad in a single-threaded world:

- The wrapper discards the original exception type and stack trace.
- Suppressed exceptions and nested causes are lost.
- Diagnostics only see text, so the owner or constant-pool failure site has to
  be guessed from logs.

Replacement:

```java
throw new RuntimeException("failed to run: " + module, e);
```

The outer message still adds module context, but the original failure remains
available to the launcher, tests, and ownership diagnostics.

### Print-Only Worker Failures

Bad shape:

```java
catch (Throwable e) {
    System.err.println("Unexpected service execution failure: " + f_sName);
    e.printStackTrace(System.err);
}
```

Why it was bad in a single-threaded world:

- The thread that observed the real failure was not necessarily the caller that
  waited for completion.
- Stderr output is not an API result, not structured diagnostics, and not a
  memory-model publication contract.
- The runtime could decrement pending work or terminate a fiber after the print,
  allowing `join()` to observe idle state and return success.

Replacement:

```java
container.recordRuntimeFailure("Unexpected service execution failure: " + service, e);
...
container.throwIfRuntimeFailed();
```

The owner container now owns the failure state. The first unexpected Java
failure is published through an atomic slot, later failures are retained as
suppressed evidence, and `join()` checks the slot before reporting completion.
Natural XTC exceptions still use the normal fiber exception path.

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
- Even small non-runtime scratch fields were copied. For example,
  `ConditionalConstant.iTest` is only brute-force link-condition simulation state,
  but shallow clone treated a warmed test index as if it were serialized predicate
  value.
- Array-backed constants had the same problem in a more ordinary form:
  `UInt8ArrayConstant`, `FPNConstant`, and `Float128Constant` used final `byte[]`
  fields as immutable hash/equality value, but construction/adoption could share
  the mutable array with caller code or another pool owner.
- Annotation constants had the same bug with `Constant[]` parameter containers.
  The array is part of immutable annotation identity, but the old constructor
  and adoption path could share the mutable container with a caller or source
  owner. A `final Constant[]` reference is not an immutable annotation value.

Replacement:

- `Constant.adoptedBy(...)` is the final owner-transfer wrapper;
- special cases implement `copyForAdoption(AdoptionContext)`;
- subclasses must define which logical constant fields are copied;
- owner-local helper/runtime fields must be fresh, cleared, or rejected;
- diagnostics must assert that adopted constants do not carry source-owner
  runtime state.

The condition family demonstrates the desired low-risk end state: each concrete
condition now reconstructs the logical predicate from name/module/version/child
fields, target registration adopts child constants as before, and transient
simulation scratch is private and not copied.

The array-backed value constants follow the same rule. Constructors and adoption
copy the byte sequence once, preserving logical value and cache behavior without
letting another owner mutate the backing array. The remaining raw `getValue()`
array API is tracked as array-immutability design debt, not as proof that adoption
may share storage.

Annotations now follow the same rule. `Annotation` copies parameter arrays at
construction/adoption time, rejects already-owned runtime handle params during
owner transfer, and `AnnotatedTypeConstant` reconstructs the shell so the
derived annotation-type cache is recomputed by the destination owner. The legacy
raw `getParams()` API is still a mutability debt, but this branch avoids adding
hot-path per-read clones while fixing the owner-transfer bug.

`MatchAnyConstant` shows why a value shell must not hide an owner decision made
by one of its children. The wildcard object itself is just logical sentinel
state and now reconstructs explicitly, but its lookup key is a `TypeConstant`.
An unrelated foreign type key is now rejected before publication; a shared key
is still passed through target registration, which now reaches clone-free
type-family adoption hooks. Shallow clone made all of these boundaries
invisible.

`TerminalTypeConstant` is the first type leaf moved to the same model. The
logical value is the defining identity, not the inherited relation/type-info/JIT
helper caches. Rebuilding the shell from that identity keeps the same
constant-pool interning behavior for shared identities and fails closed for
unrelated foreign identities.

The dependant child/property type shells now follow that rule too. Parent plus
child name/class/property identity is logical type value; resolved child
structures and `PropertyInfo` objects are owner-derived metadata and must not be
copied. `RecursiveTypeConstant` also needs a dedicated hook because a generic
terminal-type copy would erase the recursive typedef subclass that shallow clone
used to preserve accidentally.

The same applies to simple type modifiers. `AccessTypeConstant`,
`ImmutableTypeConstant`, and `ServiceTypeConstant` are one logical child type
plus one modifier bit of meaning. Rebuilding those wrappers is as cheap as a
shallow clone, preserves the same pool lookup behavior, and removes the inherited
helper-cache copy from the ownership transfer path.

Storable relational type expressions follow the same pattern with two children:
`UnionTypeConstant`, `IntersectionTypeConstant`, and `DifferenceTypeConstant`
are rebuilt from their logical child types and registered in the target owner.
`CastTypeConstant` is not a storable relational value; it is a transient
compiler/JIT marker, and its `assemble(...)` method already rejects pool storage.
Failing adoption for that class makes the existing invariant enforceable.

The other transient type markers are now explicit. `TypeSequenceTypeConstant`
is a stateless formal marker and is reconstructed directly. `PendingTypeConstant`
and `UnresolvedTypeConstant` are mutable compiler/name-resolution placeholders;
adopting them into another pool would publish unfinished compiler state as if it
were completed runtime metadata, so adoption fails closed.

Pseudo constants show the same boundary in a smaller form. `ThisClassConstant`,
`ParentClassConstant`, and `ChildClassConstant` are logical path records, so they
can be rebuilt cheaply from target-owned child identities. `KeywordConstant` is
a per-format pool singleton and is rebuilt the same way. `DeferredValueConstant`,
`ExpressionConstant`, and `UnresolvedNameConstant` are not serialized metadata;
they are unresolved compiler/AST placeholders. Shallow-cloning them would copy
resolution callbacks, AST pointers, or temporary name/hash state. This branch
also copies unresolved-name input arrays because a caller-owned `String[]` is not
an immutable unresolved-name value.

Named identity constants now follow the same explicit rule. `ModuleConstant`,
`PackageConstant`, `ClassConstant`, `MultiMethodConstant`, and
`TypedefConstant` are logical path identities, so adoption rebuilds the target
shell from the target-owned parent plus name/version. This preserves the same
pool interning behavior while dropping owner-derived helper state such as
`TypedefConstant.m_fInitialized`. `DecoratedClassConstant` and
`PureIdentityConstant` are type-keyed artificial identities; adoption rebuilds
them only when the type graph is shared/adoptable into the target pool, so the
target shell is born with target-owned type constants. `NativeRebaseConstant`
is the opposite case: it is a runtime-only facade around a native rebase
interface and is documented as never registered with a pool. Adoption now fails
closed instead of shallow-cloning that runtime identity into serialized pool
metadata.

Immutable scalar values are now explicit too. `ByteConstant`, `IntConstant`,
`StringConstant`, `RegExConstant`, the fixed decimal value, and the fixed-size
binary float values do not currently carry owner-local caches. That is exactly
why they are cheap to fix correctly: reconstructing the logical scalar in the
target pool preserves semantics and performance while removing the default that
a future helper field would be copied across owners by `Object.clone()`.

Composite values show why array containers are not harmless implementation
details. `ArrayConstant` and `MapConstant` later rewrite their child arrays
during recursive registration so the elements point at the registering pool.
If construction or adoption shares those arrays, one caller or owner can change
another owner’s logical value. This branch copies array/map containers at the
constructor and adoption boundaries; it does not claim to solve the separate
type-family ownership issue for the array/map type constants.

Parsed literals are a smaller version of the same design bug. `LiteralConstant`
stores literal text as serialized value and `m_oVal` as an on-demand parsed
helper. Shallow clone copied both. The correct transfer copies the text/format
and drops the helper cache; if the target needs a `PackedInteger`, `BigDecimal`,
or parsed float later, it recomputes it from the target-owned literal text.

The same rule applies to method/parameter copies. This branch fixed a
single-threaded bug where `Parameter.cloneBody()` mutated the source parameter
while copying it, and a separate owner bug where `MethodStructure.cloneBody()`
attached copied parameters back to the source method. `Parameter` now uses an
owner-explicit `copyFor(MethodStructure)` helper instead of `Object.clone()`,
and method copies pass the cloned method as the target owner.

Array cloning needs the same discipline. `ClassStructure.ensureMethodDelegation`
used to clone only `Parameter[]` containers and share the mutable `Parameter`
elements with the source method. The delegated-method factory now copies the
elements for the synthetic method owner before publication.

The same problem showed up in runtime handles. `GenericHandle.cloneAs(...)`
needed to create a cheap access view, so sharing the final field array was the
right performance shape for regular fields. The old code then rewrote inflated
`RefHandle.$outer` values inside that shared array. That was broken even without
parallel execution: constructing one view changed the refs observed through an
existing view. The branch keeps the shared regular field backing and moves only
the view-specific inflated refs into sparse per-view overrides.

### ConstantPool Early Public Registration

Bad shape:

```java
constant.setPosition(f_listConst.size());
f_listConst.add(constant);
mapConstants.put(constant, constant);
constant.registerConstants(this);
constant.checkValidPools(f_setValidPools);
```

Why it was bad in a single-threaded world:

- The public pool list/map and the private registration worklist were the same
  structure.
- A constant had a public index before its child constants and owner-sensitive
  fields were fully registered in the destination pool.
- Subclass `registerConstants(...)` implementations could still rewrite fields
  that participate in logical identity, locator resolution, or owner validation.
- Correctness depended on an undocumented phase rule: nobody except the current
  registration call stack may look at the newly inserted constant yet.

Why it becomes must-fix for same-JVM containers:

- Thread A can publish the outer constant, then block or recurse while adopting
  children.
- Thread B can read the public pool index or lookup map and observe a partial
  graph with source-pool children or incomplete locator state.
- This is not just a data-race problem. Even with accidental single-thread
  ordering, the API boundary is wrong because "registered" actually meant
  "temporarily discoverable but not finished".

Branch replacement:

```java
beginRegistrationCompletion(constant);
try {
    publishForSameThreadRecursion(constant);
    constant.registerConstants(this);
    constant.checkValidPools(f_setValidPools);
} finally {
    finishRegistrationCompletion(constant, failure);
}
```

The actual branch code preserves the old same-thread recursive lookup behavior,
because recursive constant graphs still need a way to find the in-progress
constant. The difference is that normal public readers now wait for the
completion marker before returning a constant published by another thread. If
recursive registration fails, waiters receive the failure instead of a partial
object graph.

This is still a compromise. The correct design is a private registration
transaction or worklist:

- resolve cycles in a private in-progress map;
- adopt/register child constants against that private state;
- validate owners and hash/locator stability;
- publish completed constants to public pool storage once.

That design would not cost more in the steady-state runtime. It would make
registration phase boundaries explicit and eliminate the need for public APIs to
understand "in-progress but visible" constants.

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
- [plans/clone-free-adoption-plan.md](plans/clone-free-adoption-plan.md)
- [plans/transactional-constant-registration-plan.md](plans/transactional-constant-registration-plan.md)

Supporting audits:

- [ambient-context-audit.md](ambient-context-audit.md)
- [constant-pool-hostile-state-audit.md](constant-pool-hostile-state-audit.md)
- [constant-adoption-clone-audit.md](constant-adoption-clone-audit.md)
- [manual-lazy-cache-audit.md](manual-lazy-cache-audit.md)
- [this-escape-tally.md](this-escape-tally.md)
- [generics-api-audit.md](generics-api-audit.md)
