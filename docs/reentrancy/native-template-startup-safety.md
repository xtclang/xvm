# Native Template Startup Safety

This document explains why the old native-template startup pattern is unsafe,
what this branch replaces it with, what semantics change and do not change, and
how to keep migrating the remaining legacy sites.

The short version: a JVM `static` field is process-global, but XVM native
templates, handles, compositions, type constants, method structures, and enum
values are runtime or container state. Storing those values in mutable static
fields makes startup depend on timing, class-loading order, and an implicit
"only one runtime is active" assumption. That assumption is already false for
parallel manual tests and for any embedding that starts several containers in
one JVM.

## Background

The old runtime pattern looked like this:

```java
public static xArray INSTANCE;

public xArray(Container container, ClassStructure structure, boolean fInstance) {
    super(container, structure, false);
    if (fInstance) {
        INSTANCE = this;
    }
}
```

Many templates also had static first-use or startup caches:

```java
private static TypeComposition BYTE_ARRAY_CLZ;
private static MethodStructure CONSTRUCT;
private static xEnum MUTABILITY;
```

Those fields were populated from constructors, `initNative()`, or an
unsynchronized first-access check:

```java
if (BYTE_ARRAY_CLZ == null) {
    BYTE_ARRAY_CLZ = container.resolveClass(pool.typeByteArray());
}
```

This is convenient in a single, ordered startup. It is not a valid concurrency
or ownership model.

## What Went Wrong

### Static Fields Hid Container Ownership

The old `INSTANCE` field mixed three different concepts:

- The stable name of a native template, such as `collections.Array`.
- The Java template object returned by a particular container lookup.
- Derived metadata from that template's container and constant pool.

Only the first concept is process-global. The other two are owned by the
runtime/container graph. When several containers are active in the same JVM, a
single mutable static field cannot represent "the current container's template".
It can only represent "the last template object that wrote this static field".

That is a last-writer-wins cache. If container A initializes a template and
container B initializes the same template later, code running for A can read B's
template through `xArray.INSTANCE`. This is not a theoretical purity problem.
Template objects carry `f_container`, `pool()`, class structures, canonical
classes, compositions, native methods, and other objects whose identity must
match the caller's runtime world.

### Constructor Assignment Leaked `this`

This is not a local style preference. Modern Java tooling explicitly recognizes
constructor-time `this` escape as a lintable bug pattern. The local JDK reports
`this-escape` in `javac --help-lint`, and Oracle's `javac` manual documents
`--help-lint` as the source of supported `-Xlint` keys. JetBrains exposes
equivalent inspections under Java initialization, including "`this` reference
escaped in object construction" and "Overridable method called during object
construction". CERT also treats constructor calls that can leak `this` or reach
overrides as a secure-coding rule, not harmless style.

The correct 2026 policy for runtime code is to make this a hard build failure
through `-Xlint:this-escape` plus warning-as-error handling in CI. A suppression
such as `@SuppressWarnings("this-escape")` should be rare, local, and justified
by a comment explaining why the constructor cannot publish an incompletely
initialized object. The default should be failure, not a warning that developers
learn to ignore.

Constructor-assigned `INSTANCE` fields publish `this` before object construction
has necessarily finished. Java final-field safety depends on a simple rule:
initialize the object in the constructor and do not make the object visible to
other threads until the constructor finishes. The old pattern breaks that rule
by making the object globally visible from inside the constructor.

Once the object escapes, another startup thread can observe:

- fields that are still `null`,
- subclass initialization that has not run yet,
- caches that `initNative()` has not populated,
- a template that is registered but not fully initialized.

That is exactly the class of bug that makes a system pass thousands of runs and
then fail under a parallel runner on a loaded machine.

The XVM codebase has normalized this pattern. On `master`, a direct audit finds
143 mutable template `INSTANCE` declarations and 139 constructor assignments of
`INSTANCE = this` in runtime templates. This branch fixes 75 mutable template
fields and 71 constructor assignments, leaving 68 mutable `INSTANCE` fields
and 68 constructor assignments for follow-up. A broader scan for escape-shaped
`this` assignments and calls reports hundreds of hits in
`javatools/src/main/java`, many of which are false positives, but the signal is
clear: publishing receivers into mutable non-final state is common enough that
reentrant startup code cannot be assumed safe by inspection. See
[state-inventory.md](state-inventory.md) for the broad inventory and
[fixed-in-this-branch.md](fixed-in-this-branch.md) for the exact
`master`-to-branch fixed-site list.

### Unsynchronized Reassignment Has No Happens-Before Edge

The Java memory model does not make ordinary static writes safely visible just
because they "happen during bootstrap". A write by one thread and a read by
another thread are safely ordered only when there is a real happens-before edge:
class initialization, volatile access, monitor lock/unlock, thread start/join,
`java.util.concurrent` synchronization, or an equivalent mechanism.

Plain writes like these do not create that edge:

```java
INSTANCE = this;
BYTE_ARRAY_CLZ = container.resolveClass(pool.typeByteArray());
MUTABILITY = container.getTemplate("collections.Array.Mutability", xEnum.class);
```

If a parallel container reads those fields without synchronization, the read is
a data race. The Java language specification explicitly allows incorrectly
synchronized programs to show surprising values and reorderings. It also gives
special guarantees to correctly constructed final fields; mutable static fields
do not get those guarantees.

### Static Metadata Caches Were Worse Than `INSTANCE`

`INSTANCE` is obviously suspicious. Static metadata caches are more dangerous
because they look harmless:

```java
private static TypeConstant FUNCTION_ARRAY_TYPE;
private static ArrayConstant EMPTY_FUNCTION_ARRAY;
private static MethodStructure TO_ARRAY;
```

Those are not pure constants. A `TypeConstant` is tied to a `ConstantPool`.
A `TypeComposition` is tied to a `Container`. A `MethodStructure` comes from a
particular `ClassStructure`. An `ArrayHandle` or `ArrayConstant` may be valid
only in the runtime heap/pool where it was built. Caching them globally either
silently pins the first container or silently switches to the last container.

Both outcomes are wrong. They only appear to work when there is one runtime
world and every reader starts after bootstrap by luck or convention.

### Natural Enums Can Temporarily Be Structs

Natural enum values are especially sharp. During construction, the enum list can
temporarily contain the mutable construction struct. The public value must be
the finalized immutable enum singleton, not the struct.

PR #534 documented a concrete failure from `manualTests:runParallel`: a public
reflect path expected `reflect.ParameterTemplate.Category`, but received
`ParameterTemplate.Category.TypeParameter:struct`. That means the natural enum's
construction handle escaped into a public/native call before singleton
construction had completed.

This is exactly what the old pattern allows:

1. Startup creates enum handles and stores them where other code can find them.
2. Another fiber reflects method parameters while the enum singleton is still
   being initialized.
3. The caller observes the construction struct as if it were the enum value.
4. Native argument checks fail with a type mismatch, or worse, code operates on
   the wrong object shape.

### SingletonConstant Had Split Mutable State

GitHub issue #436 is another reported crash in the same family. It is still open
as of 2026-08-20. The issue describes `manualTests:runParallel` intermittently
crashing with `IllegalStateException: Circular initialization
"ecstasy.xtclang.org"` in `TestNesting`.

The old `SingletonConstant` lifecycle used separate mutable fields for:

- completed handle,
- whether initialization was active,
- which fiber was initializing,
- future/waiter state.

Those fields were read and written in separate steps. Under parallel lightweight
containers sharing a constant pool, that permits impossible lifecycle snapshots:
one fiber sees initialization active but no completed handle, another installs
an initializing placeholder, another reads that placeholder and treats it as a
real value. In issue #436 the observed path was ordinary string formatting:
`toString()` reached `Class.displayName`, which needed the
`ecstasy.xtclang.org` module singleton while another fiber was initializing it.

The lesson is broader than one bug: any path that looks like a read, including
`toString()`, logging, exception formatting, reflection, or debugger display,
can become a startup race if it lazily initializes shared singleton state.

## Practical Failure Modes

Here are concrete things the old model can do.

### Wrong Container Template

Container A initializes `xArray.INSTANCE`. Container B initializes its runtime
and overwrites `xArray.INSTANCE`. Later, code running in A calls a static helper
that reads `xArray.INSTANCE.f_container`. The helper now manufactures an array
handle using B's template, B's pool, or B's cached `TypeComposition`.

Potential symptoms:

- `TypeMismatch` when a native method receives a handle from the wrong
  composition.
- A handle that cannot be assigned into the caller's frame because its
  composition came from another runtime world.
- A foreign constant registered into the wrong pool.
- A service or reflection value whose native template identity no longer
  matches the caller's container.

### Half-Initialized Template

A constructor assigns `INSTANCE = this` and then initializes lazy metadata or
subclass fields. Another thread reads the static `INSTANCE` between those two
steps. The object reference is non-null, so the reader proceeds, but the
metadata it needs is still null.

Potential symptoms:

- intermittent `NullPointerException` from startup-only fields,
- unexpected fallback paths because a cache looks uninitialized,
- registration order bugs where one template's `initNative()` calls into
  another template that has escaped but not finished initialization.

### Mixed Metadata Graph

One field is initialized from container A and another from container B:

```java
BYTE_ARRAY_CLZ = containerA.resolveClass(poolA.typeByteArray());
BYTE_DELEGATE  = xRTDelegate.INSTANCE from containerB;
```

Each individual field is non-null and may look valid in a debugger. Together
they are invalid because the delegate, composition, class structure, and pool
do not belong to the same runtime graph.

Potential symptoms:

- array delegates whose element type disagrees with the array composition,
- reflect templates that produce handles with stale or wrong method structures,
- empty cached arrays from the wrong heap/pool.

### Enum Struct Escapes As Public Value

The natural enum list contains a construction struct while the singleton is
being built. A public reflect path reads the raw enum list and passes the struct
to a native constructor expecting the finalized enum value.

Potential symptoms:

- the PR #534 `ParameterTemplate.Category.TypeParameter:struct` type mismatch,
- enum ordinal/name lookups returning an object that must not be public,
- reflection or injection code seeing a mutable `:struct` where XTC semantics
  require an immutable enum singleton.

### Singleton Initialization Owner Race

Two fibers concurrently touch the same `SingletonConstant`.

Potential symptoms:

- both fibers believe they own initialization,
- non-owner fibers wait on different futures,
- a placeholder `InitializingHandle` is observed by unrelated fibers,
- `Circular initialization` is thrown for a non-recursive access,
- an abort leaves waiters behind or allows stale handles to be read.

Issue #436 describes this exact failure family.

### Hidden Side Effects From `toString()`

Issue #436 also shows that string formatting can trigger singleton resolution:
`Object.toString()` goes through `Class.displayName`, which can reach service
type-system state. A read-looking path therefore starts or observes singleton
initialization. That makes logging, error formatting, or debugger inspection
able to perturb runtime startup.

That is architecturally dangerous even after the immediate data race is fixed.
Read paths should avoid lazy service initialization where possible.

## Java Memory Model Proof Points

This design is problematic under the Java specification, not just under a
particular JVM or CPU. The relevant spec points are:

| Runtime pattern | Why it is unsafe | Specification basis |
| --- | --- | --- |
| Mutable static `INSTANCE` and metadata fields | Static fields are shared variables. A write by one startup thread and a read by another startup thread are conflicting accesses. Without a happens-before edge, that is a data race. | [JLS 17.4.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.1), [JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5) |
| Assuming bootstrap order makes plain writes visible | The JMM only gives visibility through program order in the same thread or through synchronization such as volatile, monitor unlock/lock, thread start/join, and other synchronizes-with edges. Ordinary static assignment is not one of those edges. | [JLS 17.4.4](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.4), [JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5) |
| `INSTANCE = this` in constructors | Final-field safety requires that the object not be made visible to another thread before the constructor finishes. Publishing `this` into a static field violates that usage model. | [JLS 17.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.5), [JLS 17.5.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.5.1) |
| Constructor calls or registration hooks that can reenter overridden code | Java uses normal dynamic dispatch during construction. An override can run before subclass field initializers have executed. | [JLS 12.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.5) |
| Lazy null caches such as `if (m_x == null) { m_x = ...; }` | A read and write of the same non-volatile field from different threads is a data race unless all callers are externally synchronized. The reader may observe stale, null, or partially related state. | [JLS 17.4.1](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.1), [JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5) |
| Multiple mutable lifecycle fields for one logical state | Even if individual reference writes are atomic, a group of plain fields is not atomically visible as one state. A racing reader can observe a mixed snapshot. Correct synchronization is required before sequential reasoning applies. | [JLS 17.4.3](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.3), [JLS 17.4.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4.5) |
| Reassigning caches after construction | Final-field semantics do not protect mutable fields assigned later. JLS 17.5 is explicit that final fields have special semantics and non-final fields do not get the same guarantee. | [JLS 17.5](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.5) |

The important positive rule is also in JLS 17.4.5: correctly synchronized
programs appear sequentially consistent. The replacement uses final immutable
state, `ConcurrentHashMap`, `AtomicReference`, and `Lazy.of()` so that reviewers
can point to real synchronization and final-field semantics instead of relying
on intended startup order.

## Replacement Model

The new model separates immutable names from runtime-owned objects.

### Central NativeTemplates Table

Converted templates no longer own an `INSTANCE` field. The immutable lookup
keys live in one runtime table:

```java
private static final NativeTemplateRef<xArray> ARRAY =
        NativeTemplateRef.of("collections.Array", xArray.class);

public xArray array() {
    return get(ARRAY);
}
```

`NativeTemplateRef` contains only:

- the native template name,
- the expected Java class.

It does not contain:

- a `Container`,
- a `ClassTemplate`,
- a `ConstantPool`,
- a `TypeComposition`,
- a method structure,
- a handle.

That makes the static keys safe. They are immutable lookup metadata, not
runtime state. The converted template classes keep compatibility getters such
as `xArray.getInstance(container)`, but those getters delegate to
`NativeTemplates` and do not hold their own singleton field.

### Container-Resolved Template Lookup

Code resolves a template through the active runtime context:

```java
xArray templateArray = NativeTemplates.get(frame).array();
xArray templateArray = NativeTemplates.get(container).array();
xArray templateArray = xArray.getInstance(container); // compatibility wrapper
```

`Container` owns the lookup table:

```java
private final NativeTemplates f_nativeTemplates = new NativeTemplates(this);
```

The table maps each `NativeTemplateRef<?>` to a `Lazy<?>`. The first lookup
calls the existing `Container.getTemplate(...)` path, so the result follows the
same ownership rules as the rest of the runtime. The cache does not invent a
new template object. For core native templates, if the existing container lookup
returns the native container's shared template, the new cache stores that same
object. For templates that are container-specific, it stores the
container-specific object.

This is an important semantic point: the new model does not change which
template `Container.getTemplate(...)` resolves. It only removes the global
mutable shortcut and memoizes the answer in the caller's container scope.

### Final Lazy Metadata Fields

Metadata derived from a template moves to final instance `Lazy` fields:

```java
private final Lazy<Map<TypeConstant, xArray>> f_arrayTemplates =
        Lazy.of(this::createArrayTemplates);
private final Lazy<ArrayInfo> f_info = Lazy.of(this::createArrayInfo);
```

`xArray` keeps the specialized-template dispatch map in a separate lazy because
`Container.getTemplate(TypeConstant)` needs that map while it is resolving array
types. `ArrayInfo` groups the heavier old `xArray` static caches:

- constructor identities,
- helper methods,
- array compositions,
- primitive delegates,
- empty byte array handle.

The expensive work is still cached. It is just cached on the owning template,
through a thread-safe lazy holder, rather than being reassigned into process
global fields.

### Container Parameters On Static Helpers

Static helpers that manufacture handles or compositions now need a `Container`
or `Frame`:

```java
xArray.makeByteArrayHandle(frame.container(), bytes, Mutability.Constant);
xString.makeArrayHandle(frame.container(), strings);
xRTFunction.makeAsyncNativeHandle(frame, method);
```

The old helper did not need a container parameter because it cheated: it read a
global static template/cache and silently used whatever container that global
object happened to point at. The new helper must know the caller's runtime
world explicitly, because creating a handle is not a pure static operation.

This is not a behavior change in XTC. It is making an existing requirement
visible in Java: handles, compositions, method structures, and type constants
must be created in the caller's container context.

The same rule applies even when the helper is not itself a converted
`NativeTemplates` lookup site. If a factory creates a runtime handle, function
handle, type handle, array handle, or composition, it needs an explicit owner.
Otherwise the implementation is forced to infer ownership from process-global
state such as `SomeTemplate.INSTANCE.f_container`.

This branch intentionally removes several no-owner overloads:

- `xString.makeArrayHandle(String[])`
- `xString.ensureEmptyArray()`
- `xRTType.makeForeignHandle(TypeConstant)`
- `xRTFunction.makeAsyncNativeHandle(MethodStructure)`

It also makes null-owner calls fail at the API boundary:

- `NativeTemplates.get(Container)`
- `NativeTemplates.get(Frame)`
- `NativeTemplates.get(ClassTemplate)`
- `xRTFunction.makeInternalHandle(Frame, MethodStructure)`
- `xRTFunction.makeInternalHandle(Container, MethodStructure)`
- `xRTFunction.makeHandle(Frame, MethodStructure)`
- `xRTType.makeHandle(Container, TypeConstant, boolean)`
- `xRTType.makeForeignHandle(Container, TypeConstant)`

For `xRTFunction.NativeFunctionHandle`, the constructor now takes a
`Container`. For finalizer chaining, `FullyBoundHandle.NO_OP` was replaced with
`FullyBoundHandle.noOp(Container)`. Both changes preserve the previous behavior
of creating a native function/finalizer anchor; they change only the owner used
for the handle's composition.

The compile failure from removing an overload is useful: it identifies a call
site that was depending on hidden global ownership. The correct repair is to
pass the caller's `frame.container()`, an existing `Container`, or the owning
service/container from the handle being adapted.

Some helpers only manufacture a `TypeConstant`, for example
`ListMap<String, Module>` or `ListMap<Parameter, Object>`. Those helpers now
take a `Container` and compute the type through the caller's `ConstantPool`.
This is not a performance regression: `ConstantPool` interns constants, so the
value is still cached by the owning pool. What is removed is the unsafe
process-global `LISTMAP_TYPE` field that could pin the first initialized
container and leak it into later containers.

### SingletonConstant State Machine

`SingletonConstant` is not replaced by `Lazy<ObjectHandle>`. Singleton
construction can suspend, recurse through fibers, abort, and wake waiters.
That is not a synchronous "compute once" problem.

The new state is a final atomic reference:

```java
private final transient AtomicReference<InitState> f_state =
        new AtomicReference<>(InitState.EMPTY);
```

`InitState` is immutable and contains the handle, owner fiber, and waiter
future. Every lifecycle transition replaces the whole snapshot with CAS. That
means other fibers cannot observe an owner from one transition and a waiter or
handle from another transition.

### Initialized Enum Access

Raw enum-list handles remain internal to `xEnum`. Public paths use helpers such
as:

```java
ensureEnumByName(frame, name)
ensureEnumByOrdinal(frame, ordinal)
Utils.ensureInitializedEnum(frame, enumHandle)
```

If the raw enum handle is still a construction struct, the helper resolves the
corresponding singleton constant through the current frame. The caller receives
the finalized value or a deferred value that waits for finalization, not the
struct.

## Safety Argument

This is the proof shape reviewers should use.

### Invariant 1: No Container-Owned Object In Static INSTANCE

Converted template classes do not have an `INSTANCE` field. The only static
lookup state is the private immutable key table in `NativeTemplates`. Therefore:

- no constructor assigns to `INSTANCE`,
- no template object escapes from the constructor through `INSTANCE`,
- no `Container` or `ConstantPool` can be captured by a converted class static,
- the Java final/static initialization rules apply only to small immutable keys.

This removes the constructor escape and last-writer-wins template cache for
converted templates.

### Invariant 2: Template Resolution Always Names The Container

Every converted call site resolves through `Frame` or `Container`. That creates
a local proof obligation:

- if a handle is returned to `frame`, resolve through `frame.container()`,
- if an async continuation returns to `frameCaller`, resolve through
  `frameCaller.container()`,
- if the method already has a `Container`, use that container.

There is no hidden process-global runtime selection.

### Invariant 3: Derived Metadata Is Owned By The Resolved Template

Metadata caches that this branch converts are final `Lazy` fields on the
template object. The supplier uses `f_container` from that template. Therefore
all derived values in one cache are computed from the same owner.

For `xArray`, the dispatch map computes only specialized template selection.
`ArrayInfo` computes array compositions, delegates, and helper methods together.
It is no longer possible for `BYTE_ARRAY_CLZ` to come from one container and
`BYTE_DELEGATE` from another converted cache.

### Invariant 4: Lazy Publication Is Synchronized

`Lazy.of()` uses `ThreadSafeLazy`: an `AtomicReference` stores the computed
value, first computation is guarded by a monitor, and the supplier is cleared
after publication. Therefore concurrent first readers call the supplier once
and later readers see the memoized value through atomic/volatile state.

`NativeTemplates` stores the lazy cell in a `ConcurrentHashMap`. The JDK
documents that a completed update for a key happens-before a retrieval that
reports that value. The map gives safe publication of the lazy cell; `Lazy`
gives safe publication of the value inside the cell.

### Invariant 5: Singleton Lifecycle Transitions Are Atomic Snapshots

`SingletonConstant` transitions replace one immutable `InitState` with another.
The owner, waiter, and handle cannot be updated independently. CAS decides
which fiber owns initialization and which waiter future is shared.

That eliminates the split-field lifecycle race from issue #436. Same-fiber
recursion is still recognized as recursion. Other fibers wait for completion.
Abort resets the state and completes the waiter exceptionally.

### Invariant 6: Natural Enum Structs Are Not Public Values

Raw enum handles can still be structs while natural enum construction is active.
The safety requirement is that those raw handles do not cross public/native
boundaries as final enum values. Converted paths use initialized/deferred enum
helpers, so a public caller receives a finalized singleton or a deferred handle
that waits for it.

## Why Final State Buys Reentrancy

Reentrant code is easy to reason about when object state is either immutable or
behind an explicit synchronization boundary. It is hard to reason about when a
method can observe half of a startup mutation sequence.

Final fields give each object a stable shape. After construction, every caller
sees the same references for the object's dependencies. A reentrant call cannot
race with later reassignment of those dependencies because there is no later
reassignment.

`Lazy` is the right companion for final fields. The `Lazy` field itself is final
and safely published with the owner. Its value is computed at first use through
a synchronization boundary. That means a reentrant caller sees one of two valid
states:

- the cache has not been computed, so the caller participates in the lazy
  computation;
- the cache has been computed, so the caller sees the memoized value.

The caller does not see the old bad middle state: one mutable field populated,
another mutable field still null, and a third mutable field pointing at a
different container. This is the architectural win. Final state removes
implicit temporal coupling. `Lazy` preserves caching without making every caller
remember which bootstrap method had to run first.

For state that can suspend, abort, or represent true recursion, use an explicit
atomic state machine instead of `Lazy`. `SingletonConstant` is in that category.
For ordinary metadata caches, final `Lazy` fields should be the default.

## Semantic Impact

### What Does Not Change

The XTC-level behavior is intended to stay the same:

- same native template names,
- same template lookup path through `Container.getTemplate(...)`,
- same enum names and ordinals,
- same array handle shapes,
- same reflect objects,
- same singleton values after initialization.

For a single correctly ordered runtime, the old static caches and the new lazy
caches should produce the same objects that `Container.getTemplate(...)` and
`Container.resolveClass(...)` already produce.

### What Does Change

The Java implementation contract changes:

- `xArray.INSTANCE` no longer exists for converted classes; callers use
  `xArray.getInstance(owner)` or `NativeTemplates.get(owner).array()`.
- Java call sites must pass a `Frame` or `Container` when resolving a template
  or manufacturing a container-owned handle.
- Ownerless helper overloads that created handles or type metadata were
  removed or changed to reject null owners. `NativeTemplates.get(Container)`,
  the frame/template overloads, and the table constructor reject null owners.
  This is intentional compile-time and fail-fast enforcement, not a runtime
  semantic change.
- Some metadata may be computed at first use instead of during `initNative()`.
  This can move an internal startup error to first use if a required method or
  structure is missing.
- Accidental cross-container Java identity is no longer preserved. If old Java
  code depended on `xArray.INSTANCE` being "whatever template was assigned
  last", that dependency was invalid.

### Footprint

The new model can add small cache cells:

- one `NativeTemplates` table per `Container`,
- one `Lazy<?>` entry per resolved native-template key per container,
- final `Lazy` fields on converted template objects,
- grouped metadata records such as `ArrayInfo`.

It does not necessarily duplicate the heavy template object per lightweight
container. `NativeTemplates.get(container).array()` calls the existing
container lookup behind the table. If the existing lookup returns a shared
native-container template, the cache stores that shared object.

The footprint increase is therefore bounded and intentional. It replaces unsafe
global state with owner-scoped memoization. The heavy values that were hot
before are still memoized; they are just memoized in the correct owner.

### Performance

The old fastest path was a plain static field read. That was cheap because it
ignored safety and ownership.

The new path adds:

- a `ConcurrentHashMap` lookup for `NativeTemplates.get(container).array()`,
- an atomic read in `Lazy.get()`,
- a one-time monitor on first computation.

The expensive work is still done once. For example, `xArray` groups the old
static caches into `ArrayInfo`, so repeated array creation does not repeatedly
resolve constructors, delegates, or compositions.

There is no startup-wide lock and no per-call recomputation. If profiling later
shows a specific helper is too hot for repeated `NativeTemplates` lookup, the
fix is local: resolve the template once at the caller boundary and reuse the
template or metadata for the loop. That keeps the same ownership model.

The expected performance impact is therefore limited to a small safe lookup at
Java native helper boundaries, not a change to runtime algorithmic behavior.

For helper-local `TypeConstant` values such as the various `ListMap<...>` types,
the replacement often does not need a separate `Lazy`: resolving through the
caller `ConstantPool` is already memoized by the pool. That keeps the old
caching behavior while ensuring the cached constant belongs to the caller's
runtime owner.

## Runnable Proofs And Stress Tests

### Deterministic Old-Pattern Demonstration

This branch adds a runnable demonstration test:

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.NativeTemplateOldPatternTest \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

The test is intentionally small. It does not try to reproduce every XVM crash.
It proves two properties of the old pattern in a deterministic way:

- a mutable static `INSTANCE` is last-writer-wins across fake containers,
- assigning `INSTANCE = this` from a constructor can expose an object before
  metadata initialization finishes.

Those are the same two properties that make the real runtime pattern unsafe.

### Singleton State Regression

The singleton lifecycle replacement is covered by:

```bash
./gradlew :javatools:test \
  --tests org.xvm.runtime.SingletonConstantTest \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

The tests verify:

- concurrent initialization has exactly one owner,
- concurrent waiters share the same completion future,
- same-fiber recursion installs an initializing placeholder without making
  unrelated fibers look recursive.

### Parallel Manual Test Stress

This branch adds an opt-in stress task that runs the existing `Runner` module
with repeated manual-test module arguments. One invocation therefore creates a
large burst of lightweight containers:

```bash
./gradlew :manualTests:runParallelStress \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

The load can be increased or narrowed:

```bash
./gradlew :manualTests:runParallelStress \
  -PstressIterations=50 \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache

./gradlew :manualTests:runParallelStress \
  -PstressModules=TestReflection,TestArray \
  -PstressIterations=100 \
  --console=plain --warning-mode=all --no-daemon --no-configuration-cache
```

For longer race hunting, run either `runParallelStress` or the existing
`runParallel` task in a shell loop:

```bash
for i in $(seq 1 100); do
  echo "manualTests:runParallelStress attempt $i"
  ./gradlew :manualTests:runParallelStress \
    --console=plain --warning-mode=all --no-daemon --no-configuration-cache || exit 1
done
```

This cannot prove the absence of all races. It can provide useful negative
evidence after the invariants above have removed the known unsound publication
paths. If it fails, the failure should be classified by owner:

- `SingletonConstant` lifecycle race,
- natural enum public value race,
- static template `INSTANCE` owner leak,
- static metadata cache owner leak,
- unrelated runtime concurrency bug.

## Implementation Guide

Use this checklist when migrating another template.

1. Add an immutable key and accessor in `NativeTemplates`:

   ```java
   private static final NativeTemplateRef<X> X_KEY =
           NativeTemplateRef.of("template.Name", X.class);

   public X x() {
       return get(X_KEY);
   }
   ```

2. Delete constructor assignment:

   ```java
   INSTANCE = this;
   ```

3. Replace identity checks with:

   ```java
   if (NativeTemplates.get(this).isX(this)) {
       ...
   }
   ```

4. Replace static template reads with container resolution:

   ```java
   X template = NativeTemplates.get(frame).x();
   X template = NativeTemplates.get(container).x();
   ```

5. Move container-derived static caches to final instance `Lazy` fields:

   ```java
   private final Lazy<SomeInfo> f_info = Lazy.of(this::createInfo);
   ```

6. Group related caches into an immutable record when they must be computed
   from the same pool/container.

7. Widen static helpers that create handles, compositions, or type constants to
   accept `Frame` or `Container`.

8. In async continuations, use the frame that receives the result:

   ```java
   frameCaller.container()
   ```

9. Do not use `Lazy` for singleton construction that can suspend. Keep those as
   explicit fiber-aware state machines.

10. Add tests for owner selection and concurrent first access when the template
    has public static helpers.

## Review Checklist

Reviewers should reject a migration if any of these remain in the converted
path:

- `INSTANCE = this` in a constructor,
- mutable static `TypeConstant`, `TypeComposition`, `ClassTemplate`,
  `MethodStructure`, or handle caches derived from a container,
- static helper creates a handle without accepting `Frame` or `Container`,
- enum lookup returns a raw natural enum struct through a public/native path,
- singleton lifecycle fields are updated independently,
- async callback uses an old global cache instead of the callback frame's
  container.

## TODO: Legacy INSTANCE Patterns Not Removed

This branch is a model and a partial migration, not a claim that all runtime
native templates are fixed. The current audit command is:

```bash
rg -l "public static (?!final)[A-Za-z0-9_<>, ?]+ INSTANCE;" \
  --pcre2 javatools/src/main/java/org/xvm/runtime/template | sort
```

At the time this document was written, it reported 68 unconverted template
files. They should be migrated in follow-up PRs. Grouping them by package:

- Root templates: `Identity`, `Proxy`, `xConst`, `xException`, `xObject`.
- Text templates: `xString`, `xChar`.
- Collection templates: `xBitArray`, `xByteArray`, `xNibbleArray`, `xTuple`,
  `xListMap`.
- Array delegates and views: `xRTBitDelegate`, `xRTBooleanDelegate`,
  `xRTFloat64Delegate`, `xRTInt8Delegate`, `xRTInt16Delegate`,
  `xRTInt64Delegate`, `xRTUInt8Delegate`, `xRTNibbleDelegate`,
  `xRTSlicingDelegate`, `xRTViewFrom*`, and `xRTViewToBitFromNibble`.
- File-system templates: `xOSDirectory`, `xOSFile`,
  `xRawOSFileChannel`.
- Native reflect templates still using mutable `INSTANCE`:
  `xRTFileTemplate`, `xRTPackageTemplate`, `xRTProperty`,
  `xRTPropertyTemplate`, `xRTSignature`.
- Annotation templates: `xAtomicIntNumber`, `xFuture`.
- Number templates: all checked and unchecked integer/decimal/float literal
  templates still listed by the audit, including `xInt*`, `xUInt*`,
  `xCheckedInt*`, `xCheckedUInt*`, `xDec*`, `xFloat*`, `xFPLiteral`,
  `xNibble`.
- Reflect templates: `xRef`, `xVar`.

Converted `INSTANCE` fields in this branch include:

- `xArray`,
- `xEnum`,
- `xService`,
- `xRTDelegate`,
- `xRTNameService`,
- `xRTClassTemplate`,
- `xRTComponentTemplate`,
- `xRTFunction`,
- `xRTMethod`,
- `xRTModuleTemplate`,
- `xRTPropertyClassTemplate`,
- `xRTType`,
- `xRTTypeTemplate`,
- `xRTViewFromBit`,
- `xRTViewFromByte`,
- `xRTViewToBit`,
- `xContainerControl`,
- `xContainerLinker`,
- `xBasicHashCollector`,
- `xRTAlgorithms`,
- `xRTCertificateManager`,
- `xRTKeyStore`,
- `xTerminalConsole`,
- `xRTCompiler`,
- `xCoreRepository`,
- `xRTNetwork`,
- `xRTRandom`,
- `xLocalClock`,
- `xNanosTimer`,
- `xRTConnector`,
- `xRTServer`,
- `xInjector`,
- `xRTDecryptor`,
- `xRTHasher`,
- `xRTKeyGenerator`,
- `xRTSigner`,
- `xRTBuffer`,
- `xRTNetworkInterface`,
- `xRTSocket`,
- `LongBasedBitView`,
- `LongDelegate`,
- `LongLongDelegate`,
- `xRTCharDelegate`,
- `xRTInt128Delegate`,
- `xRTInt32Delegate`,
- `xRTStringDelegate`,
- `xRTUInt128Delegate`,
- `xRTUInt16Delegate`,
- `xRTUInt32Delegate`,
- `xRTUInt64Delegate`,
- `xRTViewToBitFromFloat64`,
- `xRTViewToBitFromInt128`,
- `xRTViewToBitFromInt16`,
- `xRTViewToBitFromInt32`,
- `xRTViewToBitFromInt64`,
- `xRTViewToBitFromInt8`,
- `xRTViewToBitFromUInt128`,
- `xRTViewToBitFromUInt16`,
- `xRTViewToBitFromUInt32`,
- `xRTViewToBitFromUInt64`,
- `xRTViewToBitFromUInt8`,
- `xAtomic`,
- `xInject`,
- `BitBasedArray`,
- `xFloat16`,
- `xIntLiteral`,
- `xClass`,
- `xClassTemplate`,
- `xEnumValue`,
- `xEnumeration`,
- `xRegEx`,
- `xRTMethodTemplate`,
- `xModule`,
- `xPackage`.

## TODO: Legacy Static Metadata Caches Not Removed

The remaining mutable `INSTANCE` fields are the broadest risk. Static metadata
caches are the next risk. Follow-up work should prioritize any static field
whose type is container, pool, structure, composition, template, method, or
handle state.

Known high-priority leftovers include:

- `Utils`: `ANNOTATION_TEMPLATE`, `ANNOTATION_TEMPLATE_TEMPLATE`,
  `ARGUMENT_TEMPLATE`, `RT_PARAMETER_TEMPLATE`, constructor method caches,
  `STRING_VALUE_OF`, array type constants, and injection/freezing signatures.
- `xRTFileTemplate`: `FILE_TEMPLATE_TYPE`, `LINK_MODULES_METHOD`.
- `xRTSignature`: return/parameter type constants, RT return/parameter
  templates, and return/parameter array compositions.
- `xRTProperty`, `xRTPropertyTemplate`, `xRTMethodTemplate`,
  `xRTPackageTemplate`: empty arrays, template compositions, and type constants.
- `xClass`: array type caches.
- `xByteArray`: numeric array compositions.
- `xConst`: native helper method caches such as estimate length, append, freeze,
  range, date/time, duration, version, path, and hash signature.
- `xException`: cached exception class compositions and formatting method.
- `xRTViewToBit`: `VIEWS`.
- `xAtomic`: `NUMBER_TEMPLATES`.
- `xFuture`: `TYPE`, `COMPLETION`.
- File-system templates: static constructor methods on `xCPDirectory`,
  `xCPFile`, `xCPFileStore`, `xOSDirectory`, `xOSFile`, and event method caches
  in `xOSStorage`.
- Miscellaneous native templates: `xRTKeyStore.s_typeNamedPassword`,
  `xRTCompiler.GET_MODULE_ID`, `xRTBuffer.PROP_RAW_BYTES`,
  `xNanosTimer.s_clzDuration`.
- Compatibility bridge: `xString.INSTANCE`, `EMPTY_STRING`, `EMPTY_ARRAY`,
  `ZERO`, `ONE`, and `METHOD_APPEND_TO` remain old-style until the remaining
  callers are container-widened.

Use this audit command for static metadata:

```bash
rg -n "private static (?!final).*(TypeConstant|TypeComposition|ClassTemplate|ClassComposition|MethodStructure|MethodConstant|SignatureConstant|ArrayConstant|ArrayHandle|xEnum)|protected static (?!final).*(xEnum|Map<TypeConstant)|public static (?!final).*(TypeConstant|ArrayHandle|ArrayConstant)" \
  --pcre2 javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/Utils.java | sort -u
```

Each cache should either become:

- a final `Lazy` field on the owning template,
- a field in a container-owned cache,
- a true `static final` immutable value that contains no runtime/container
  state,
- or a method-local value when it is cheap and not hot.

The broader cache-smell appendix is maintained separately:
[state-inventory.md](state-inventory.md).
It maps the current mutable `INSTANCE` inventory, field-shaped
`if (field == null) { field = ... }` lazy caches, and other unsafe global-state
patterns. That inventory should be treated as a migration backlog.

## References

- PR #534, "Fix runtime enum singleton initialization race":
  https://github.com/xtclang/xvm/pull/534
- Issue #436, "Race condition in SingletonConstant causes Circular
  initialization crash under parallel execution":
  https://github.com/xtclang/xvm/issues/436
- Java Language Specification, Chapter 17, Threads and Locks:
  https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html
- Oracle `javac` manual:
  https://docs.oracle.com/en/java/javase/26/docs/specs/man/javac.html
- `ConcurrentHashMap` Java 21 API:
  https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html
- JetBrains Inspectopedia, "`this` reference escaped in object construction":
  https://www.jetbrains.com/help/inspectopedia/ThisEscapedInObjectConstruction.html
- JetBrains Inspectopedia, "Overridable method called during object construction":
  https://www.jetbrains.com/help/inspectopedia/OverridableMethodCallDuringObjectConstruction.html
- CERT MET05-J:
  https://cmu-sei.github.io/secure-coding-standards/sei-cert-oracle-coding-standard-for-java/rules/methods-met/met05-j/
