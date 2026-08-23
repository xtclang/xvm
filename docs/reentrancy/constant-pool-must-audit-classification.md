# ConstantPool Must-Audit Classification

Scope: `javatools/src/main/java/org/xvm/asm/ConstantPool.java`,
`javatools/src/main/java/org/xvm/asm/Constant.java`, and
`javatools/src/main/java/org/xvm/asm/constants`.

This report is a closure-oriented classification of the remaining
`ConstantPool`/`Constant` issues called out by the reentrancy audit documents.
It uses the current working tree line numbers. It intentionally does not claim
that every listed item is a proven failing test. `MUST AUDIT` means the design
still relies on an owner, phase, or confinement assumption that is not encoded
in the API.

## Executive Classification

| Status | Category | Bottom line |
| --- | --- | --- |
| DONE IN THIS BRANCH as a guard; MUST FIX long term with clone-free adoption | Base `Constant.adoptedBy(...)` shallow clone contract | `Constant.adoptedBy(...)` is now the final owner-transfer wrapper, and the default `copyForAdoption(...)` path rejects shallow-clone adoption unless a constant family explicitly opts in and documents why its copied fields are logical value state. This stops new unsafe constants from silently inheriting `Object.clone()` ownership transfer or bypassing common owner/ref checks. The long-term fix is still explicit copy/adoption constructors instead of clone. |
| DONE IN THIS BRANCH as a guard; MUST FIX long term with transactional registration | `ConstantPool.register(...)` publishes before recursive registration completes | The branch preserves same-thread cycle resolution but marks newly published constants as incomplete and makes other threads wait until recursive `registerConstants(...)` and valid-pool checks finish. The long-term fix is still private transactional registration that does not expose in-progress constants through public pool APIs. |
| MUST FIX for runtime freeze; DONE IN THIS BRANCH for known first `ClassComposition` access-type prewarm | Runtime-published pool mutation, including first `ClassComposition` construction | Late registration is diagnostic-only, so normal runtime pools can still mutate. The branch now prewarms known access-type constants before the diagnostic publication marker and adds a first-composition test for that narrow subcase. |
| MUST FIX for runtime-published pools; MUST AUDIT otherwise | `f_listConst`, locator maps, and constant lookup maps | The storage supports single-thread reentrant validation, not arbitrary parallel mutation after publication. |
| MUST AUDIT, with runtime-published use as MUST FIX | Live `HandleConstant` / `ObjectHandle` state in constants | The second-adoption guard is fixed, but fresh runtime handle constants and filesystem handle caches are still live runtime state reachable from constants. |
| SHOULD FIX, becomes MUST AUDIT when a pool is shared concurrently | Per-pool implicit/core lazy caches | Owner is local to the pool, but writes are plain lazy field writes and can register constants after runtime publication. |
| MUST AUDIT | Owner-derived helper caches on constant subclasses | `TypeConstant`, identity/member constants, and JIT helpers mix concurrent and plain caches. Adoption clears several, but same-owner parallel publication is not fully proven. |
| MUST AUDIT, MUST FIX if reachable on runtime pools | Destructive `optimize()`, `replaceModule(...)`, and disassembly mutations | These reorder positions, clear maps, and rewrite pool contents. They must remain compiler/serialization-only or be guarded out of runtime pools. |
| DONE IN THIS BRANCH for semantic getter removal; MUST AUDIT for remaining bridges | Ambient current-pool effects inside constants | `getCurrentPool()` is gone and source-shape tests guard it. Remaining `withPool(...)` scopes are transitional bridge boundaries and must keep explicit owner assertions until they are replaced by explicit owner APIs. |
| DONE IN THIS BRANCH | Static mutable metadata maps | `ConstantPool` implicit maps are now frozen after class initialization, and the unused `UnionTypeConstant.SpecialFunkies` mutable set was removed. |

## 1. Base `Constant.adoptedBy(...)` Shallow Clone Contract

Classification: DONE IN THIS BRANCH as a guard. MUST FIX long term with
clone-free adoption.

Evidence:

- `javatools/src/main/java/org/xvm/asm/Constant.java:71` declares `Constant`
  as `Cloneable`.
- `javatools/src/main/java/org/xvm/asm/Constant.java:312` through
  `javatools/src/main/java/org/xvm/asm/Constant.java:321` implements base
  adoption with `super.clone()`, `setContaining(pool)`, and `resetRefs()`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:211` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:214` adopts a foreign
  constant during registration.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:230` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:235` performs the same
  adoption path for locator constants.
- `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:60`
  through `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:63`
  makes validation opt-in only.

Why the old design is bad:

Adoption changes structural owner from one pool to another, but `Object.clone()`
copies every reference field, including `final` helper cells, locks, thread
locals, runtime handles, JIT names, and in-progress caches. `transient` does not
change clone behavior. A subclass author must remember every non-logical field
and override adoption or clear state during owner change.

Practical same-JVM/parallel failure mode:

Pool B can receive a constant object that still points at pool A's runtime
state. The proven failure family was singleton lifecycle state: an adopted
singleton reused another container's initialized handle. The same shape applies
to future default-cloned constants with `ObjectHandle`, `Container`,
`TypeComposition`, lock, `ThreadLocal`, or mutable collection fields.

Branch fix:

`Constant.adoptedBy(...)` is now the final owner-transfer wrapper. It constructs
an `AdoptionContext`, calls `copyForAdoption(...)`, checks that the copy belongs
to the requested pool, and resets refs. The default `copyForAdoption(...)`
fails closed by calling `allowsDefaultAdoptionClone()`, which returns `false` by
default. Constant families that still use the transitional shallow-clone helper
must override that method and explain why copied fields are logical value state
or are cleared by owner-change hooks. Special runtime-bearing constants now
implement `copyForAdoption(...)` under the wrapper.

This turns the old "safe unless somebody remembers a problem" contract into
"illegal unless the constant family declares the policy". It is still a
compromise because the opted-in families still use `Object.clone()` internally.
The helper is named `cloneForAdoption(...)` and is available only through
`copyForAdoption(...)`, so future owner-bearing state has a review choke point.

Existing reproducer, test, or diagnostic:

- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:49` through
  `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:183` covers
  fixed adoption subcases for `TypeConstant`, parameterized types, signatures,
  type parameters, `HandleConstant`, and filesystem constants.
- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:211` through
  `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:284` proves
  the validator catches a bad default clone and rejects bad registration when
  the validation property is enabled.
- `ConstantAdoptionTest.defaultAdoptionCloneRequiresExplicitPolicy()` proves a
  new `Constant` subclass cannot silently inherit shallow-clone adoption.
- `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:153`
  through `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:170`
  define the current forbidden reference classes.

Proper fix:

Remove shallow clone as the default ownership-transfer mechanism. Keep
`Constant.adoptedBy(...)` as the final wrapper, but replace the remaining
opted-in family default with explicit owner-aware `copyForAdoption(...)`
implementations that list logical fields and intentionally drop, rebuild, or
reject non-logical state. Keep `ConstantAdoptionValidator` enabled in stress/CI
while migrating.

Expected performance and semantic impact:

No steady-state runtime cost is required. Adoption may allocate explicit copies
instead of using a shallow clone, but adoption already allocates on cross-pool
registration. Semantics should stay "same logical constant value in target
pool" while eliminating copied helper/runtime state.

Recommended PR slice:

Create a dedicated constant adoption contract PR. First add a source-shape or
reflection test that no concrete `Constant` subclass relies on base shallow
clone without an explicit annotation/override. Then convert subclasses in small
format families and leave the validator property enabled in direct same-JVM
stress.

Done in this branch:

- `Constant.adoptedBy(...)` is now the final owner-transfer wrapper and the
  default `copyForAdoption(...)` path rejects shallow clone unless a constant
  family explicitly opts in with `allowsDefaultAdoptionClone()`.
- `Constant.cloneForAdoption(...)` isolates the remaining transitional clone
  helper behind an explicit method name so owner-transfer clone use is searchable
  and reviewable.
- `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java:262`
  through `javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java:267`
  constructs a fresh adopted singleton.
- `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:238`
  through `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:245`
  clears copied filesystem handle/path cache state.
- `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:123`
  through `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:129`
  clears copied filesystem handle state.
- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:54`
  through `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:63`
  rejects second adoption of an already-owned live handle.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7932`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7950`
  clears owner-derived type helper caches on owner change.
- `javatools/src/main/java/org/xvm/asm/constants/DynamicFormalConstant.java`
  reconstructs adoption from serialized register identity and rejects a
  register type that is not shared with the destination pool, instead of
  shallow-cloning the transient compiler `Register`.
- `javatools/src/main/java/org/xvm/asm/constants/RegisterConstant.java`
  reconstructs adoption from serialized register index and rejects unknown
  registers, instead of shallow-cloning the transient compiler `Register`.
- `javatools/src/main/java/org/xvm/asm/constants/MethodBindingConstant.java`
  reconstructs adoption from method identity, allowing `FrameDependentConstant`
  to fail closed instead of granting shallow clone to future subclasses.
- The `ConditionalConstant` family no longer opts into default clone. Concrete
  condition leaves reconstruct logical predicates from name/module/version/child
  state, and the transient `iTest` simulation slot is private scratch state that
  is not adopted.
- `UInt8ArrayConstant`, `FPNConstant`, and `Float128Constant` now copy byte-array
  constructor inputs and reconstruct adopted values with fresh arrays, so caller
  mutation or source-pool mutation cannot rewrite the target-pool constant's
  hash/equality backing bytes.
- Immutable scalar value constants now reconstruct the same logical value in the
  destination pool instead of inheriting family shallow clone. This preserves the
  old allocation/interning behavior and closes the design hole where a future
  scalar cache/helper field would be owner-leaked by default.
- `ArrayConstant`, `MapConstant`, and `RangeConstant` now reconstruct composite
  value containers explicitly. Array/map constructors copy caller-provided arrays,
  and target registration still performs recursive child-value adoption. This
  fixes the container-aliasing part of the bug without claiming type-family
  adoption is complete.
- `LiteralConstant`, `VersionConstant`, and `DecimalAutoConstant` now reconstruct
  parsed/delegated values explicitly. Literal adoption drops transient parsed
  helper state, version adoption preserves the concrete subclass, and decimal-auto
  target registration adopts the delegated decimal child.
- `MatchAnyConstant` now reconstructs the wildcard shell explicitly. This fixes
  the value-class clone smell, rejects unrelated foreign type keys before the
  value is published, and keeps clone-free type reconstruction in the
  type-family work where it belongs.
- `TerminalTypeConstant` now reconstructs the type leaf explicitly from its
  defining identity. Shared identities still register through the destination
  pool, while unrelated foreign identities fail in all modes instead of relying
  on `TypeConstant.setContaining(...)` assertions.
- `AccessTypeConstant`, `ImmutableTypeConstant`, and `ServiceTypeConstant` now
  reconstruct their logical single-child wrappers explicitly. Shared child types
  still register through the destination pool; unrelated foreign child types fail
  before the wrapper can be published.
- `UnionTypeConstant`, `IntersectionTypeConstant`, and `DifferenceTypeConstant`
  now reconstruct their logical two-child relational shells explicitly. Shared
  child types still register through the destination pool; unrelated foreign
  child types fail before publication.
- `VirtualChildTypeConstant`, `InnerChildTypeConstant`,
  `AnonymousClassTypeConstant`, and `PropertyClassTypeConstant` now reconstruct
  dependant child/property type shells explicitly. Shared parent and child
  identities still register through the destination pool; resolved child
  structure and property-info caches are rebuilt locally.
- `RecursiveTypeConstant` now reconstructs the recursive typedef shell
  explicitly so clone-free terminal adoption cannot erase the recursive subclass.
- `Annotation` now copies parameter arrays at construction/adoption time and
  rejects already-owned runtime handle params. Annotation params participate in
  immutable logical identity, so caller-owned or shallow-cloned arrays were not
  acceptable owner-transfer state.
- `AnnotatedTypeConstant` now reconstructs the annotation/underlying-type shell
  explicitly and drops the derived annotation-type cache so the destination
  owner recomputes it.
- `TypeSequenceTypeConstant` now reconstructs its stateless marker explicitly.
- `PendingTypeConstant` and `UnresolvedTypeConstant` now reject adoption because
  they are mutable compiler/name-resolution placeholders, not completed pool
  metadata.
- `ThisClassConstant`, `ParentClassConstant`, and `ChildClassConstant` now
  reconstruct pseudo class-path shells with target-owned child identities. The
  focused test proved that locator adoption by itself is not enough when
  recursive registration is deferred; the shell field must be born target-owned.
- `KeywordConstant` now reconstructs the same per-format singleton shell in the
  destination pool.
- `DeferredValueConstant`, `ExpressionConstant`, and `UnresolvedNameConstant`
  now reject adoption because they are unresolved compiler/AST placeholders.
  `UnresolvedNameConstant` also copies caller name arrays at construction.
- `CastTypeConstant` now rejects adoption because it is a transient compiler/JIT
  marker and already cannot be assembled into a pool.

Remaining linked finding:

- The `MatchAnyConstant` shell and foreign-key boundary are closed, but its
  locator is still a `TypeConstant`. Terminal, single-child type wrappers,
  storable relational shells, dependant child/property shells, and recursive
  typedef shells are closed. Annotated type shells, pseudo path constants, the
  stateless sequence marker, and pending/unresolved placeholders are also closed
  by this branch. Remaining clone-free work is now the reviewed abstract/base
  fallback plus the eight identity/path leaves that still intentionally opt in
  to the transitional clone helper.

## 2. `ConstantPool.register(...)` Publishes Before Recursive Registration Completes

Classification: DONE IN THIS BRANCH as a guard. MUST FIX long term with
transactional registration.

Evidence:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:217` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:225` assigns the
  position, appends the constant to `f_listConst`, and puts it in the lookup
  map.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:246` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:263` delays recursive
  `registerConstants(...)` until after publication.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:265` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:269` runs valid-pool
  checks after the constant is already visible.
- `javatools/src/main/java/org/xvm/asm/constants/NamedConstant.java:166`
  through `javatools/src/main/java/org/xvm/asm/constants/NamedConstant.java:168`
  rewrite child references during recursive registration.
- `javatools/src/main/java/org/xvm/asm/constants/ArrayConstant.java:283`
  through `javatools/src/main/java/org/xvm/asm/constants/ArrayConstant.java:285`
  rewrites the element type and value array.
- `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:625`
  through `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:631`
  clears cached type state and rewrites parent/signature constants.

Why the old design is bad:

Interning maps use constants as keys. Publishing the key before all owned child
constants are adopted and all equality/hash-relevant state is stable makes the
map depend on mutation that happens later. The current design works only if
registration is effectively a single-owner phase and if post-publication field
rewrites never change key identity.

Practical same-JVM/parallel failure mode:

Thread A registers a constant and publishes it in the list/map. Thread B looks
it up or iterates the pool before A finishes recursive child registration.
Thread B can observe foreign-pool child constants, stale locator state, or a key
whose logical equality changes after insertion. That can produce duplicate
interning, map misses, wrong-owner child graphs, or valid-pool failures that
depend on timing.

Existing reproducer, test, or diagnostic:

- `ConstantPoolDiagnosticsTest.otherThreadsWaitForRecursiveRegistrationCompletion()`
  creates a synthetic constant whose `registerConstants(...)` blocks after the
  constant has received a pool index. A second thread attempts to read that index
  through the normal public API. On the old design, that reader can observe the
  half-registered constant. In this branch, the read remains blocked until the
  registration owner releases the recursive phase.
- `ConstantPoolDiagnosticsTest.registrationOwnerCanResolveInProgressConstant()`
  proves the reason early publication existed in the first place: the same
  registration owner can still resolve its own in-progress constant during
  recursive registration.
- `ConstantPoolDiagnosticsTest.failedRecursiveRegistrationStaysFailedForReaders()`
  proves that a failure after early publication remains visible to later public
  readers instead of returning a partial constant graph.
- The adoption validator runs before publication for copied helper references,
  and the late registration diagnostic catches new constants after publication.
  Those guards are complementary: they do not replace the publication-order
  guard.

Branch fix:

`ConstantPool.register(...)` now records a `RegistrationCompletion` immediately
before it assigns a position and inserts a new constant into `f_listConst` and
the constant lookup map. Same-thread recursive registration can still retrieve
the in-progress constant, which preserves the legacy cycle-breaking behavior.
Other threads that reach `getConstant(...)`, `getConstants()`, or
`getConstant(Constant)` wait for the completion future. If recursive
registration fails, the completion is failed exceptionally, so a waiting reader
does not treat a broken partial graph as complete.

This is a defensive bridge, not the final architecture. It still publishes the
object early for the registration owner because too much legacy code expects
positions and recursive lookups to exist while `registerConstants(...)` runs.
The correct architecture is to keep that recursion map private to registration
and publish only completed constants.

Proper fix:

Make registration transactional from the perspective of readers. Either adopt
and recursively register into a private work item before publishing to
`f_listConst` and maps, or explicitly confine registration to a single builder
phase and freeze before parallel readers. Add assertions that
`registerConstants(...)` does not change equality, hash, locator, or owner
fields after map insertion.

Expected performance and semantic impact:

The preferred runtime impact is positive: fewer visible partial states and no
runtime registration races. Registration may do more work before acquiring the
publish point and may need a cycle-aware worklist, but this is compiler/linker
or warmup work rather than steady-state execution.

Recommended PR slice:

Create a registration publication-order PR. Add a diagnostic test constant whose
`registerConstants(...)` blocks or records observation, prove the current
publish-before-recursion shape, then introduce a two-phase worklist or explicit
single-owner registration guard.

Done in this branch:

- `ConstantPool.register(...)` marks a newly inserted constant as incomplete
  until recursive registration and valid-pool checks finish.
- `getConstant(int)`, `getConstant(int, Class<T>)`, `getConstants()`, and
  `getConstant(Constant)` wait when they encounter a constant still being
  completed by another thread.
- Registration failures complete the in-progress marker exceptionally, so
  waiting readers do not treat a failed partial graph as stable.
- A failed marker remains installed because a registration exception after early
  publication leaves the pool structurally suspect. Later public readers should
  see the failure, not a half-registered constant.
- Same-thread recursive lookup still sees the in-progress constant, preserving
  the cycle-breaking behavior that the old early-publication design relied on.

## 3. Runtime-Published Pool Mutation And First `ClassComposition` Construction

Classification: MUST FIX for runtime freeze generally. DONE IN THIS BRANCH
for the known already-in-pool class/type first-`ClassComposition` access-type
subcase.

Evidence:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:282` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:288` installs the
  runtime publication marker only when
  `xvm.asm.validateConstantPoolLateRegistration` is enabled, and prewarms known
  runtime access-type constants only on that diagnostic path.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:298` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:335` scans already-known
  class/type constants and interns private/protected/struct access types before
  the marker is recorded.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:350` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:360` fails late
  registration only when the marker exists.
- `javatools/src/main/java/org/xvm/runtime/MainContainer.java:220` through
  `javatools/src/main/java/org/xvm/runtime/MainContainer.java:264` marks the
  pool after entry setup and module singleton resolution, immediately before
  user call-chain invocation.
- `javatools/src/main/java/org/xvm/runtime/Container.java:412` through
  `javatools/src/main/java/org/xvm/runtime/Container.java:429` can construct a
  first `ClassComposition` through `computeIfAbsent`.
- `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:67` through
  `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:73` prewarms
  private/protected/struct access type constants during composition creation.
- `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:221` through
  `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:254` still
  interns access constants for non-canonical revealed types.

Why the old design is bad:

A runtime-visible pool should be a read-mostly frozen artifact. The current
guard is a diagnostic property, not a structural freeze. Normal runs can still
call `ensure*Constant(...)` paths after publication and grow or rewrite the
pool while runtime readers are active.

Practical same-JVM/parallel failure mode:

Container A begins executing user code while another fiber or callback lazily
creates a class composition, access type, annotation type, function signature,
or core constant in the same pool. Readers can observe pool growth, stale map
views, or partial registration. Repeated same-JVM runs make this worse because
one run can warm a path that another run assumes was already frozen.

Existing reproducer, test, or diagnostic:

- `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java:89`
  through `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java:143`
  prove the opt-in late-registration guard is disabled by default, catches a
  new constant when enabled, and does not create a lookup map while failing.
- `javatools/src/test/java/org/xvm/runtime/ClassCompositionLateRegistrationTest.java:23`
  through `javatools/src/test/java/org/xvm/runtime/ClassCompositionLateRegistrationTest.java:53`
  covers the fixed subcase where `ensureAccess(PROTECTED)` on an already-created
  composition no longer registers after publication.
- `javatools/src/test/java/org/xvm/runtime/ClassCompositionLateRegistrationTest.java:55`
  through `javatools/src/test/java/org/xvm/runtime/ClassCompositionLateRegistrationTest.java:80`
  covers the fixed subcase where the first `ClassComposition` for an already-known
  type does not register access-type constants after diagnostic publication.
- There is still no complete runtime-freeze test that proves every runtime
  `ensure*Constant(...)` path is warmed or forbidden after publication.

Proper fix:

Separate mutable compiler/linker pools from frozen runtime pools, or make
runtime post-publication registration a hard failure after a complete warmup
phase. The new diagnostic prewarm is the right shape for known class/type access
views, but it is property-gated and does not freeze normal runtime pools.
Runtime composition construction should either be part of warmup, or it must
prove it only allocates owner-local composition objects and does not intern new
constants after the freeze point.

Expected performance and semantic impact:

Warmup shifts lazy allocation earlier. Steady-state runtime should improve or
stay neutral because runtime paths stop taking registration and map mutation
paths. Semantics should not change except that latent missing-warmup bugs fail
at the boundary instead of racing later.

Recommended PR slice:

Create a runtime pool freeze/prewarm PR. Start by promoting the existing
diagnostic property in a targeted stress profile, keep the first-composition
test as coverage for the access-type subcase, and enumerate every
`ensure*Constant(...)` reached from runtime after `MainContainer.invoke0(...)`
publication.

Done in this branch:

The already-created composition `ensureAccess(PROTECTED)` subcase is fixed by
prewarming access type constants in the constructor. The diagnostic publication
path also prewarms known class/type access constants before marking the pool, and
the new first-composition test verifies that specific path for an already-known
type.

## 4. `f_listConst`, Locator, And Lookup Map Concurrency

Classification: MUST FIX for runtime-published pools; MUST AUDIT for
compiler/linker phases.

Evidence:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:83` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:84` reads
  `f_listConst` directly.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:116` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:117` reads list size
  directly.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:130` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:132` snapshots through
  `ArrayList.toArray(...)` without synchronizing with writers.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:217` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:225` synchronizes the
  append/map publication path.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:239` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:242` publishes locator
  entries.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2606` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2642` returns a live,
  mod-count-blind iterator over `f_listConst`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3151` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3184` builds
  `m_mapConstants` with volatile copy-on-write shape.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3204` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3225` builds
  `m_mapLocators` similarly.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4093`,
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4100`, and
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4107` define the
  mutable list and volatile map fields.

Why the old design is bad:

The storage model is half synchronized. Writers publish under `synchronized
(this)`, but many readers and iterators do not synchronize. The lookup maps are
concurrent at the inner-map level, but the outer `EnumMap` is copied and then
sometimes cleared in place by destructive paths.

Practical same-JVM/parallel failure mode:

A runtime reader can see stale size, miss a just-added constant, observe a
constant before recursive registration is complete, or iterate while another
thread mutates the backing list. Locator lookups can race with locator adoption
or map clears, producing wrong or missing interned values.

Existing reproducer, test, or diagnostic:

There is no direct concurrent list/lookup-map stress test. The current
`getContained()` comment documents a deliberate single-thread reentrant
validation workaround, not a parallel guarantee.

Proper fix:

Freeze `f_listConst` and lookup maps before runtime publication. Runtime public
traversal should use immutable snapshots or immutable arrays. Keep the
mod-count-blind iterator only as an internal validation worklist until
validation no longer appends through `register(...)`. Replace in-place map
clears with fresh-map publication when destructive compiler phases still need
them.

Expected performance and semantic impact:

Runtime reads become simpler and safer, likely faster after freeze. Compiler
registration may allocate a snapshot or frozen array at the phase boundary.
Semantics remain identical if no runtime code depends on seeing late constants.

Recommended PR slice:

Create a pool storage freeze PR. First add phase assertions and a stress test
for concurrent `register(...)` plus `getConstants()`/`getContained()` access.
Then introduce a frozen read representation for runtime and keep mutable
`ArrayList` storage for build phases only.

## 5. Live `HandleConstant` / `ObjectHandle` State In Constants

Classification: MUST AUDIT, with runtime-published/shared-pool use as
MUST FIX.

Evidence:

- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:13`
  through `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:17`
  defines a runtime-only handle constant.
- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:25`
  through `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:36`
  stores and returns a live `ObjectHandle`.
- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:54`
  through `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:63`
  permits only first registration while unowned and rejects movement after
  ownership exists.
- `javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java:90` holds
  the final live handle reference.
- `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java:523`
  through `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java:553`
  creates `HandleConstant` values from live annotation argument handles.
- `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:230`
  through `javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java:234`
  stores filesystem handles with assert-only single assignment.
- `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:115`
  through `javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java:119`
  does the same for file stores.

Why the old design is bad:

Constants are normally logical serialized pool values. `ObjectHandle` is live
runtime state owned by a `Container`, `Frame`, service context, or composition.
Putting a handle behind a `Constant` makes it look internable and shareable
even when the payload is not.

Practical same-JVM/parallel failure mode:

Runtime annotation construction or filesystem singleton creation can leave a
handle from container A reachable through a constant in a pool reused or
adopted by container B. Plain `setHandle(...)` fields can also race under
parallel first initialization, especially because the single-assignment check is
an assertion and is disabled in normal production.

Existing reproducer, test, or diagnostic:

- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:166`
  through `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:183`
  verifies an already-owned `HandleConstant` cannot move to another pool.
- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:252`
  through `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:267`
  documents the legacy first-registration exception.
- `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:173`
  through `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java:183`
  encodes that exception.

Proper fix:

Do not allow live handles in frozen/shared pool state. Runtime annotations that
need handle-valued arguments should use an owner-local runtime annotation
record, handle table, or frame-time resolver rather than a logical pool
constant. Filesystem handle caches should be owner-local caches with explicit
container validation and compare-and-set or synchronization instead of
assert-only assignment.

Expected performance and semantic impact:

Runtime annotation access may pay one owner-local lookup or descriptor
resolution. Filesystem constants keep the same lazy runtime handle behavior,
but the cache cell moves to an owner-safe location. Logical constant equality
should become less surprising because live handles stop participating as pool
identity.

Recommended PR slice:

Create a live-handle constant policy PR. First add diagnostics that reject
`HandleConstant` registration after runtime publication and validate
filesystem handle owners. Then replace the annotation path with an owner-local
representation.

Done in this branch:

Second adoption of already-owned `HandleConstant` is blocked, and
filesystem constant adoption clears copied handle state. That does not remove
live handle storage from current-owner runtime constants.

## 6. Per-Pool Implicit And Core Lazy Caches

Classification: SHOULD FIX; MUST AUDIT when a single pool is touched by
parallel runtime readers/writers.

Evidence:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:1321` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:1360` lazily fills
  `f_implicits`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2237` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2271` lazily fills
  `m_setJitPrimitives`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2276` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2459` contains the
  compact manual lazy block for core classes, types, values, and signatures.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4123` stores
  `f_implicits` as a plain `HashMap`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4165` onward stores
  the core caches as transient plain fields.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4040` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4068` builds static
  mutable implicit metadata maps.

Why the old design is bad:

The owner is the `ConstantPool`, which is better than a static global, but the
caches use plain null checks and plain writes. They can call `register(...)`
while lazily filling. That makes them harmless only if the pool is confined or
fully warmed before publication.

Practical same-JVM/parallel failure mode:

Two runtime threads can compute the same helper concurrently, publish without a
happens-before edge, or race through `register(...)` after the pool is supposed
to be runtime-visible. The most likely result is duplicate work, but the
published values include owner-bearing singleton and signature constants, so the
category cannot be dismissed without a freeze/warmup proof.

Existing reproducer, test, or diagnostic:

No dedicated lazy-cache concurrency test exists. The late-registration
diagnostic can catch a lazy cache that interns a new value after a publication
marker, but only with the property enabled.

Proper fix:

Warm required core caches before runtime publication, then freeze the pool. For
remaining runtime-visible lazy values, use owner-local `volatile`/`Lazy` cells
or `ConcurrentMap.computeIfAbsent`. Convert static implicit metadata maps to
immutable `Map.copyOf(...)`.

Expected performance and semantic impact:

Warmup increases startup work slightly and reduces first-hit runtime latency.
Replacing plain caches with `volatile` or `ConcurrentMap` should be limited to
runtime-visible paths to avoid unnecessary footprint across every constant
pool. Semantics should not change.

Recommended PR slice:

Create a core-pool warmup and lazy-cache PR. Treat static metadata map
immutability as a small separate cleanup. Keep broader generated/cache-table
work out of the registration-order PR.

## 7. Owner-Derived Helper Caches On Constant Subclasses

Classification: MUST AUDIT.

Evidence:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1935`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1973`
  uses atomic updaters for TypeInfo and invalidation counts.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7786`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:7803`
  formerly cached a runtime `TypeHandle` on the type constant. This branch
  moves that runtime cache to `Container.ensureTypeHandle(TypeConstant)`, so
  the owner dimension is explicit.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8015`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8043`
  lazily allocates relation, consumes, and produces maps with mixed concurrency
  guarantees.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8243`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8299`
  declares the main helper cache fields.
- `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:607`
  through `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:612`
  caches a resolved `Component`.
- `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:787`
  through `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:790`
  resets identity cache state on owner change.
- `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:287`
  through `javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java:325`
  caches JIT method names. Adoption now drops this cache via
  `MethodConstant.copyForAdoption(...)`; same-owner JIT ownership still belongs
  to the broader JIT audit.
- `javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:367`
  through `javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java:384`
  caches JIT property names with synchronization. Adoption now drops this cache
  via `PropertyConstant.copyForAdoption(...)`; same-owner JIT ownership still
  belongs to the broader JIT audit.

Why the old design is bad:

These caches are not serialized logical constant values. They are answers from
the current component graph, type graph, runtime container, or JIT type system.
Some use atomics/concurrent maps, some use plain fields, and some are only
cleared indirectly by adoption/registration hooks.

Practical same-JVM/parallel failure mode:

A shared or adopted constant can serve helper state computed for another owner
or another graph version. Within one pool, parallel TypeInfo/relation builds can
race between placeholder, incomplete, and complete answers or publish JIT names
before the owning type system is agreed on.

Existing reproducer, test, or diagnostic:

- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:49` through
  `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java:164` covers
  several adoption-time helper cache resets.
- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java` covers
  adoption dropping `MethodConstant` type and JIT-name caches.
- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java` covers
  adoption dropping `PropertyConstant` metadata/JIT caches and preserving
  `FormalTypeChildConstant` format.
- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java` covers
  `DynamicFormalConstant` adoption with a shared module type and proves that
  the illegal foreign register-type case fails at the adoption boundary.
- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java` covers
  `RegisterConstant` adoption for allocated registers and proves that unknown
  moving registers fail at the adoption boundary.
- `javatools/src/test/java/org/xvm/asm/ConstantAdoptionTest.java` covers
  `MethodBindingConstant` adoption and proves target registration adopts the
  method identity into the destination pool.
- `javatools/src/test/java/org/xvm/asm/constants/MethodInfoTest.java:126`
  through `javatools/src/test/java/org/xvm/asm/constants/MethodInfoTest.java:164`
  covers parallel `MethodInfo` ownership construction.
- `javatools/src/test/java/org/xvm/asm/constants/TypeInfoMemberOwnershipTest.java:157`
  through `javatools/src/test/java/org/xvm/asm/constants/TypeInfoMemberOwnershipTest.java:207`
  covers parallel property/child metadata ownership.

Proper fix:

For each cache, document owner, key, invalidation source, and publication
guarantee. Runtime handles should be keyed by `Container`/pool and not by a
constant that may be visible from another owner. JIT names need a `TypeSystem`
or classloader ownership story. Plain per-constant maps need confinement proof
or conversion to owner-local concurrent structures.

Expected performance and semantic impact:

Most fixes should preserve the current memoization shape. Some caches may move
from per-constant to per-owner maps, which can increase map entries but reduces
wrong-owner reuse. Runtime hot paths should use existing concurrent maps rather
than per-object `Lazy` where footprint matters.

Recommended PR slice:

Create a TypeConstant/cache ownership PR after the registration/freeze work.
The runtime `TypeHandle` cache and MethodConstant/PropertyConstant adoption
cache copies are fixed in this branch. Continue with relation caches, then
separately audit same-owner JIT name caches because they involve `TypeSystem`
and classloader ownership.

## 8. Destructive Pool Optimization, Module Replacement, And Disassembly Mutations

Classification: MUST AUDIT; MUST FIX if reachable on a runtime-published pool.

Evidence:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2578` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2586` documents
  `replaceModule(...)` as destructive and only for a freshly created copy.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2586` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2594` rewrites
  identities in `f_listConst`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2685` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:2689` clears the list
  and lookup maps during disassembly.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3031` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3037` repopulates the
  list and resolves constants after disassembly.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3114` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3132` toggles
  recursive registration and runs `optimize()`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3834` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4034` clears caches,
  reorders positions, clears maps, and clears implicit caches.
- `javatools/src/main/java/org/xvm/asm/Constant.java:436` through
  `javatools/src/main/java/org/xvm/asm/Constant.java:437` increments reference
  counts with a plain field.

Why the old design is bad:

These methods mutate fundamental pool identity: indexes, list contents, lookup
maps, cached core constants, and reference counts. That is appropriate for
assembly/disassembly work, but it is catastrophic if any runtime reader holds
references into the same pool.

Practical same-JVM/parallel failure mode:

An executing frame or resolved op can retain a constant index or object while
another phase clears/reorders the pool. Readers can find a different constant
at the same index, miss lookup-map entries, or see core caches reset and
recomputed under a different phase.

Existing reproducer, test, or diagnostic:

No focused runtime-reachability guard exists for these destructive paths. The
late-registration diagnostic does not detect list clears, position rewrites, or
map resets.

Proper fix:

Encode the phase boundary. Destructive methods should assert or check that the
pool is not runtime-published and is owned by a compiler/serialization copy.
Longer term, separate mutable assembly pools from immutable runtime snapshots.
If map clearing remains, publish fresh `EnumMap` instances instead of clearing
the volatile map object in place.

Expected performance and semantic impact:

No runtime overhead is needed beyond a phase check. Compiler/serialization
behavior should remain the same. Runtime snapshots may consume memory but give
clear ownership and stable indexes.

Recommended PR slice:

Create a destructive-phase guard PR. Add tests that mark a pool runtime
published and verify `replaceModule(...)`, disassembly reload, and
optimization entry points cannot run on it. Then move any legitimate runtime
uses to private copies.

## 9. Remaining Ambient Current-Pool Bridge Effects Inside Constants

Classification: DONE IN THIS BRANCH for semantic `getCurrentPool()` removal;
MUST AUDIT for remaining scoped bridges.

Evidence:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3784` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3802` assert that a
  scoped pool matches an explicit owner.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3813` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3817` implements the
  remaining `withPool(...)` bridge over a thread-local holder.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4154` through
  `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4155` define
  `s_tloPool`.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1901`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1907`
  scopes the pool while bootstrapping root `Object` TypeInfo.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:2072`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:2077`
  scopes the owning pool around TypeInfo construction.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5835`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:5856`
  uses a dynamically scoped type relation context.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6310`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:6326`
  scopes that context for union covariance checks.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8312`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8316`
  declares the `ScopedValue<TypeConstant>`.

Why the old design is bad:

Hidden current-pool lookup makes owner selection invisible in method
signatures. `withPool(...)` is better than the removed open getter because it is
lexical, but it still depends on dynamic scope and assertion-only validation in
many runtime builds. The `TypeConstant` relation context has the same audit
shape: dynamic scope is safer than mutable static state, but still hidden from
APIs.

Practical same-JVM/parallel failure mode:

Nested compiler/runtime/JIT work can run inside another pool's scope or with no
scope at all. Work moved to another Java thread can lose the scope. A helper
that manufactures constants during TypeInfo construction can accidentally use
the wrong owner if the explicit owner is not threaded through every layer.

Existing reproducer, test, or diagnostic:

- `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java:37`
  through `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java:87`
  verifies current-pool assertions accept the matching scope and reject wrong
  scopes when assertions are enabled.
- `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java:164`
  through `javatools/src/test/java/org/xvm/asm/ConstantPoolDiagnosticsTest.java:195`
  verifies no semantic `getCurrentPool()` call sites outside `ConstantPool` and
  no callable getter.
- `javatools/src/test/java/org/xvm/asm/constants/TypeConstantOwnerApiTest.java:17`
  through `javatools/src/test/java/org/xvm/asm/constants/TypeConstantOwnerApiTest.java:51`
  covers explicit pool parameters for covariance helpers.
- `javatools/src/test/java/org/xvm/asm/constants/ConstantRangeOwnerTest.java:19`
  through `javatools/src/test/java/org/xvm/asm/constants/ConstantRangeOwnerTest.java:80`
  covers numeric range folding without ambient pool and with wrong ambient
  pool.
- `javatools/src/test/java/org/xvm/asm/constants/NestedIdentityOwnerTest.java:21`
  through `javatools/src/test/java/org/xvm/asm/constants/NestedIdentityOwnerTest.java:52`
  covers resolver-backed nested identity owner selection.

Proper fix:

Thread `ConstantPool`, `Frame`, `Container`, or an explicit TypeInfo build
context through constant and metadata APIs that can manufacture constants or
owner-derived helper state. Keep `withPool(...)` only at narrow legacy
boundaries until the constants/metadata helpers no longer need it. Replace
assertion-only runtime owner checks with diagnostics that also run without
`-ea` when the relevant validation property is enabled.

Expected performance and semantic impact:

Passing explicit owner parameters is allocation-free and usually faster than
thread-local access. Signature churn is the main cost. A TypeInfo build context
may add a small object per build, but it makes deferred worklists and owner
selection explicit.

Recommended PR slice:

Create a TypeInfo owner-context PR. Start with the two `TypeConstant.withPool`
sites, introduce an explicit build context/worklist, and leave tool/compiler
boundary `withPool(...)` sites for a later compatibility cleanup.

Done in this branch:

Semantic `ConstantPool.getCurrentPool()` is removed, and the old constant-level
semantic users for range folding, covariance/contravariance, nested identity,
method/property metadata, and function compatibility have focused tests.

## 10. Static Mutable Metadata Maps

Classification: DONE IN THIS BRANCH for the audited `ConstantPool` implicit maps
and unused union helper set.

Evidence:

- Former `javatools/src/main/java/org/xvm/asm/ConstantPool.java` static
  implicit metadata maps were mutable `HashMap` instances referenced by static
  final fields.
- Former `javatools/src/main/java/org/xvm/asm/constants/UnionTypeConstant.java`
  `SpecialFunkies` was a mutable static `HashSet` and had no current uses.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8301`
  through `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8310`
  is the fixed contrasting case: a process-wide recursion diagnostic set now
  uses `ConcurrentHashMap.newKeySet()`.

Why the old design is bad:

Class initialization safely publishes the map/set references, but the object
types remain mutable and process-wide. That makes accidental mutation possible
from any future code path and makes the intended immutability invisible.

Practical same-JVM/parallel failure mode:

If a future tool/runtime path mutates these maps, every container and compiler
request in the JVM sees the change. The current scan did not find such a path,
so this was cleanup rather than a current runtime blocker. The dead union set
had no runtime behavior and only preserved a mutable global object.

Existing reproducer, test, or diagnostic:

`ConstantPoolDiagnosticsTest.staticImplicitMetadataMapsAreImmutable()` reflects
the private static maps and verifies that mutating either map fails. `rg` shows
no remaining `SpecialFunkies` reference because the field was dead and removed.

Proper fix:

Use `Map.copyOf(...)` for implicit metadata after cloning parser-returned
arrays into the static map. Remove unused mutable static sets instead of
freezing dead state.

Expected performance and semantic impact:

No semantic change. Lookup performance is equivalent: the same private static
map lookup remains, with immutable map implementations after class
initialization. The per-pool `f_implicits` identity cache is unchanged. Removing
the unused union set removes one allocation and no behavior.

Recommended PR slice:

This branch folds the small cleanup into the constant-pool hardening set. If
split later, it can be a tiny immutable-static-metadata PR with the diagnostics
test above.

## Suggested PR Order

1. Runtime pool freeze/prewarm: make late registration a hard runtime boundary
   after warmup and expand the first-`ClassComposition` coverage beyond the
   already-known access-type subcase.
2. Registration publication ordering: stop exposing partially registered
   constants or formally confine registration to a single builder phase.
3. Constant adoption contract: remove base shallow clone as the default and
   require explicit subclass adoption behavior.
4. Live-handle constant policy: remove or strictly owner-localize live
   `ObjectHandle` storage in constants.
5. Pool storage freeze: immutable runtime list/map snapshots and no in-place
   map clears visible to runtime readers.
6. TypeInfo/ambient bridge cleanup: replace remaining constant-internal
   `withPool(...)` and dynamic relation context dependencies with explicit
   owner/build context APIs.
7. Cache cleanup: warm or harden per-pool core caches; the audited static
   metadata collections are already immutable or removed in this branch.
