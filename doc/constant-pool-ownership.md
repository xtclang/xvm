# Constant-pool ownership: contracts, changes and evidence

Status: extracted on `lagergren/constant-pool-ownership-only` from master `601a68e8b` on
2026-09-24. This branch contains the ownership implementation and its minimal execution
prerequisites. It excludes the Gradle DIRECT/PERSISTENT work and broad resource/shutdown changes.
The proposed next architectural step is in [the separation plan](constant-pool-architecture-plan.md).
The original combined work remains on `lagergren/constant-pool-ownership`; its
[submission plan](https://github.com/xtclang/xvm/blob/lagergren/constant-pool-ownership/plugin/doc/plans/embedded-runtime-pr-plan.md)
still governs the larger embedded-runtime series.

The audit found real source corruption, wrong destination selection, lost diagnostics and
incorrect runtime singleton lookup. It also found shared cache/guard state that needed
isolation even where a wrong runtime result had not been demonstrated. These are different
claims, and the change catalogue below distinguishes them.

This is not a guarantee that no ownership defect remains. It records the paths inspected,
the changes made, the counterexamples and tests, and the limits of the evidence. In particular,
it does not establish arbitrary concurrent compilation, definition-generation compatibility,
all container models, or JIT correctness.

## Scope and provenance

The original two ownership commits were:

- `9e413ea1e`: allow selected Java operations to work without an ambient pool. Its fallback
  behavior was an intermediate step; the later changes remove internal ambient selection.
- `99a46e385`: pass destination pools explicitly through signature compatibility and variance.

The subsequent combined implementation, tests and audit were committed as `11bc8932a` on the
source branch. This extraction applies their final behavior to master's APIs, rather than importing
the intermediate ambient fallback implementation or the unrelated embedding history. The local
implementation commit is `65e5ce149`, following prerequisite `d8c6c3176`. The audit and architecture
proposal are a separate documentation commit. The original source branch remains at `07776fc99`;
creating this branch neither rewrites it nor removes the embedded-runtime submission plan.

The standalone execution prerequisite is `d8c6c3176`: the Frame/ServiceContext failure-continuation
support from `e1eb9fb1a`, plus the waiter readiness correction from `d5947a903`. It includes focused
unit tests. No socket, HTTP, file watcher, resource registry or runtime-wide shutdown code is pulled
in with those hunks. Unit fixtures use master's existing `Runtime.shutdownXVM()`. XDK fixtures use
its public embedding compiler, then explicit MainContainers in one Runtime for the execution test.
The execution fixture serializes and reads the compiled artifact, matching master's launcher
input. A compiler-linked file has not undergone native preparation; using it directly as a
runtime repository template bypassed that preparation and failed on the missing native types.
They do not need the later session factory, RunRequest, close API or Gradle integration.

The final error-listener migration incorporates the relevant compiler/listener work from
`lagergren/errs`: the implementation slice at `17d4a15a5` and final listener APIs/tests at
`f98b0fe87`. It does not import the LSP, semantic snapshots or their build/CI changes.

JIT work remains on
[archive/embedded-jit-ownership](https://github.com/xtclang/xvm/tree/archive/embedded-jit-ownership).
See its [handoff](https://github.com/xtclang/xvm/blob/archive/embedded-jit-ownership/doc/embedded-jit-handoff.md).
CP-A4, JIT callable-type caches and generated-name resets belong there. Neither those fixes nor
the integration branch's JIT-removal commits are imported here; master's existing JIT behavior
is preserved. Shared listener API adaptations do not establish JIT execution correctness.

## The ownership model

Five different concepts must not be conflated:

| Concept | Meaning | How code obtains it |
|---|---|---|
| Definition owner | The pool containing this constant and interpreting its index | `constant.getConstantPool()` |
| Operation destination | The pool in which a caller needs a new or specialized result | An explicit argument, compilation context or target TypeInfo |
| Ambient binding | A temporary compatibility value associated with the current Java thread | `ConstantPool.withPool(...)`; internal destination selection no longer reads it |
| Runtime-value owner | The container that owns a singleton's initialization and live handle | `getOriginContainer` followed by canonical registration in that container |
| Diagnostic recipient | The request that must receive errors and warnings | A non-null `ErrorListener` passed through the operation |

`getConstantPool()` remains necessary: it tells us where a definition actually lives. Removing
ambient selection does not remove definition ownership. Nor does supplying destination B mean
that every input from A must be forcibly rebound to B.

### Same-pool operations

Registering a definition in its own pool normally returns its canonical entry. It must retain
valid same-owner caches and singleton initialization state. Registration is not a general cache
reset. An owner-local operation, such as inspecting a declaration's annotations or folding a
number without an explicit destination, uses that definition's owner.

### Cross-pool operations

These are normal, necessary operations, not exceptional errors:

- A repository supplies library definitions from pool A; compilation links or copies them into B.
- A generic declaration in A is specialized for target metadata in B.
- A completed method is copied into another file/request and needs independent Ops, ASTs and registers.
- A nested container needs a definition locally while a permitted shared singleton's live value
  belongs to an ancestor.

Suppose A owns a library, B owns the application and an unrelated pool C happens to be bound
to the current thread. Constructing a specialized signature for B must use B, preserve A, and
ignore C. Delayed resolution must remember B even after the caller's scope has closed.

`B.register(value)` can resolve a typedef, reuse an equivalent B entry, adopt a definition into
B, or return an unresolved/unshareable input unchanged. **Always use the return value and inspect
its actual owner where the next operation mutates it.** A source-owned return is not permission
to rewrite that source's children into B. Permitted upstream references are not evidence of an
ownership failure by themselves. Constant indices are local and cannot be copied as portable IDs.

Type relations have an existing rule for choosing an operand pool capable of interpreting both
types, with relation state attached to the canonical type. That rule was documented, not replaced
with an ambient-pool rule or a new global relation cache. `TypeConstant.isShared(pool)` expresses
definition/type compatibility; it does not authorize sharing a mutable singleton instance.

## Threads: what is local and what can be shared

### Creating and closing the binding

`ConstantPool` has one static, ordinary `ThreadLocal<ConstantPool>` per defining classloader.
It is not an `InheritableThreadLocal` and does not create a pool. `FileStructure` and the normal
repository/container preparation paths create pools; the thread-local stores a reference to one.

`withPool(pool)` records the calling thread and its previous binding, sets the new binding, and
returns a close action. The caller uses try-with-resources. Closing on the creating thread restores
the previous binding; restoring null calls `remove()`. Nested scopes close in reverse order.
Exceptions follow the same cleanup path. `withPool(null)` temporarily isolates an operation from
a host's binding and restores it afterward.

Closing on another thread now throws `IllegalStateException` **before changing either binding**.
The initial unguarded plain-reference implementation restored the saved value on
whichever thread called close, leaving the creator's binding in place and potentially overwriting
the worker's binding. This misuse was reproduced in `ConstantPoolScopeTest`; the guard completes
the local binding migration. All inspected production callers use lexical same-thread scopes.
No existing production dispatch of a scope object was found.

The close action is not an asynchronous context carrier. It does not enforce a general stack
protocol against manual out-of-order or repeated close calls; use lexical try-with-resources.
The compatibility `setCurrentPool` API also remains available to external callers, which are
responsible for restoring their own manually managed bindings.

### Moving work to another thread

A new worker starts without the caller's binding. A reused worker sees its own binding, which
must have been restored by its preceding work. Neither executor submission nor an XVM fiber
moving between workers transfers a Java thread-local automatically.

Runtime work already establishes the scope at the execution boundary:

- `ServiceContext.drainWork` binds that service's `f_pool` while executing fibers and restores
  both the pool and service-context bindings on exit.
- Native filesystem and HTTP callback paths that need a compatibility binding establish their
  own lexical scopes. They do not close the submitting thread's scope.
- Compiler and native bootstrap phases establish their own scopes and restore the caller's
  binding. This extraction does not introduce the later reusable-session preparation API.

These boundary examples are inspected existing behavior, not claims that every callback was
newly changed by this ownership patch.

Two threads **can deliberately bind the same pool reference**. Their bindings are independent,
but the object is the same: no copy, ownership transfer, locking or safe publication is performed
by `ThreadLocal.set`. A task submission or other normal Java synchronization must publish shared
references. This is relevant to real runtime workers and shared core definitions, not just a
hypothetical external caller.

Some pool internals support concurrent runtime access: for example, registration uses concurrent
lookup maps and synchronizes insertion. This does not make all pool/file/compiler operations
concurrent. Recursive reference counting/optimization is a single-thread compilation phase;
mutable AST construction, linking and unrestricted metadata copying are not authorized for
concurrent mutation. This extraction does not add a new concurrency guarantee to embedding or
change master's execution lifecycle.

### Other thread-local state

The ambient binding is separate from `TransientThreadLocal` recursion/deferred-work guards:

| Holder | Purpose and lifecycle |
|---|---|
| `ConstantPool.f_tlolistDeferred` | A per-pool, per-thread list of deferred TypeInfo work; taking the list removes that thread's entry |
| `TypeConstant.m_tloInProgress` | Type-relation recursion detection; remove the relation in `finally`, and remove the entry when the set is empty |
| `TypeParameterConstant.comparisonRecursion` | Avoid recursive comparison of a formal parameter; lexical `push` restores/removes the guard |

`TransientThreadLocal` uses a per-thread identity map keyed by the holder. Its values do not
cross threads automatically, but two copied constants sharing the same holder would incorrectly
share a recursion marker **on the same thread**. Adoption now gives destination metadata its own
guards and locks. This is why merely saying “it is thread-local” was not enough. Non-empty transient
entries retain their keys/values until removed; they are not a substitute for cleanup.

The thread tests use actual single-thread executors and `Future.get()` to establish ordering.
They check non-inheritance, explicitly binding the same object, exception restoration, subsequent
work on the same worker, and rejected cross-thread close. There are no sleeps or latency assertions;
the JUnit timeout only guards a hang. They do not prove that every mutable metadata operation is
safe to run concurrently.

## How to read the evidence

- **Reproduced:** a concrete bad result or invariant violation was observed before its fix.
- **Source-confirmed:** the erroneous path/state is identifiable in the old code, but no separate
  end-to-end failure was isolated for that particular hunk.
- **Preventive isolation:** tests expose an unwanted alias or retained calculation state; they do
  not establish an observed race, deadlock or memory leak.
- **Contract migration:** code now explicitly states its destination or listener. Ordinary
  execution often already worked because the surrounding launcher supplied the matching context.
- **Preserved:** an inspected behavior remains intentional. A new test may protect it without
  implying that it was broken before.

The catalogue accounts for semantic changes. Import ordering, changed signatures at their callers,
and adjacent Javadoc/formatting adjustments are included in the corresponding entry; they are not
separate defects. The file inventory at the end makes the scope checkable.

## Definition and destination changes

### C01 — compatibility scopes and removal of implicit selection

`ConstantPool` replaces the thread-local one-element pool array with a plain pool reference, removes
an empty binding, and guards scope close against a different thread. `currentOr` remains only for
external compatibility. `Constant.poolInUse` is removed after migrating internal callers.

An unbound read used to allocate a typed holder even when no pool was needed. Avoiding that holder
is retention hygiene, not a demonstrated classloader leak. The cross-thread close defect is
reproduced; internal ambient selection is a contract migration. `ConstantPoolAmbientTest` preserves
the external fallback contract, while `ConstantPoolScopeTest` tests scope behavior described above.

### C02 — explicit signature compatibility and variance

`SignatureConstant.isSubstitutableFor` and the TypeConstant variance path take the operation's
destination explicitly; `TerminalTypeConstant` and callers pass it through. A compiler caller uses
its compilation pool; a metadata lookup uses its target TypeInfo's pool. An unrelated ambient pool
can no longer redirect specialization.

Most launcher-driven cases happened to work because the bound pool equalled the intended target.
The XDK `SignatureCompatibilityTest` exercises real generic parameters, returns and auto-narrowing
unions with no binding and with an unrelated binding, and asserts destination ownership as well as
compatibility. During development, restoring ambient selection failed the owner assertions even
when the compatibility boolean still passed. This changes Java method signatures; downstream
callers/subclasses must migrate, not silently rely on old overloads.

### C03 — never mutate an unadopted source type (CP-A1)

`TypeConstant.registerTypeConstants` now recursively registers a returned type's children only
when that type belongs to the destination. Previously, `register` could return an unshareable
source type and the next line would rewrite its references into the destination anyway.

**Reproduced:** registering a signature containing `Source.Actual` into an unrelated destination
corrupted the source's ownership relationships. Same-pool or adoptable inputs concealed the bug.
`ConstantOwnershipTest.registeringASignatureCannotRebindAnUnshareableSourceType` verifies source
identity, owner and category remain usable. `RegistrationOwnershipTest` covers same/cross-pool,
canonical reuse, unresolved returns and four ambient states. The `ConstantPool.register` Javadoc
now states the already-existing possibility of an unchanged source-owned result.

### C04 — copy repository modules before rebinding (CP-A2)

`FileStructure.linkModules` no longer registers a versioned repository module and its children
before cloning them. The source repository may deliberately return the same definition object
to several requests; a consumer cannot mutate that object's ownership first.

**Reproduced:** a versioned extracted module changed under linking. Repository wrappers that
already supplied fresh copies or the unversioned path could hide the defect.
`FileStructureOwnershipTest.linkingAVersionedModulePreservesRepositoryOwnership` uses a raw
repository with the same source object, checks compile-time and runtime linking, compares source
identities/serialized bytes, and serializes and reads back the consumer.

### C05 — independent completed method bodies (CP-A10)

`MethodStructure.cloneBody` snapshots completed source-owned code/AST data before copying it.
The destination decodes independent Ops, AST nodes and parameter registers. Code, decoded AST,
AST parameters and registry caches are reset; local/super constant arrays are cloned and
initialization state is cleared. The obsolete `Code.cloneOnto` path is removed; the wrapper
constructor used by BlackHole code remains.

**Reproduced:** copying a decoded or newly constructed AST could rewrite shared source nodes.
Copies made only from serialized, undecoded definitions avoided the alias. Simply clearing the
AST was also wrong when it was the only completed representation. `FileStructureOwnershipTest`
covers new, decoded, read-only and decoded/read-only bodies, unassembled Ops and parameter
register independence. This is a completed-definition copy contract, not support for copying
a method while another thread is compiling it.

### C06 — detach compiler registers on adoption

`RegisterConstant` snapshots the assigned register index. `DynamicFormalConstant` snapshots its
method, name, index, register ID, type and formal identity; adopted constants no longer keep the
source compiler's live `Register`. Detached comparison remains stable while live-register
comparisons keep their original identity behavior. Unresolved constants still remain in their
source pool; direct adoption of unresolved register state is rejected.

**Source-confirmed/preventive isolation:** shallow copies retained compiler objects. Serialized
definitions normally already had indices, so request copying through disk often worked.
`ConstantOwnershipTest.copiedDefinitionsDetachCompletedCompilerRegisters` checks detachment and
stable meaning. No measured long-lived compiler-object leak is claimed.

### C07 — lazy identities retain their destination (CP-A3)

`IdentityConstant.NestedIdentity` stores the destination passed by MethodConstant/PropertyConstant
callers. Later hash, equality and ordering work resolves there instead of selecting a pool at the
time of use. The compare fast path checks the nested-identity object, not merely the underlying
declaration: two resolvers can specialize the same declaration differently.

**Reproduced:** delayed resolution ignored its requested destination. Immediate resolution in
the usual compilation scope hid the problem. `DestinationOwnershipTest.delayedNestedResolutionKeepsItsExplicitDestination`
checks delayed resolution under empty/unrelated bindings and distinguishes resolver results.

### C08 — changed pending wrappers use the destination (CP-A5)

`ParameterizedTypeConstant.resolvePending` constructs a changed wrapper in the explicit pool.
An unchanged result may still return `this`; that is an intentional no-op, not rebinding.

**Reproduced:** an explicitly cross-pool resolution produced a source-owned wrapper. Same-pool
calls passed. `DestinationOwnershipTest.pendingWrapperResolutionUsesDestination` checks both
the changed result and the no-op with no/unrelated ambient binding.

### C09 — method layering and super signatures (CP-A6)

`MethodInfo.layerOn` compares Object identities structurally using the declaration's owner.
Previously it could use a different ambient pool's Object identity and add an extra Object body.
`getSuperSignature` resolves generics in the target TypeInfo's pool.

The extra layered body is **reproduced** by
`DestinationOwnershipTest.unrelatedAmbientPoolDoesNotDuplicateObjectMethods`. The adjacent
super-signature destination correction is **source-confirmed/contract migration**, covered by
the wider metadata/signature tests without a separate wrong-super-result reproducer.

### C10 — declaration and runtime queries use their actual context

`MethodBody` uses the declaration's pool to inspect Override/Auto/Op annotations.
`PropertyInfo.isIdentityValid` resolves a missing query namespace with the target TypeInfo's pool
(or the identity's owner when no target metadata is present). ConstantPool's function/method
compatibility helper uses its receiver's empty-tuple type. `InterpreterConnector.invoke0` builds
argument String arrays with the main container's pool.

These are **contract migrations** from ambient selection. `MethodBodyAmbientPoolTest` checks
annotation queries and diagnostic formatting with no binding; property lookup and argument
construction are covered by broader compiler/interpreter runs, not standalone wrong-owner
regressions. Ordinary Runner execution already bound the matching main pool.

### C11 — explicit constant-folding destinations

`Constant.apply(destination, operator, operand)` registers operands before dispatch and the result
afterward. The owner-local overload remains. ByteConstant/IntConstant range construction uses the
owner; CmpChainExpression, CmpExpression, RelOpExpression, UnaryMinusExpression and
UnaryComplementExpression pass the compiler's destination explicitly.

Unbound range construction and wrong-owner results are protected by `ConstantPoolAmbientTest`
and `RegistrationOwnershipTest`. The matrix covers absent, source, destination and unrelated
ambient bindings. Matching launcher bindings previously hid the dependency. CaseManager's numeric
distance calculation intentionally keeps owner-local folding because it only consumes the numeric
value. The listener-only changes in that file are separate (D04).

### C12 — inference results belong to the requested pool

`TypeCollector.inferFrom` registers inputs/results with its explicit destination, including the
frozen-result path. This is **source-confirmed/contract migration**; the earlier audit initially
listed it as follow-up work, but it is now implemented.

XDK `SignatureCompatibilityTest.inferredCommonTypeUsesTheRequestedDestination` exercises actual
generic library types across source A, destination B and unrelated ambient C. It checks ownership,
not just the inferred type's textual equality. It is not evidence of a previously observed
interpreter crash.

### C13 — copied metadata must not share owner-sensitive caches (CP-A7)

The following changes happen on adoption/owner change, not same-pool canonical registration:

| Class | State affected |
|---|---|
| `IdentityConstant` | Clear cached canonical nested identity alongside the component cache |
| `TypeConstant` | In addition to existing TypeInfo/relation/handle/normalization resets, clear consumes/produces caches, relation recursion holder and validation state; allocate an independent recursion counter |
| `ParameterizedTypeConstant` | Independent resolution lock; retain existing generic-resolution invalidation |
| `SignatureConstant` | Independent comparison lock; clear remembered strong/weak comparison targets |
| `TypeParameterConstant` | Independent comparison recursion holder |
| `PropertyConstant` | Clear member TypeInfo and invalidate type/signature/constraint caches |
| `PropertyClassTypeConstant` | Clear cached property TypeInfo |

The TypeInfo recursion counter is now decremented in `finally` on exceptional exits. Existing
weak retention of foreign signature-comparison targets is preserved: local targets may be strong;
foreign targets remain weak. Parsed scalar literal caches are portable and remain intact.

The stale canonical nested identity is **reproduced**. Guard/lock/counter aliases are **preventive
isolation**, checked reflectively in `ConstantOwnershipTest` and `RegistrationOwnershipTest`.
These tests demonstrate independent state, not an observed race/deadlock or a GC-based leak.
Some property-cache resets have source inspection and broad metadata coverage rather than an
individual primed-cache reproducer. The exception-safe recursion decrement has no dedicated
injected failing-TypeInfo-builder test. Existing `TypeInfoMemberOwnershipTest` remains relevant.

### C14 — native bootstrap restores its caller (CP-A8)

`NativeContainer.loadNativeTemplates` uses a lexical pool scope instead of resetting/leaking the
binding manually. **Reproduced:** direct bootstrap could lose a caller's binding on success or
leave bootstrap's binding after failure. InterpreterConnector already wrapped normal startup,
which masked this on the usual embedding path.

XDK `ConstantPoolOwnershipTest.nativeBootstrapRestoresTheCallerPoolEvenOnFailure` checks success
and an injected failure with a pre-existing caller binding. The large-looking source diff is
mostly the try-with-resources indentation around the existing initialization body.

## Runtime values and singletons

### C15 — copied definitions do not copy initialized runtime state

Adopted `SingletonConstant`, `FSNodeConstant` and `FileStoreConstant` instances clear materialized
handles. Singleton copies also clear initializer fiber/future state. These are definition copies,
not transfers of live application values. Same-pool registration retains canonical live state.

**Source-confirmed/preventive isolation:** shallow adoption retained execution objects. Normal
serialized repository paths did not serialize those Java caches, so they often avoided it.
`ConstantOwnershipTest.adoptingDefinitionsDoesNotCopyExecutionHandles` and
`SingletonOwnershipTest` verify copied versus same-owner behavior and independent waiter state.
They do not establish an observed retained-heap leak in the ordinary serialized request path.

### C16 — canonical singleton owner and initialization context

`Container.ensureSingletonConstant` first finds the defining container, then registers the
definition in its pool. `ConstHeap.ensureConstHandle` and `MethodStructure.addSingleton` use that
canonical constant before consulting a handle or scheduling initialization.

`Utils.initConstants` routes each singleton to its owner's main service context. Completed values
take the fast path; initialization fiber/future state belongs to that owner. Cross-context work
dispatches **one singleton at a time**, then resumes the caller's original list, so a list mixing
ancestor-owned and local definitions is not moved wholesale into the ancestor. Existing
same-fiber recursion, waiting-fiber behavior, queue relief and Critical synchronicity are retained.
The owner constructs the value locally. Immediate and asynchronous constructor failures abort
initialization and release waiters; asynchronous cleanup uses `Frame.addExceptionCleanup` from
the small, independently extracted continuation prerequisite. It does not require shutdown changes.

**Reproduced:** cold lookup through a local alias of a core singleton could return a deferred
singleton instead of the canonical native True/False/Null handle. A warm heap could hide it.
XDK `nativeSingletonIdentityDoesNotDependOnHeapWarmup` checks exact native handle identity for
both cold and warm heaps. `SingletonOwnershipTest` checks explicit shared/unshared origins,
mixed-owner dispatch, independent waiters, abort and retry. Its recording service context is a
deterministic synchronous stub; it does not simulate every concurrent initializer interleaving.

### C17 — a rejected parent handle must not escape

`ConstHeap.getConstHandle` now returns null when the parent handle is not shareable. The old code
declined to cache it but still returned it. It also prevents borrowing a singleton from the
parent when the current container is its origin, even if compatible type definitions exist above.

**Reproduced:** parent lookup could return disallowed application state. Shared native values and
cache hits obscured the difference. `ConstHeapOwnershipTest` checks accepted/rejected arbitrary
handles and the crucial case of a compatible type with an unshared singleton. The nested `.x`
fixture verifies application state isolation through actual interpreter execution.

### C18 — nested containers need independent application definitions

`xContainerLinker.completeResolveAndLink` copies the resolved FileStructure before constructing
each NestedContainer. An already-linked template may otherwise supply the same structure again.
This is **source-confirmed** alias prevention. The `.x` regression exposed the combined definition
and parent-heap boundary; cloning alone did not fix it, and the unshared parent-heap guard was
decisive. There is no claim of an independently isolated runtime failure for this clone hunk.

`ownership/Singletons.x`, run twice in fresh application containers under one native root, checks child/parent/sibling/
grandchild service state, stable distinct const values, switch lookup, and two caught failed
constructor attempts followed by a count assertion. Const initialization assertions test stable
ownership, not an exact count of every field-initializer evaluation.

### What is actually shared

Core definitions and immutable native singleton handles can be root-owned and reused. An explicitly
shared application module can resolve to its highest owning ancestor. An unshared application
module's singleton state stays local, even when names and types compare equal.

The runtime supports an explicit shared-module list. The XDK Java integration test
`explicitlySharedNestedModulesUseTheHighestOwningAncestor` exercises that through child and
grandchild containers. It primes the singleton value to check canonical lookup; it does not run
a concurrent constructor for that shared module.

The Ecstasy linker currently accepts only `Lightweight` and passes an empty application-sharing
list. Its existing shared/additional-module TODO and unsupported Custom/Secure models are not
implemented here. The `.x` fixture tests the actual core-only sharing behavior. It would be wrong
to describe these fixes as completing the documented full container model.

### C19 — runtime annotation arguments are captured values

`HandleConstant` now requires an explicit, non-null capture pool and records that original owner.
`xRTTypeTemplate.invokeAnnotate` supplies `frame.poolContext()`. `AnnotatedTypeConstant.isShared`
rejects promotion of a type containing a captured handle into another pool; composite types
inherit this restriction. Registering an unshareable annotated type leaves it source-owned.

A HandleConstant represents the captured value itself. Clearing it like a singleton's disposable
cache would destroy the annotation argument. Adoption does not turn the captured object into a
destination-owned value or make it serializable.

**Reproduced:** a runtime-captured annotated type incorrectly reported itself shareable with a
foreign pool. `ConstantOwnershipTest.runtimeAnnotationValuesCannotBePromotedIntoAnotherPoolsTypeCache`
checks direct/composite types, source preservation and the retained value. This is not a measured
classloader leak or a `.x` cross-container annotation-execution test.

## Diagnostics: request ownership instead of ambient file state

### D01 — one non-null listener contract

`ErrorListener.log(ErrorInfo)` returns void; callers ask `isAbortDesired()` separately. Named
budgets replace unexplained limit conventions. Collecting/tee/cancellation/branch behavior is
explicit. `Silence.PROBE`, `CASCADE` and `DISCARD` replace BLACKHOLE and null-as-policy.
`ErrorList.clear` also clears diagnostic UID deduplication state, allowing replay after a reset.
Branch abort state accounts for its parent.

These are a **contract migration** from the errs work plus fixes to deduplication/abort behavior,
not dozens of independent constant-pool failures. Compiler, lexer, parser, stage-manager and tool
listeners are final and non-null where their lifetime is fixed. `@NotNull` documents the API;
`requireNonNull` enforces key boundaries. Deliberately inactive scope state is not a nullable
listener accepted by an active operation.

`ErrorDeduplicationTest`, `ErrorListenerAbortTest`, `ErrorListenerBranchTest`,
`ErrorListenerCancelTest`, `ErrorListenerSilenceTest` and `ErrorListenerSiteTest` cover the
listener contract. Existing Java implementers must migrate the changed `log` signature.

### D02 — scoped reporting and speculative parsing

New `Reporting` holds a non-null listener only while an operation is active; reading an inactive
holder fails. Its lexical `to(listener)` scope restores the prior listener or inactive state.
Parser has a final holder; NameResolver binds it for each resolution, avoiding retention of a
past request. Parser `Attempt` saves the cursor and a branch listener: commit merges diagnostics,
while closing an abandoned attempt restores/discards state. Existing nesting restrictions remain.

New `ValidationScope(ctx, errs)` replaces loosely paired mutable validation context/listener
fields. An absent validation phase may have no scope; an active scope cannot have a null listener.
Reporting is not a concurrent shared sink. `ReportingTest`, `ParserAttemptTest` and
`CompilerDiagnosticsTest` cover restoration, speculative rollback and diagnostics.

### D03 — files no longer retain or find request listeners (CP-A9)

FileStructure's listener state/accessors are removed. XvmStructure logging receives the listener
explicitly; it cannot borrow one through an ambient file/pool. Cloning reusable definitions must
not copy a host callback, and a request must not accidentally report into another request's sink.

This boundary is **reproduced/source-confirmed** by the earlier ambient/file behavior and
`FileStructureErrorListenerTest`. That test checks separate listeners for source/copy under an
unrelated binding, null rejection, tee branch rollback/commit, abort and replay after clear.
Callers of the removed file-level listener APIs must pass their request listener instead.

### D04 — explicit reporting policy at every migrated caller

Compiler AST, type-validation, reflection/compiler natives and tools now pass an actual listener
or an intentional silent policy. PROBE suppresses failed alternatives that are not accepted
program diagnostics; CASCADE suppresses secondary noise from incomplete metadata. For example,
`Expression.testFitAsType` no longer stages a speculative type interpretation with the caller's
real listener. Its answer is the fit result, not leaked speculative diagnostics. Committed
validation continues to report through the request listener.

Source/Token/AstNode logging uses explicit diagnostic site helpers. Code that previously used a
boolean return from `log` checks abort state separately. Method signatures, constructor calls and
tests are migrated together. The many files in this group mostly carry these API/policy updates,
not independent ownership algorithms. Compiler diagnostic/parser tests and full manual compilation
cover the combined migration; there is no individual pre-fix regression for every call site.

### D05 — cache diagnostic values, never request listeners (CP-A9)

TypeInfo stores immutable ErrorInfo values and merges diagnostic UIDs with synchronized updates
and volatile publication. TypeConstant records diagnostics at actual TypeInfo build boundaries,
including deferred/internal construction, attaches them to completed results, and replays them
to each later caller. A no-argument/silent build still records values for a subsequent real request.
Reusing existing TypeInfo views preserves their recorded diagnostics.

**Reproduced:** the first request received duplicate-Atomic warning VERIFY-75, but a later request
using cached metadata received none. A single compile or rebuilding all metadata hid the bug.
XDK `cachedMetadataReplaysWarningsToEachRequest` checks two recipients, both normal-first and
silent-first construction. The listener tests cover deduplication/branch semantics; this one real
warning is not exhaustive coverage of every error-producing metadata path.

### D06 — embedding and tool boundaries preserve the contract

EmbeddingSupport validates listeners before its failure-conversion try/catch. Caller misuse
therefore fails explicitly instead of becoming an internal compiler diagnostic. Ordinary reported
`LauncherException` failures return the documented compile failure result; other runtime failures
report once. InterpreterControl likewise holds a non-null listener and no longer silently skips
completion reporting. Launcher/tool defaults choose intentional silence where required.

`EmbeddingListenerContractTest` checks null rejection before configuration or I/O; existing
compiler and tool suites cover reported failures and normal paths. New final-listener source changes in shared
classes do not change the JIT implementation. A PROBE replacement in a shared
class's JIT helper is an API adaptation, not validated new JIT behavior.

## Tests, results and what they establish

Unit fixtures belong under `javatools/src/test`: they construct minimal definitions and do not
assume that an XDK distribution happens to exist. Tests requiring native libraries, real generic
metadata or `.x` execution belong under `xdk/src/test`, whose task declares its build prerequisite.
The new tests do not silently skip for missing installed binaries.

| Test group | Main ownership assertion | Important limit |
|---|---|---|
| ConstantOwnershipTest | Source integrity, detached registers/caches/handles, annotation capture | Reflective state assertions are not leak measurements |
| RegistrationOwnershipTest | Same/cross registration, four ambient states, comparison guard/lock isolation | No arbitrary concurrent metadata mutation test |
| FileStructureOwnershipTest | Versioned source unchanged; independent completed code/AST/registers | No copy while compilation is in progress |
| DestinationOwnershipTest | Lazy/pending destination; Object layering under unrelated ambient | Does not isolate every adjacent helper correction |
| ConstantPoolAmbientTest / MethodBodyAmbientPoolTest | No-binding operation and intentional owner/explicit-destination selection | External compatibility fallback deliberately remains |
| ConstantPoolScopeTest | Restoration, no inheritance, worker reuse, wrong-thread close rejection | Scope must still close lexically in reverse order |
| ConstHeapOwnershipTest / SingletonOwnershipTest | Correct origin, sharing rejection, owner dispatch, abort/retry | Recording dispatch is not a concurrency stress test |
| XDK SignatureCompatibilityTest | Generic return/parameter/union/inference destination with real library metadata | No stable module-generation cache contract |
| XDK ConstantPoolOwnershipTest | Bootstrap restoration, root handle identity, explicit shared ancestry, warning replay, nested `.x` state | Unsupported container models remain unsupported |
| Listener/parser/compiler tests | Non-null sinks, branch/abort/replay and speculative policy | Not every diagnostic category has a separate integration case |

Validation of this master-based extraction is recorded separately from the source stack:

- The prerequisite commit passed 4 deterministic execution unit tests and `spotlessCheck`.
- The extracted unit suite passed **507 discovered, 467 executed, 40 existing disabled/skipped**,
  with zero failures/errors. All new ownership and listener cases executed.
- The focused XDK ownership/signature suite passed all **16 tests**, including the nested `.x`
  fixture, with no skips. The full XDK suite first passed with its 8 existing opt-in cases skipped; enabling
  `RUN_INTEGRATION_TESTS=true` then passed **all 33 tests**, with zero skips/failures/errors.
- `./gradlew :javatools:test :xdk:test spotlessCheck --console=plain` passed. The final aggregate
  invocation reused the already-passing unit result and executed the full XDK suite. The subsequent
  `RUN_INTEGRATION_TESTS=true ./gradlew :xdk:test --rerun --console=plain` forced the test task to
  execute with its opt-in cases enabled. Counts were read from JUnit XML, not inferred from
  Gradle's success message.

The source stack's earlier 506 passed unit tests, 36 XDK integrations, plugin results and manual
DIRECT/xUnit results are historical context only. This extraction excludes the lifecycle and
resource test classes accounting for much of the count difference. Its adapted embedding listener
test covers only the compile/run overloads available on master. The test for the later file-tree
compiler overload remains with that API. No inherited success is reported as a new execution.

Useful focused commands (no standalone distribution assumptions):

```bash
./gradlew :javatools:test --tests org.xvm.asm.ConstantPoolScopeTest --console=plain
./gradlew :xdk:test --tests org.xvm.xdk.ConstantPoolOwnershipTest \
  --tests org.xvm.xdk.SignatureCompatibilityTest --console=plain
./gradlew :javatools:test spotlessCheck --console=plain
```

If an unchanged test task is up-to-date, use `--rerun-tasks --no-build-cache` when a fresh execution
is needed and read the JUnit XML counts. A green task that did not execute is not a new test result.
Timeouts are hang guards; there are no timing thresholds or forced-GC success conditions in the
new ownership regressions. Every extracted PR needs its own verification; passing this combined
implementation does not establish that every future extraction or architecture stage builds independently.

## Audit coverage and remaining boundaries

The source pass covered registration/adoption, copy/clone/reset state, explicit destination
parameters, ambient reads/writes, relation/generic/member caches, diagnostics, singleton heap
lookup/initialization and nested-container preparation. An AST-based scan examined unused
ConstantPool parameters and implementations accepting a destination but reading their owner.

Intentional unchanged cases include no-op/return-this overrides, unsupported operations,
TypeSequence's opaque handling, PendingType returning existing operands, owner comparisons,
portable scalar caches, VirtualChildType origin registration/child-cache reset, MethodBinding
structural references and existing relation-pool selection. ConstHeap relocation and JumpVal/
JumpVal_N lookup paths were inspected; switch execution is covered by the `.x` fixture, not
every possible relocation shape. DeferredSingletonHandle creation remains downstream of
canonicalization. Arity-only method lookup's existing TODO is a separate compiler limitation.

No internal production caller of `getCurrentPool`, `currentOr`, `setCurrentPool` or `poolInUse`
remains outside ConstantPool's compatibility implementation. Lexical `withPool` scopes remain.
That removes the audited dependency on ambient destination selection; it does not prove that
every future API use or external plugin follows the contract.

Still outside the demonstrated guarantee:

1. **JIT ownership:** CP-A4 and JIT cache/name fixes remain in the archive for the JIT branch.
2. **Full container models:** automatic Lightweight application sharing and other models need
   separate implementation and lifetime/type-system tests.
3. **Definition generations:** module identity/presence is not a stable generation identity.
   No cross-generation/global metadata cache or same-name replacement guarantee is introduced.
4. **General concurrency:** no concurrent compiler use or arbitrary shared mutable metadata use
   is enabled. Thread-local bindings, independent copy guards and specific synchronized caches
   do not prove whole-pool thread safety.
5. **Coverage gaps explicitly named above:** individual property/super-signature/argument helper
   paths, injected TypeInfo recursion failure, concurrent shared singleton construction and
   cross-container runtime annotation execution do not all have standalone regressions.
6. **Retention bounds:** state/alias assertions do not substitute for heap/classloader profiling
   or a defined retained-memory budget for a long-lived host.

These limits must stay visible in the architecture and submission plans. None warrants describing an observed
failure as fixed without a test, or a passing combined test as proof of untested concurrency.

## Rules for subsequent changes

1. State whether an operation reads a declaration, constructs for a destination, or obtains a
   live value. Use that owner/context explicitly; do not guess from the current thread.
2. Use the result of registration. Preserve an unchanged foreign input when adoption is disallowed.
3. On copying metadata, separate portable definition data from caches, compiler objects, recursion
   guards and live values. Keep valid canonical same-owner state.
4. For singletons, select the defining container and canonical constant before inspecting a handle,
   then initialize in that owner's service context. Definition compatibility alone is insufficient.
5. Pass a non-null request listener. Reusable metadata may retain diagnostic values, not a host sink.
6. Scope compatibility bindings on the executing thread. A dispatched task opens its own scope;
   shared object publication and synchronization remain separate responsibilities.
7. Test absent and unrelated ambient bindings, source preservation, destination identity, same-owner
   reuse, failed operations and repeated requests. Label tests' limits rather than implying proof.

## File inventory

The following inventory maps changed production and test files to the catalogue. Paths are relative
to their linked source roots. It includes the extracted implementation and minimal prerequisite;
JIT extraction files are accounted for separately in the handoff above. D04 includes call-site,
diagnostic-site, explicit silence, abort-check, signature, import and accompanying Javadoc changes.

### Production files

Root: `javatools/src/main/java/org/xvm/`.

| File | Catalogue entries |
|---|---|
| [api/EmbeddingSupport.java](../javatools/src/main/java/org/xvm/api/EmbeddingSupport.java) | D06 |
| [api/InterpreterConnector.java](../javatools/src/main/java/org/xvm/api/InterpreterConnector.java) | C10 |
| [api/InterpreterControl.java](../javatools/src/main/java/org/xvm/api/InterpreterControl.java) | D06 |
| [asm/Annotation.java](../javatools/src/main/java/org/xvm/asm/Annotation.java) | D04 |
| [asm/ClassStructure.java](../javatools/src/main/java/org/xvm/asm/ClassStructure.java) | C02, D04 |
| [asm/Component.java](../javatools/src/main/java/org/xvm/asm/Component.java) | D04 |
| [asm/ComponentResolver.java](../javatools/src/main/java/org/xvm/asm/ComponentResolver.java) | D04 |
| [asm/Constant.java](../javatools/src/main/java/org/xvm/asm/Constant.java) | C01, C11 |
| [asm/ConstantPool.java](../javatools/src/main/java/org/xvm/asm/ConstantPool.java) | C01, C03, C10 |
| [asm/ErrorList.java](../javatools/src/main/java/org/xvm/asm/ErrorList.java) | D01 |
| [asm/ErrorListener.java](../javatools/src/main/java/org/xvm/asm/ErrorListener.java) | D01 |
| [asm/FileStructure.java](../javatools/src/main/java/org/xvm/asm/FileStructure.java) | C04, D03; copy/merge contract documentation |
| [asm/MethodStructure.java](../javatools/src/main/java/org/xvm/asm/MethodStructure.java) | C05, C16, D04 |
| [asm/PropertyStructure.java](../javatools/src/main/java/org/xvm/asm/PropertyStructure.java) | C02, D04 |
| [asm/Reporting.java](../javatools/src/main/java/org/xvm/asm/Reporting.java) | D02 |
| [asm/XvmStructure.java](../javatools/src/main/java/org/xvm/asm/XvmStructure.java) | D03; owning-pool accessor contract |
| [asm/constants/AnnotatedTypeConstant.java](../javatools/src/main/java/org/xvm/asm/constants/AnnotatedTypeConstant.java) | C19 |
| [asm/constants/ByteConstant.java](../javatools/src/main/java/org/xvm/asm/constants/ByteConstant.java) | C11 |
| [asm/constants/DynamicFormalConstant.java](../javatools/src/main/java/org/xvm/asm/constants/DynamicFormalConstant.java) | C06 |
| [asm/constants/FSNodeConstant.java](../javatools/src/main/java/org/xvm/asm/constants/FSNodeConstant.java) | C15 |
| [asm/constants/FileStoreConstant.java](../javatools/src/main/java/org/xvm/asm/constants/FileStoreConstant.java) | C15 |
| [asm/constants/HandleConstant.java](../javatools/src/main/java/org/xvm/asm/constants/HandleConstant.java) | C19 |
| [asm/constants/IdentityConstant.java](../javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java) | C07, C13 |
| [asm/constants/ImmutableTypeConstant.java](../javatools/src/main/java/org/xvm/asm/constants/ImmutableTypeConstant.java) | D04 |
| [asm/constants/IntConstant.java](../javatools/src/main/java/org/xvm/asm/constants/IntConstant.java) | C11 |
| [asm/constants/MethodBody.java](../javatools/src/main/java/org/xvm/asm/constants/MethodBody.java) | C10 |
| [asm/constants/MethodConstant.java](../javatools/src/main/java/org/xvm/asm/constants/MethodConstant.java) | C07 |
| [asm/constants/MethodInfo.java](../javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java) | C09 |
| [asm/constants/ParameterizedTypeConstant.java](../javatools/src/main/java/org/xvm/asm/constants/ParameterizedTypeConstant.java) | C08, C13 |
| [asm/constants/PropertyClassTypeConstant.java](../javatools/src/main/java/org/xvm/asm/constants/PropertyClassTypeConstant.java) | C13 |
| [asm/constants/PropertyConstant.java](../javatools/src/main/java/org/xvm/asm/constants/PropertyConstant.java) | C07, C13 |
| [asm/constants/PropertyInfo.java](../javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java) | C10 |
| [asm/constants/RegisterConstant.java](../javatools/src/main/java/org/xvm/asm/constants/RegisterConstant.java) | C06 |
| [asm/constants/SignatureConstant.java](../javatools/src/main/java/org/xvm/asm/constants/SignatureConstant.java) | C02, C13 |
| [asm/constants/SingletonConstant.java](../javatools/src/main/java/org/xvm/asm/constants/SingletonConstant.java) | C15 |
| [asm/constants/TerminalTypeConstant.java](../javatools/src/main/java/org/xvm/asm/constants/TerminalTypeConstant.java) | C02 |
| [asm/constants/TypeCollector.java](../javatools/src/main/java/org/xvm/asm/constants/TypeCollector.java) | C12 |
| [asm/constants/TypeConstant.java](../javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java) | C02, C03, C13, D04, D05; preserved relation-pool contract |
| [asm/constants/TypeInfo.java](../javatools/src/main/java/org/xvm/asm/constants/TypeInfo.java) | D05 |
| [asm/constants/TypeInfoReal.java](../javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java) | C02, D04 |
| [asm/constants/TypeParameterConstant.java](../javatools/src/main/java/org/xvm/asm/constants/TypeParameterConstant.java) | C13 |
| [asm/constants/UnresolvedTypeConstant.java](../javatools/src/main/java/org/xvm/asm/constants/UnresolvedTypeConstant.java) | D04 |
| [compiler/Compiler.java](../javatools/src/main/java/org/xvm/compiler/Compiler.java) | D01, D03, D04 |
| [compiler/EvalCompiler.java](../javatools/src/main/java/org/xvm/compiler/EvalCompiler.java) | D01, D04 |
| [compiler/Lexer.java](../javatools/src/main/java/org/xvm/compiler/Lexer.java) | D01, D04 |
| [compiler/Parser.java](../javatools/src/main/java/org/xvm/compiler/Parser.java) | D01, D02, D04 |
| [compiler/Source.java](../javatools/src/main/java/org/xvm/compiler/Source.java) | D04 |
| [compiler/Token.java](../javatools/src/main/java/org/xvm/compiler/Token.java) | D04 |
| [compiler/ast/AnnotationExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/AnnotationExpression.java) | D04 |
| [compiler/ast/AnonInnerClass.java](../javatools/src/main/java/org/xvm/compiler/ast/AnonInnerClass.java) | D04 |
| [compiler/ast/ArrayAccessExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java) | C02, D04 |
| [compiler/ast/AsExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/AsExpression.java) | D04 |
| [compiler/ast/AssignmentStatement.java](../javatools/src/main/java/org/xvm/compiler/ast/AssignmentStatement.java) | D04 |
| [compiler/ast/AstNode.java](../javatools/src/main/java/org/xvm/compiler/ast/AstNode.java) | C02, D04 |
| [compiler/ast/CaseManager.java](../javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java) | D04 |
| [compiler/ast/CmpChainExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/CmpChainExpression.java) | C11, D04 |
| [compiler/ast/CmpExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/CmpExpression.java) | C11, D04 |
| [compiler/ast/Context.java](../javatools/src/main/java/org/xvm/compiler/ast/Context.java) | D04 |
| [compiler/ast/ElseExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/ElseExpression.java) | D04 |
| [compiler/ast/ElvisExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/ElvisExpression.java) | D04 |
| [compiler/ast/Expression.java](../javatools/src/main/java/org/xvm/compiler/ast/Expression.java) | D04 |
| [compiler/ast/ForEachStatement.java](../javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java) | D02, D04 |
| [compiler/ast/ForStatement.java](../javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java) | D02, D04 |
| [compiler/ast/InvocationExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java) | D04 |
| [compiler/ast/LambdaExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java) | D04 |
| [compiler/ast/ListExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/ListExpression.java) | D04 |
| [compiler/ast/MapExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/MapExpression.java) | D04 |
| [compiler/ast/MethodDeclarationStatement.java](../javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java) | D04 |
| [compiler/ast/NameExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java) | D04 |
| [compiler/ast/NameResolver.java](../javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java) | D02, D04 |
| [compiler/ast/NamedTypeExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java) | D04 |
| [compiler/ast/NewExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java) | D04 |
| [compiler/ast/NonBindingExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/NonBindingExpression.java) | D04 |
| [compiler/ast/NotNullExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/NotNullExpression.java) | D04 |
| [compiler/ast/ParenthesizedExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/ParenthesizedExpression.java) | D04 |
| [compiler/ast/PrefixExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/PrefixExpression.java) | C02 |
| [compiler/ast/RelOpExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/RelOpExpression.java) | C02, C11, D04 |
| [compiler/ast/ReturnStatement.java](../javatools/src/main/java/org/xvm/compiler/ast/ReturnStatement.java) | D04 |
| [compiler/ast/SequentialAssignExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/SequentialAssignExpression.java) | D04 |
| [compiler/ast/StageMgr.java](../javatools/src/main/java/org/xvm/compiler/ast/StageMgr.java) | D04 |
| [compiler/ast/StatementBlock.java](../javatools/src/main/java/org/xvm/compiler/ast/StatementBlock.java) | D04 |
| [compiler/ast/StatementExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java) | D04 |
| [compiler/ast/TemplateExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/TemplateExpression.java) | D04 |
| [compiler/ast/TernaryExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/TernaryExpression.java) | D04 |
| [compiler/ast/TraceExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/TraceExpression.java) | D04 |
| [compiler/ast/TryStatement.java](../javatools/src/main/java/org/xvm/compiler/ast/TryStatement.java) | D02, D04 |
| [compiler/ast/TupleExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/TupleExpression.java) | D04 |
| [compiler/ast/TypeCompositionStatement.java](../javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java) | D04 |
| [compiler/ast/TypeExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/TypeExpression.java) | D04 |
| [compiler/ast/UnaryComplementExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/UnaryComplementExpression.java) | C11, D04 |
| [compiler/ast/UnaryMinusExpression.java](../javatools/src/main/java/org/xvm/compiler/ast/UnaryMinusExpression.java) | C11, D04 |
| [compiler/ast/ValidationScope.java](../javatools/src/main/java/org/xvm/compiler/ast/ValidationScope.java) | D02 |
| [compiler/ast/WhileStatement.java](../javatools/src/main/java/org/xvm/compiler/ast/WhileStatement.java) | D02, D04 |
| [runtime/ConstHeap.java](../javatools/src/main/java/org/xvm/runtime/ConstHeap.java) | C16, C17 |
| [runtime/Container.java](../javatools/src/main/java/org/xvm/runtime/Container.java) | C16; owner/template contract documentation |
| [runtime/Frame.java](../javatools/src/main/java/org/xvm/runtime/Frame.java) | C16 prerequisite: exception cleanup and external-waiter readiness |
| [runtime/NativeContainer.java](../javatools/src/main/java/org/xvm/runtime/NativeContainer.java) | C14 |
| [runtime/ServiceContext.java](../javatools/src/main/java/org/xvm/runtime/ServiceContext.java) | C16 prerequisite: dispatch exception cleanup |
| [runtime/Utils.java](../javatools/src/main/java/org/xvm/runtime/Utils.java) | C16 |
| [runtime/template/_native/lang/src/xRTCompiler.java](../javatools/src/main/java/org/xvm/runtime/template/_native/lang/src/xRTCompiler.java) | D04 |
| [runtime/template/_native/mgmt/xContainerLinker.java](../javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xContainerLinker.java) | C18 |
| [runtime/template/_native/reflect/xRTType.java](../javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java) | D04 |
| [runtime/template/_native/reflect/xRTTypeTemplate.java](../javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTTypeTemplate.java) | C19 |
| [tool/Bundler.java](../javatools/src/main/java/org/xvm/tool/Bundler.java) | D01, D04, D06; explicit listener constructors/defaults |
| [tool/Compiler.java](../javatools/src/main/java/org/xvm/tool/Compiler.java) | D01, D04, D06; explicit listener constructors/defaults |
| [tool/Disassembler.java](../javatools/src/main/java/org/xvm/tool/Disassembler.java) | D01, D04, D06; explicit listener constructors/defaults |
| [tool/Initializer.java](../javatools/src/main/java/org/xvm/tool/Initializer.java) | D01, D04, D06; explicit listener constructors/defaults |
| [tool/Launcher.java](../javatools/src/main/java/org/xvm/tool/Launcher.java) | D01, D04, D06; explicit listener constructors/defaults |
| [tool/ModuleInfo.java](../javatools/src/main/java/org/xvm/tool/ModuleInfo.java) | D01, D04, D06; explicit listener constructors/defaults |
| [tool/Runner.java](../javatools/src/main/java/org/xvm/tool/Runner.java) | D01, D04, D06; explicit listener constructors/defaults |
| [tool/TestRunner.java](../javatools/src/main/java/org/xvm/tool/TestRunner.java) | D01, D04, D06; explicit listener constructors/defaults |

### Changed and new tests

| File | Catalogue entries |
|---|---|
| [javatools/java/org/xvm/api/EmbeddingListenerContractTest.java](../javatools/src/test/java/org/xvm/api/EmbeddingListenerContractTest.java) | D06 |
| [javatools/java/org/xvm/asm/ConstantOwnershipTest.java](../javatools/src/test/java/org/xvm/asm/ConstantOwnershipTest.java) | C03, C06, C13, C15, C19 |
| [javatools/java/org/xvm/asm/ConstantPoolAmbientTest.java](../javatools/src/test/java/org/xvm/asm/ConstantPoolAmbientTest.java) | C01, C11 |
| [javatools/java/org/xvm/asm/ConstantPoolScopeTest.java](../javatools/src/test/java/org/xvm/asm/ConstantPoolScopeTest.java) | C01 |
| [javatools/java/org/xvm/asm/ErrorDeduplicationTest.java](../javatools/src/test/java/org/xvm/asm/ErrorDeduplicationTest.java) | D01 |
| [javatools/java/org/xvm/asm/ErrorListenerAbortTest.java](../javatools/src/test/java/org/xvm/asm/ErrorListenerAbortTest.java) | D01 |
| [javatools/java/org/xvm/asm/ErrorListenerBranchTest.java](../javatools/src/test/java/org/xvm/asm/ErrorListenerBranchTest.java) | D01 |
| [javatools/java/org/xvm/asm/ErrorListenerCancelTest.java](../javatools/src/test/java/org/xvm/asm/ErrorListenerCancelTest.java) | D01 |
| [javatools/java/org/xvm/asm/ErrorListenerSilenceTest.java](../javatools/src/test/java/org/xvm/asm/ErrorListenerSilenceTest.java) | D01 |
| [javatools/java/org/xvm/asm/ErrorListenerSiteTest.java](../javatools/src/test/java/org/xvm/asm/ErrorListenerSiteTest.java) | D01 |
| [javatools/java/org/xvm/asm/FileStructureErrorListenerTest.java](../javatools/src/test/java/org/xvm/asm/FileStructureErrorListenerTest.java) | D01, D03, D05 |
| [javatools/java/org/xvm/asm/FileStructureOwnershipTest.java](../javatools/src/test/java/org/xvm/asm/FileStructureOwnershipTest.java) | C04, C05 |
| [javatools/java/org/xvm/asm/RegistrationOwnershipTest.java](../javatools/src/test/java/org/xvm/asm/RegistrationOwnershipTest.java) | C03, C11, C13 |
| [javatools/java/org/xvm/asm/ReportingTest.java](../javatools/src/test/java/org/xvm/asm/ReportingTest.java) | D02 |
| [javatools/java/org/xvm/asm/constants/DestinationOwnershipTest.java](../javatools/src/test/java/org/xvm/asm/constants/DestinationOwnershipTest.java) | C07, C08, C09 |
| [javatools/java/org/xvm/asm/constants/MethodBodyAmbientPoolTest.java](../javatools/src/test/java/org/xvm/asm/constants/MethodBodyAmbientPoolTest.java) | C10 |
| [javatools/java/org/xvm/compiler/CompilerDiagnosticsTest.java](../javatools/src/test/java/org/xvm/compiler/CompilerDiagnosticsTest.java) | D01, D02, D04 |
| [javatools/java/org/xvm/compiler/ParserAttemptTest.java](../javatools/src/test/java/org/xvm/compiler/ParserAttemptTest.java) | D02 |
| [javatools/java/org/xvm/runtime/ConstHeapOwnershipTest.java](../javatools/src/test/java/org/xvm/runtime/ConstHeapOwnershipTest.java) | C17 |
| [javatools/java/org/xvm/runtime/ExternalCompletionTest.java](../javatools/src/test/java/org/xvm/runtime/ExternalCompletionTest.java) | C16 prerequisite: waiter completes after external readiness |
| [javatools/java/org/xvm/runtime/FrameExceptionCleanupTest.java](../javatools/src/test/java/org/xvm/runtime/FrameExceptionCleanupTest.java) | C16 prerequisite: cleanup on failure, disarm on success, composed continuations |
| [javatools/java/org/xvm/runtime/SingletonOwnershipTest.java](../javatools/src/test/java/org/xvm/runtime/SingletonOwnershipTest.java) | C15, C16 |
| [javatools/java/org/xvm/tool/BundlerTest.java](../javatools/src/test/java/org/xvm/tool/BundlerTest.java) | D06; migrate explicit listener arguments |
| [javatools/java/org/xvm/tool/LauncherErrorHandlingTest.java](../javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java) | D06; migrate explicit listener arguments |
| [javatools/java/org/xvm/tool/LauncherVersionTest.java](../javatools/src/test/java/org/xvm/tool/LauncherVersionTest.java) | D06; migrate explicit listener arguments |
| [javatools/java/org/xvm/tool/ModuleInfoTest.java](../javatools/src/test/java/org/xvm/tool/ModuleInfoTest.java) | D06; migrate explicit listener arguments |
| [xdk/java/org/xvm/xdk/ConstantPoolOwnershipTest.java](../xdk/src/test/java/org/xvm/xdk/ConstantPoolOwnershipTest.java) | C14, C16, C18, D05 |
| [xdk/java/org/xvm/xdk/SignatureCompatibilityTest.java](../xdk/src/test/java/org/xvm/xdk/SignatureCompatibilityTest.java) | C02, C12 |
| [xdk/resources/ownership/Singletons.x](../xdk/src/test/resources/ownership/Singletons.x) | C17, C18; executed by XDK ConstantPoolOwnershipTest |

This inventory accounts for **111 production files and 29 test/resource files**.
Existing supporting tests named in the catalogue but unchanged by these ownership commits are
not counted as newly modified tests. Documentation on this branch is this file and the separation
architecture plan. The larger embedding plans and JIT archive documentation remain on their
original branches.
