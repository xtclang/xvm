# Reentrancy Documentation Split Guide

This guide explains how to turn the broad reentrancy documentation set into
reviewable issue and PR narratives. The large branch documents are intentionally
broad because the same root problem appears in many shapes: owner-bearing state
was stored in process globals, constructor-time receivers escaped before they
were complete, and hidden ambient context selected owners that should have been
explicit.

Do not dump the entire documentation set into one PR description. Use the broad
docs as evidence and extract one cause/effect/fix story per review slice.
Reviewers should be able to read a small problem statement, inspect the matching
diff, run the matching proof, and understand what is deliberately out of scope.

## What To Read First

Read these in order when preparing or reviewing a split PR:

1. [../must-audit-backlog.md](../must-audit-backlog.md) for the live
   must-fix, must-audit, and should-fix task list.
2. [../fixed-in-this-branch.md](../fixed-in-this-branch.md) for the branch
   delta and behavior-preservation notes.
3. [../test-failure-evidence.md](../test-failure-evidence.md) for tests that
   fail on `master`, branch-only shape guards, and stress evidence.
4. The topic-specific audit for the PR slice:
   [../native-template-startup-safety.md](../native-template-startup-safety.md),
   [../this-escape-removal-audit.md](../this-escape-removal-audit.md),
   [../ambient-context-audit.md](../ambient-context-audit.md),
   [../constant-pool-hostile-state-audit.md](../constant-pool-hostile-state-audit.md),
   [../constant-adoption-clone-audit.md](../constant-adoption-clone-audit.md),
   [../runtime-metadata-op-cache-classification.md](../runtime-metadata-op-cache-classification.md),
   [../jit-implications.md](../jit-implications.md), or
   [../compiler-lexer-parser-this-escape.md](../compiler-lexer-parser-this-escape.md).
5. [github-issue-breakdown.md](github-issue-breakdown.md) for the current
   branch-to-PR ordering and commit-folding guidance.

## Split Rules

Each review slice should answer five questions:

- What owner did the old code implicitly assume?
- Where could that assumption break in same-JVM, parallel, or reentrant use?
- What is the new owner and cache boundary?
- Which behavior and cache hits are intentionally preserved?
- Which test, diagnostic, lint gate, or source-shape scan proves the new
  invariant?

Keep these separate unless a dependency forces them together:

- native template ownership and value-handle factories;
- runtime decoded-op caches;
- constructor escape removal;
- ambient constant-pool lookup removal;
- constant adoption and clone hardening;
- diagnostics and stress harnesses;
- JIT follow-up work;
- compiler/parser follow-up work;
- long-term API/readability cleanup.

## Slice Matrix

| Slice | Classification | Primary docs | Review narrative | Required proof |
| --- | --- | --- | --- | --- |
| Native template `INSTANCE` globals | Implemented in this branch | `native-template-startup-safety.md`, `fixed-in-this-branch.md`, `state-inventory.md` | Mutable process-global template fields confused JVM class identity with container-owned runtime state. | Source scan for no `INSTANCE = this`; native-template tests; ownership diagnostics across two containers. |
| Owner-scoped core value handles | Implemented in this branch | `fixed-in-this-branch.md`, `ownership-diagnostics.md` | Static handles such as booleans, strings, bits, and enum values were owner-bearing values but looked like constants. | Typed owner factory tests and diagnostics proving returned handles belong to the active container/native parent. |
| Owner-local template metadata caches | Implemented in this branch | `manual-lazy-cache-audit.md`, `fixed-in-this-branch.md` | Static `TypeConstant`, `TypeComposition`, and method/template metadata caches pinned the first or last owner. | Lazy/owner table tests and source scans proving cache state moved under `Container`, `NativeTemplates`, or template-owner fields. |
| Decoded `Op` frame-derived caches | Partly implemented; remaining follow-up must-fix | `runtime-metadata-op-cache-classification.md`, `must-audit-backlog.md` | Shared decoded instructions must not store handles or type constants derived from the first executing frame. | `OpRuntimeCacheTest`, two-container switch tests, and ownership validation during parallel first execution. |
| `JumpVal` and `JumpVal_N` switch caches | Follow-up must-fix until committed | `runtime-metadata-op-cache-classification.md`, `test-failure-evidence.md` | `amapJumpSmall`, case handle arrays, range tables, algorithms, and column types are runtime switch tables, not bytecode metadata. | Container-keyed cache tests, source-shape tests showing no owner-bearing switch fields on the op, and stress with diagnostics. |
| `JumpNFirst.m_fVisited` | Implemented in this branch | `runtime-metadata-op-cache-classification.md`, `must-audit-backlog.md` | `assert:once` is deliberately decoded-op scoped, but the old plain boolean made concurrent first execution racy. | Keep the final `AtomicBoolean` and sequential/concurrent `OpRuntimeCacheTest` coverage. |
| Runtime and ASM `this-escape` removal | Implemented in this branch, except separate utils/JIT items | `this-escape-removal-audit.md`, `this-escape-tally.md`, `fixed-in-this-branch.md` | Constructors called overridable methods, assigned owner links, registered objects, or captured receivers before construction completed. | Forced `-Xlint:this-escape` runs plus focused construction hook tests. |
| Utility `this-escape` hazards | Fixed separately | `this-escape-removal-audit.md` | Utility constructors published `this` to a static set or called overridable view factories. | Separate branch tests that fail on the old constructor shape. |
| Compiler/parser `this-escape` hazards | Implemented in this branch, but should be submitted separately | `compiler-lexer-parser-this-escape.md`, `this-escape-removal-audit.md` | Incremental compilation cannot tolerate AST/lexer/parser constructors calling subclass hooks or publishing parent links early. | Compiler construction tests and targeted lint output showing no compiler/AST warnings. |
| Ambient `ConstantPool` lookup | Implemented in several branch waves; remaining bridge is must-audit | `ambient-context-audit.md`, `scoped-value.md`, `constant-pool-hostile-state-audit.md` | Hidden "current pool" state made methods depend on the Java thread instead of their real owner. | Tests that run with no ambient pool and with a wrong ambient pool; source-shape guard rejecting semantic `getCurrentPool()`. |
| Constant adoption and clone hazards | Implemented for known runtime cases; base clone remains must-audit | `constant-adoption-clone-audit.md`, `clone-usage-audit.md`, `stress-discovered-runtime-issues.md` | Shallow clone preserved runtime state, locks, atomics, thread-local cells, and handles across pool owners. | Adoption validator, focused adoption tests, and `TestProps` parallel stress with ownership validation. |
| Runtime late-registration diagnostics | Implemented for known class-composition access-type cases; broader pool freeze is must-audit | `constant-pool-hostile-state-audit.md`, `constant-pool-state-audit.md`, `stress-discovered-runtime-issues.md` | Runtime-visible pools should not grow because a handle asks for an access view. | Late-registration tests and diagnostics around runtime publication. |
| Runtime ownership diagnostics and stress | Implemented in this branch; keep expanding | `ownership-diagnostics.md`, `plans/same-jvm-launcher-stress.md`, `test-failure-evidence.md` | Wrong-owner failures need to fail at the boundary that created them, not later as misleading XTC-level assertions. | Same-JVM sequence stress, parallel stress, validator assertions, and dump evidence. |
| JIT ownership and generated statics | Follow-up must-fix/must-audit, separate from interpreter PR | `jit-implications.md`, `jit-global-owner-classification.md`, `plans/jit-xvm-owner-refactor.md`, `scoped-value.md` | JIT uses `Xvm`, `Ctx`, classloaders, generated statics, and bridge classes rather than interpreter `Container`/`ObjectHandle` state. | Separate JIT startup/classloader/`Ctx` tests and generated-static source-shape guards. |
| Typed APIs and generics | Should-fix, must-audit when casts hide owners | `generics-api-audit.md`, `bad-design-decisions-reference.md` | Raw APIs force casts at owner boundaries, hiding whether a value belongs to the expected owner/type. | Gradual typed helper APIs, cast-isolation tests, and source scans for unchecked owner paths. |
| Immutable metadata and arrays vs lists | Should-fix, must-audit when arrays are shared owner state | `array-list-immutability-study.md`, `state-inventory.md`, `runtime-metadata-op-cache-classification.md` | Arrays expose mutable element slots even when the field is final. Metadata intended to be immutable should not leak writable arrays. | Immutable holders, `List.copyOf`, defensive copies, and tests that mutation through returned views cannot corrupt owners. |
| Modern Java syntax and lint gates | Should-fix; lint gate becomes must-have after cleanup | `modern-java-syntax-audit.md`, `lint-parallelism-risk-audit.md`, `this-escape-tally.md` | Boilerplate loops and weak lint settings bury ownership logic and let known-bad constructor escapes return. | Enable full-repo `this-escape` lint as an error after inventory is clean; keep modern syntax cleanup separate from correctness PRs. |

## Examples To Cite

Use short examples in issue/PR descriptions. The goal is to show the shape of
the bug, not to paste whole methods.

### Process-Global Native Template

Old shape:

```java
public static xArray INSTANCE;

public xArray(Container container, ClassStructure structure, boolean instance) {
    super(container, structure, false);
    if (instance) {
        INSTANCE = this;
    }
}
```

Problem: the field is one JVM slot, while the template object contains one
container, one pool, and owner-local caches. It also publishes `this` before
construction is complete.

Target shape:

```java
var template = frame.container().nativeTemplates().array();
```

or:

```java
var template = NativeTemplates.get(container).array();
```

The cache still exists. It is just owned by the `Container` that will use the
template.

### Owner-Bearing Runtime Handle Constants

Old shape:

```java
return value ? xBoolean.TRUE : xBoolean.FALSE;
```

Problem: these look like constants, but the values are runtime handles whose
composition and native template belong to a container/native parent.

Target shape:

```java
return xBoolean.makeHandle(container, value);
```

The handle can still come from a small cached table, but the table is selected
through the owner.

### Decoded Op Switch Cache

Old shape:

```java
private ObjectHandle[][] cases;
private Map<ObjectHandle, Long>[] jumpMaps;
private TypeConstant[] columnTypes;

cases = frame.getConstHandle(...);
jumpMaps = buildJumpMaps(cases);
```

Problem: `ObjectHandle` and `TypeConstant` values are derived from a particular
executing `Frame`/`Container`. A decoded `Op` graph can be shared by more than
one execution owner. Synchronizing the builder only means the first owner wins
atomically; it does not make the cache correct for later owners.

Target shape:

```java
var cache = frame.container().getRuntimeOpCache(this, SWITCH, SwitchCache.class);
if (cache == null) {
    cache = frame.container().putRuntimeOpCacheIfAbsent(
            this, SWITCH, buildSwitchCache(frame), SwitchCache.class);
}
```

The switch table shape is preserved. The difference is the owner: each
container builds and reuses its own switch table instead of writing first-frame
runtime values onto the shared instruction object.

### Constructor Escape By Registration

Old shape:

```java
public NativeContainer(Runtime runtime, ModuleRepository repository) {
    super(runtime, repository);
    initializeNativeTemplates(); // can publish/use this before constructor exit
}
```

Target shape:

```java
public static NativeContainer create(Runtime runtime, ModuleRepository repository) {
    return new NativeContainer(runtime, repository).initializeNativeTemplates();
}
```

or any equivalent factory where the visible work runs after construction
returns. The exact code may use a local variable if the initializer returns
`void` or if the style is clearer.

### Constructor Escape By Virtual Dispatch

Old shape:

```java
protected Base() {
    clear();           // overridable
    checkParent(this); // can inspect incomplete subclass state
}
```

Target shape:

```java
private static State initialState(Input input) {
    return validate(input);
}

protected Base(Input input) {
    this.state = initialState(input);
}
```

The constructor should use private/static/final helpers or explicit constructor
data, not subclass-overridable behavior.

### Ambient Current Pool

Old shape:

```java
return ConstantPool.getCurrentPool().typeTuple0();
```

Problem: the Java thread is not the owner. A reused worker, nested helper, or
callback can have no pool or the wrong pool.

Target shape:

```java
return typeTuple0(); // receiver pool
```

or:

```java
return type.isCovariantReturn(pool, required, actual);
```

The owner is visible in the call or receiver.

### Constant Adoption Through Shallow Clone

Old shape:

```java
Constant that = (Constant) super.clone();
that.setContaining(pool);
```

Problem: `Object.clone()` copies final references too. A final
`AtomicReference`, lock, `ThreadLocal`, runtime handle, or cached type handle
remains the same mutable object after adoption unless the subclass explicitly
replaces or clears it.

Target shape:

```java
protected SingletonConstant adoptedBy(ConstantPool pool) {
    return new SingletonConstant(pool, format, classConstant);
}
```

or, for existing clone-based legacy classes:

```java
var that = (FSNodeConstant) super.adoptedBy(pool);
that.runtimeHandle = null;
that.pathConstant  = null;
return that;
```

Fresh-constructor adoption is preferred. Clearing cloned runtime/helper fields
is an acceptable small containment fix when replacing the whole adoption model
would be too broad for the PR.

## PR Slice Details

### 1. Native Template Owner Table

Classification: implemented in this branch.

Reviewer story: mutable `INSTANCE` fields were wrong-owner globals and
constructor-publication hazards. The replacement is a `Container`-owned
`NativeTemplates` table, with typed accessors so callers do not cast or guess
which owner owns the returned template.

Examples to cite:

- `INSTANCE = this` assigned from native-template constructors.
- static helpers that read `xString.INSTANCE` or `xBoolean.TRUE`.
- replaced access through `frame.container()` or an explicit `Container`.

Proof:

- source scans for no mutable template `INSTANCE` declarations and no
  `INSTANCE = this` assignments in runtime templates;
- native-template and ownership diagnostic tests;
- same-JVM sequence stress to prove stale templates from earlier runs are not
  reused.

Cache/performance equivalence: the old cache was one JVM pointer. The new cache
is one pointer per owner. That is the intended behavior because templates carry
container-owned state. Hot access still becomes a map/lazy hit through the
container; it does not rebuild a template per call.

### 2. Owner-Scoped Runtime Values

Classification: implemented in this branch.

Reviewer story: values such as booleans, bits, strings, nullability values, and
native enum handles were treated like Java constants, but they are runtime
handles. Their class composition and heap/template owner matter.

Examples to cite:

- `xBoolean.TRUE` / `FALSE`;
- `xNullable.NULL`;
- `xOrdered.EQUAL`;
- string empty/zero/one handles;
- natural enum values returned through public/native reflection paths.

Proof:

- owner-required factory tests;
- source-shape tests rejecting ownerless factories where they were removed;
- ownership diagnostics over two warmed containers.

Cache/performance equivalence: small-value caches remain small-value caches.
They are located under the native template or container owner instead of under a
process-global static field.

### 3. Owner-Local Metadata Caches

Classification: implemented in this branch for the current PR scope; broader
metadata caches remain must-audit.

Reviewer story: old static metadata fields cached `TypeConstant`,
`TypeComposition`, `MethodStructure`, enum templates, and helper handles in a
single JVM slot. Those values are not process constants. They belong to a pool,
container, template, or method metadata owner.

Examples to cite:

```java
private static TypeConstant LISTMAP_TYPE;
```

Target:

```java
private final Lazy.Owner<MyTemplate, TypeConstant> listMapType =
        Lazy.ofOwner(owner -> owner.pool().ensure...);
```

or one grouped metadata record:

```java
private final Lazy.Owner<MyTemplate, Info> info =
        Lazy.ofOwner(Info::from);
```

Proof:

- tests showing the value is cached after first access for the same owner;
- ownership diagnostics proving the cached value belongs to the owner being
  inspected;
- source scans for static mutable owner metadata removed from the slice.

Review caution: do not replace every cold interned value with a `Lazy` by
default. If the `ConstantPool` already interns the value and the old explicit
cache is not semantically required, the simpler target may be an explicit owner
parameter plus no extra cache. Keep that decision documented per site.

### 4. Decoded Op Runtime Caches

Classification: implemented for some owner-bearing conditional/common-type
caches; `JumpVal`, `JumpVal_N`, and `JumpNFirst` are follow-up must-fix until
the corresponding code is committed.

Reviewer story: decoded op objects are bytecode metadata. They must not store
runtime values derived from the first `Frame` that executes them. If they need
runtime acceleration, the cache owner must be the executing container, method
runtime state, or another owner-local table.

Examples to cite:

- `JumpCond` / `JumpNCond` condition constants;
- runtime writes to common type fields on `OpTest` / `OpCondJump`;
- `JumpVal` case-handle maps;
- `JumpVal_N` `amapJumpSmall`, wildcard arrays, column type arrays, range
  tables, and algorithm arrays;
- `JumpNFirst.m_fVisited`.

Why removing `amapJumpSmall` and friends is sufficient only with a replacement:
those arrays are not just decoded opcode shape. They contain `ObjectHandle`
keys and `TypeConstant` values produced through a specific frame/container.
Moving them from instance fields on the op into a container-keyed immutable
switch cache preserves the lookup table and hot-path behavior while preventing
one container's handles from becoming another container's switch table.

Proof:

- source-shape test rejecting owner-bearing runtime cache fields on decoded op
  classes;
- two-container switch execution where each container builds or reuses only
  its own cache;
- parallel first-execution test for the same op;
- ownership diagnostics after switch-heavy stress.

Cache/performance equivalence: the new design should still build the same maps,
range lists, wildcard masks, and algorithm decisions once per owner. A race may
compute the same immutable owner-local table twice and publish one winner with
`putIfAbsent`; after warmup, the hit path is still a cache lookup. The old
single cache was faster only by being wrong when more than one owner existed.

### 5. Constructor This-Escape Removal

Classification: implemented in this branch for runtime/ASM/compiler/JIT-local
sites, with two utility hazards fixed on a separate branch and `Xvm` kept as a
JIT follow-up.

Reviewer story: `this` escapes are not warnings to suppress. They are
construction hazards. In this codebase they appear as owner registration,
constructor-time lazy captures, calls to public/protected methods, virtual
deserialization hooks, AST parent linkage, and child metadata owner assignment.

Examples to cite:

- constructor assigns `this` to a static field;
- constructor registers `this` in a runtime registry;
- constructor calls an overridable method such as `clear()`,
  `setConditionalReturn(...)`, `newKeySet()`, or parser/lexer hooks;
- constructor gives child metadata a parent before subclass fields are ready.

Target patterns:

- private constructors plus static factories;
- private/static/final helpers;
- constructor arguments that carry shape instead of virtual predicate calls;
- owner links installed after construction returns;
- `Lazy.Owner` instead of suppliers that capture an incomplete owner.

Proof:

- full forced `-Xlint:this-escape` capture for the source set in the PR;
- hook tests where a subclass would fail if a superclass constructor calls an
  override;
- source-shape tests where behavior is otherwise hard to trigger.

### 6. Ambient ConstantPool Removal

Classification: implemented for semantic `getCurrentPool()` call sites in this
branch; the remaining scoped bridge is must-audit.

Reviewer story: "current pool" is a hidden global precondition. It is wrong
even for single-threaded code because a helper can run with no ambient pool or
inside a nested scope for another pool. In parallel runtimes and reused worker
threads, it becomes arbitrary.

Examples to cite:

- an instance method on `ConstantPool` asking the thread-local pool for
  `Tuple<>`;
- numeric range folding registering range constants in the ambient pool;
- covariance/contravariance helpers manufacturing helper constants through a
  hidden current pool;
- resolver-backed nested identities ignoring the explicit output pool;
- file diagnostics redirected through an unrelated current pool.

Target patterns:

- use the receiver pool when the receiver is already owned;
- add an explicit `ConstantPool`, `Container`, or `Frame` parameter when the
  caller owns the operation;
- keep `withPool(...)` only as transitional boundary glue with assertions and
  stress-mode validation.

Proof:

- tests that pass with no ambient pool;
- tests that run under a wrong ambient pool and still create results in the
  receiver/requested pool;
- source-shape guard preventing semantic `getCurrentPool()` calls from coming
  back.

### 7. Constant Adoption, Clone, And Late Registration

Classification: implemented for known runtime failures; base
`Constant.adoptedBy(...)`, clone-heavy structure copying, and runtime-published
pool mutation remain must-audit.

Reviewer story: adoption exists for a valid reason: the same logical constant
value often must be registered in a destination pool. The bug is the
implementation style. Shallow clone preserves runtime state that should not
cross the pool boundary.

Examples to cite:

- `SingletonConstant` final `AtomicReference<InitState>` copied into another
  pool;
- file-system constants copying runtime handles;
- parameter clone clearing the source object instead of the copy;
- method clone assigning copied parameters back to the source owner;
- access-view `cloneAs(...)` sharing field arrays and rewriting inflated refs;
- access-type constants registered after runtime publication.

Target patterns:

- fresh owner-explicit adoption constructors;
- clearing transient runtime/helper state at adoption boundaries where a small
  containment fix is required;
- owner-copy factories such as `copyFor(owner)`;
- diagnostics that reject live owner-bearing state copied into a new pool;
- prewarming constants before runtime publication while keeping runtime helper
  objects lazy and owner-local.

Proof:

- adoption tests that fail on the old shallow-copy behavior;
- validator runs with `-Dxvm.asm.validateConstantAdoption=true`;
- late-registration tests for runtime-published pools;
- stress evidence from `TestProps` showing the old owner leak and the new
  boundary failure/fix.

### 8. Diagnostics And Stress Harnesses

Classification: implemented in this branch; expansion remains a verification
backlog.

Reviewer story: these tools are how the branch proves owner correctness beyond
unit tests. They make hidden wrong-owner values visible as structural failures
instead of waiting for later language-level assertions.

Examples to cite:

- `OwnershipDiagnostics.dump(containerA, containerB)`;
- `OwnershipDiagnostics.assertValid(...)`;
- `assertHandleValidIfEnabled(...)` at hot native boundaries;
- `manualTests:runDirectSequenceStress`;
- `manualTests:runParallelStress`.

Proof expectations:

- every runtime-owner PR should include at least one focused unit test;
- every cross-container/cache PR should run with ownership validation;
- stress tasks should record the modules, iteration count, and JVM ownership
  validation flags;
- diagnostic output should identify expected owner, actual owner, object kind,
  and path where possible.

### 9. JIT Follow-Up Separation

Classification: follow-up must-fix and must-audit, not part of the
interpreter-runtime PR unless the touched shared ASM API requires it.

Reviewer story: the JIT is not merely another interpreter execution mode. It
has its own `Xvm`, `Ctx`, classloaders, generated static fields, native bridge
classes, and type-system caches. Interpreter `Container` fixes are necessary
for shared ASM state, but they do not prove JIT generated statics safe.

Examples to cite:

- `Xvm` startup passes `this` into native type-system construction;
- generated `<clinit>` loads `$scN` constants through the active `Ctx`;
- generated `$INSTANCE` singleton fields are classloader-wide;
- bridge helpers such as `$ctx()`, `$xvm()`, and `$owner()` hide context.

Target patterns:

- an explicit JIT owner shell/factory for `Xvm` startup;
- generated statics only for classloader/type-system-owned immutable state;
- container-owned state reached through `Ctx.container` or owner tables;
- source-shape guards for static `Ctx.get()` use in generated/bridge classes.

Proof:

- parallel JIT startup tests;
- two-container generated-class initialization tests;
- bridge tests for no `Ctx` and wrong `Ctx`;
- JIT ownership dump rooted at `Xvm`.

### 10. Compiler And Parser Follow-Up Separation

Classification: implemented in this branch, but should usually be submitted as
a separate compiler/incremental-compilation cleanup PR.

Reviewer story: the compiler was historically request-local, but language
support and incremental compilation require reentrant compile operations in one
process. Constructor escapes in lexer/parser/AST code make partially built
nodes visible through parent pointers, component links, validation state, and
synthetic wrappers.

Examples to cite:

- `Lexer` constructor calling overridable whitespace parsing;
- `Parser` constructor priming by calling `next()`;
- AST constructors attaching parent/component links before construction
  completes;
- conversion wrappers doing constructor-time validation through overridable
  helpers.

Target patterns:

- lazy token priming;
- factories that finish parent/component ownership after construction;
- static conversion helpers that preserve runtime-conversion fallback behavior;
- structured diagnostics/logging instead of `System.err` soft asserts.

Proof:

- `CompilerThisEscapeConstructionTest`;
- lint capture showing no compiler/AST constructor warnings;
- behavior tests for compile-time folding fallbacks where practical.

## Longer-Term Code-Quality Goals

These are not all first-PR correctness work, but they are part of the intended
end state.

### Typed APIs And Generics

Classification: should-fix; must-audit where raw casts cross owner boundaries.

The codebase often uses raw accessors followed by casts:

```java
var template = (xEnum) container.getTemplate(name);
```

Target:

```java
var template = container.getTemplate(name, xEnum.class);
```

or a named typed accessor where the template is common. This is not only style:
typed boundaries let the runtime assert owner and payload type together.

### Immutable Metadata

Classification: should-fix; must-audit when mutable metadata is runtime-shared.

Metadata fields that are populated by `if (field == null) field = ...` need an
owner/lifetime decision. Some can become eager final fields. Some should become
final `Lazy` cells. Some should move into owner-local tables. Some should
disappear because the `ConstantPool` already interns the value.

The proper fix is site-specific. Do not blindly convert every nullable field to
`Lazy`.

### Arrays Vs Lists

Classification: should-fix; must-audit when arrays expose owner-bearing
elements.

Final arrays are still mutable:

```java
private final MethodBody[] bodies;
```

If callers can mutate elements, the owner graph can be corrupted. Prefer
immutable holders, `List.copyOf`, private arrays plus defensive copies, or
explicit copy-on-write where mutation is intentional and hot.

Keep performance honest. Arrays may still be correct for tight interpreter
paths, but then the ownership and mutation contract must be explicit.

### Modern Java Syntax

Classification: should-fix.

Modern syntax is useful when it exposes the business logic more clearly:

```java
Arrays.setAll(info.bodies(), i -> info.bodies()[i].forMethod(info));
```

Use it for touched code where it improves readability. Keep mechanical style
cleanup out of must-fix correctness PRs unless it is necessary to make the
owner change readable.

### Lint Gates

Classification: follow-up must-have after cleanup.

Once the inventory is clean, `this-escape` should be enabled for the full Java
build and treated as an error. Any deliberate exception should require a local
suppression plus a comment that states:

- what object escapes;
- who can observe it;
- why construction is complete enough;
- why the owner/lifetime cannot leak across containers or compiler requests.

Do not turn unrelated lint categories into fatal errors in the same PR. That
would obscure the ownership regression gate.

## Intended End State

The target architecture is straightforward:

- owner-bearing values are reached through explicit owners;
- caches live on the object that owns the value or in an owner-local table;
- decoded bytecode metadata does not hold frame/container runtime values;
- constructors do not publish incomplete objects or call overridable behavior;
- constant-pool adoption preserves logical values but never runtime/helper
  state;
- runtime-published pools do not grow from normal execution paths;
- diagnostics can dump and validate owner graphs after same-JVM or parallel
  execution;
- JIT-generated statics are proven classloader/type-system-owned or moved to
  explicit owner tables;
- compiler/parser state is request-owned and can support incremental/reentrant
  compilation;
- lint gates prevent the known bad shapes from returning.

This is not extra ceremony. Most fixes replace an implicit global dependency
with the owner that the code already had nearby: `Frame`, `Container`,
`ConstantPool`, `ClassTemplate`, `TypeInfo`, `MethodInfo`, `PropertyInfo`, or
JIT `Ctx`. The branch should be reviewed as a set of small owner-boundary
repairs, each with a focused proof and an explicit statement of what cache
behavior remains the same.
