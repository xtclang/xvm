# Compiler AST And Context Mutation Audit

Record-only inventory for must-audit backlog row 268
(`must-audit-backlog.md:268`, "Compiler AST and context mutation", classified
2026-08-25 as compiler-scoped record-only). This is the dedicated home the
board row points at as its evidence target. It supersedes and expands the
narrower embedded table in
[compiler-lexer-parser-this-escape.md](compiler-lexer-parser-this-escape.md)
("Compiler AST And Context Mutation Audit (2026-08-25)", which closed the
older row 175); that section stays valid but is a subset of what follows.

## Scope And Policy

This document inventories the compiler-side (`org.xvm.compiler` and
`org.xvm.compiler.ast`) mutable state that assumes single-request,
single-thread compilation and would break under incremental or parallel
compilation. It is **record only**: nothing here is fixed on this
runtime/ownership branch. Compiler-side fixes are deferred by policy to a
separate compiler-reentrancy branch — the interpreter runtime and the compiler
are disjoint worlds over the shared `org.xvm.asm` structures, and no runtime or
ASM owner leak graduated out of this audit.

Classification framework, per surface:

- **ALWAYS-broken-under-incremental/parallel**: reusing the state across a
  second compile request (even sequentially, same JVM, same thread) already
  yields a wrong or stale result unless the AST is rebuilt/cloned per request.
- **CONCURRENCY-ONLY**: correct for one sequential request that owns its AST,
  but broken if two threads validate/resolve the same shared AST node
  concurrently (unsynchronized plain-`HashMap`/`ArrayList`/field mutation).

Every surface below is at minimum CONCURRENCY-broken, because none of the
mutable state uses synchronization or concurrent collections; the axis that
varies is whether sequential cross-request reuse is *also* already broken.

## Why This Is A Real Category: The Fixed-Point Pass Model

The compiler drives each phase as a fixed-point loop over the **same** AST node
instances. `Compiler.resolveNames(boolean)`
(`javatools/src/main/java/org/xvm/compiler/Compiler.java:181`),
`Compiler.validateExpressions(boolean)` (`Compiler.java:223`), and
`Compiler.generateCode(boolean)` (`Compiler.java:265`) each carry the contract
"this method MUST be called again" until it returns true, re-walking the module
AST through a `StageMgr` (`m_mgr = new StageMgr(m_stmtModule, ...)` at
`Compiler.java:195`, `:237`, `:279`). Nodes advance through a `Stage` enum
(`Resolving`→`Resolved`, `Validating`→`Validated`, `Emitting`→`Emitted`) and
defer/retry.

The consequence: a single compile request revisits each node many times and
accumulates/mutates transient scratch fields on it across passes. That is safe
only while (a) exactly one request owns the AST graph and (b) exactly one thread
walks it. Both are implicit, unencoded preconditions. Incremental compilation
breaks (a); parallel compilation breaks (b). The AST `clone()` methods copy the
transient scratch **by reference** (see each surface), so a clone-based reuse
strategy carries stale per-pass state forward unless it explicitly clears it.

## Inventory

### Surface 1 — `Context` name / narrowing / assignment maps

File: `javatools/src/main/java/org/xvm/compiler/ast/Context.java`.

Mutable per-`Context` state, all lazily created plain `HashMap`s with no
synchronization:

- `m_ctxOuter` (`:2718`) — outer-context link forming the request's context chain.
- `m_mapByName` (`:2735`) — name → `Argument`; created by `ensureNameMap()`
  (`:1201-1205`), mutated by `replaceArguments(...)` (`:1315`, `:1330`),
  register/unregister (`:764`, `:771`), and formal-type narrowing (`:1364`).
- `m_mapWhenTrue` / `m_mapWhenFalse` (`:2741` / `:2747`) — branch narrowing
  maps via `ensureNarrowingMap(boolean)` (`:1440-1444`).
- `m_mapFormal` / `m_mapFormalWhenTrue` / `m_mapFormalWhenFalse`
  (`:2753` / `:2759` / `:2765`) — formal-type maps via
  `ensureFormalTypeMap(Branch)` (`:1470-1477`).
- `m_mapAssigned` (`:2771`) — definite-assignment map via
  `ensureDefiniteAssignments()` (`:1131-1134`), merged by `merge(...)`
  (`:514`, `:524`) and `prepareJump(...)` (`:436`, `:457`).
- `m_regThis` (`:2776`); nested branching contexts `m_ctxWhenTrue` /
  `m_ctxWhenFalse` (`:1965` / `:1966`) and `m_fExclusive` (`:2042`).
- `CaptureContext` inner class: `m_mapCapture` (`:2670`), `m_mapRegisters`
  (`:2675`), `m_mapFormalInfo` (`:2681`), `m_fCaptureThis` (`:2686`) — lambda
  variable-capture bookkeeping.

Classification: **CONCURRENCY-ONLY.** These maps are rebuilt within a single
validation walk and are owned by that walk's `Context` chain, so sequential
cross-request reuse is not the primary hazard (a fresh validation constructs a
fresh chain). Two threads narrowing/assigning the same shared `Context`
concurrently corrupt the plain `HashMap`s and race branch state.

### Surface 2 — `NameResolver` staged resolution state

File: `javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java`.

A single resolver is a mutable state machine advancing one AST name through the
`Stage` enum (`CHECK_IMPORTS` → `RESOLVE_FIRST_NAME` → `RESOLVE_DOT_NAME` →
`RESOLVE_TURTLES` → `RESOLVED`/`ERROR`). Mutable fields: `m_sName` (`:813`),
`m_stage` (`:818`, initial `Stage.CHECK_IMPORTS`), `m_stmtImport` (`:823`),
`m_blockImport` (`:828`), `m_constantFirst` (`:834`), `m_constant` (`:839`),
`m_component` (`:844`), `m_typeMode` (`:859`), `m_errs` (`:864`). They are
advanced by `resolve(...)`/`forceResolve(...)` (`:121-339`). Only `m_node`
(`:803`), `m_iter` (`:808`), and `m_fTypeGoal` (`:853`) are final.

Crucially, resolvers are **cached on AST nodes** and reused across passes:
`ImportStatement.m_resolver` (transient, `ImportStatement.java:266`) is lazily
created and cached by `getNameResolver()` (`ImportStatement.java:140-151`) and
copied by reference in `clone()` (`ImportStatement.java:285`). Some callers
instead build a fresh resolver per call (`AnnotationExpression.java:536`).

Classification: **ALWAYS-broken-under-incremental** (and CONCURRENCY-broken).
Because the staged resolution result is memoized on the AST node, a second
request that reuses (or naively clones) that node observes an
already-`RESOLVED`/`ERROR` resolver bound to the first request's constants,
components, and `ErrorListener` — wrong even single-threaded. Concurrently, two
threads driving one cached resolver race `m_stage`/`m_constant`.

### Surface 3 — `InvocationExpression` validation / codegen target caches

File: `javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java`.

Transient fields assigned during validation and read in later type/codegen
paths: `m_fBindTarget` (`:3054`), `m_targetInfo` (`:3060`), `m_argMethod`
(`:3061`), `m_method` (`:3062`), `m_typeTarget` (`:3064`). `m_method` is set
from the resolved method (`:2314`) and read at `:402`, `:764`, `:1242`,
`:2535`.

Classification: **CONCURRENCY-ONLY** primarily. The cache is populated and
consumed within one validate→generate sequence for one request; a fresh request
re-validates and re-derives it. It becomes ALWAYS-broken only if an AST node is
reused across requests without re-running validation. Two threads validating one
shared `InvocationExpression` under different contexts corrupt the target
selection.

### Surface 4 — `LambdaExpression` generated-method cache

File: `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java`.

`m_lambda` (transient `MethodStructure`, `:1516`) caches the `MethodStructure`
synthesized for the lambda. Validation asserts it is null, creates it, and later
uses it for component lookup / codegen; `clone()` deliberately clears it (it
already acknowledges clone/retry sensitivity).

Classification: **ALWAYS-broken-under-incremental** (and CONCURRENCY-broken).
The cache produces a container/pool-owned `MethodStructure`; reusing the node
across requests would reattach a stale generated method to a new request's
structure graph. The intentional clone-clears-`m_lambda` behavior is exactly the
"rebuild per pass" mitigation and confirms the state is request-scoped by design.

### Surface 5 — break / continue / short-circuit assignment lists

Transient control-flow validation scratch, populated during validation and
drained/discarded, copied by reference in `clone()`:

- `Statement.m_listBreaks` (`Statement.java:288`) — added at `:88-91`, iterated
  and consumed at `:147-148`, presence-checked at `:98`/`:218`; clone copies the
  reference (`:304`).
- `ForStatement.m_listContinues` / `m_listShorts` (`ForStatement.java:736` /
  `:741`) — added at `:88-91`, drained and nulled at `:414-427`; clone copies
  (`:772`).
- `ForEachStatement.m_listContinues` (`ForEachStatement.java:1381`) — added at
  `:92-97`, drained at `:526-528`; clone copies (`:1410`).
- `IfStatement.m_listShorts` (`IfStatement.java:398`).
- `SwitchStatement.m_listContinues` (`SwitchStatement.java:677`, asserted null
  at `:63`) and `m_listBreaks` (`List<Map<String,Assignment>>`,
  `SwitchStatement.java:685`).

Each `Break` record carries the merged assignment/narrowing maps captured at the
break/continue site.

Classification: **CONCURRENCY-ONLY.** These lists are built and consumed within a
single validation pass of the owning statement and reset between uses, so a
sequential fresh request rebuilds them cleanly; the clone-copies-reference shape
is a latent incremental hazard only if a partially validated statement is
cloned. Two threads validating the same loop/switch concurrently corrupt the
`ArrayList`s.

### Surface 6 — label / jump-target scratch and label map

Transient codegen label state, copied by reference in `clone()`:

- `CaseStatement.m_label` (`CaseStatement.java:170`) — asserted null then set at
  `:72-73`; clone copies (`:186`).
- `AssignmentStatement.m_labelCondFalse` (`AssignmentStatement.java:1167`); clone
  copies (`:1189`).
- `SwitchStatement.m_labelContinue` (`SwitchStatement.java:670`), plus the
  switch's `m_casemgr` (`:658`) and `m_listGroups` (`:664`).
- `CaseManager` label map: `m_mapLabels` (`ListMap<Label, CookieType>`,
  `CaseManager.java:1354`, populated at `:417-418`), `m_labelCurrent` (`:1324`),
  `m_labelDefault` (`:1329`), `m_labelConstant` (`:1334`).

Classification: **CONCURRENCY-ONLY** for the per-node labels (rebuilt during one
emit pass), trending **ALWAYS-broken-under-incremental** for `CaseManager`'s
accumulated `m_mapLabels` if the manager is reused across requests. All are
unsynchronized.

## Distinct Surface Count And Verdicts

Six distinct compiler-mutation surfaces:

| # | Surface | Key sites | Timing verdict |
| --- | --- | --- | --- |
| 1 | `Context` name/narrowing/assignment maps | `Context.java:2718-2776`, `:2670-2686` | CONCURRENCY-ONLY |
| 2 | `NameResolver` staged state (AST-cached) | `NameResolver.java:813-864`; `ImportStatement.java:140-151,266,285` | ALWAYS-under-incremental + concurrency |
| 3 | `InvocationExpression` target caches | `InvocationExpression.java:3054-3064` | CONCURRENCY-ONLY |
| 4 | `LambdaExpression.m_lambda` method cache | `LambdaExpression.java:1516` | ALWAYS-under-incremental + concurrency |
| 5 | break/continue/short-circuit lists | `Statement.java:288`; `ForStatement.java:736,741`; `ForEachStatement.java:1381`; `SwitchStatement.java:677,685` | CONCURRENCY-ONLY |
| 6 | label/jump-target scratch + label map | `CaseStatement.java:170`; `AssignmentStatement.java:1167`; `CaseManager.java:1354` | CONCURRENCY-ONLY (CaseManager trends ALWAYS) |

## Required Closure (High Level)

Deferred to the compiler-reentrancy branch. At a high level the branch must make
each compile request own its AST/`Context` graph and hold no shared mutable AST,
by one of:

1. **Prove one AST/`Context` graph per request** — no cross-request or
   cross-thread sharing of any AST node, so the transient scratch is trivially
   confined. This is the current implicit model; the closure is to make it
   explicit and enforced.
2. **Move validation/codegen scratch to request-owned context state** — carry
   `NameResolver` results, invocation/lambda caches, break/continue lists, and
   labels in a per-request structure keyed by node, rather than as mutable
   transient fields on shared AST nodes.
3. **Rebuild or clone the AST per concurrent validation pass** — with the clone
   explicitly clearing (not reference-copying) the transient scratch, as
   `LambdaExpression.clone()` already does for `m_lambda`.

No shared mutable AST across requests or threads. This is an inventory and
classification only; it names no fix for this branch and graduates no
runtime/ASM must-fix.
