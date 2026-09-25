# Continue the constant-pool state-separation work

Snapshot: 2026-09-25. This file is a continuation prompt for a fresh session on another machine.
All paths below are relative to the repository root. No files from the previous machine's temporary
folders are needed.

## Task to continue

Continue work on `xtclang/xvm`, branch **`lagergren/constant-pool-state-separation`**. Read
`AGENTS.md`, this file, and the current completion records in the architecture plan before editing.
The user wants a complete, enforced separation of definition images, runtime descriptors, semantic
metadata, and execution state, implemented in reviewable stages and separate commits.

**Scopes 1–5 are implemented and verified within their documented boundaries.**
Captured annotation values now live in their container's constant heap, behind
non-transferable descriptor tokens. Reflection values have requesting owners, and foreign Type
handles retain their exact source execution. File-system definitions no longer retain handles;
their native adapters select constructors from each application's prepared declarations. All six
focused frozen reflection/file-system cases pass without skips. The architecture record audits
remaining classloader-wide native values and does not claim independent native-root isolation.
Scope 6 is the next activation gate; it has not been enabled. Preserve the service-owned method
execution boundary and same-image regressions established in scopes 4 and 5.
Continue the existing architecture; do not broaden this into the embedding, Gradle, error-listener
or JIT projects.

The ultimate goal is genuinely frozen runtime definition graphs. It has **not** been achieved yet.
Do not describe the branch as universally safe for frozen execution, concurrent executable
synthesis, arbitrary metadata sharing, or removal of application copies.

## Get the branch on the new machine

For a fresh checkout:

```sh
git clone --branch lagergren/constant-pool-state-separation \
    git@github.com:xtclang/xvm.git xvm-state-separation
cd xvm-state-separation
git status --short --branch
git log -5 --oneline
```

For an existing checkout, inspect its working tree and branches before fetching or switching;
preserve local work. Use the checked-in Gradle wrapper and Java 25. The wrapper currently uses
Gradle 9.7.1; let the repository's toolchain configuration select/provision its dependencies.
Do not copy build outputs from the previous machine as a prerequisite.

The original handoff commit was **`f63473dfd`**. Scope 3 is **`b7c6f5378`** (stable native
preparation) and **`2087e376a`** (delegation/accessor ownership). Scope 4 starts with
**`703ed5f40`** (service-owned initialization); the following decoded-code/layout commit updates
this prompt. Check the actual local and remote tips when resuming; this file cannot contain its
own commit hash. Scopes 3 and 4 were published through `8d857af9e`. The user also requested
publication of scope 5 on 2026-09-25, including the captured-value and reflection commits below
and the following file-system/native-audit commit. Check the actual remote tip when resuming.
Do not push subsequent work or open a PR without a new request.

## Essential references and branch boundaries

Read these in order:

1. [AGENTS.md](AGENTS.md): local operating rules, composite build semantics, style, and test caveats.
2. [Architecture plan](doc/constant-pool-architecture-plan.md): the active staged plan. Focus on
   "Generated methods and other freeze blockers", "Remaining commits before frozen activation",
   "Scope-1 completion: ordinary runtime destinations", "Scope 2: owner-specific semantic metadata",
   "Scope 3: stable preparation and generated executables", and
   "Scope 4: compiled method execution ownership", and "Scope 5: reflection and native-value
   ownership".
   Earlier sections are historical snapshots; use the later completion records for current status.
3. [Ownership audit](doc/constant-pool-ownership.md): original same-pool, cross-pool and ambient-pool
   fixes, singleton semantics, diagnostic handling, evidence, and explicit limits.

Keep these branches distinct:

| Branch | Purpose |
|---|---|
| `lagergren/constant-pool-state-separation` | Current experimental architecture work; continue here |
| `lagergren/constant-pool-ownership-only` | Narrow ownership correctness baseline, excluding the general error-listener redesign |
| `lagergren/constant-pool-ownership` | Original larger embedding/Gradle/reuse/shutdown work and its PR extraction plan |
| `JIT` | Destination for JIT development; do not add JIT execution work to this branch |

The baseline was extracted from master `601a68e8b`, with prerequisite `d8c6c3176` and initial
extraction `65e5ce149`, then narrowed. Do not rebase, merge the larger branch, or reintroduce its
listener/runtime lifecycle changes as incidental cleanup. The original submission plan is on the
larger branch at `plugin/doc/plans/embedded-runtime-pr-plan.md`; it is not this branch's active task.

## User preferences and operating constraints

- **Gradle and `jcmd` are already authorized. Do not ask for permission for each invocation or flag
  variation, including `RUN_INTEGRATION_TESTS=true`.** Local repository edits are also authorized.
  Use `env RUN_INTEGRATION_TESTS=true ./gradlew ...` consistently for integration-enabled runs.
  Honor any enforced tool sandbox requirements without inventing another approval flow.
- Do not use SICS AI / ai-dev skills or plugins for this repository. The user restricted those to
  their `zombiesnack` repository.
- Keep distinct architectural changes in separate commits. Update the existing architecture and
  ownership documents as stages complete, explaining ownership, why each change is needed, tests,
  and remaining limits. This root handoff file was explicitly requested.
- Follow `AGENTS.md` for remote operations. The user requested publication through scope 5.
  Do not push subsequent work or open a PR without a new user request.
- Use modern Java 25 where it improves touched code: records, generics, immutable collections,
  obvious `var` assignments, and suitable `Lazy` / `Lazy.Bound` holders. No new Hungarian field
  names, unnecessary fully qualified names, or arrays where a collection is the better API.
  Existing array-based APIs can still require arrays. Keep imports sorted and formatting consistent.
- Add useful Javadocs explaining ownership and API call order, using `<p>` for paragraphs. Prefer
  existing ownership abstractions to repeated field traversal. Avoid unrelated modernization.
- Tests must not depend on timing, GC luck, or sleeps. Use explicit synchronization for concurrency
  tests; timeouts are hang guards, not pass criteria. Ecstasy tests should assert, without success logs.
- Java unit tests must build their own fixtures. Do not add tests that quietly skip unless an XDK
  binary or installed library happens to exist. Put real compiler/interpreter integration tests
  under the distribution-provisioned XDK test task.
- Do not add automatic JIT runs or expensive new CI/Gradle dependencies. Configuration cache must
  keep working. Do not change execution modes or heap/metaspace flags as part of this work.
- Avoid multiple simultaneous builds in the same checkout. Settle focused failures before starting
  a long full verification run. The previous session restarted full runs too early and cancelled
  its own superseded builds; the user questioned this. Do not kill unrelated JVMs, other checkouts'
  builds, or shared daemons. Establish process ownership before stopping a task you started.
- No AI attribution in commits, documentation, issues, or PR text.

## Architecture and invariants already established

There are different ownership domains; structural equality alone never grants interchangeability:

1. **Definition image:** prepared `FileStructure` / declaration graph and indexed constants.
   Serialized positions belong to this image. Linking, stable synthesis and native preparation
   must finish before publication. Compiler workspaces remain mutable.
2. **Runtime descriptors:** `RuntimeTypeContext`, retained by its container, captures the exact
   linked definition graph. Its `DescriptorPool` is a transitional `ConstantPool` factory adapter,
   with no serialized indices. Runtime combinations may grow here without extending image tables.
3. **Semantic results:** owner-specific `TypeRelations` and `TypeMetadata`. These can be cleared
   without changing canonical descriptor identity. They are not the interner or execution state.
4. **Execution state:** singleton initialization, handles, generated bodies, frame layouts, decoded
   Ops, instrumentation and native bindings have their own lifetimes. Several of these migrations
   are still open; do not treat them as disposable semantic memoization.

Important APIs and rules:

- `RuntimeTypeContext.intern` / `getDescriptorPool()` accept operands from the exact captured image
  graph or the same descriptor context. A same-name replacement image or sibling context is rejected.
- `Container.importSharedType` / `importSharedConstant` translate references using the known source
  container and actual module-sharing ancestry. Core/simple values can be owned by a sharing ancestor.
  Same definitions or equal module names do not authorize singleton or handle sharing.
- `Frame.runtimeConstant` imports compiled operands through the executing body/receiver boundary.
  `Frame.runtimeTypeOf` describes a shared runtime value in the frame's descriptor context using its
  composition owner. Preserve inception versus exposed/masked type semantics at each call site.
- `Frame.poolContext()` is a runtime descriptor destination. Decode compiled operand indices from
  the method's local constant table. Never use descriptor positions as compiled/image indices.
- `RuntimeTypeContext.freezeDefinitions()` freezes the exact linked definition graph. It is an
  explicit enforcement/test boundary and is **not enabled by default for general activation**.
- `RuntimeTypeContext.clearMetadata()` clears semantic answers and relations, retaining descriptors,
  images and execution state. Already acquired completed metadata can finish serving its callers.
- Ambient pool bindings are scoped compatibility plumbing, not a cache-owner selection policy.
  Preserve the existing restoration/worker-isolation tests. Do not introduce another ambient owner.
- Compiler normalization/substitution can temporarily return an upstream operand before adoption is
  possible. It is returned uncached. Runtime results must belong to the selected descriptor owner.
- Canonical operand keys use identity equality where needed. `ConcurrentHasherMap<>(Hasher.identity())`
  wraps JDK concurrency with that equality policy; replacing it with structural-key `ConcurrentHashMap`
  changes semantics. Existing comments explain this choice.

## Most recent commits and what changed

| Commit | Scope |
|---|---|
| `8333d0560` | Completed scope 1 ordinary runtime descriptor destinations and recorded remaining freeze failures |
| `a02d9124b` | Fixed shared-value destinations exposed by the new interpreter regression |
| `e59d5b597` | Separated semantic metadata from canonical descriptors, with deterministic unit regressions |
| `37bbeab30` | Completed member lookup keys, integration tests and scope 2 documentation |
| `b7c6f5378` | Prepared template-defined native rebases before frozen application execution |
| `2087e376a` | Owned generated delegation/accessor bodies in runtime descriptor contexts |
| `703ed5f40` | Moved method singleton-initialization completion into each executing service |
| `8d857af9e` | Owned decoded method bodies and frame layouts in each service; pushed |
| `40743698e` | Owned captured annotation values in container heaps |
| `1078cf248` | Owned reflective values and foreign dispatch in execution contexts |

`2087e376a` gives late delegation/accessor bodies a `RuntimeMethods` owner in
the runtime descriptor context. It extends `RuntimeMethodStructure` with independent parameters
and unattached lexical parents, publishes assembled candidates only, and keeps generated bodies
across semantic clears. `RuntimeDelegation.x` covers generic receivers/methods, getter/setter,
inherited and atomic delegation, bound methods and stable const/Stringable helpers. Cold helper
checks also corrected signature truncation before descriptor adoption and replaced the foreign
static hash signature with the executing pool's signature. See the scope-3 architecture record
for the ownership and publication limits.

`a02d9124b` changes five runtime boundaries: `OpCallable.constructChild`, `MoveRef`,
`xRTDelegate.GenericArrayDelegate.checkAssign`, `xRef.ensureClassHandle`, and `UnionTypeConstant`
equality/ordering/hash dispatch. They import shared operands through existing ownership APIs.
The assertion-only `xdk/src/test/resources/ownership/MetadataQueries.x` exercises covariance,
contravariance, generic array insertion and exception rendering in two independent applications.
These were missed destinations in this branch's runtime migration; no master reproduction is claimed.

`e59d5b597` introduces `javatools/src/main/java/org/xvm/asm/TypeMetadata.java`, obtained through a
final lazy holder on `ConstantPool`, separately for each compiler pool/runtime descriptor owner.
It moves TypeInfo, validation, normalization, bounded generic-substitution results, formal variance,
NakedRef specialization and declaration-derived types off constants. Relevant adapters are in
`TypeConstant`, `ParameterizedTypeConstant`, `PropertyConstant`, `PropertyClassTypeConstant`,
`FormalTypeChildConstant`, `TypeParameterConstant`, `MethodConstant`, and `AnnotatedTypeConstant`.

TypeInfo placeholders, partial results, deferred work and retry depths are calculation-local.
Only successful outer calculations publish complete, error-free results. Nested failure prevents
publication even if caught. Clears detach publication maps, and compiler invalidation keeps its
existing class-dependency/watermark protocol. Variance includes access, name, direction and receiver;
recursive assumptions remain provisional, with calculation-local reuse for unresolved compiler
queries. TypeInfo ignores a caller's temporary relation-probe scope and restores that scope on exit.
Diagnostic values still replay to the current request; completed metadata never retains its listener.
The general error-listener redesign was not imported.

`37bbeab30` separates inferred matches from declaration indices in `TypeInfoReal`. Lookup keys
include receiver and compiler/runtime mode where applicable, and structural name/operator/arity/kind/
parent inputs. Foreign, unresolved or contextual queries are not retained as reusable matches.
Property queries consult current owner metadata after a clear. Integration regressions cover actual
public/private variance, runtime matches not contaminating compiler lookup, cold independent contexts,
property refresh and warning replay.

Scope 2 is not a claim that all `TypeInfo`-owned member graphs are immutable. Scope 3 separates
generated delegation bodies; the scope-4 record below covers shared compiled execution state.

## Verification at the original handoff

Final combined implementation passed on 2026-09-25:

- Java suite: **519 cases; 483 passed, 36 existing skips; no failures or errors**.
- All **18 `TypeMetadataTest` cases** executed without skips. The focused run together with
  `ConstantOwnershipTest` had **24 passing cases**, no skips.
- XDK suite: **45 passed, no skips**, including **20 ownership cases**. The new `.x` program ran
  through the interpreter in two independent applications; no JIT was used.
- The full XDK distribution rebuilt successfully. `spotlessCheck` and `git diff --check` passed.
- These are correctness results, not a performance benchmark or a universal frozen-runtime proof.

Useful commands, from the repository root:

```sh
# Focused unit tests, without an installed-XDK assumption:
./gradlew :javatools:test --tests org.xvm.asm.TypeMetadataTest \
    --tests org.xvm.asm.ConstantOwnershipTest --rerun --console=plain

# Build the distribution needed for interpreter integration/manual freeze audits:
./gradlew :xdk:installDist --console=plain

# Focused integration tests:
RUN_INTEGRATION_TESTS=true ./gradlew :xdk:test \
    --tests org.xvm.xdk.ConstantPoolOwnershipTest --rerun --console=plain

# Full verification used for scope 2:
RUN_INTEGRATION_TESTS=true ./gradlew :xdk:test --rerun \
    :javatools:test --rerun --console=plain

./gradlew spotlessCheck --console=plain
git diff --check
git status --short --branch
```

Check that the requested tests actually execute instead of being `UP-TO-DATE` or restored/skipped.
Read counts and failure stacks from `javatools/build/test-results/test/TEST-*.xml` and
`xdk/build/test-results/test/TEST-*.xml`. The `--rerun` task option forced the named test tasks in the
recorded runs. `AGENTS.md` also documents `--rerun-tasks --no-build-cache` for forcing the whole graph;
do not routinely disable caches or rebuild everything for each small edit. Run `clean` alone if it
is ever necessary; never combine it with other tasks.

The final test failures during development were fixed. Important migration lessons were preserving
all access-qualified Object bootstrap views, keeping unresolved variance reuse local to a calculation,
and supplying a system-module stub in Java-only descriptor fixtures. Do not resurrect those failures
by copying an earlier intermediate implementation or temporary probe jar.

## Scope-3 verification

The combined implementation passed on 2026-09-25 using the full verification command above:

- Java: **527 cases; 491 passed, 36 existing skips; no failures or errors**. All eight added cases
  executed without skips, including native preparation, executable publication and cold signature
  truncation with a frozen image.
- XDK: **49 passed, no skips**, including **24 ownership cases**. Frozen application regressions
  cover both original failures and the new delegation fixture, each in two independent images.
- Fresh-process Java 25 audits passed for `Singletons.x`, `RuntimeConstruction.x` and
  `RuntimeDelegation.x`. The distribution rebuilt successfully; `spotlessCheck` and
  `git diff --check` passed.

These results establish the scope-3 boundary described in the architecture plan, not universal
frozen execution or safe execution of shared compiled bodies.

## Scope 3: completed work and reproduction record

Implementation paths:

- `javatools/src/main/java/org/xvm/asm/RuntimeMethodStructure.java` and
  `javatools/src/main/java/org/xvm/asm/RuntimeMethods.java` for generated executable ownership.
  `javatools/src/main/java/org/xvm/runtime/ClassComposition.java` retains the previously separated
  field-initializer mechanism through the same runtime method structure.
- `MethodInfo.ensureOptimizedMethodChain` -> `ClassStructure.ensureMethodDelegation`.
- `PropertyInfo.createDelegatingChain` -> `ClassStructure.ensurePropertyDelegation`.
- `TypeConstant.createMemberInfo` -> `MethodStructure.markNative`, plus native marking in
  `MethodInfo` and `ClassTemplate` preparation.
- Stable const/helper synthesis (`FileStructure.synthesizeChildren`,
  `ClassStructure.synthesizeConstInterface`, `synthesizeAppendTo`) and native preparation order.

The original failures were reproduced at `f63473dfd` before changing code:

| Program | Original result | Scope-3 result |
|---|---|---|
| `RuntimeDescriptors.x` | Passed with frozen application definitions | Passes in the frozen XDK regression |
| `SingletonPaths.x` | Passed with frozen application definitions | No new frozen claim; normal ownership regression retained |
| `Singletons.x` | Failed at `ClassStructure.ensureMethodDelegation` | Passes in the frozen XDK regression |
| `RuntimeConstruction.x` | Failed at `TypeConstant.createMemberInfo` → `MethodStructure.markNative` | Passes in the frozen XDK regression |
| `RuntimeDelegation.x` | New scope-3 fixture | Passes in the frozen XDK regression and fresh-process audit |

The manual harness is committed, independent of temporary scripts. These direct invocations need
Java 25 selected for `java`; unlike Gradle, they do not select a toolchain themselves:

```sh
./gradlew :xdk:installDist --console=plain
java -ea -cp xdk/build/install/xdk/javatools/javatools.jar \
    xdk/src/test/manual/FrozenImageAudit.java \
    xdk/build/install/xdk xdk/src/test/resources/ownership/Singletons.x
java -ea -cp xdk/build/install/xdk/javatools/javatools.jar \
    xdk/src/test/manual/FrozenImageAudit.java \
    xdk/build/install/xdk xdk/src/test/resources/ownership/RuntimeConstruction.x
```

Run audits in fresh processes when checking cold startup. The harness compiles and reloads the
artifact, performs native preparation/linking, freezes before cold entry/metadata lookup, and fails
on forbidden writes. The native root is not frozen by this application audit. An expected failing
audit is still an open failure, not a passing test.

The implementation distinguishes stable declarations completed before publication from late
specialization-dependent executable bodies. Put late method/property delegation and accessors in
an explicitly owned executable overlay. Preserve identity/signature lookup, reflection/dispatch
agreement, generic substitution, native binding and frame/local-constant correctness. Do not insert
methods/properties into frozen image classes, assign descriptor image indices, prewarm queries to
hide writes, weaken owner/read-only guards, or fall back to ambient pools.

Native preparation and delegation/body ownership are separate commits. Focused Java and
assertion-only `.x` regressions cover method/getter/setter delegation, generics, cold native rebasing,
failure/retry, deterministic concurrent publication and independent contexts. Frozen application
checks compare declaration trees and constant membership/positions. Same-image tests establish
generated-body isolation; they do not execute shared compiled bodies in two contexts or establish
general execution concurrency safety.

After each slice, update the plan with exact changed paths, why the owner is correct, actual checks,
and remaining failures. Do not silently absorb scopes 4–6 merely to claim scope 3 is complete.

## Scope 4: completed implementation and reproduction record

The original Java-only reproductions at `2087e376a` showed the same mutable Ops in two containers'
arrays and a shared initialization flag causing the second container to skip its singleton operands.
`703ed5f40` moves the completion flag/protocol into `ServiceContext` entries keyed by exact
`MethodStructure` identity. Canonical singleton values and construction still use container owners.

The next commit gives each `MethodExecution` a final lazy decoded body, local operand array,
frame layout and original line map. `Frame`, calls, constructors, function handles and native
adapters size arguments through the executing service. `MethodStructure` no longer owns execution
frame counts or automatic runtime assembly. Failed decoding remains retryable; generated methods
must be assembled before use. Metadata clears retain service execution entries and generated bodies.

Two additional issues appeared during integration:

- An inflated-reference initializer's anonymous Java Op wrote no bytes, causing EOF when its
  generated body was freshly decoded. `RuntimeMethodStructure.InitRef` now uses a private NOP
  placeholder plus prepared callback operands, restoring independent Ops before simulation.
  The XTC format and the initializer's instruction order are unchanged.
- Cold super-use queries installed compiler Code on getter declarations. Super-use, no-op and
  injection queries now inspect code without installing it; interpreter frames use their own
  decoded body. Compiler/tooling Code remains available through its existing APIs.

`MethodExecutionTest` has eight self-contained cases covering cold decoding/layout, independent
services/containers, debugger reset isolation, concurrent publication, native argument sizing and
initialization/preparation retry. `RuntimeMethodsTest` adds a generated-callback decoding regression.
The four new XDK cases execute `RuntimeDescriptors.x`, `RuntimeConstruction.x`, `Singletons.x`
and `RuntimeDelegation.x` twice against the exact same frozen image, without a second copy/relink.
Frozen checks compare compiler-Code identity, declaration trees and constant membership/positions.

Per-service decoding deliberately retains more code for isolation. This is not a performance
benchmark, a new service scheduling model, or a claim of arbitrary concurrent execution within
one service. Native-root freezing, static native caches and captured reflection values remain open.
Final XML results on 2026-09-25: **536 Java cases, 500 passed and 36 existing skips; all 53 XDK
cases passed without skips**, including 28 ownership cases. All new scope-4 tests executed.
`spotlessCheck` and `git diff --check` passed. See the architecture plan's scope-4 validation
record for commands and the corrected older copy test.

## Scope 5: implementation and reproduction record

`40743698e` separates capture tokens from their live values. `ConstHeap` owns captured annotation
arguments, and exact-context resolution rejects unknown or foreign tokens, including tokens nested
in otherwise shared types. Semantic clears retain captures. Three self-contained Java regressions
cover these rules, with 24 passing focused Java cases and no skips at that checkpoint.

`1078cf248` moves runtime reflection helpers, signature results and empty member arrays into the
requesting descriptor/composition tables and heap. Foreign Type handles retain their exact source;
reflective operations require an owner that can import every operand before constructing a result.
Five focused XDK cases pass without skips. Three interpreter programs exercise generic/child
constructors, properties and Ref round trips, bound methods/functions, annotations and foreign
reflection, each in two applications over the same frozen image. Explicit source-dispatch and
helper tests cover metadata clears and same-image ambiguity/rejection.

The following file-system commit removes unused handle slots from FSNodeConstant/FileStoreConstant
and native-root constructor caches from CPFile/CPDirectory/CPFileStore. Materialization now selects
the composition's prepared application declaration. Cold timestamp parsing also exposed literal
construction, diagnostic formatting, function compatibility and operator argument comparisons
using the wrong owner; those paths now adopt or import before deriving/comparing runtime types.
The new file-system test materializes file, directory and store values in two applications over
one frozen image, checks their owners and distinct identities, and verifies retained heap values
after metadata clears. All six focused XDK scope-5 cases pass without skips.

The architecture plan's native-state table distinguishes migrated application values from remaining
bootstrap/native-root static bindings, core values, compiler-template helpers and native callback
arrays. It is an audit, not a claim that multiple independent NativeContainers can run concurrently
without interference. The native root is not frozen, and existing unimplemented reflection
features remain unsupported. No ownership guard was relaxed to make these tests pass.

Final scope-5 verification on 2026-09-25 used:

```sh
env RUN_INTEGRATION_TESTS=true ./gradlew :javatools:test --rerun \
    :xdk:test --rerun spotlessCheck --console=plain
git diff --check
```

JUnit XML reports **538 Java cases: 502 passed, 36 existing skips, no failures/errors**; all
**59 XDK cases passed without skips**, including 33 ConstantPoolOwnershipTest cases and the new
FileSystemConstantOwnershipTest. All new scope-5 cases executed. The distribution rebuilt,
`spotlessCheck` and whitespace checks passed, and Gradle stored the configuration cache.
The user requested publication of all three scope-5 commits after verification.

## Work that follows scope 5

Scope 6 enables freezing at activation only after the interpreter workloads pass with guards
enabled, including cold metadata, delegation, reflection, independent contexts and unchanged
images. It must respect the native-root limits recorded above; a successful bounded application
audit does not automatically authorize wider runtime sharing.

Optional wider sharing and image-copy removal require their own correctness and retention evidence.
General error-listener migration, embedding/Gradle/shutdown work and JIT execution remain separate.

When reporting progress, state what is complete, what was actually tested, commit boundaries, and
what is still open. A metadata-cache move alone cannot guarantee complete runtime state separation.
