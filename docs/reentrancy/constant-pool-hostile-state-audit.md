# ConstantPool Hostile State Audit

This is a side-task audit catalog and backlog. It is not part of the main
this-escape fix, and it does not claim that the findings below are fixed by
that work. The purpose is to enumerate `ConstantPool` and closely related
`org.xvm.asm.constants` state patterns that are hostile to reentrancy,
parallelism, or multiple runtime owners in one JVM.

Classifications:

- **Must fix**: unsafe for parallel runtime or multi-owner execution unless
  guarded out of that path.
- **Must audit**: plausible owner/race hazard; needs a proof, stress coverage,
  or a narrower guard before being treated as safe.
- **Should fix**: design debt that is owner-local today but easy to make safer.
- **Benign/proven owner-local**: audited pattern that already constructs fresh
  owner-local state or only keeps immutable logical value.

## Fixed In This Branch

### TypeConstant recursion diagnostic set

References:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5968`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5971`
  (`s_setRecursions.add(...)`)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8296`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8302`
  (`ConcurrentHashMap.newKeySet()`)

Old cause: a process-global `HashSet` suppressed repeated type-relation
recursion diagnostics. The set was only diagnostic, but type relation checks can
run concurrently across pools, so a plain `HashSet` could be corrupted or race
while deciding whether to print a recursion report.

Fix: the set is still one process-wide diagnostic suppression set, but it is now
created with `ConcurrentHashMap.newKeySet()`.

Why behavior is preserved:

- the same seed recursion is still present;
- the same `add(...)` result still decides whether the message prints once;
- no semantic type relation result depends on the set;
- there is no added per-pool or per-type footprint;
- the only extra cost is the concurrent-set operation on an unusual diagnostic
  recursion path.

Proof: `TypeConstantRecursionDiagnosticsTest` verifies the field is backed by a
concurrent key set, not `HashSet`, and stresses parallel diagnostic additions.
That test would fail on `master` because the backing set is a `HashSet`.

Design rule: process-global diagnostic state is still shared mutable state. If a
diagnostic cache is updated from code that can run on runtime/compiler/JIT
workers, it must either be immutable, concurrent, owner-local, or guarded by a
documented single-thread phase. "It is only logging" is not a valid reason to
use unsynchronized mutable collections.

### TypeConstant covariance and contravariance owner lookup

References:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6268`
  (`isCovariantReturn(ConstantPool, TypeConstant, TypeConstant)`)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6352`
  (`isContravariantParameter(ConstantPool, TypeConstant, TypeConstant)`)
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:447`
  and `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:1576`
  (callers pass an explicit owner pool)

Old cause: these helpers looked like pure type predicates, but when the simple
answer was not available they called `ConstantPool.getCurrentPool()` to resolve
auto-narrowing and generic helper constants. That meant the answer depended on
whatever pool had been installed in the current Java thread by an outer caller.

Why this was broken: with more than one container or compiler/runtime activity
in the same JVM, there is no process-wide "current" pool. A reused worker
thread, nested runtime call, async callback, or stale scoped bridge could make a
type relation check manufacture helper constants in the wrong pool or fail with
no pool at all. The method signature did not communicate that dependency, so
callers could not audit it locally.

Fix: both helpers now require an explicit `ConstantPool` owner parameter. The
implementation rejects `null` and uses that owner for auto-narrowing and generic
resolution. Direct call sites in `SignatureConstant`, `TerminalTypeConstant`,
and compiler return-fit checking now pass the pool they already own.

Why behavior is preserved:

- correct old callers already had the same pool installed in the ambient scope;
  they now pass it directly;
- helper constants are still interned in the same owner pool;
- no new cache is added and no existing cache is removed;
- the only runtime cost is one null check on a relation path that already does
  type analysis.

Proof: `TypeConstantOwnerApiTest` verifies that the old two-argument method
signatures are gone, the new signatures require a `ConstantPool`, and a missing
owner fails immediately instead of falling through to ambient thread state.

Design rule: a method that resolves or interns type metadata is not a pure
predicate unless the owner is explicit. Do not hide pool selection behind
`ConstantPool.getCurrentPool()` just because plumbing an owner through the call
chain is inconvenient.

### Numeric range constant folding

References:

- `javatools/src/main/java/org/xvm/asm/constants/ByteConstant.java:295`
  through `javatools/src/main/java/org/xvm/asm/constants/ByteConstant.java:376`
  (range-producing byte/nibble operations)
- `javatools/src/main/java/org/xvm/asm/constants/IntConstant.java:725`
  through `javatools/src/main/java/org/xvm/asm/constants/IntConstant.java:767`
  (range-producing integer operations)

Old cause: numeric compile-time range operations created `RangeConstant`
results through `ConstantPool.getCurrentPool()`.

Why this was broken: a range constant is owner-scoped constant-pool state. If no
ambient pool was installed, folding `1..3` could fail with a null current pool.
If a stale or nested ambient pool was installed, folding a range from constants
owned by pool A could create a `RangeConstant` in pool B. That gives later code a
valid-looking range whose containing pool does not match its operands or caller.

Fix: `ByteConstant` and `IntConstant` now create folded ranges through the
receiver constant's `getConstantPool()`. The receiver is already an owned
constant, so this is the narrowest explicit owner available without changing
the entire arithmetic `Constant.apply(...)` API.

Why behavior is preserved:

- correct old callers had the receiver's pool installed as current; they now
  use the same pool directly;
- the same `RangeConstant` shape and endpoint constants are produced;
- no cache is removed, because `ensureRangeConstant(...)` creates the same
  non-interned range constant as before;
- no per-owner or per-value footprint is added.

Proof: `ConstantRangeOwnerTest` verifies that byte and integer range folding
works with no ambient pool and that a wrong ambient pool is ignored. The
no-ambient test would fail on `master`; the wrong-ambient test would produce a
range owned by the wrong pool.

Design rule: if a receiver object is already owned, use that owner. Ambient
current-pool lookup is not a substitute for a real owner parameter or an
owner-bearing receiver.

### ConstantPool function compatibility

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3346`
  (`checkFunctionCompatibility(...)`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3471`
  (receiver `typeTuple0()`)

Old cause: `checkFunctionCompatibility(...)` is an instance method on
`ConstantPool`, but one compatibility exception asked
`ConstantPool.getCurrentPool().typeTuple0()` for the empty tuple type.

Why this was broken: even single-threaded code can call an instance method
outside an ambient scope, or inside a nested scope for another pool. In parallel
or same-JVM runtime execution, the current Java thread has no inherent
relationship to the pool whose function types are being compared.

Fix: the method now uses receiver `typeTuple0()`.

Why behavior is preserved:

- correct old callers had this same pool installed as current;
- the compatibility rule is unchanged;
- no cache is removed or added; the receiver pool still interns `Tuple<>` in
  its existing common-type cache.

Proof:
`ConstantPoolDiagnosticsTest.functionCompatibilityUsesReceiverPoolWithoutAmbientPool()`
builds the compatibility case that reaches this rule with no ambient pool and
expects the receiver pool to answer correctly. The old implementation would
dereference a missing current pool.

Design rule: an object method must not ignore its own owner and consult
thread-local state for the same owner. That is a hidden global precondition, not
an API.

## Must Fix

### Ambient current pool is still semantic state

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3730`
  (`getCurrentPool()`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3770`
  (`withPool(...)`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4116`
  (`ThreadLocal<ConstantPool[]> s_tloPool`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3472`
  (`getCurrentPool().typeTuple0()`)
- `javatools/src/main/java/org/xvm/asm/constants/ByteConstant.java:295`
  and `javatools/src/main/java/org/xvm/asm/constants/ByteConstant.java:370`
  (range constants from current pool)
- `javatools/src/main/java/org/xvm/asm/constants/IntConstant.java:725`
  through `javatools/src/main/java/org/xvm/asm/constants/IntConstant.java:767`
  (range constants from current pool)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6272`
  and `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6352`
  (covariant/contravariant type helpers)
- `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:506`
  (generic signature resolver)
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:683`
  (duck-type identity repair)
- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:1475`
  and `javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:692`
  (private `pool()` helpers)

Cause: the owner pool is pulled from hidden thread state instead of being passed
as an explicit argument. `assertCurrentPool(...)` and
`assertCurrentPoolIfPresent(...)` are also Java assertions
(`ConstantPool.java:3742`, `ConstantPool.java:3756`), so normal production runs
do not enforce the owner check.

Effect: nested runtime scopes, reused worker threads, async callbacks, or
parallel type work can select the wrong pool or `null`. The call site gives no
static indication that owner context is required.

Recommended guard/fix: remove semantic `getCurrentPool()` use from constants
and metadata helpers by threading `ConstantPool`, `Container`, or `Frame`
explicitly. Keep `withPool(...)` only as a temporary boundary bridge, and make
runtime owner mismatch diagnostics independent of `assert`.

This branch fixed the nested identity resolver case. The old API was especially
misleading because `resolveNestedIdentity(pool, resolver)` already had an
explicit `pool` parameter. Resolver-backed `NestedIdentity` objects discarded
that parameter and later called `ConstantPool.getCurrentPool()` when comparing
or hashing nested method signatures. That is bad even in a single-threaded
world: a nested helper can temporarily install another pool, or no pool, and
the resolver will silently use that hidden state instead of the owner requested
by the caller. The replacement stores the caller's output pool in the
resolver-backed nested identity and uses it for `SignatureConstant` generic
resolution. Canonical nested identities still avoid owner state, preserving the
old cacheable key shape when no resolver is involved.

### Runtime-published pools are still mutable by default

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:206`
  (`assertRegisterBeforeRuntimePublished(...)` call)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:282`
  through `javatools/src/main/java/org/xvm/asm/ConstantPool.java:305`
  (diagnostic marker and fail-fast check)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4111`
  (`runtimePublication`)

Cause: publication is guarded only when
`xvm.asm.validateConstantPoolLateRegistration` is enabled. Otherwise the same
pool can keep registering constants during runtime execution.

Effect: parallel runtime readers can observe pool growth, cache invalidation,
or partially registered constants after the pool has become container-visible.
The diagnostic property is useful, but it is not a structural freeze.

Recommended guard/fix: split mutable compiler/linker pools from frozen runtime
pools, or make post-publication registration a hard error on runtime paths.
The property can remain as extra diagnostics, not as the only guard.

## Must Audit

### Default adoption shallow-clones constants while changing ownership

References:

- `javatools/src/main/java/org/xvm/asm/XvmStructure.java:41` through
  `javatools/src/main/java/org/xvm/asm/XvmStructure.java:52`
  (design text says parentage is fixed)
- `javatools/src/main/java/org/xvm/asm/XvmStructure.java:120`
  (`setContaining(...)`)
- `javatools/src/main/java/org/xvm/asm/Constant.java:312` through
  `javatools/src/main/java/org/xvm/asm/Constant.java:320`
  (`super.clone()`, `setContaining(pool)`, `resetRefs()`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:211` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:214`
  (normal foreign-constant adoption)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:230` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:235`
  (locator adoption)
- `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:55`
  through `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:57`
  (opt-in validator)

Cause: base adoption copies every reference, including transient and final
helper cells, then mutates the owner. The validator is property-gated and does
not make adoption intrinsically safe.

Effect: owner-local runtime state, locks, thread locals, caches, JIT names, or
handles can be copied into a destination pool unless every subclass proves that
its copied fields are logical value only or clears them on owner change.

Recommended guard/fix: replace generic clone adoption with an explicit copy
contract or generated copy constructors. At minimum, keep a validator enabled
in CI/stress runs and expand it to cover arrays, `ObjectHandle`, `Component`,
and other owner-bearing helper objects.

### `register(...)` publishes before recursive registration completes

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:217` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:225`
  (position, list add, map put)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:262` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:269`
  (recursive `registerConstants(...)` after publication)
- `javatools/src/main/java/org/xvm/asm/Constant.java:560` through
  `javatools/src/main/java/org/xvm/asm/Constant.java:587`
  (cached hash code)
- `javatools/src/main/java/org/xvm/asm/Constant.java:982`
  (`m_iHash`)
- `javatools/src/main/java/org/xvm/asm/constants/NamedConstant.java:166`
  through `javatools/src/main/java/org/xvm/asm/constants/NamedConstant.java:168`
  (field rewrites during registration)
- `javatools/src/main/java/org/xvm/asm/constants/ArrayConstant.java:285`
  and `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:625`
  through `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:631`
  (more registration-time field rewrites)

Cause: the constant is inserted into `f_listConst` and the lookup map before
its underlying constants are recursively registered and before some subclasses
finish rewriting owner-sensitive fields.

Effect: another thread can find a partially registered constant. If any
registered-as-key field changes equality/hash behavior after map insertion, the
lookup map can become inconsistent.

Recommended guard/fix: either make registration a single-owner phase, or build
and recursively adopt into a private work item before publishing to list/map.
Add assertions that `registerConstants(...)` does not change logical hash or
equality after insertion.

### `f_listConst` is not a concurrent storage boundary

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:83`,
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:117`, and
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:130`
  (unsynchronized readers)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:217` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:224`
  (synchronized append)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2560` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2594`
  (live, mod-count-blind iterator)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3006` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3008`
  (assembly iteration)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4055`
  (`ArrayList<Constant> f_listConst`)

Cause: appends are guarded by `synchronized (this)`, but many reads and
iterations are not. The custom `getContained()` iterator is intentionally
reentrant for validation that appends during traversal.

Effect: this supports a narrow single-thread reentrant traversal pattern, not
arbitrary parallel mutation. Concurrent runtime readers can see stale size,
incomplete publication, or structural changes during iteration.

Recommended guard/fix: freeze the list before runtime publication, return
snapshots for public traversal, and isolate the current validation worklist
behavior behind an explicit internal API.

### Copy-on-write `EnumMap` caches are cleared in place

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3104` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3135`
  (`m_mapConstants` construction)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3157` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3175`
  (`m_mapLocators` construction)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2640` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2642`
  (disassembly clears maps)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3994` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3995`
  (optimization clears maps)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4062` and
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4069`
  (volatile `EnumMap` fields)

Cause: the fields are documented as copy-on-write because `EnumMap` is not
thread-safe, but destructive paths call `clear()` on the current map.

Effect: any reader holding the old volatile reference can race with in-place
mutation. This is likely compiler/serialization phase state, but the code does
not enforce that phase boundary.

Recommended guard/fix: replace `clear()` with assignment of fresh `EnumMap`
instances under the same lock, or assert that destructive paths run only before
runtime publication and with no parallel readers.

### Recursive assembly registration state is single-thread by convention

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:253` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:263`
  (`m_fRecurseReg`, `addRef()`, recursive registration)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3067` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3082`
  (`preRegisterAll()` and `postRegisterAll()`)
- `javatools/src/main/java/org/xvm/asm/Constant.java:436` through
  `javatools/src/main/java/org/xvm/asm/Constant.java:437`
  (`m_cRefs++`)
- `javatools/src/main/java/org/xvm/asm/Constant.java:993`
  (`m_cRefs`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4080`
  (`m_fRecurseReg`)

Cause: assembly registration toggles a pool-wide boolean and increments
per-constant reference counts with plain fields.

Effect: parallel registration during assembly would lose counts and may order
or discard constants incorrectly.

Recommended guard/fix: keep assembly as a single-owner phase with explicit
state assertions, or make the registration tally an isolated worklist that can
be reduced deterministically.

### Deferred TypeInfo work is hidden in per-pool thread-local state

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3192` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3214`
  (`f_tlolistDeferred` operations)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4090`
  (`TransientThreadLocal<List<TypeConstant>>`)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1768`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1788`
  (deferred TypeInfo rebuild loop)

Cause: deferred TypeInfo work is stored in hidden per-thread state while
TypeInfo itself is cached on `TypeConstant` instances and invalidated through
the pool.

Effect: two threads building related TypeInfo for the same pool can maintain
different deferred worklists while publishing to shared per-type caches.

Recommended guard/fix: pass an explicit TypeInfo build context/worklist through
the build stack, or guard TypeInfo building per pool/type so one context owns a
complete build.

### Type relation context uses hidden `ScopedValue`

References:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5835`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5842`
  (`getContext()`)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5855`
  and `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6306`
  (`ScopedValue.where(...)`)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8303`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8307`
  (`ScopedValue<TypeConstant> s_context`)

Cause: a type comparison context is dynamically scoped instead of being an
explicit parameter.

Effect: `ScopedValue` is safer than a raw mutable `ThreadLocal`, but the owner
dependency is still hidden. Nested relation probes can become hard to reason
about, and code moved to async/parallel execution must preserve dynamic scope
deliberately.

Recommended guard/fix: pass relation context explicitly in new APIs. Keep
`ScopedValue` only as a transitional adapter around legacy recursive relation
code.

### TypeConstant helper caches mix concurrent and plain mutable state

References:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1935`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1972`
  (atomic TypeInfo and invalidation updaters)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7778`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7785`
  (`m_handle` TypeHandle cache)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7952`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7958`
  (`m_fValidated`)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8007`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8034`
  (relation/consume/produce cache allocation)
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8235`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8291`
  (cache fields)

Cause: some caches use atomic updaters or `ConcurrentHashMap`, while others are
plain booleans, plain `HashMap`s, or unsynchronized runtime/JIT helper fields.

Effect: duplicate computation may be benign, but not all fields are published
or invalidated with the same guarantees. `m_handle` is especially owner
sensitive because it contains runtime type-handle state.

Recommended guard/fix: classify each cache as immutable-after-compute,
duplicate-compute-ok, or single-owner-only. Use `volatile`, `ConcurrentMap`, or
explicit owner locks for runtime-visible caches, and keep runtime handles keyed
by container/pool rather than by a potentially shared type object.

### Live runtime handles are stored in constants

References:

- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:25`
  through `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:29`
  (`ObjectHandle` constructor)
- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:54`
  through `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:64`
  (adoption guard)
- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:90`
  (`m_hValue`)
- `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:230`
  through `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:234`
  (`setHandle(...)`)
- `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:115`
  through `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:119`
  (`setHandle(...)`)
- `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:435`
  and `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:231`
  (transient `ObjectHandle` fields)

Cause: constants can hold live `ObjectHandle` values or transient runtime
handle caches. `HandleConstant` rejects moving an already-owned handle constant
to another pool, but it still allows initial adoption of a fresh unowned handle
constant.

Effect: live runtime/container state can become reachable from a shared pool.
The `FSNodeConstant` and `FileStoreConstant` setters also rely on asserts and
plain non-volatile fields.

Recommended guard/fix: keep live handles out of frozen/shared pools when
possible. If runtime annotation construction requires `HandleConstant`, enforce
same-owner and not-runtime-published checks. Replace assert-only handle
initialization with compare-and-set or an owner-local runtime cache.

### Valid upstream pool set is mutable lazy state

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:327`
  through `javatools/src/main/java/org/xvm/asm/ConstantPool.java:346`
  (`buildValidPoolSet()` / `contributeToValidPoolSet(...)`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:269`
  (`checkValidPools(f_setValidPools, ...)`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4075`
  (`IdentityHashMap`-backed set)

Cause: the valid-pool set is an unsynchronized identity set built lazily.

Effect: concurrent build/check can race, and partial upstream visibility can
change assertion behavior.

Recommended guard/fix: build the set eagerly before parallel registration, or
publish an immutable snapshot with synchronization.

### Identity and member constants carry transient owner-derived caches

References:

- `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:778`
  through `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:781`
  (`setContaining(...)` resets cached info)
- `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:805`
  through `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:815`
  (`m_component`, `m_canonicalNid`, `m_fNested`)
- `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:625`
  through `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:631`
  (`registerConstants(...)` clears type only)
- `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:724`
  and `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:729`
  (`m_type`, `m_sJitName`)
- `javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:283`
  through `javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:287`
  (`invalidateCache()`)
- `javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:454`
  through `javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:474`
  (`m_type`, `m_constSig`, `m_typeConstraint`, `m_info`, `m_sJitName`)

Cause: these classes mostly rely on default adoption and targeted cache
clearing during registration or owner change.

Effect: some caches are reset, but JIT names and metadata caches need an
explicit proof that copying across pools is harmless. Otherwise a target pool
can inherit source-owner helper results.

Recommended guard/fix: add `adoptedBy(...)` overrides or shared
`setContaining(...)` reset hooks for every owner-derived cache. Treat JIT names
as runtime/type-system helper state until proven globally stable.

## Should Fix

### Per-pool implicit and common-type caches are plain lazy fields

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:1273`
  through `javatools/src/main/java/org/xvm/asm/ConstantPool.java:1309`
  (`f_implicits` lazy population)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2189`
  through `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2222`
  (`m_setJitPrimitives`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2228`
  through `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2404`
  (large `m_clz*`, `m_type*`, `m_val*`, `m_sig*` lazy block)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4085`
  (`HashMap f_implicits`)
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4127`
  through `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4308`
  (transient common constants)

Cause: per-pool helpers are cached through unsynchronized null checks and plain
field writes.

Effect: this is owner-local, so it is not a cross-container static bug, but a
single shared pool can duplicate work or publish helper values without a clear
happens-before relationship.

Recommended guard/fix: warm these caches before pool publication, or replace
hot runtime-visible caches with owner-local `ConcurrentMap.computeIfAbsent`,
`volatile`, or a small lazy helper.

### Static immutable metadata is stored in mutable collections

References:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4002`
  through `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4017`
  (`s_implicits`, `s_implicitsByPath`)
- `javatools/src/main/java/org/xvm/asm/constants/UnionTypeConstant.java:709`
  (`SpecialFunkies`)

Cause: static final fields hold mutable `HashMap`/`HashSet` instances after
class initialization.

Effect: no current code path mutates these after initialization, so this is not
a proven race, but the type does not communicate immutability.

Recommended guard/fix: use `Map.copyOf(...)` and `Set.of(...)`/`Set.copyOf(...)`
after construction.

### Adoption validator is useful but not comprehensive

References:

- `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:33`
  (`xvm.asm.validateConstantAdoption`)
- `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:143`
  through `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:160`
  (forbidden shared references)

Cause: the validator catches several classes of shared mutable helper state,
but only when enabled and only for selected object/reference categories.

Effect: it is a diagnostic safety net, not a proof that default adoption is
safe for all constants.

Recommended guard/fix: enable it in reentrancy stress runs and CI profiles that
exercise adoption. Extend the forbidden set to cover arrays of mutable helpers,
`ObjectHandle`, runtime templates/compositions, and owner-derived components.

## Benign Or Proven Owner-Local

### TypeConstant adoption clears owner/runtime helper state

References:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7924`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7941`

Why benign for adoption: `TypeConstant.setContaining(...)` resets TypeInfo,
relation, thread-local recursion, consume/produce, runtime handle, JIT name,
and normalized-type caches when the containing pool changes.

Residual risk: the caches themselves still need parallel-read/write audit
within one owner, as listed above.

### Fresh-constructor adoption for known mutable helper cells

References:

- `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java:262`
  through `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java:267`
  (fresh singleton constant)
- `javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java:1040`
  through `javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java:1045`
  (fresh parameterized type)
- `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:749`
  through `javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java:758`
  (fresh signature while preserving property marker)
- `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:229`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java:232`
  (fresh type parameter)

Why benign for adoption: these overrides avoid sharing final locks,
atomics, thread-local cells, or comparison/JIT helper state that base
`Object.clone()` would otherwise copy by reference.

### Runtime handle adoption clears for filesystem constants

References:

- `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:238`
  through `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:245`
  (`m_handle` and `m_constPath` cleared)
- `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:123`
  through `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:129`
  (`m_handle` cleared)

Why benign for adoption: copied filesystem constants do not retain source-pool
runtime handles or derived path constants after adoption.

Residual risk: handle initialization is still assert/plain-field based and is
listed under must-audit for runtime parallelism.

### Clone-on-write array registration avoids mutating shared cloned arrays

References:

- `javatools/src/main/java/org/xvm/asm/Constant.java:699`
  through `javatools/src/main/java/org/xvm/asm/Constant.java:721`
  (`registerConstants(pool, array)`)
- `javatools/src/main/java/org/xvm/asm/constants/ArrayConstant.java:198`
  through `javatools/src/main/java/org/xvm/asm/constants/ArrayConstant.java:207`
  (clone-on-write in typedef resolution)
- `javatools/src/main/java/org/xvm/asm/constants/MapConstant.java:223`
  through `javatools/src/main/java/org/xvm/asm/constants/MapConstant.java:247`
  (clone-on-write for keys and values)

Why benign for this narrow pattern: when registration or typedef resolution
needs to rewrite an array element, the helper clones the array first, avoiding
in-place mutation of an array that may have been shared by shallow adoption.

Residual risk: the default adopted object can still share an array until a
rewrite occurs. That is safe only if the array is treated as immutable outside
the owner-changing registration path.
