# Failures with nowhere to go

Upstream defects and compatibility bridges are tracked in [errs-upstream-issues.md](errs-upstream-issues.md).

**Current watch/rename audit:** missing external roots need a flat watch at their nearest existing
ancestor as well as the eventual recursive root. Delayed Created events for already compiled inputs
must not cancel rename proofs; source membership/text and resource fingerprints now distinguish
those no-op events from real changes. The comparison is bounded and uses no compiler API. Failed
current analyses always invalidate, even if recreated files match an older successful build. A
vanished module container also retains a missing resource input instead of throwing during lookup.
Deterministic paused-proof and recreation regressions pass in the latest 24-test selection; all 72
packaged protocol checks pass. X124/X134 pass in VS Code without manual refresh.

**Current editor acceptance:** the VS Code selection passes across `run-iGx1M2` (18 cases) and
`run-YMDUeW` (corrected X130 selection). The latter verifies both source folders before native
Cut/Paste, then one Undo/Redo. An earlier run exposed an upstream Explorer `Data tree node not found`
error after the physical move; that failed receipt and manual follow-up remain recorded. Native
IntelliJ passes the same selection across `run-17174738471798629344` and
`run-1746762976235942700`, plus START, with zero IDE errors. The shared catalog has 140 cases;
these are selected receipts.
See [the diagnoses and validation record](errs-integration-plan.md#watcher-move-and-log-view-acceptance-follow-up-2026-09-30).

**Native Move acceptance correction:** the manifest registered `moveHandler` instead of the
actual `refactoring.moveHandler`; a new regression checks IntelliJ's extension-point constant.
The registered handler now reaches compiler proof before mutation. X130 also needs project-level
Undo for a file-only move, not the unchanged Consumer editor's history. Its previous 45-second wait
was a harness timeout after a 389 ms proof; corrected Move/Undo/Redo takes 4.98 seconds.

**Previous native audit:** X118's indefinite Rename was a lock cycle between the IDE write lock,
LSP4IJ's synchronous file-operation listener and diagnostic refresh acquiring a read lock on the
transport worker. Ordered shared-pool cache updates fixed it. X128 preflights at the host action
because VFS before-events arrive after physical Rename. The temporary eager-action compatibility
fix is now superseded by the selected lazy-action bridge, which uses a normal undo command.

**Earlier hardening batch validated:** L12 fixes anonymous declaration token overlap; L16/L27
add project source-graph settings; L68 adds negotiated pull diagnostics, including closed roots
and result IDs. The combined run passes 861 compiler/backend, 54 packaged-stdio and 50 IntelliJ
unit tests (965 total), with zero failures/errors/skips. Updated X76/X118 and new X123 pass in both
editors; IntelliJ reports zero IDE failures. This is selected acceptance, not a full 128-case
catalog run. See the [batch receipt](errs-integration-plan.md#l12-project-settings-and-l62l68-hardening-batch-2026-09-29)
and the newly found [real-platform blockers](errs-integration-plan.md#platform-demo-blockers-2026-09-29).

**Pull-diagnostics interop failure:** the first new IntelliJ acceptance run exposed an LSP4J
1.0.0 decoder ambiguity in the nested `relatedDocuments` map. It terminated the JSON-RPC reader,
leaving Rename waiting for an unread reply and causing subsequent restarts. Thread captures showed
idle UI/compiler threads and full client pipes; the completed listener future supplied the actual
`Ambiguous Either type` exception. The launcher now registers the missing report-kind distinction;
protocol regressions reproduce the failure without it and pass with it. Native X76/X118/X123 now
pass, including Rename/Undo/Redo and error/clear delivery. This was a client decoding failure,
not expensive rename proof.

**Real-platform demo fixes:** `CompositionNode.getSource` now handles a typeless default before
parent adoption; module resource roots participate in lookup, cache identity and watching. The
rebuilt packaged server returns 49 clean reports across eleven platform modules after explicitly
configuring platformUI's build-defined `gui/dist` resources. Hover, signature parameter mapping,
chained member completion and narrowed JSON-object type navigation also pass real-source rechecks.
See [PLAT1–PLAT3](errs-integration-plan.md#platform-demo-blockers-2026-09-29) for commits, focused
regressions and the combined validation receipt. Evaluated build-model import, richer paths/origins
controls and new native acceptance remain tracked follow-ups.

**L64 validation:** the first combined run exposed the required literal record-pattern
update, old exact-completion expectations and optional TypeScript metadata. New regressions exposed
an invalid type-goal resolver owner and missing mid-token value recovery; fixes reuse real lexical
scope and existing parser recovery boundaries. Method documentation needs a passive accessor to
the existing AST comment because method components do not copy it today. No diagnostic suppression
or AST cache was added. See the [batch record](errs-integration-plan.md#l64-completion-and-signature-batch-2026-09-29)
for the corrected 337-test passing receipt and selected editor results. Initial development commits
are not independently green extraction units; their validation corrections must accompany extraction.

**Repair identity comparison:** an unresolved method signature can still expose a written parameter
before the compiler has a resolved method slot. After a public-type auto-import, the same parameter
has both identities. Comparing source-declaration identity with slot identity falsely rejected the
valid `Document echo(Document doc) = doc` repair. Repair proofs now normalize written parameters
to their declaration on both sides; generated/composed slots and normal rename retain their slot
proof. The existing bundled-import regression exposes this mismatch.

**Partial AST packaging:** four syntax nodes now live in `ast.partial`; stateless `PartialQueries`
connects them to the package-private validation/inference helpers that remain in `ast`. Child-field
registration supports traversal, cloning and edits without public representation fields. The
[checkpoint map](errs-integration-plan.md#partial-ast-implementation-checkpoints) records validation
and the two override modifiers corrected during compilation. The
[broader inventory](errs.md#broader-ast-placement-inventory) classifies all 61 changed AST files;
AST5 now shares read-only cursor/argument syntax queries through `partial.PartialSyntax`, leaving
trial parenting and semantic validation in the root package. AST1 and AST3 now consolidate cursor
scope collection and anonymous-property projection in their existing package-private helper
methods. AST2 and AST4 remain conditional investigations, not completed reductions.
Real dispatch fixtures cover Explicit/Default, Delegating and Union; the generated/native/conditional
route inventory remains incomplete.

The corrected P1–P4 batch passes 107 Java and 395 LSP tests (502 total), with zero failures,
errors or skips, plus root/lang Spotless. This is backend package/ownership validation; it does
not replace or extend the existing native-editor receipts. AST5 separately passes 37 Java and
221 LSP tests (258 total), with zero failures/errors/skips, plus root/lang Spotless; see its
[validation record](errs-integration-plan.md#shared-partial-syntax-implementation-ast5).
AST1/AST3 pass a further 4 Java and 196 LSP tests (200 total), with zero failures/errors/skips,
plus root/lang Spotless. The [follow-up receipt and commit map](errs-integration-plan.md#scope-and-capture-helper-follow-ups-ast1-and-ast3)
record unchanged visibility, validation/clone-owner guards and future PR placement.

**Orphan server JVMs:** [the process-lifecycle audit](errs-lsp-process-lifecycle.md) documents
the reproduced Tree-sitter worker leak after EOF, LSP4IJ stop-before-start race and master's
logging-only exit handler. Fixes are isolated in `8e976f868` and `eefc1b8e6`; child-process tests
pass across all three backends. Installed IDE close/restart acceptance remains separate.

L56–L59 combined validation exposed three additional boundaries. Operator `testFit` can return
an optimistic type before validating the missing operand, so argument proposals also validate
each completed expression. Constructor proposals must still follow normal validation order:
class result inference happens afterward and cannot retroactively constrain earlier arguments.
A synthetic `NamedTypeExpression` uses its type's display string for
printing; feeding that string back through name lookup loses lexical qualification. Written-bound
queries now keep real constraint syntax on disposable clones and preserve the compiler restriction
that a formal qualifier cannot expose an ordinary typedef/static nested class. Finally, a resolved
formal identity can still have a cyclic constraint: snapshot nullability recursively followed
`T extends T` and overflowed. The Kotlin copier rejects cycles along constraint edges before
querying nullability, while allowing recursion through concrete generic arguments (`T extends Chain<T>`).
This is a snapshot guard, not evidence that the normal compiler's cyclic-bound handling is complete.
Final combined validation passes: 1,463 executed JVM tests and selected VS Code X42/X97/X108.
Seven existing tests are disabled. Native startup editing subsequently passed its dedicated
five-phase check. X103 exposed and then verified the fix for a startup-hook/VFS rename lock cycle;
the later full IntelliJ checkpoint passes all 113 scenarios with zero IDE errors. Exact receipts
are in the integration plan.

**X103 native freeze:** VFS rename held the EDT write lock while LSP4IJ awaited didOpen; our
transport snapshot callback waited for an IDE read lock. Reading the Document API's immutable
text directly removes that cycle. Both rename directions and startup replacement/reopen now pass.
Queue/API/reply tracing records counts, ordered jobs and timing for further investigations; see the
[native and tracing record](errs-integration-plan.md#native-startup-rename-deadlock-and-execution-tracing-2026-09-28).

Current follow-up tasks are centralized in the
[full compiler LSP completion checklist (L55–L83)](errs-integration-plan.md#full-compiler-lsp-completion-checklist).
L55/L56 track the large-graph proof and startup issues below; L57–L60 track semantic breadth and
native parity. L61–L82 cover feature/protocol omissions and the completion gate. An absent
optional LSP handler is separate from an error-listener defect or a missing native assertion.

L55 acceptance now uses the real generated teaching workspace (currently 25 roots), with one
and five unsaved buffers. It exposed a missing simple delegate-name binding and overly broad
comparison of unsupported bindings in independent modules. The fix uses existing composition
facts and limits equivalence comparison to the edited/consumer closure, while still compiling the
whole graph. L61 adds explicit declaration support; L62 adds proven companion-directory and
simple discovered-module moves. Combined backend validation passes: 1,273 executed server tests
and 67 stdio tests, with three existing server skips. The integration plan records native
acceptance separately and lists the remaining conservative refusals.

Native parity audit (L60): the 50 missing case bodies and X20/X81/X82 assertions are now written,
with all 50 cases and all three strengthened assertion sets passing individually. The full
113-case checkpoint passes in `run-6034631232732848040` with zero IDE errors. Inspection found
two client boundaries that the tests must not hide: LSP4IJ drops diagnostics for URIs without a
virtual file, and its workspace-edit routine
does not inspect document versions. The former needs protocol-trace assertions distinct from
Problems-view claims. X57 reproduced the latter and now passes through a native Ecstasy rename
handler that checks an immutable request snapshot inside the edit's write command. X53/X54/X60
also pass, retaining normal rename behavior. This guards symbol rename; other LSP4IJ edit entry
points and null-version closed-file races are not covered by that fix.
Dependency diagnostic publications now invalidate completed semantic caches, which LSP4IJ
otherwise keys only to the requesting file's PSI stamp. The shared X31/X32 negative expectations
were stale after formatting/refactoring/argument-completion development and are updated in both
IDE drivers. The full native pass closes L60; client limitations remain explicitly documented.
The resumed batch passed all 61 selected scenario assertions but correctly failed the IDE-error
gate on X41's PSI read outside a read action. That test-driver defect is fixed in `6d7e5b7e5`,
together with inactive-overload highlighting expectations and 7a.9's missing fixture. X41/X108
pass with zero IDE errors. A later full run passed 61 scenarios before Starter's default
ten-minute session timeout shut down the IDE during X86; the limit is now 30 minutes. Its rerun
passed 17 scenarios before losing desktop focus at X17. Neither closed the full-run gate.
Popup recovery now restores focus without pointer input and rejects replay after document edits.
The dedicated focus regression and seven selected native scenarios pass without IDE errors;
both harnesses show completed/remaining counts and the current case. The final full run passes
all 113 scenarios, including X30 after correcting its PID-versus-document readiness assumption.
The startup check also confirms that the untouched balloon disappears. The integration plan
records the measured native speedup and each separate acceptance result.

Native open-member rename (L54): advertising and handling `workspace/didRenameFiles` enables
LSP4IJ's file-rename lifecycle. Without that capability, its VFS listener skipped old-URI close
and new-URI open; reversing a rename with the member open retained a duplicate source overlay.
The notification now refreshes both paths, with server diagnostic relocation and native round-trip
regressions. This changes server protocol handling, not compiler/AST ownership.

Large-graph refactoring follow-up (L55): the 24-root regression reproduced heap exhaustion.
Proof repositories included unrelated earlier roots, and raw comparison constants retained all
compiler pools across the before/after graph. The adapter now filters source inputs to their
configured dependency closure and copies comparison keys/dispatch chains before releasing each
attempt. Success, rejection, cancellation and failure workloads release their compiler objects
within the existing 512 MiB heap. The real 25-root teaching workspace now also passes with one
and five unsaved buffers, unchanged diagnostics and collection of query-owned compiler objects.

Native cancellation deadlock (L54): X103 also reproduced an inversion between LSP4J's request-map
lock and the server's document lock. Result cleanup and backend cancellation must run outside the
transport cancellation thread. A bounded regression fails against synchronous cleanup and checks
that cancellation returns while navigation owns the document lock.

Native hierarchy deadlock (L54): X100 reproduced a document-lock/compiler-worker cycle. A hierarchy
request waited for queued compilation while a preceding analysis/query completion waited for the
server publication lock. Publication callbacks are dispatched off the compiler worker; version and
close guards still run under the same lock. The regression covers both callback kinds.

IntelliJ startup follow-up (L54): bulk replacement of the first document before LSP4IJ's initial
`didOpen` completed left diagnostics out of sync and exposed stale folding ranges in
`run-5679669588027986266`. Waiting for client readiness makes focused playbook fixtures reliable;
it is a harness fix, not a production cure for that startup race. Reproduce ordinary edits during
server startup before submission and fix/report the client behavior if confirmed. See the
[current validation record](errs-integration-plan.md#header-slots-and-native-editor-parity-c28l53l54).

C28/L53 extends explicit header queries to empty type operands, trailing dots, qualifier tokens,
multiple-return declarations, generic constraints and module/package compositions. Immutable written
formal-name sets prevent accidental lookup of shadowed outer types; root recovery retains only the
written module namespace. The parser and AST ownership changes, native parity additions and
validation are documented in the [current batch](errs-integration-plan.md#header-slots-and-native-editor-parity-c28l53l54).

C27/L51 adds written function/sequence type-header completion using existing parser recovery and
listener branches; no new listener path or AST state. Single-report, clone/source ownership and
ordinary-parser controls accompany the shared X106 scenario. Native parity additions and remaining
bounds are in the [current checkpoint](errs-integration-plan.md#functionsequence-header-completion-and-native-parity-c27l51l52).

L50 refactoring extends compiler-proven edits without changing listener delivery. Successful graph
attempts inspect property dispatch using the host listener; failed repair attempts only copy existing
facts, and every proposed import must then compile completely. The only added AST API consists of
two passive import accessors. Scope and ownership are recorded in the
[refactoring checkpoint](errs-integration-plan.md#broader-refactoring-checkpoint-l50).

Live workspace/source navigation (L47–L49): unsaved headers and workspace-folder changes refresh the
compiler graph; detached graph queries are reused, and healthy modules remain navigable beside a
broken neighbor. Matching bundled XDK declarations open read-only source files. Complete reference
and refactoring proofs still fail closed. This adds no AST state or compiler listener changes.
See [scope, ownership and validation](errs-integration-plan.md#live-workspace-and-source-navigation-checkpoint-l47l49).


The original audit below records findings to triage: each site needs a
judgement about whether the failure is real and where it ought to go, and those judgements belong
to whoever owns the code.

Measured on `lagergren/errs`. First taken against `origin/master` at `fbdeb86c7`; re-checked
after the branch was rebased onto `4a1eae6f7`, and the counts are unchanged - this branch
had deliberately fixed none of them. The 2026-09-22/23 follow-ups at the end record the subsequent
compiler/embedding triage and fixes; the original totals are historical.

## What was counted

| | |
|---|---|
| `System.err.print*` / `printStackTrace()` in `javatools/src/main/java` | **56** |
| `catch (… ignore) { }` with an empty body | **35** |

### Printed rather than reported, by area

| area | sites |
|---|---|
| `runtime` | 16 |
| `asm` | 8 |
| `asm/constants` | 8 |
| `javajit` | 6 |
| `runtime/template/_native/web` | 5 |
| `compiler/ast` | 3 |
| `javajit/builders` | 3 |
| `runtime/template/_native/crypto` | 2 |
| `tool` | 2 |
| `asm/ast`, `runtime/template/_native/fs`, `runtime/template` | 1 each |

### What the empty catches swallow

| exception | sites | |
|---|---|---|
| `IOException` | 14 | |
| `RuntimeException` | 6 | |
| `CompilerException` | 4 | |
| **`Exception`** | **4** | **catches every checked exception too** |
| **`Throwable`** | **3** | **catches `Error` as well** |
| `WrapperException`, `ArithmeticException`, `NameNotFoundException`, `KeyStoreException`, `IllegalStateException` | 1 each | |

The last two rows were missing from the first version of this audit, which is unfortunate,
because they are the rows that matter most. A `catch (Throwable)` with an empty body swallows
`Error` - an `OutOfMemoryError`, a `StackOverflowError`, a tripped assertion - and continues as
though nothing had happened. That is exactly the shape the prior-art branch found a real bug in:
a DNS continuation caught `Throwable`, so an `Error` reached XTC code disguised as "host not
found", and an interrupt was dropped without restoring the flag.

The seven:

| site | catches |
|---|---|
| `asm/Argument.java:56` | `Throwable` |
| `asm/OpVar.java:109` | `Throwable` |
| `javajit/JitConnector.java:151` | `Throwable` |
| `runtime/template/annotations/xFuture.java:781` | `Exception` |
| `tool/Disassembler.java:285` | `Exception` |
| `tool/Launcher.java:795` | `Exception` |
| `tool/ModuleInfo.java:561` | `Exception` |

All 35 now name the variable `_`, the unnamed variable, rather than `ignore` or `ignored`. That
was a sweep across the whole tree, not a judgement about any of these sites: it makes "this
exception is deliberately dropped" something the language says rather than something a convention
implies, which is worth having before anyone works through the list. The question for each is
unchanged - whether the deliberate choice is still right.

## The sites worth looking at first

Of the 56 printed failures, **five** occur where a listener appears to be in scope - that is, the
code could have reported and chose to print. Those are the clearest candidates:

| site | what it prints |
|---|---|
| `compiler/ast/Expression.java:814` | `"No conversion found for " + constVal` |
| `compiler/ast/ConvertExpression.java:93` | `"No conversion found for " + aVal[i]` |
| `compiler/ast/NameExpression.java:1186` | `"TODO: AST for " + this` |
| `asm/ConstantPool.java:3944` | each error of a local `ErrorList`, then throws |
| `asm/constants/TypeConstant.java:4723` | commented out; not a live site |

**Deliberately not changed here.** The first two carry `// TODO GG: remove the soft assert below`,
so they are someone's marker rather than an oversight, and "fixing" them would silently change what
a compile prints. They are listed so the decision can be made by whoever wrote them.

`ConstantPool:3944` is different in kind: it is the static implicits bootstrap, where no
caller-supplied listener exists, so printing is close to the only option available. It is evidence
for the ownership discussion in `errs.md` rather than a defect on its own.

## Why this is worth doing eventually

The prior-art branch ran the same audit and found real bugs in this category, not just untidy code:

- a DNS continuation caught `Throwable`, so an `Error` reached XTC code as "host not found", and an
  interrupt was discarded without restoring the flag;
- `KeyStoreOperations.deleteKeyStoreEntry` swallowed a failed delete, so a certificate revocation
  reported success with the revoked certificate still in the store;
- `Launcher.showSystemVersion` relied on catching an NPE to mean "not in the repository".

The shape to look for is the same each time: a `catch` whose body is empty because there was
nowhere to report, in code where the caller had every right to be told.

## How to work through it

1. Start with the three `Throwable` and four `Exception` sites above. They are the smallest group
   to inspect. Only the three `Throwable` catches swallow `Error`; `Exception` does not.
2. Then triage the 14 `IOException` sites, which are the richest seam - an I/O failure that
   nobody hears about usually means a later failure with a confusing cause.
3. For each, decide: genuinely nothing to report, report to a listener already in scope, or
   propagate. Only the middle case is a code change of the kind this branch has been making.
4. Do the `runtime` prints last. They are the largest group and the least reachable by a listener
   today, because the runtime side of ownership has not been done - see `errs.md`.

Expect the output to be issues rather than commits. A site that turns out to be a real bug deserves
its own fix with its own test, not a sweep.

## Compiler/embedding follow-up, 2026-09-22

The original search counted stderr prints, but module repositories also printed failures to
**stdout**. `FileRepository.readFileInfo`, `FileRepository.readFileStructure` and
`DirRepository.ModuleInfo.tryLoad` caught exceptions, printed the message and returned no module.
That both discarded the cause at the embedding boundary and could corrupt a stdio host's protocol.

Malformed-header and truncated-payload regressions reproduce the file-repository loss. The fix
propagates I/O failures as `UncheckedIOException`, preserving the path and cause, and lets unexpected
runtime failures propagate. Failed reads are not cached as absent modules. Directory-scan cache
version 2 invalidates earlier entries that could remember a corrupt module as silently unavailable.
An embedding regression confirms the host receives `EMB-5`; that message now includes its exception
parameter, which was previously retained structurally but omitted from the displayed message.
This intentionally makes a corrupt `.xtc` file in a searched repository a visible load failure.

The other initial candidates have different meanings:

| Path | Current classification |
|---|---|
| `Argument.toIdString`, `OpVar.getName` | Best-effort diagnostic/debug formatting with runtime context; not the normal compiler reporting path. Narrowing their catches needs tests that preserve the original failure being formatted. |
| `JitConnector` and `xFuture` | Runtime/JIT failure ownership; outside the compile-only milestone. |
| `Launcher.showSystemVersion` | CLI display fallback, not compilation. The null case should eventually replace exception-driven control flow. |
| `Disassembler` date parsing | Optional display metadata; fallback text is intentional. |
| `ModuleInfo.loadBinaryFile` / `extractModuleName` | Discovery probes, with invalid/unknown state or no name as the result. The 2026-09-23 follow-up below pins valid/malformed source with an unreadable adjacent binary, and fixes reporting of a non-module root. |
| Parser speculation catches | Owned by `Attempt` rollback; a failed alternative is not a user diagnostic. |
| Parser include-file I/O catches | Already produce `INVALID_PATH` before aborting; not silently successful. |
| Constant-folding catches in unary/relational/comparison expressions | Fall back to runtime evaluation; relational arithmetic overflow already reports `VALUE_OUT_OF_RANGE`. Do not report every failed fold as invalid source. |
| `Expression` / `ConvertExpression` conversion prints | Explicit fallback to runtime conversion; printing is debug noise, not evidence that compilation must fail. |
| `NameExpression` “TODO: AST” | Incomplete binary-AST generation for bound generic functions. Requires a dedicated compiler reproducer; not safely repairable by changing the listener. |
| `ConstantPool` static bootstrap | No caller listener exists at initialization; exceptions must remain observable at the host boundary. The commented TypeConstant print is not live code. |

A fresh lexical recount, excluding comments and the overload declaration, finds **121** no-argument
`ensureTypeInfo()` calls: 29 under `compiler`, 25 under `asm`, 39 under `runtime`, 27 under `javajit`
and one under `api`. These are directory counts, not a compile-time call graph; the older
124/126-site totals and 53-site compile-time estimate must not be treated as a current migration list.

| TypeInfo call family inspected | Disposition |
|---|---|
| `RelOpExpression` inference/fit/operand selection; `ArrayAccessExpression` inference/fit/accessor selection; `Expression` assignability | Speculative candidate searches. Keep quiet; explicit `PROBE` is appropriate when a site is changed. |
| `AstNode.transformType`, enum narrowing in `CmpExpression` / `TypeCollector`, `StatementBlock.isReservedNameReadable` | Metadata or feasibility queries. Their reporting validation callers must be tested before deciding to propagate a listener here. |
| Array accessor validation, assignment-operator lookup, constructor-super lookup, property target metadata | Initially selected for source reproducers; see the bounded follow-up below. Listener availability alone does not establish that a site owns reporting. |
| `NameExpression` bound-function/atomic BAST and `ToIntExpression` conversion metadata | Post-validation code generation. Keep failures visible at the embedding boundary; establish a missing-diagnostic reproducer before altering replay. |
| `TypeInfoReal`, property metadata, virtual-child fallback, mixin annotations | Shared recursive metadata paths; some run during provisional composition. Need caller/stage evidence. |
| `EvalCompiler`, initializer/delegation generation, JIT descriptors and runtime op lookups | Include debugger/runtime consumers; their location in `compiler` or `asm` does not make them LSP validation sites. |

The earlier sample of three out of seventy suppressed messages remains a sample. This pass does
not close that survey or claim every silence is justified. It fixes the reproduced repository loss
and makes the next TypeInfo investigation narrower: selected validation consumers first, with
source-level evidence and explicit checks against provisional-composition cascades.

### Bounded validation follow-up

Four added source cases in `TypeInfoDiagnosticsTest` exercise indexed access, compound assignment,
superclass construction and a property reference on types with the known duplicate-annotation
warning. Each compiles successfully and reports `VERIFY-75` exactly once, without internal errors
or extra cascades. These are examples of current reporting behavior, not proof that every generic
composition or external-library use reports correctly.

| Candidate | Caller evidence and disposition |
|---|---|
| `ArrayAccessExpression.validate` / `findArrayAccessor` | The accessor search is shared with inference and selection; the later validation read obtains metadata for the chosen method. The indexed-access case preserves the warning. Keep the search quiet; a loss involving a newly instantiated target type still needs a reproducer. |
| `AssignmentStatement.findInPlaceAssignMethod` | Called during binary-AST generation, after validation of the synthesized operation. The zero-candidate branch appears to select the ambiguous-operator code, but ordinary missing-operator source never reaches it: `EmbeddingDiagnosticsTest` confirms validation reports `COMPILER-50`. This was a suspected defect, not a demonstrated user-facing loss; leave it unchanged. |
| `TypeCompositionStatement` superclass-constructor lookup | `findSuperConstructor` immediately calls `typeSuper.ensureTypeInfo(errs)` before the no-argument read retrieves the selected method structure. The constructor case preserves the warning. No missing listener at that second read was demonstrated. |
| `PropertyConstant.getPropertyInfo` / `getValueType` | Shared metadata APIs with compile-time and other consumers. The property-reference case preserves the warning, but does not establish reporting ownership for every caller. Keep explicit caller/stage investigation on the backlog. |

No additional production listener migration is justified by these cases. Remaining suppression
work needs generic/external-type source cases or instrumentation identifying a diagnostic that the
host actually misses. The previous sample of seventy suppressed messages remains open.


### Fresh TypeInfo capture, 2026-09-22

A forced local `:xdk:installDist` build (`--rerun-tasks --no-build-cache`) was observed with a
temporary probe in `SilentErrorListener.log`. It selected `CASCADE`, severity ERROR or worse,
and call stacks containing `TypeConstant.ensureTypeInfo`; it recorded the rendered diagnostic
and complete compiler stack. Deduplication by message plus stack yielded **301 observations**;
deduplication by rendered message yielded **37 distinct messages**: `VERIFY-70` 27, `VERIFY-67` 8,
`COMPILER-38` 1, `COMPILER-140` 1. The instrumentation was removed before normal verification.
This is a fresh, explicitly scoped capture, not a claim that the historical 70 messages shrank
through fixes. Those counts came from a different worktree/capture and cannot be subtracted.

| Observed group | Distinct messages | Caller/stage evidence and disposition |
|---|---:|---|
| `ByteArray`, `BitArray`, `NibbleArray`: `appendTo`, `toString`, `estimateStringLength` | 9 × `VERIFY-70` | All three are generic mixins `into Array<Element>`. Stacks include `Expression.getTypeInfo` during invocation fit and `Expression.isAssignable`, with recursive conditional composition. Missing supers on an isolated mixin are not evidence of a missing super on the composed array. Keep quiet; no selected-use failure was demonstrated. |
| `ListMapIndex`: `appendEntry`, `makeImmutable`, `clear`, `deleteEntryAt`, `indexOf` | 5 × `VERIFY-70` | Conditional mixin into `ListMap<Key, Value>`. Observed during type validation and later allocation/fit/assignment searches through `mergeConditionalIncorporates`. Its `into` target supplies the members. Keep provisional reporting suppressed. |
| `Interval.adjoins`; `PeekAhead.openObject` / `peekMetadata` | 3 × `VERIFY-70` | Mixin/annotation composition into `Range` / `ObjectInputStream`. Stack paths include indexed-range inference, annotation validation and member lookup. These extend the previously sampled shapes to the other observed method. No host-facing loss established. |
| Array translators' anonymous `Object:1.element.assigned` properties | 6 × `VERIFY-67`, 6 × `VERIFY-70` | Bit→Nibble, Bit→Byte, Byte→NumType, Nibble→Bit, Nibble→Byte and Number→Bit. The property value type and the Boolean `assigned` property appear as conflicting `Referent`s; stacks enter through `Expression.calcFit` / `NameExpression.testFit` on return validation. Classified as provisional property-explosion/type-fit results, not six invalid source declarations. A direct final-composition type-system regression remains useful; suppression alone does not prove these compositions are correct. |
| `Client.DBObjectImpl.dbChildren.calc().Map:1.get` | 1 × `VERIFY-70` | Anonymous generic `Map` member during provisional name/type lookup while `NewExpression` catches up its `RoughDraft`. The `calc(?)` spelling is normal method-parent rendering, not evidence of an unresolved identity. Keep the provisional diagnostic suppressed; the final artifact check below verifies the instantiated member. |
| XML `ContentList.cursor().Cursor:1` | 2 × `VERIFY-67`, 3 × `VERIFY-70` | `ContentList implements List<Content>`; its anonymous cursor overrides `value`, `insert`, and accessors. The preliminary comparison uses `List.Element` against `Content`. Stacks enter through property validation/annotation validation within method generation. This is a generic/virtual-child composition case deserving a focused final-type regression, not a blanket listener migration. |
| `Future<PendingTypeParameter>` | 1 × `COMPILER-38` | `AstNode.calculateReturnFit` → overload search → invocation return-type fitting. The name contains the compiler's pending formal placeholder. Candidate-fit diagnostic; retain silence. |
| `@Parsed @ContentNode Data:private` | 1 × `COMPILER-140` | `AstNode.collectMatchingMethods` → invocation validation; emission is in `TypeConstant.mixin`, where constructor lookup for annotation arguments returns no candidate. `Parsed` requires offset/length; this stack probes an annotated target without a complete applicable constructor. Keep the failed candidate quiet; this does not establish every annotation application is valid. |

The top compiler stage is often `generateCode`, but its stack also contains
`StatementBlock.compileMethod` and expression validation. It would be wrong to label all those
observations “after validation” from the outer stage name alone. Some silences arise inside recursive
composition even when the outer lookup has a real reporting listener. Replacing the 121 lexical
no-argument calls therefore neither isolates nor fixes this set.

The consumer regressions now cover generic `Derived<String>` and `Derived<Int>` against both a
same-source base and a base compiled, serialized to bytes, and loaded into a fresh repository. The
consumer's duplicate annotation still reaches its listener as `VERIFY-75`. A new concrete generic
TypeInfo first requested silently replays its warning to a later reporting caller without rebuilding.
The dependency's original compiler/AST/TypeInfo caches are not reused. Already rejected redundant
annotations in a compiled dependency are a different boundary; the test puts the offending override
in the consumer so the source diagnostic belongs to that compilation.

### Final-composition follow-up, 2026-09-22

A fresh JVM loaded the installed, serialized `ecstasy`, `xml` and `jsondb` modules and requested
the final anonymous-class TypeInfo with explicit reporting listeners. All eight classes from the
three open families produced no diagnostics:

- The six Array translators have Boolean `assigned`, `Type<Boolean, Object>` for its `Referent`,
  and an assigned-getter chain containing `NakedRef.get<Boolean>`. Element get/set retain the
  appropriate Byte, Nibble, Bit or formal element type.
- XODB's `dbChildren.calc().Map:1.get(String)` returns `(Boolean, DBObject)` and includes the
  inherited `Map.get` declaration with those substitutions.
- XML's `ContentList.cursor().Cursor:1` resolves `value`, get/set and `insert` to `Content`, with
  the corresponding inherited method chains present.

The probe also checked the Boolean Array translator and six nested XODB classes: fifteen final
compositions in total, with no diagnostics or exceptions. A small fresh-source anonymous Byte
property compiled cleanly and produced the correct Boolean `assigned` metadata. The initial probes
are now retained in
[`TypeInfoFinalCompositionTest`](../lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TypeInfoFinalCompositionTest.kt).
The tests deserialize fresh copies of compiled modules supplied by test-only Gradle `xtc` variants,
then inspect the linked dependencies in their owning pools. They require no installed distribution
or `XDK_HOME`. All fifteen compositions must build without diagnostics; selected Array, XML and XODB
members additionally have assertions for their substituted types and inherited chains. The
fresh-source control deliberately introduces an unmatched `@Override` and requires a real `VERIFY`
error naming that method, a failed compilation and no `EMB-5`.

The captured Array stacks reach the provisional lookup through `TypeConstant.getConverterTo`
and `Expression.calcFit`. XODB/XML stacks reach it while `NewExpression` builds its `RoughDraft`.
Actual anonymous-class construction uses a reporting listener branch and checks the selected
TypeInfo. Also, `NamedConstant.getValueString()` deliberately renders method parents with `(?)`;
that spelling cannot establish that a method identity is unresolved.

**Disposition:** no new lost diagnostic was reproduced, so no production TypeInfo listener sweep
was made. Every message in this capture has a classified source/caller group, and the three
previously open families now have direct final-metadata evidence supporting provisional silence.
The artifact and fresh-source regressions pass, including the invalid-override control. They do not
establish all runtime behavior or justify every historic silence. The
`@Parsed` constructor family was outside this deeper pass; the 2026-09-23 follow-up below covers it.
Existing unmatched `@Override` and
duplicate-annotation tests continue to pin real errors/warnings reaching the host.

### Fatal forwarding follow-up, 2026-09-22

The module-session regression reproduced a separate reporting loss. Renaming a base class in the
module root left a member extending the missing class. The compiler produced FATAL `COMPILER-30`,
but `Launcher.log(ErrorInfo)` called console reporting before its host delegate. Console reporting
threw `LauncherException`, so the member's original diagnostic never reached the collector and the
embedding boundary synthesized `EMB-5` on the root.

Forwarding the structured diagnostic before console reporting fixes the loss while preserving the
CLI abort. `LauncherErrorHandlingTest` checks delivery of the original fatal diagnostic before the
exception; `XdkModuleServerTest` checks its member URI and version after the root edit. Both pass.
This is a demonstrated pipeline defect, independent of the provisional TypeInfo suppressions above.

### Parser recovery and unterminated strings, 2026-09-22

Separating parser recovery from the launcher's stage-abort policy exposed an existing lexer loop.
At EOF inside a string, `Lexer.eatStringChars` reported `STRING_NO_TERM` but neither exited the loop
nor advanced input. The identical diagnostic could be deduplicated forever, so a finite ErrorList
budget did not necessarily stop it. The code is also present at the local base `4a1eae6f7`.

The lexer now reports once and returns the unterminated literal token, leaving the source error
recorded. A regression covers ordinary and template strings, including empty and Unicode content,
with an unlimited listener. Its observer fails immediately if the diagnostic repeats, avoiding a
test that leaves a spinning lexer behind. This fixes termination; it does not make the source valid.

### Annotation metadata and module-source follow-up, 2026-09-23

The `@Parsed` follow-up found a metadata defect, not a missing listener. A consumer of freshly
deserialized XML modules can compile `new @Parsed(offset, length) @ContentNode Data(text)` with
runtime arguments and no diagnostics. A subsequent reporting lookup of the constructed type's
TypeInfo nevertheless emitted `COMPILER-140`. Annotation type descriptors deliberately omit runtime
arguments (and provisional resolution can strip arguments). `TypeConstant.mergeMixinTypeInfo`
mistook that omission for an invalid zero-argument construction. The same check exists at the local
branch base `4a1eae6f7`.

The metadata builder now checks constructor applicability when constants are actually supplied;
an argument-free descriptor does not assert that a zero-argument constructor was called. Actual
annotation application validation still checks required arguments and target compatibility. The
eight added cases in `TypeInfoFinalCompositionTest` cover:

- Constant and runtime arguments, followed by reporting metadata queries for `offset`, `length`
  and `text` with their expected Int/String types.
- Missing, mistyped and extra construction arguments, missing declaration-annotation arguments,
  and an incompatible annotation target. All remain source errors without `EMB-5`.
- An invalid supplied constant in a directly constructed annotated type. Its error still reaches
  a reporting caller after an initial silent lookup and is deduplicated by the receiving ErrorList.

The file-discovery check also reproduced a reporting defect: a file containing `class NotModule {}`
reached the embedding compiler's module-root check, which used a console-only message. The host
then saw a synthesized internal error instead of the source problem. The embedding check now emits
`EMB-6` through the structured listener at the offending declaration. `EmbeddingDiagnosticsTest`
covers file and in-memory entry points, including source ranges, and verifies that malformed source
beside an unreadable `.xtc` retains its parser diagnostics while valid source still compiles. Discovery
fallback remains intentional; loading a corrupt dependency through a repository remains a visible
failure under the earlier repository regressions.

This completes the bounded diagnostic-audit task: the fresh capture's groups and inspected failure
families have dispositions, and the two reproduced defects have regressions. It does not certify
every historical silence. The original seventy-message survey is not the same dataset as the
37-message capture, and no one-to-one comparison is available. The `NameExpression` bound-generic
binary-AST TODO was unresolved at this checkpoint; the post-L18 findings below now reproduce and
fix its bound-function typing/emission path. Debug formatting, runtime/JIT
ownership and optional CLI display fallbacks remain separate follow-ups as classified above.

### Dependency source ownership follow-up, 2026-09-23

The new Kotlin dependency copier initially read signature metadata from constants deserialized in
an unlinked dependency artifact. A consumer then failed while resolving the core `Int` class during
type inspection. This was a new copier ownership error, not evidence of another pre-existing
ambient-pool or error-listener defect. Selecting a thread-local pool alone did not link the artifact.

The copier now uses artifact constants only for identity equality/source association and reads
semantic metadata through the matching constant in the linked consumer pool. Every attempt opens
fresh dependency structures; retained `XdkDependency` values contain only immutable bytes, copied
source locations and revisioned symbol keys. No new Java fallback, AST state or listener policy was
needed. The index covers the exact emitted artifact and source revision, not independently edited
library sources.

`XdkDependencyTest` covers serialization, overload/module separation, inherited generic bodies,
source-module precedence, transitive replacement and compile/cursor cancellation. The server
regression verifies that replacing a dependency return type produces an ordinary consumer diagnostic
at the unchanged document version and that restoring the artifact clears it. This follow-up audits
the new host boundary; it does not broaden the historical diagnostic-suppression survey.

## C3 extraction lifetime audit, 2026-09-24

The [fourth extraction batch](errs-integration-plan.md#fourth-local-extraction-batch-c3-2026-09-24)
checks scoped reporting on C2 rather than treating integrated-branch tests as proof of the subset.
It found two gaps in the integrated `9c432f778` reference, now fixed in both C3 and the
integrated branch by the subsequent synchronization pass:

- Grouping label context/listener fields into `ValidationScope` did not itself restore them on
  exceptions or early returns. The extracted loop/try owners now restore previous state in
  `finally`; `Statement.validate` restores its common context too. Regressions throw while each
  scope is active and verify release, including the finally block's own statement context.
- `Parser.Attempt` implemented reporting but inherited default false state queries. That concealed
  a caller's abort request from nested attempts. Delegating the queries to its branch preserves
  the request; the nested-attempt regression verifies it.

The extraction retains `ModuleInfo.Node`'s drain-after-forward behavior and tests against replaying
already-forwarded diagnostics. `NameResolver` callbacks see the active caller and release it after
normal, deferred, nested and exceptional exits. These are lifetime guarantees, not concurrent-use
support. Parser scopes still do not redirect lexical diagnostics; silent module-name scanning must
supply an explicit discard listener to the lexer/parser constructor.

The C3 fixes and regressions now live on both `errs/c3-reporting-scopes` and `lagergren/errs`.
The [synchronization pass](errs-integration-plan.md#synchronize-extraction-improvements-back-into-errs-2026-09-24)
also restores C1's legacy structure-report source attribution and brings over missing earlier
regressions. Standalone slice validation and integrated-branch validation are recorded separately.
The broader suppression audit and TypeInfo ownership work retain their existing scope and later
slice assignments.


## Problems-view source positioning follow-up, 2026-09-24

The automated warning check confirms one `VERIFY-75` with warning severity and code, but its
location is the requesting file's `(0,0)` range. This is the existing `Site.At` conversion in
`XdkAdapter`, which has no source-span association; positioned `Site.In` source errors do retain
their precise ranges. The warning's delivery/deduplication fix is valid, but an annotation-level
squiggle or jump has not been implemented. The playbook now checks the actual file-level location
and unsaved clearing, rather than claiming annotation navigation works.

Should fix before claiming complete diagnostic positioning: map source-owned compiler structures
to their declaration source spans, including warnings emitted for closed member files. Use compiler
identity/source associations; do not infer locations from diagnostic message strings or invent
locations for binary-only structures. Preserve the whole-document fallback when no association is
available, and add controls for the warning's true owner and unrelated file groups.

## Property/accessor inspection follow-up, 2026-09-24

Ordinary source property composition supplies the facts needed by implementation lookup. Host
method tables can omit composed mixin accessors, but the corresponding `PropertyBody` still carries
the written getter/setter structure and compiler composition order. The Kotlin copier reads those
facts and respects field/default precedence; it does not infer an implementation from a name match.

A probe that constructed a separate `PropertyClassTypeConstant` for every custom property was not
a suitable general inspection path. Valid ordinary source fixtures gained Ref/Var override diagnostics
(`VERIFY-81`), and some property/host combinations failed the property's ownership assertion. The
implemented lookup does not require that probe or suppress its diagnostics. Existing host TypeInfo
and property composition suffice; output/purity tests cover inspection without changing compiled
bytes. This is an inspection-boundary finding, not a new claim about an upstream compiler defect.

Delegating and Ref/Var-annotated properties remain explicit negative cases. Delegation's optimized
chain APIs can generate forwarding methods, so resolving their source meaning needs its own consumer
and output/lifetime checks before enabling it. Property rename was disabled at this checkpoint; L50 now proves ordinary source families.

## Post-L18 diagnostic and emission findings (2026-09-24)

The structure-location follow-up above is implemented. Kotlin builds an attempt-local association
from written declaration structures and their identities to original name tokens; it does not parse
message text. Closed members retain their own URI, overlays move the span, and binary-only sites
keep the document fallback. The real VERIFY-75 probe revealed that PropertyInfo logged against the
inherited base identity. Duplicate/superfluous annotations now report the contributed declaration,
which is the same owner named in the diagnostic parameters. ErrorList still deduplicates replay.

The bound-function TODO now has a reproducer: a generic static `id(T)` returned as a
`function Int(Int)` by name (with and without `&`). Before the correction it fails validation with
COMPILER-43 because the hidden type parameter remains in the exposed function type. Removing that
already-bound parameter reaches the documented missing binary AST and produces EMB-5. The fix
constructs BindFunctionAST from the same binding indices/arguments as FBind, for static and bound
instance paths, and retains the actual resulting function type. Artifact serialization/deserialization
is covered. This is an existing compiler typing/emission defect, not a missing listener replay;
no listener suppression or public error contract was changed. The atomic binary-AST and
ToIntExpression audit items remain separate until reproduced.

Post-L18 verification passes all 76 automated editor cases, including the exact derived-property
warning range, all 788 executed LSP tests (three existing skips), 16 packaged stdio tests and 29
focused Java tests. The 120-cycle editing/cancellation workload releases 2,400 tracked attempts,
pools and source roots; p50/p95 rebuild times were 217/231 ms including debounce. This bounded
run does not substitute for a multi-hour editor soak or remaining manual visual checks.

## Ref/Var annotation lookup follow-up, 2026-09-24

The previous extra-PropertyClassType probe is unnecessary for annotation accessors. The compiler's
`TypeConstant.explodeProperty` already layers Ref/Var annotation bodies into the adopting host's
nested method chains. The Kotlin implementation copier now reads those chains through its existing
explicit listener and cancellation boundary. Generic substitutions, annotation order and explicit
property accessor precedence come from compiler composition; no standalone property TypeInfo is
constructed and no diagnostics are suppressed.

The initial regression compiled successfully but returned no targets for the annotated property.
Removing the blanket annotation exclusion exposed the written getter/setter identities. The broader
lookup suite then caught native `@Lazy` storage being mapped to the property declaration. Annotated
field/native fallbacks are now excluded: only proven written accessor bodies are copied. Binary
annotation bodies require the host's source index, just like other dependency implementations.
Focused lookup/dependency/purity regressions pass with unchanged emitted bytes and no new diagnostics.
Full verification is recorded in the integration plan's L22 follow-up.

## Incomplete function/constructor signatures, 2026-09-24

Function-valued probes exposed two distinct validity issues. A silent PROBE listener alone does not
remember serious errors, so validating an unreadable function variable could leave a usable-looking
type. The probe now uses a locally collecting silent listener and rejects serious errors, while
forwarding cancellation. Constructor type validation uses the same policy. These private speculative
errors never replace normal source diagnostics; explicit TypeInfo lookup still reports to the host.

The expression `make(fn)(1, )`, for a function type with `Int, String` parameters, also exposed
`InvocationExpression.testFunction` overwriting failed argument fitting with successful return
fitting. The invalid invocation could then publish a shortened, apparently validated signature.
The validator now combines both results. Positive complete calls and invalid arity/type regressions
check compilation outcomes and absence of invalid function bindings. This is an ordinary compiler
validation fix (future I6), not another listener suppression or a cursor-only workaround.

Function candidates have no guessed runtime target, parameter names or defaults. Constructor
candidates cover ordinary named types, explicit class type substitution, overload fitting, named
slots and defaults. Virtual/inner/array/annotated construction and omitted class-type inference
remain outside the proof. No AST semantic fields or clone/reset machinery were added. The
[integration record](errs-integration-plan.md#incomplete-function-and-constructor-signature-help)
separates C11 compiler facts, L23 consumers and I6, and records full verification.

## Atomic binary-AST and switch-conversion audit, 2026-09-24

The remaining atomic and `ToIntExpression` metadata consumers now have source-level emission
probes in `CompilerEmissionAuditTest`, including serialized output. The audit found compiler
correctness defects, rather than evidence for broader TypeInfo diagnostic replay:

- Atomic prefix/postfix `++` and `--` serialized an invocation with zero results, even for an
  expression returning `Int`. All four forms reproduced the mismatch. The existing validator's
  result types now describe the invocation in the binary AST.
- Atomic lookup handled only `This` and `Left`; an unqualified module property reached the
  unhandled singleton case and surfaced as `EMB-5`. Lookup now includes singleton and outer-instance
  owners. Both AST producers use the same resolved Ref/Var type as lookup, including a concrete
  generic referent, rather than resolving the property without its receiver.
- After outer-instance operations could compile, their serialized property owner was still typed
  `Int64` instead of the enclosing `Counter`. The existing assignment generator now uses the outer
  register's type. All four sequential forms pin the owner and return types after serialization.

These paths exist in local `origin/master` at `4a1eae6f7430bda4204f0b4ec8248f942d58d6ba`, including
the empty return-type array, incomplete access-plan switch and incorrect outer AST type. This
establishes source provenance; no new master checkout/execution is claimed. The tests live downstream
of the compiler because Gradle must first supply compiled core modules.

The listener controls compile generic source and serialized-dependency atomic uses with a duplicate
annotation: both report `VERIFY-75` exactly once. Invalid atomic operations report ordinary compiler
errors before emission. None of these fixes changes diagnostic suppression or adds a host callback.
The compiler's existing exception path remains visible as `EMB-5`; the valid-source reproducer now
compiles instead of relying on that fallback.

`ToIntExpression` is used by the dense-switch JumpInt path after validation. Fifteen built-in type
cases cover extraction (Bit/Nibble/Char), nonzero offsets and conversion from each signed/unsigned
integer family. Tests assert the emitted operations and original binary-AST condition type after
serialization, rather than accepting compilation success alone. An enum control confirms that
normal enum switches avoid ordinal lowering, so its legacy ordinal accessor is not reached by
this path. No lost source diagnostic or broken conversion metadata was reproduced; no listener
migration is justified here. This is a bounded metadata/emission audit, not exhaustive numeric
execution or proof about every silent TypeInfo consumer.

The [I7 integration record](errs-integration-plan.md#atomic-binary-ast-and-switch-conversion-audit)
tracks scope, AST ownership, validation and future PR placement. The following cursor-recovery
checkpoint addresses the next implementation item.

### Missing delimiters around an explicit cursor, 2026-09-24

The parser previously discarded an intact cursor site when an enclosing grouping parenthesis or
index bracket was absent. A retained site followed by a real inner closer could also lose its outer
call. Recovery now follows existing syntax ownership, retains only the missing closing suffix at
statement/outer-delimiter boundaries, and leaves actual closing tokens for their owning constructs.
An explicit EOF cursor covers missing block ends under its existing `PARSER-30` diagnostic. The
ordinary compilation still reports its usual errors; a cursor request never replaces that cache.

This adds no error-listener interface, AST field or semantic collector. Speculation, cancellation
and error budgets still stop recovery; unrelated malformed statements prevent semantic analysis.
This checkpoint did not repair missing operands, declaration headers or tuple/literal delimiters.
C17/L30 now extends the latter two around supported cursor holes; unfinished declaration names/types
and missing operands remain unsupported. See the
[C12/L24 record](errs-integration-plan.md#missing-delimiter-cursor-recovery) for regressions,
editor coverage, ownership and future extraction boundaries.

### Argument-value completion, 2026-09-24

Copied expected types alone do not preserve the compiler's inference and conversion rules for a
proposed argument. `PartialCallResolver` now probes visible readable source variables through the
existing fitter, copying only accepted variables into immutable `CursorBinding.argumentValues`.
Trials retain lexical context without adding children or mutable semantic state to the source AST.
No candidate is published as a selected call, and no synthetic value repairs the actual source.

Speculative mismatches remain private, cancellable PROBE diagnostics; local error state rejects
the value. Actual compilation diagnostics and explicit TypeInfo reporting keep their existing
listeners. Tests compare proposals against compiling the corresponding real expressions and check
that source text, argument lists, diagnostics and selected-call facts are unchanged by the query.
The [C13/L25 record](errs-integration-plan.md#argument-value-completion) describes API migration,
supported slots, negative controls and protocol/editor evidence.

### Typed argument-prefix completion, 2026-09-25

A final bare argument name previously became a scope-completion site and lost the enclosing call's
fitting context. The parser now retains that name's original token on the CALL site, including a
separate pending named label where present. Complete earlier arguments remain source children;
the selected prefix is syntax to replace, not a value to validate. Compiler fitting uses the same
immutable accepted-value list as empty slots, with no new semantic node state or record components.

The explicit probe still reports `PARSER-30` once and cannot emit the incomplete method. Rejected
proposals use the existing cancellable private PROBE listener; ordinary diagnostics remain cached
until an actual source edit. Same-prefix incompatible variables, overload alternatives, inference,
conversions, narrowing, unsaved signatures and cancellation have regression coverage. Exact token
edits include escaped identifiers, UTF-16 and CRLF. The
[C14/L26 record](errs-integration-plan.md#typed-argument-prefix-completion) documents syntax ownership,
unsupported contexts and host/editor verification.

## IntelliJ diagnostic-delivery checkpoint, 2026-09-25

The initial real-IDE compiler playbook now checks type/unresolved-name errors and clearing,
source-dependency recompilation, and the inherited-annotation warning from 7a.8. The warning
arrives once with Warning severity and clears after removing the duplicate annotation.
The test runs IDEA 2026.2.3's free feature set with LSP4IJ 0.21.0 and asserts Ultimate remains
unloaded. It inspects editor diagnostic highlighters; the rendered Problems tool-window layout
and the remaining IntelliJ playbook rows are still separate checks.

`XtcLanguageClient` previously returned null for all configuration sections except formatting.
It now delegates compiler settings to LSP4IJ's existing lookup/lifecycle, so source graphs can
arrive at startup and change live. No listener, compiler pipeline or AST API change was needed
for this client fix. See [I8/L27 and validation](errs-integration-plan.md#intellij-compiler-playbook-and-client-configuration).

### Property/constant argument completion, 2026-09-25

The cursor argument fitter now checks implicit property and constant reads as well as locals.
Explicit TypeInfo inspection forwards diagnostics; speculative name validation uses collecting
silent PROBE listeners so rejected reads remain private without losing failure/cancellation state.
Immutable `CursorBinding.argumentProperties` records identity and validated type. Kotlin copies
these while on the compiler worker; no AST fields, cloning rules or listener policies were added.

The initial seven property regressions failed on the previous implementation. Additional checks
cover inherited access, static receivers, unreadable shadowing locals, generic substitution,
overload alternatives, detached facts and unsaved inherited property types. Bundled module
`simpleName`/`qualifiedName` are valid String suggestions. Ordinary `Object` properties still fail
String arguments after an `is(String)` guard; normal compilation reports `COMPILER-150`. This
confirms the completion must not invent the local-variable narrowing that the compiler declines
for ordinary properties. See [C15/L28 and validation](errs-integration-plan.md#property-and-constant-argument-completion).

Verification: 19 property regressions pass; the complete run reports 472 executed Java tests,
928 executed LSP tests, 29 packaged-stdio tests and 87 editor cases, all with zero failures/errors.
The prior 40 Java and three LSP skips remain. The retention workload includes property proposals
and releases all 2,402 observed references; no AST ownership changes were needed.


## Specialized constructor and declaration recovery audit (2026-09-25)

Commit `46d6c1442` closes the next cursor API gaps. Constructor probes reuse
normal preparation for inner/virtual/annotated/formal types and required-type inference; array
sizes remain written arguments before a parenthesized supplier. Provisional class inference does
not constrain later arguments more than normal compilation does. Accepted completions are checked
against complete compiler inputs, including a supplier for an element without a default value.

Declaration recovery exposed a lost-facts path: property constant evaluation validated a disposable
clone and never reached the source-owned cursor. Explicit cursor analysis now uses the existing
real initializer path for values containing a hole. No clone facts are promoted and normal constant
evaluation is unchanged. Method and shorthand-constructor defaults, property values and expression
bodies have regressions. Tuple/list/set/map recovery retains source ranges and later declarations;
missing values and unrelated syntax damage remain rejected. No new mutable AST fields were needed.

Anonymous construction was deferred here and is handled by the follow-up below. Array-dimension
cursors, multidimensional construction and unfinished declaration names/types remain explicit gaps.
The qualified-type fallback's existing `ctx.exit()` call was observed during preparation review but
was not changed: the complete/partial qualified-inner regressions pass, and this pass did not
establish an independent defect there. See the
[C16/L29/C17/L30 validation and extraction record](errs-integration-plan.md#specialized-constructors-and-declarationliteral-cursor-recovery).

Validation: 473 executed Java tests, 964 executed LSP tests, 36 packaged-stdio tests and all 92
editor cases passed. The existing 40 Java/three LSP skips are unchanged. The additional focused
18-case recovery run verifies exact boundary diagnostics and non-emission. The retention workload
retained zero of 2,402 observed objects. The integration record links the full XML and editor report.


## Anonymous constructor ownership audit (2026-09-25)

Commit `cd1732d42` resolves the anonymous constructor cursor boundary. Calling normal
preparation on a detached clone would still register class components under the source method.
Instead, the partial-analysis attempt prepares its retained anonymous declaration and existing
`anon` child. Constructor signature lookup requires that class shape but does not require capture
analysis, synthetic forwarding code or emitted method bodies. Existing Candidate facts suffice;
no new mutable AST field, clone reset, detached metadata API or public record component was needed.

Own constructor declarations and superclass signatures are fitted with compiler rules. The
provisional synthetic class default is excluded from suggestions in favor of real superclass
constructors; interface implementations retain their valid no-argument default. Source-derived
labels avoid exposing generated `:1` names. Access tests compare public/protected/private dependency
constructors with normal compilation. Repaired programs exercise read-only and mutable captures;
cursor attempts must have one owned class shell, no capture bindings, no method AST or operations,
original source text and exactly the cursor boundary diagnostic.

The probe does not validate the entire anonymous body or claim a selected overload. Array cursors
were the next item at this checkpoint and are covered by C19 below; unfinished headers remain. See
[C18/L31](errs-integration-plan.md#anonymous-constructor-cursor-support) for verification and extraction.

Validation: 473 executed Java tests, 986 executed LSP tests, 38 packaged-stdio tests and all 94
VS Code editor cases pass. Existing skips remain 40 Java/three LSP; no editor or stdio cases skip.
The retention workload releases all 2,421 observed objects. The integration record preserves the
initial startup timeout, the corrected fixture-anchor collision and final report paths. It also
tracks the observed X76 semantic-token overlap warning as a separate L12 follow-up.

## Array dimension cursor audit (2026-09-25)

C19/L34 is implemented in `0b800c392`, after `3f46568af`. Type parsing previously read size expressions
to count dimensions before rewinding for `NewExpression`; a cursor could unwind before its owner
existed. The existing listener branch now isolates that lookahead, and the owning bracket parse
retains the cursor. A non-deduplicating collector checks one diagnostic, so ErrorList deduplication
cannot hide a duplicate. FIRST_ERROR and cancellation prevent semantic results.

Array candidates must compare the underlying declaration identity: TypeInfo's specialized method
ID differs from `ArrayTypeExpression.getSupplyConstructor()`. Resolving that existing declaration
avoids hard-coded Int/type-fit rules and unrelated Array overloads. Fourteen adapter cases cover
empty/prefix/complete size slots, missing brackets, following suppliers, narrowing/readability,
properties, invalidation, invalid literals and unsupported multidimensional forms. Following
supplier expressions are consumed for parser recovery but are outside the prefix fitting proof.
Ordinary compilation still requires a supplier for String, which has no element default.

No new public API component, AST cache or clone remapping is needed. The embedding test checks
the compiler-established source parentage; the parser-only test checks independent cloned children
because parentage is introduced later in the pipeline. Shared X90 and packaged stdio exercise the
original UTF-16 edit range and diagnostic repair. Final validation is recorded under
[C19/L34](errs-integration-plan.md#array-dimension-cursors).

## Unfinished declaration-header audit (2026-09-25)

Native follow-up on 2026-09-26 found a folding integration defect rather than a parser range defect:
X92's method had the correct ending line, but line-only transport let LSP4IJ extend it to the
module's closing brace. Commit `2e98860e1` preserves a precise end column before the real closing
delimiter. Adapter and packaged transport regressions cover following same-line declarations,
complete/recovered headers, emoji, LF/CRLF and actual EOF. No AST state or embedding API change is
needed. See [fold boundaries](errs-integration-plan.md#precise-compiler-fold-boundaries) for native
verification status and the remaining client EOF limitation.

Commit `68a291c0f` (C20/L35) retains incomplete method headers structurally and completes simple member/parameter type
prefixes. A syntax-only `IncompleteDeclarationStatement` is preferable to a partial
`MethodDeclarationStatement`: registration of a fabricated signature could leak parameters,
methods and invalid type identities into normal compiler stages. The new node owns only original
source metadata and selected cursor syntax; its child list follows ordinary AST cloning, while
metadata stays final. The discarded body cannot leak its local declarations into the owner.

Header name queries use `NameResolver` in the real enclosing class, and reuse the existing
`CursorBinding.NamedType` output. They do not create a method Context or enumerate value members.
Regression controls cover imported aliases, wildcard imports, typedefs, nested owners, shadowing,
unsaved member/root overlays, current-version invalidation, exact edits and source ownership.
Missing names remain absent; generic-method, qualified/compound and type-composition headers are
not inferred. Missing `)` or a parameter name still produces normal diagnostics after accepting a
type completion. Skipping a body uses brace depth and checks cancellation for each token; it keeps
the actual closing-token range for folds. Parser tests check a collector without deduplication,
first-error abort, speculative isolation, mid-body cancellation and independent cloned children.

The first prototype used the generic delimiter-skip helper; review found that helper does not
advance `prev()` for every skipped token. The header scanner now consumes actual tokens so the
retained range reaches the body end. A generic-method negative fixture was corrected from a
redundant-return-list spelling to `<T> void damaged(...)`; this was a test-input issue.

Remaining bounds: multi-return/generic/type-composition headers, qualified/compound type names,
method-formal candidates and name completion. No separator before a later declaration, or complex
default/header syntax with its own braces, can still limit retention. X91–X92 keep shared editor
inputs. Native X92 now verifies diagnostics/Problems/repair, Structure inclusions/exclusions and
the exact fold boundary for both incomplete headers. Its strict folding assertion exposed the
line-only transport defect fixed by `2e98860e1`; the post-fix run passes X92 before stopping later
on explicit popup focus loss. See the [native checkpoint evidence](errs-integration-plan.md#intellij-native-assertion-parity)
for separate run counts and the remaining unexecuted cases.
Final evidence and extraction boundaries are in the [integration plan](errs-integration-plan.md#unfinished-declaration-headers).

## Qualified declaration-type audit (2026-09-26)

C21/L36 extends the preceding header slice with flat dotted type names. The parser transfers the
original NamedTypeExpression into the existing cursor target child; only the final token defines
the edit. No expression validation, semantic field, node-local resolver cache or clone rule is added.
Parser tests check independent cloned targets and one diagnostic with a non-deduplicating collector.
The embedding test checks original source/parent ownership and unresolved passive name bindings.

NameResolver is needed for module/package/class names and aliases, but dotted resolution internally
allows PRIVATE access. Therefore successful resolution alone is not the visibility proof. The query
uses contextual TypeInfo child access and compiler nestmate checks, then checks the resolved identity's
containing components, including ancestors hidden by an alias. This prevents enumerating a private
nested namespace while retaining private types inside the permitted owner. Candidate names are
resolved again through the same written qualifier. Inherited child and typedef candidates are covered.

Tests include bundled XDK/package aliases, public/private/protected/value children, inherited classes,
accepted compilations, unknown/value/private qualifiers with positive controls in the same enclosing
source, unsaved root/member overlays, cached diagnostic isolation, budgets and cancellation. Qualified
queries join the existing retention workload and UTF-16/CRLF packaged transport tests. Shared X93
checks qualifiers, access, final-name edits, absent signature help and repair in both editors.

Empty trailing-dot slots, mid-token cursors, parameterized/compound names, formal/generic-method and
type-composition headers remain outside this proof. A malformed header remains erroneous after a
completion if its parameter name or delimiter is still missing. See the
[verification and extraction record](errs-integration-plan.md#qualified-declaration-type-prefixes).

## Parameterized/compound header audit (2026-09-26)

C22/L37 extends syntax selection to a written leaf inside generic arguments and compound types.
The existing enclosing compiler lookup remains unchanged. Parameterization/constraints belong to
normal type validation; candidate visibility alone cannot certify the enclosing type. Recovery
carries an explicit call-stack flag only through declaration type grammar. Ordinary parsing,
speculative attempts and function/sequence-type interiors do not enable this mode.

Missing angle/group closers are tolerated only when a selected written prefix exists, at bounded
header/outer-delimiter/EOF positions. No empty operand, type argument, declaration or token is
invented. The parser transfers only the original named leaf into the existing target child;
retaining the whole generic type would give the cursor syntax children extending past its range.
The declaration retains the real source extent. Adoption/cloning establish independent ownership,
and the embedding regression checks original source, unvalidated bindings, stopping listeners
and the absence of registered incomplete method/body declarations.

Tests distinguish accepted complete types from accepted names whose missing closers still report
errors. A non-deduplicating parser listener sees exactly one cursor diagnostic. The ordinary parser
control still rejects missing closers. Shared X94, UTF-16/CRLF stdio variants and nested-generic
retention queries extend the consumer proof. See the [verification record](errs-integration-plan.md#parameterized-and-compound-declaration-types).

## Class/interface header audit (2026-09-26)

C23/L39 extends bounded structural recovery to type-composition headers. Reusing a real registered
class with an omitted base would invent inheritance and could register its members in the wrong
scope. Instead, the retained syntax is a `TypeCompositionStatement` subtype with no component.
It remains compatible with member-file assembly and existing outline/folding readers. Compiler
stages defer its body; only a selected header type is queried from the real enclosing scope.

The new cursor list is final and immutable. A specialized constructor-based clone copies/adopts
body and cursor syntax independently, rather than letting reflective cloning replace a final field.
No new mutable field, Context, NameResolver or listener is retained. Tests cover cloning, attempted
list mutation, strict speculative parsing, first-error/cancellation stopping, qualified visibility,
unsaved root/member invalidation, exact edits and ordinary diagnostic repair. The initial qualified
probe exposed NameResolver's requirement that dotted lookup originate at a NameResolving syntax
node; lookup now retains the original type node and skips the syntax-only declaration as a component
scope. Test fixture corrections removed an illegal class-extends-interface repair and compare the
member document cache's diagnostics/symbols rather than module-wide metadata. The first editor run
passed 99 cases but rejected X95's attempt to extend another owner's virtual nested class. The
fixture now makes that base static; the compiler correctly kept the illegal-inheritance diagnostic.

Candidates prove visibility, not inheritance-kind compatibility or generic constraints. Formal type
parameters and the other unproven grammar shapes remain follow-ups. See
[C23/L39](errs-integration-plan.md#class-and-interface-composition-headers) for final verification.

## Generic type completion audit (2026-09-26)

C24/L41 retains the original final token even when the cursor is inside it. Prefix filtering and
whole-token replacement use separate values, avoiding duplicated suffixes after acceptance.
Empty generic slots recognize the lexer's combined closing-angle tokens without changing ordinary
parsing. The initial batch exposed nested `>>` recognition and duplicate method-formal candidates;
the parser recognizes all peelable closing-angle forms, and Kotlin deduplicates the same formal
symbol's local/type representations while preserving real value shadowing. A Type-valued local
or parameter is not promoted to a formal type merely because its value is a Type; the register
identity must match the compiler's method formal.

Parameterized qualifiers are resolved only on disposable compiler-owned copies, with collecting
PROBE listeners for each lookup. Ordinary source diagnostics remain separate. Visibility includes
the written qualifier's ancestors and nested children; failure never falls back to local types.
Registered class/method formal identities are reused; unregistered declaration-header formals stay
unsupported. No new mutable AST state is introduced. The substituted type is an explicit candidate
fact rather than an LSP reconstruction from names. See the
[C24/L41 scope and verification record](errs-integration-plan.md#generic-type-completion-batch).

## Five-area integration audit (2026-09-26)

The combined C25–C26/L42–L46 run exposed and corrected two argument-query regressions: outer-call
facts must not replace the inner member's receiver, and multidimensional array brackets must not
be fitted as ordinary positional constructor arguments. Workspace queries also normalize URI aliases
for unopened sources while preserving the chosen source view's output URI. The new import fixture
uses an actual XTC library declaration; compiler diagnostics were not relaxed.

Both shared catalog guards now include X97/X98. Full validation passes 495 executed Java tests,
1,161 executed LSP tests and all 51 packaged stdio cases; existing skips remain 40/3/0 respectively.
VS Code X94–X98 pass, with 98 cases explicitly not selected. Both editor drivers compile; native
IntelliJ remains deferred. All 24 distribution artifacts are in the production module index, and
library-resolution/replacement/rename boundaries are tested against that same bundle. See the
[commit map, AST placement and remaining limitations](errs-integration-plan.md#five-area-functionality-batch).


## Rename contract and host-boundary audit (2026-09-28)

The five-checkpoint rename batch reuses existing compiler facts for primary-header properties,
lambda source bindings and positional method-value calls. No Java AST state, hook, public API or
clone protocol is added. `COMPILER-141` independently verifies that function values do not export
named argument spellings; the previous blanket method-escape refusal was too conservative.
Composed parameter proof now follows the written callable contracts and preserves their slot.

Explicit sourceModules roots can include external consumers. A detached proposal scope records
the configured/discovered input boundary and revision; missing registered roots and changed disk
snapshots cannot silently reduce the proof. Unknown consumers remain outside that boundary.
Native testing corrected a VS Code scope guard: a single-folder workspace's own settings are
also exposed as folder values, while saved multi-root folder overrides require separate refusal.

Validation passes 94 LSP tests, six IntelliJ configuration tests, five pure VS Code settings tests,
and selected X57/X118–X121/CFG1–CFG3 in both editors (plus IntelliJ START and zero IDE errors).
See the [receipts and extraction map](errs-integration-plan.md#checkpoint-and-validation-map).
These are selected runs, not a new full 126-case receipt. Unsupported generated/union/native
dispatch fixtures and native multi-root/racing-settings acceptance remain explicit gaps.
The later [structural checkpoint](errs-integration-plan.md#next-checkpoint-isolate-partial-ast-syntax)
implements the bounded four-node `ast.partial` move and documents why semantic helpers remain in `ast`.


## Editor settings audit (2026-09-30)

The UI1–UI7 batch found an unused VS Code `inlayHintsEnabled` initialization field, missing live
formatting notifications, inert legacy tab-width/line-width controls, and unguarded ordering of
formatting configuration replies. Inlays now use native provider filtering; formatting changes
notify the existing connection; a revisioned immutable formatter state rejects stale/invalid replies.

IntelliJ LSP4IJ 0.21.0 has `didSave` but no native `willSaveWaitUntil` implementation. The plugin
explicitly disables server-save selection and uses native Actions on Save. VS Code supports server
save edits and suppresses that hook when native formatting owns the current document's save.
Settings-only project overrides were also checked against inherited compiler graphs, so preference
changes cannot steal source graph ownership. Combined validation follows the five local commits.

Editor acceptance found that `XdkAdapter` inherited the interface's no-op formatting setter:
the server reported the requested indentation while the adapter used request defaults. A final
atomic holder now carries the immutable preference into document, range and on-type formatting;
tests also verify resetting to request defaults. Presentation-only notifications preserve pending
compiler configuration and do not schedule compilation. LSP4IJ's unsupported save-hook flags are
cleared during initialization, alongside the existing disabled save-owner control.

X137's VS Code delay was an execute-command symbol cache in the harness, not slow compilation:
the captured worker dump was idle, the measured compile took 62 ms, and the corrected case takes
3.3 seconds for both transport restarts. Native save acceptance must invoke Save All/Save Document
actions; calling `FileDocumentManager.saveDocument` directly does not trigger Actions on Save.

Final selected acceptance passes X118/X132/X135–X139 in both editors and IntelliJ START with no IDE
errors. The final correction checks pass 27 backend tests and 73 IntelliJ unit tests, alongside
the earlier full backend/packaged/VS Code extension results. Exact receipts and remaining manual
boundaries are in the integration plan's editor-settings checkpoint.


### Mutable-state and deprecated-API audit (2026-09-30, checkpoint)

Reviewed the LSP server's document/publication lock, detached resolve/diagnostic/token stores,
compiler queue snapshots and configuration revisions; IntelliJ connection ownership, root leases,
startup/diagnostic transport bridges and settings callbacks; VS Code startup/restart state, build
model/watch maps and configuration/rename flows; Tree-sitter parser/index lifetimes. Function-local
builders are not shared state. A concurrent map alone does not protect compound lifecycle changes.

Concrete fixes in this batch:

- Pending ordinary readers now share semantic-query ownership: cancellation does not cancel shared
  analysis, and close retires the public request even if analysis ignores cancellation.
- IntelliJ diagnostic replies recheck request ownership after the unlocked editor snapshot lookup.
  Close/cancel can no longer reinsert a retired result/owner into the cache.
- Tree-sitter scan coordinators no longer join child jobs submitted to the same bounded executor.
  Parsing was already serialized, so the nested scheduling added a starvation/shutdown hazard.
  Native parsing and disposal now share ownership locks; post-close reindexing is ignored.
- VS Code connection callbacks and rename middleware retain their owning connection. A retired
  connection cannot restart itself or replace the current connection's status; crash counters are
  scoped to each connection.
- IntelliJ compiler-settings reports use a revision guard, including disposal, so an older reply
  cannot overwrite a newer refresh. Server formatting settings likewise reject replies after close.
- Server configuration requests require the client's advertised `workspace.configuration` support.
  Minimal clients receive neither configuration nor unnegotiated refresh requests.

The protocol progress collections are confined to a single dispatcher. Refresh uses one atomic
running/dirty pair per provider, sends outside compiler locks, and coalesces changes while awaiting
a reply. These ownership rules require regression testing; they are not a claim that every
possible interleaving is proven safe. The three follow-up concerns below now have implementations and controlled regressions;
validation of the new batch is pending. Selected editor restart/settings checks exercise the normal flows; they do not exhaustively
schedule every late callback or settings race.

Follow-up checks, with controlled interleavings rather than timing sleeps:

- [x] Implementation: `WorkspaceIndexer` / `TreeSitterAdapter`: make open-buffer content win over a scan that read
  an older disk snapshot; cover edits during initial indexing and close/reopen.
- [x] Implementation: VS Code `compiler-paths.ts`: reject or merge a stale path-editor draft if source-module
  settings change while its Quick Pick/input dialogs are open.
- [x] Implementation: `ResourceFileWatchers`: bound stalled registration/unregistration replies and retire the
  queue on disconnect without allowing an old registration to replace current watch ownership.

Deprecated API inventory: no Kotlin suppression remains for legacy LSP roots. `rootUri`/`rootPath`
are protocol-deprecated compatibility inputs only; current clients should send `workspaceFolders`.
The TypeScript compiler API found no selected deprecated call signatures: OutputChannel.show and
assert.fail have deprecated overloads, but our calls select supported overloads. Hover's inherited
MarkedString union is still accepted as input by VS Code. Deprecated formatter configuration keys
are compatibility aliases explicitly marked in the manifest, not deprecated implementation calls.
Fresh nonincremental JVM compilation of the DAP server, DSL and IntelliJ production/unit/integration
sources passes without deprecation warnings. TypeScript compilation passes. The VS Code run still
logs Node `DEP0169` (`url.parse`) from editor processes; no call exists in the extension's own
source/scripts, and the warning does not identify its dependency stack. IntelliJ's launcher also
logs JNA native-access restrictions. Neither is evidence of a deprecated Kotlin call; neither is
silenced by this audit.

The [protocol validation record](errs-integration-plan.md#protocol-hardening-batch-l80l81-2026-09-30)
records the full backend run and focused fixture correction, 73 packaged tests, 74 IntelliJ unit
tests and selected X136/X137/X140/X141 in both editors. Native parser/index concurrency regressions
ran without skips. The first Unicode fixture also exposed missing semantic facts in constant-folded
property initializers, independently of character encoding; L83 records the compiler ownership
follow-up. That earlier receipt does not cover L83; the subsequent detached-initializer implementation
and X142 acceptance are recorded in the current follow-up validation receipt.

### Follow-up: bounded resource watcher replies

Registration and removal acknowledgements now have a ten-second bound. A late successful
registration is removed by its unique ID; a removal timeout retires its IDs so later roots
cannot reuse a subscription still being removed. Disconnect releases outstanding and queued
updates. Controlled-future regressions cover each interleaving without elapsed-time sleeps.
The controlled regressions pass in the 51-test focused backend run.

### Follow-up: compiler path dialog ownership

VS Code path edits and reset now validate the captured settings, workspace and connection before
writing. New dialogs, configuration/folder changes and model watcher events invalidate older
drafts; disposal retires them too. The resulting message asks the user to reopen the dialog rather
than replacing newer settings. Extension regressions exercise invalidation, actual settings changes
and disposal. All three regressions pass in the 23-test VS Code extension run.

### Follow-up: disk indexing and editor overlays

The Tree-sitter indexer now owns per-file revisions under its existing native-parser lock. Disk
reads publish only into the revision that requested them, and scans/watcher events cannot replace
an open buffer. Compilation indexes open buffers during initial scanning too. Closing restores
the current disk contents (or removes a deleted/untitled file), while reopening invalidates an
outstanding close read. Controlled read barriers cover these interleavings. No additional parser
or AST locks are introduced. The controlled regressions pass without skips.

### L80 wire-format and edit application audit

- Completion kinds now fall back to the advertised set; completions explicitly use plain text.
  No snippet, insert/replace, annotation or draft snippet-edit payloads are emitted.
- Code-action literals require negotiation; preferred metadata requires its own capability.
  `context.only` matches descendants and the empty root kind. The protocol explicitly lets literal
  clients handle unknown kinds gracefully; their `valueSet` is not an action suppression list.
- Legacy command clients with `workspace.applyEdit` get one-use bounded action handles. Execution
  checks the connection's diagnostic revision before sending an edit, rejects replay/stale handles,
  handles client refusal and bounds the reply. Clients supporting neither form get no action provider.
- Resource renames require `documentChanges` and the rename resource operation. Text edits precede
  moves, never overwrite destinations, and never degrade into unversioned edits. The client owns
  its advertised `failureHandling` policy (abort/transactional/text-only-transactional/undo); the
  server does not assume an edit succeeded or update its source graph on proposal. File/document
  notifications remain authoritative. No server rollback guarantee is implied.
- Diagnostics/symbols currently emit no tags; no tag capability is needed until producers add them.
  Location results remain ordinary locations, valid with or without location-link support.
- IntelliJ selected lazy actions and native Rename/Move use `XtcRenameEdit` snapshots, checked in
  the write command. Generic server-initiated text edits now reuse `DocumentStartupMessages` sent
  versions/text/ownership and recheck all targets in one native Undo command. Closed versioned
  documents and ambiguous reused versions after reopen are refused. Resource, snippet and
  confirmation edits remain outside this generic path. `TODO LSP4IJ:` documents the upstream
  version-validation gap in `XtcClientFeatures`. New X144 and unit regressions pass;
  its actual receipts are recorded below, independently of the older rename/action checks.


Regressions and updated realistic client fixtures pass in the focused backend and packaged stdio suites.

Validation-review correction: unacknowledged watcher removals retain retry ownership separately
from active subscriptions. Late acknowledgements retire only their IDs. File URI spelling
(`file:/` versus `file:///`) is normalized for disk/overlay ownership. The VS Code draft invalidation
event is fired before its emitter is disposed.

### L80/L81 native acceptance and L82 lexical workload follow-up

Native generic edit guarding is implemented in `992b47f26`: the existing transport lock owns
sent versions/text/epochs; immutable edit plans cross to the EDT, which rechecks every target
before writing. No private version accessor, duplicate counter, new AST field or compiler API
is introduced. X144 passes in VS Code `run-4fS87C` and IntelliJ
`run-9059436229437082618` (current edit, native Undo, all-target refusal for a stale batch).

Shared X145 was added in `7a312dd9d`. Harness corrections observe the actual SDK progress model,
match the human title, and retry only ContentModified reads after delayed fixture-create watches.
The current 5,000-method workload passes IntelliJ `run-5136524067786146169` in 7.6 seconds;
the current workload also passes VS Code `run-06Z6tq` in 16.0 seconds after that correction.
Cancellation uses the native progress model/token, not a synthetic server cancellation or sleep.

Live stack sampling identified repeated whole-source line scans in lexical resource detection
and semantic-token projection. `3987e26c1` creates immutable line indexes once per projection;
three large UTF-16/newline regressions and eleven existing presentation/resource tests pass.
No cache, lifetime ownership or AST/embedding changes are needed. Full catalogs are in progress.

### Native Save All and fixture cleanup follow-up (2026-09-30)

The long IntelliJ run exposed error notifications that the IDE fatal-error collector does not see.
LSP4IJ 0.21.0's `LSPFormattingSupport.format` passes a nullable editor through request creation
but calls `editor.getDocument()` when applying the reply. Dirty closed tabs therefore fail during
Save All. The Ecstasy formatting service uses the captured document and native asynchronous edit
application instead; X139 adds that closed-tab regression. Keep this workaround with its
`TODO LSP4IJ:` marker until upstream fixes the nullable-editor path.

Separately, completed parity scenarios left dirty, closed documents in IntelliJ. Later save actions
and shutdown could touch those fixtures, including the X127 File Cache Conflict captured by the
user. Cleanup now explicitly discards only the completed scenario's dirty documents. It does not
silence conflicts or discard edits in an active scenario. X57/X126 no longer require a started
server to observe a completed close, and X143 collects its token's actual wire batches through a
scoped lifecycle listener instead of counting a rolling console. The focused seven-case run
`run-5152567950207711961` passes with startup, zero IDE errors and a successful Gradle exit.
The failed full/resume receipts remain in the integration plan.

### No-op directory watches during incomplete queries (2026-09-30)

The full VS Code run ends with 126 passes and 24 failures. Its trace shows delayed directory-created
events cancelling incomplete completion/signature requests with ContentModified. The watcher proof
used disk text for unsaved sources and rejected every failed analysis, including current analyses
with valid captured inputs. Compare those inputs with the open overlay instead; retain conservative
invalidation for actual changes and fallback builds that predate a dependency failure. Controlled
in-flight cursor tests cover both no-op preservation and real-change cancellation, alongside resource
and dependency recreation regressions (38 focused tests pass). X130 independently needs Explorer
keyboard focus before verifying a multi-folder selection. Corrected full VS Code `run-06Z6tq`
passes all 23 previously failing cursor cases, with only the host repaint failure below remaining.


X130 follow-up: the pinned VS Code 1.140.0 host can throw while clearing Cut highlighting after a
successful move. Its bundled `Lln` Paste handler calls `setToCopy([], false)` in `finally`, which
passes retired source nodes to `itemsCopied` and unconditionally rerenders them. The full
`run-06Z6tq` reproduces this after the compiler-proven move; native Undo/Redo and all resource
contents pass before the test rethrows the host exception. This remains an explicit native UI
failure, not a compiler failure or a green full-catalog receipt. No VS Code binaries are patched.


Native harness ownership follow-up (`05841a78a`): `OpenedDocument` can exist before its nullable
synchronizer is installed; startup waits now model that lifecycle explicitly. Also, focusing an
editor component does not necessarily retire the active Find Usages tool window. X105's server
reply contained the correct import action, but the native action used the earlier preview's context.
The driver now activates the editor area without moving the pointer and checks editor data identity
before native actions. X100/X103/X105 pass together in the resumed run; complete receipts follow
in the integration plan. These changes belong to the test driver, not the compiler or client API.

Final verification: uninterrupted IntelliJ `run-1843149430446112481` passes all 150 scenarios
plus startup, reports no IDE errors and shuts down successfully. X139 exercises the corrected open
and closed Save All paths; X127 cleanup no longer leaves a shutdown conflict. Current backend and
packaged transport results are 1,504 passed plus three existing disabled tests, and 74/74,
respectively. The full VS Code run remains **149 passed / 1 failed**, not green: X130 retains
the host repaint exception described above. Smoke fixture/settings isolation passes 23/23;
both VS Code runners now share a status counter, with selected X144/X145 also passing. Detailed
receipts, commit separation and remaining acceptance are in the integration plan.

### Late UI reports and reliability follow-up (2026-10-01)

VS Code's effective-configuration command awaited a server reply then unconditionally cleared and
repopulated its output view. A slower previous command, settings change or retired connection could
therefore publish obsolete data. Its UI-owned generation now changes on requests, configuration
changes and disposal; publication also requires the same active connection. Shared X147 holds a
real reply until a newer report/settings/restart has completed, then verifies the old command returns
without publishing. The old client's close callback must not restart it.

IntelliJ already had a request/disposal revision guard. It now also captures the settings content,
and its publication guard rejects a retired server connection. The real settings component
is driven with controlled report futures; each assertion waits for an EDT barrier after publication,
so it cannot pass before the stale callback runs. Its injected reader is a UI data boundary, not a
new compiler API or mutable AST field. X146 observes actual provider refresh after a dependency edit
and checks the untouched consumer's inferred type in both hosts. Both scenarios now pass in each
editor; the integration plan preserves the initial native failure and focused correction receipt.

The platform workload uses only unsaved overlays and asserts source hashes remain unchanged.
Heap sampling comes from the JVM management bean in the nonblocking service-status response;
queue metadata remains copied under its existing lock. The sampler does not submit compiler jobs,
force GC or alter scheduling. Sampled peak heap is not a retained-memory or RSS measurement.


Native X147 exposed that `CompilerSettings.content` can retain global graph ownership after a
service-only project setting changes. Capture effective language-service settings as well as graph
settings. Capture connection identities at request start and recheck them at EDT publication; checking
only when a reply arrives leaves a second restart window. X147 exercises both windows with the real
server, a controlled report future and an EDT barrier. The correction passes X136/X137/X147 plus
startup with no IDE errors. No additional mutable field or compiler API is introduced.

The packaged platform baseline passes 30 cycles/cancellations across three server processes, with
source hashes unchanged and no forced termination. Trace aggregation records compiler API timings
and readable queued jobs in addition to periodic heap/queue samples. All observed compiler API
calls remain serialized. Exact measurements and their sampling limits are in the integration plan.
X130 passes the selected VS Code run, while the corrected extension-free probe records
`not-reproduced`; the earlier full-run repaint failure remains unresolved.


### X130 host isolation (2026-10-01)

Two fresh empty-extension probes reproduce the original `itemsCopied`/`setToCopy` exception when
Refresh Explorer runs during an asynchronous rename participant. Ecstasy is absent, no LSP process
runs, and the participant returns no edit. Cut retains old tree items across the refresh; after
Paste moves the current items, unconditional repaint of the old ones fails. The normal X130 trace
also proves the compiler returns an empty `documentChanges` list. Native Move/Undo/Redo and every
resource check succeed before the test reports the host exception.

The latest checked release (1.140.0) and upstream main retain this code. There is no supported
extension API to repair its private tree or cleanup. The safe follow-up is an upstream fix and the
same controlled regression; no host binaries, clipboard behavior or native actions are patched.
X130 remains failed when reproduced. The [diagnosis and receipts](errs-integration-plan.md#x130-isolated-host-defect-and-harness-focus-correction-2026-10-01)
include commands, the pinned source link and all failed/passing attempts.

Separately, six Undo failures in the initial preceding-case run disappear after requiring native
window focus before dispatch. The harness uses the host Focus Window command without moving the
pointer, waits only when focus was absent, and never retries completed edits. X118–X129 then pass;
X130's remaining failure is the independently reproduced host repaint defect.


### L80 final capability contract audit (2026-10-01)

This audit maps the current response producers to their advertised providers and client gates.
It does not add optional protocol features merely to make the capability set larger. The compiler
adapter remains opt-in; Tree-sitter remains the shipping default. No compiler, AST or embedding
API change is needed for these wire-format corrections.

Three missing response-field gates were fixed:

- Document-link tooltips now require `textDocument.documentLink.tooltipSupport`, both eagerly
  and after `documentLink/resolve`. Targets and bounded resolve handles retain their existing
  behavior. See the [document-link contract](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#documentLinkClientCapabilities).
- Per-signature active parameters now require
  `textDocument.signatureHelp.signatureInformation.activeParameterSupport`. The legacy top-level
  active parameter uses the selected overload's mapping, so named-argument highlighting survives
  for clients without the newer field. String parameter labels remain valid without offset support;
  the server does not produce the 3.18 explicit-null/no-active-parameter form. See the
  [signature-help contract](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#signatureHelpClientCapabilities).
- Pull reports now gate `Diagnostic.relatedInformation` independently of push diagnostic support
  and `relatedDocumentSupport`. The choice covers document items, related-document items on full
  and unchanged reports, and workspace items before partial-result batching. Suppression retains
  the error message and its external source URI. See the
  [pull-diagnostic contract](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.18/specification/#diagnosticClientCapabilities).

The compiler-only `xtcRenameProposal` experimental advertisement is also absent from other
adapters now. A stale source-comment matrix naming implemented features as missing was removed;
client capability logging and the maintained feature matrix describe the current implementation.

| Methods / producers | Capability contract and current limits | Regression evidence |
|---|---|---|
| Initialization and provider inventory | Each of the 25 adapter flags controls its corresponding provider. A featureless adapter exposes only synchronization and UTF-16. Compiler-only proposals, pull diagnostics and workspace file operations have separate gates. Unimplemented providers, including notebooks, remain absent. | `CapabilityContractTest` exercises every flag individually, no flags, and the real compiler inventory; `ProtocolLifecycleTest` covers lifecycle order. |
| Document synchronization and workspace roots | Full text by default; incremental transport is an initialization option. Save hooks require client support. Explicit workspace folders, including empty folders, precede legacy URI/path roots. Positions remain UTF-16, which clients must support; no UTF-8/UTF-32 encoding is advertised. | `DocumentSynchronizationTest`, `ClientPresentationTest`, packaged `XdkStdioTest`, X137/X140. |
| Push and pull diagnostics | Push versions and related information are independently gated. Pull mode, related documents and related information are independently negotiated; workspace report versions are part of that protocol. Messages are strings; no tags, code descriptions, markup messages or diagnostic data are emitted. | `DiagnosticPresentationTest`, `XdkPullDiagnosticsTest`, packaged pull/partial-report tests, X123. |
| Hover and completion | Hover follows the first supported client markup preference, with plain-text fallback. Completion kinds fall back to the client's set or legacy kinds. Completion documentation is a string; insertion is plain text and ordinary `TextEdit`, without snippets, insert/replace edits, item defaults, tags or label details. Documentation-only resolve requires that property. | `ClientPresentationTest`, `CapabilityNegotiationTest`, `XdkResolveProtocolTest`, completion stdio cases, X131/X140. |
| Signature help | Global active parameter remains available; per-signature mapping is gated. Parameter labels and documentation are strings. No offset labels or explicit-null parameter values are produced. | `CapabilityNegotiationTest`, `XdkCursorServerTest`, rich/minimal packaged stdio cases. |
| Definitions, declarations, type definitions, implementations and references | Producers return ordinary locations, not location links. Link support does not need negotiation until a link producer is added. Partial results preserve the negotiated request's result shape. | `XdkLanguageServerTest`, `PartialResultsTest`, navigation stdio and shared navigation cases. |
| Document/workspace symbols and type/call hierarchies | Document symbols flatten unless hierarchy support is present. Document/workspace kinds use their respective supported sets; no symbol tags or outline labels are emitted. Hierarchy items use their protocol's standard kinds and opaque detached identity. | `ClientPresentationTest`, `XdkFeatureResolveProtocolTest`, hierarchy stdio cases. |
| Semantic tokens | Full relative UTF-16 tokens use the advertised legend. Range and delta require the corresponding request capabilities; refresh is separately gated. Producers use single-line tokens and remove lexical overlaps with semantic spans. No capability-dependent multiline representation is emitted. | `XdkSemanticTokenProtocolTest`, `XdkPresentationTest` overlap/recovery checks, packaged delta/range cases, X126. |
| Selection, highlighting, folding and linked editing | Standard ranges/kinds only; no collapsed-text payload. Folding's range limit is advisory and currently ignored; line-only clients ignore character offsets as specified. Selection nesting and linked ranges have no optional wire variant in use. | Structural adapter/server tests and existing shared editor cases. |
| Formatting and save edits | Ordinary text edits for whole-file, range/ranges, on-type and negotiated save hooks; no annotated or snippet edits. Multi-range support is explicitly advertised. | `DocumentSynchronizationTest`, formatting stdio cases, X132/X138/X139. |
| Actions, commands and workspace edits | Literals and preferred metadata are independently gated; `context.only` includes descendants. Legacy actions require `workspace.applyEdit` and one-use handles. Versioned/resource renames require their edit capabilities; client failure-handling policy is not a server rollback promise. No disabled/action-tag/annotation producer exists. | `CapabilityNegotiationTest`, `XdkResolveProtocolTest`, file-operation protocol tests, X127/X128/X144. |
| Links, lenses and hints | Link tooltip support is now enforced. Traditional link/lens resolve and newer property lists retain eager fallback. Inlay `MarkupContent` is part of the base inlay protocol, with no separate markup capability; tooltip deferral requires resolve support. Run commands are editor commands, not advertised server execute commands. | `CapabilityNegotiationTest`, `XdkFeatureResolveProtocolTest`, packaged link checks, X131. |
| Watchers, configuration, progress and refresh | Watchers wait for `initialized` and dynamic-registration support; relative patterns are gated. Failed/late registration and removal retain correct retry/retirement ownership. Configuration and refresh requests require support. Work-done creation waits for readiness; partial results require request tokens. | `ResourceFileWatchersTest`, `ClientPresentationTest`, `ClientNotificationsTest`, `ConnectionProgressTest`, `PartialResultsTest`, X141/X143/X145. |
| Native edit application | IntelliJ versioned text edits check all target ownership before one Undo command. Generic resource/snippet/confirmation edits remain explicitly refused; native Rename/Move owns its supported resources. | Existing `XtcRenameEdit`/client ownership regressions and X144; no new native handler in this batch. |

The capability tests exercise absent, false and true flags, eager/resolved links, selected-overload
legacy fallback, independent push/pull metadata and cached related-document reports. Real packaged
sessions cover both rich and reduced presentation capabilities. The installed editor drivers cannot
change their client's initialize capabilities midway through a session, so these reduced-client
checks belong to protocol tests rather than a new shared UI scenario. X131's existing drivers check
link targets, not an unconditional tooltip field.

Validation receipt and extraction checkpoint are recorded in
[the integration plan](errs-integration-plan.md#l80-final-capability-contract-audit-2026-10-01).
The audit closes the current producer/capability review; future producers must extend this inventory.
L81 manual acceptance, L82 release evidence and the independent upstream X130 failure remain open.


## L81 progress and connection audit (2026-10-01)

The [L81 checkpoint](errs-integration-plan.md#l81-progress-refresh-and-transport-checkpoint-2026-10-01)
records the late progress-create acknowledgement fix, timer retirement, per-connection cancellation,
all-five-provider refresh checks, malformed reader recovery and two-process semantic isolation.
Progress details use copied queue metadata: file/workspace, active compiler job and pending count.
They do not acquire compiler/document locks or retain ASTs. The dispatcher still owns progress maps;
a delayed report rechecks the exact live entry before sending or rescheduling.

The [upstream issue register](errs-upstream-issues.md) now collects all existing LSP4IJ TODO sites,
underlying LSP4J defects and the independently reproduced VS Code X130 failure under stable UP IDs.
The malformed typed-parameter case uses named LSP4J error codes. UP15 remains incorrect upstream
classification even though the same-reader recovery test passes. Native Cancel/multi-window acceptance
is recorded separately from protocol coverage and must not be inferred from unit or child-process tests.

IntelliJ's current visible-control X145 passes with zero IDE failures; VS Code's physical-click
check remains separate from its automated SDK callback. A diagnostic 20,000-method IntelliJ
fixture exposed a 21.3-second range-marker update freeze during bulk replacement. The failed
receipt and L82 investigation remain recorded; the bounded 5,000-method pass does not close it.

The focused [L82 investigation](errs-integration-plan.md#l82-large-file-intellij-freeze-investigation-2026-10-01)
now isolates the interval-tree bottleneck without any LSP client: plain documents with 20k/40k/80k
markers take approximately 0.3/1.2/5.6 seconds to replace the same tail. The guarded native
reproduction counts 80,005 semantic highlighters and fails the IDE freeze gate after a 14.6-second
replacement. UP17 belongs to IntelliJ Platform, not compiler locking. Bulk mode does not remove
the cost; neither a smaller fixture nor replacing before highlights arrive counts as a fix.

L67 presentation follow-up: the separate packaged-server workload reproduces quadratic token
overlap checks and repeated whole-model lookup for inferred-hint tooltips. Request-local sorted
coverage and rendering from known declaration facts remove those scans without additional AST
state or compiler APIs. One-line hint requests now filter before rendering. Fifteen focused tests
pass; four large-file server sessions pass with normal process exit. The
[measurement receipt](errs-integration-plan.md#l67l82-semantic-response-measurements-2026-10-01)
separates cold project recompilation, warm query work and serialization/output. UP17 remains open.
### Native lifetime acceptance follow-up (2026-10-01)

The L81 native lifetime drivers now pass: two project frames in one IntelliJ instance, and two
normal VS Code instances with separate profiles. Close overlaps compiler work and a pending request;
the closed PID exits, reopening starts a new PID, and the sibling retains correct replies and its
unsaved source. IntelliJ saves the closing project; VS Code restores its actual unsaved backup.
Development-host VS Code restoration attempts failed because that host intentionally has no persistent
backup path; the corrected test uses a disposable installed-extension profile. IntelliJ trusts only
the generated fixture path so opening cannot block on the trust dialog. See the
[receipts and extraction boundary](errs-integration-plan.md#l81-native-projectwindow-lifetime-batch-2026-10-01).
Physical VS Code Cancel selection remains unverified; a completed uncanceled request is recorded as a
failed attempt, not a passing cancellation check. Shared-process VS Code windows and cross-platform
release coverage remain separate gates.

L82's extended platform run completed 2,400 successful edit/cancel/query cycles across two compiler
processes, including expected EOF retirement. The third planned session was deliberately interrupted;
the aggregate report remains failed/interrupted. Both completed sessions show post-GC heap rising from
about 83 MiB at cycle 100 to 98 MiB at cycle 1,200. Investigate that repeated growth before claiming
bounded retained memory; no plateau or particular leaking owner has been established. The
[bounded workload receipt](errs-integration-plan.md#l82-bounded-extended-workload-checkpoint-2026-10-01)
records timings, sampled RSS and deferred combined/full-editor gates.

Functionality continuation: import-producing completion reuses whole-graph repair proof, with
plain-text atomic additional edits and cancellation across both stages. No AST state or embedding
API change. Backend/protocol tests and the new X105 variants pass in the combined/selected gates.

L65/L62 continuation follows existing compiler redirect metadata for implementation lookup and
written rename families. Real manual mixin/delegation fixtures now exercise these paths, including
reverse edits and refusals. No AST modifications, optimized-body generation or new mutable state.

L63 adds exact-selection literal-return extraction entirely on the Kotlin LSP side. Ordinary Java
parser ranges locate the edit; complete compilation and binding/dispatch proof gate publication.
No new AST classes, mutable fields or compiler public APIs. X148 acceptance and unit tests added.

Manual-module validation found and repaired missing proof identity for predefined this receivers
and incorrect declaration classification of conditional-incorporation formal names. Receiver facts
are derived from existing register metadata; no mutable builder field or AST support was added.
The restored build passes the 188-test regression gate, followed by 34 lookup/dispatch and 28
lifecycle/protocol tests. Both editor additions pass in selected runs. See the functionality
checkpoint map in errs-integration-plan.md for commits, failed development attempts and receipts.


L64 syntax completion stays entirely on the Kotlin side: Java lexer/parser ranges and block ownership
produce detached naming/keyword/template suggestions. No compiler AST change or public embedding API
is needed. Snippet insertion and indentation are negotiated independently from semantic completion;
new X149/X150 pass in both editor drivers. The 142-test regression gate, final 25-test focused gate
and four IntelliJ capability tests pass without skips. Native testing exposed LSP4IJ's ignored AsIs
snippet mode; UP18 records the capability constraint and its removal gate. Validation receipts and
remaining L64 boundaries are tracked in errs-integration-plan.md.


L64 empty-name recovery now retains complete written named types in required property/parameter
name slots, including primary constructors and EOF. It uses the existing partial cursor node with
no new field, invented declaration name or semantic binding. The parser owns grammar recognition;
Kotlin owns name spelling, collision avoidance and empty-range edits. Ambiguous bare local
expressions stay excluded. The AST inventory in errs.md records placement and clone ownership;
42 Java and 148 LSP tests pass without failures or skips, and shared X151 passes in both editors.
Detailed receipts and the remaining L64 boundaries are tracked in errs-integration-plan.md.


L64's real-source continuation checks the platform CircularBuffer class, not only minimized
invented modules. It exposed deferred anonymous-body discovery and trial-body identity loss,
plus operator-boundary recovery. The fixes preserve ordinary ownership and use existing capture
context lifetime under try/finally. The new proposal-only token fixes empty-span numeric spelling;
all accepted argument spellings are checked by ordinary compilation. Shared X97/X108/X150/X151
are extended and X152 is registered in both drivers. See the latest integration-plan receipt for
completed validation and the explicit conservative boundaries; no full editor catalog run is implied.


The L64 closure inventory covers all original completion/signature topics with explicit supported
forms and refusal boundaries. New tests exercise immutable fact updates, ordinary enclosing values,
missing inferred-local syntax/cloning, compiler-fitted lambda arities, real platform callbacks and
minimal/rich-client snippets. X150/X151/X152 are extended in the common catalog and both drivers.
The L64 gate passes 68 Java and 418 LSP tests; the L65 gate passes 78 tests, all without failures
or skips. L65 found and fixed missing conditional implementations on concrete validated source
types; accessor redirects and binary debug-span refusals have direct regressions. X153/X154 cover
the corresponding native navigation/classification behavior. L64/L65 shared cases have passing receipts in both editors. Full VS Code still exposes UP16/X130;
IntelliJ covers all cases across a full attempt and continuation, retaining the first run's X105
popup timeout as a stability follow-up. See errs-integration-plan.md for exact hashes and receipts.

October 2 L63 proof update: returned-expression extraction and adjacent single-use returned-local
inline use the existing Java parser/AST read APIs and detached semantic graph. No new AST fields
or public compiler accessors were needed. Exact source relocations are tracked by the Kotlin edit
plan, and compiler facts verify preserved bindings/dispatch after recompilation. The adversarial
same-spelling/different-parameter relocation is rejected. The focused extraction/inline/proof
batch passed 31 tests without skips; shared X156/X157 editor execution remains pending.

October 2 L66/L67 continuation: resolved import source links are copied into immutable Kotlin
semantic views while the compiler worker owns the attempt; stale text and absent source indices
produce no guessed link. Explicit aliases reuse the compiler-proven lexical ownership facts.
The graph join audit also found that reconstructed views dropped lambda signatures; the follow-up
preserves them and tests binary source-index/source-graph replacement. No Java AST changes were
needed. L66's 15 focused tests and L67's 20 lifecycle/dependency/navigation tests passed without skips.
The replacement regression exposed and fixed missing host-indexed binary source URIs for unopened
workspace navigation. Module navigation already carried those URIs; both now use the same conversion.

October 2 validation receipt: the combined continuation passes 204 LSP tests, zero failures/errors/
skips, plus LSP/IntelliJ formatting checks. X155–X158 pass in VS Code `run-Um9auo` and IntelliJ
`run-13592235693442712133` (START also passes, zero IDE errors). These selected receipts supersede
the pending editor notes above; broader feature and release gates remain open in the integration
plan. No Java AST changes were introduced by this continuation.

The subsequent L62 boundary audit adds real compiler regressions for conditional members without
formal host declarations, closed consumers, separate conditional families, compiling-but-unsafe
contract merges and nested generic method/property delegates. Shared X159/X160 use the existing
rename drivers to check cross-file changes and Undo. No compiler or AST API extension was needed
for these cases; they were missing coverage. All 78 tests across 11 audit suites pass without
failures/errors/skips; both drivers compile and formatting checks pass. X159/X160 pass in both
editors (VS Code `run-RGeVCM`; IntelliJ `run-17726972701009018455`, including START and no recorded
IDE errors). The earlier IntelliJ timeout was blocked by macOS permission dialogs visible in its
saved screenshot; no permission choice was made and no completed edit was replayed. Cross-package qualification
rewrites, explicit graph relocation and richer union/generated/cyclic callable identities remain
implementation tasks in the integration plan.


October 2 L62 type relocation follow-up: same-name class files can now move between compiler-proven
packages in one module. Detached compiler name/namespace facts drive import and qualification
edits, including closed consumers and old-package sibling references. Companion sources/resources
follow the move. Prefix changes have a narrow proof allowance; retained bindings, calls, import
targets and dispatch must remain equivalent. No Java AST or embedding API changes were needed.
The final 118-test backend gate passes (17 suites, zero failures/errors/skips), including the
organize-import regression corrected by `2ad97134f`; X161 Move/Undo/Redo passes in VS Code `run-KieFUI` and IntelliJ
`run-15583928886685829346` (including START, no IDE errors). The following graph-relocation checkpoint extends this scope. Interacting batch qualifications
and richer union/generated/cyclic callable identities remain open; see `errs-integration-plan.md` for limits.

October 2 L62 graph relocation follow-up: `xtc/renameFiles` carries complete move operations and
source/resource graph before/after values. Both editor integrations persist the replacement using
the existing guarded Undo/Redo transaction. Module-root moves preserve companion trees and pin
default resource paths left behind; custom roots retain order and `[]` semantics. Proposed-path
resource lookup and detached embedded-value hashes reject changed fallback contents, including
consumers without source-dependency edges. The parser's lowered string/byte includes are covered.
Shared X162/X163 cover these paths; selected validation and remaining refusals are recorded in
[the relocation receipt](errs-integration-plan.md#l62-explicit-source-graph-relocation-2026-10-02).
No Java AST/embedding change is added. Broader L62 and the VS Code non-veto/Explorer limits remain.
The gate passes 80 backend and 15 IntelliJ unit tests, with zero failures/errors/skips, plus selected
X118/X161/X162/X163 in both editors. IntelliJ's UP19 descendant-connection repair also verifies
post-Redo unsaved typing and diagnostic recovery; its fix and removal gate are documented separately.


October 3 L62 callable continuation: existing `NameExpression` receiver and selected-method APIs
now supply per-site detached union/cyclic dispatch facts in Kotlin. Method constants alone can
name only one written contract and therefore cannot preserve a union target set. Proof retains
receiver identities, ordered contracts/delegate properties and finite recursive back edges;
untranslatable facts refuse the edit. No Java AST field or public embedding API was added.
Generated shorthand/implicit virtual constructors stay construction contracts; runtime-only
implementation lookup remains conservative. X164/X165 share closed-consumer rename and Undo
across both drivers, alongside existing X119/X120/X121 regression coverage. The final gate passes
202 backend tests (zero failures/errors/skips), both driver compilations and formatting checks. All
five selected cases pass in VS Code `run-VqFiDW` and IntelliJ `run-12344641320847299097` (START
also passes; no IDE failures or internal-error log markers). See the October 3 integration-plan
section for commit dependencies and remaining generic/generated-route boundaries. L62 remains open.


October 3 L62 substituted-receiver continuation: detached callable facts now retain ordered
generic arguments, source formals, annotation identities and supported annotation values. Nested
union delegation preserves each branch's receiver/contract/property route and typed cycle anchors.
The adapter uses existing compiler APIs; no Java AST state or embedding API is added. Validation
corrected identity lookup for concrete nested substitutions, access handling on relational delegate
receivers and cycles closing on a union. Large fixtures use multiline Kotlin raw strings with
line-aware caret/edit helpers.

The combined gate passes 228 backend tests across 21 suites with zero failures/errors/skips; both
drivers compile and formatting checks pass. X164–X168 pass in VS Code `run-oSVuMJ`. IntelliJ's
first selected attempt stalled in the test Driver's focus call while Rename was open; compiler
preparation had already finished and the worker was idle. The test-only modal dispatch repair and
its acceptance are tracked under UP20 in `errs-upstream-issues.md`. Repaired IntelliJ
`run-907034856191389577` passes START and X164–X168; `run-7549109475022481665` passes
the dedicated focus/replay guard test. JUnit confirms two passing tests without failures/errors/skips;
IDE/server logs contain no internal-error markers. The integration plan maps the local checkpoints,
required proof correction and separately extractable harness repair.

L62 stays partial: unsupported annotation constants/type shapes, whole-relational annotations,
wider escaped/generated transformations and broader relocation remain open. Binary contracts
remain read-only; unconfigured consumers remain unknown.


October 3 L62 combined-relocation continuation: compiler mode can rename a type while moving it
between proven namespaces in one module, relocate and rename explicit module roots with their
dependency/resource settings, and plan interacting type moves against one final graph. Explicit
import aliases keep their names; imported targets, bare uses, constructors and closed consumers
follow the renamed identity. Inline collisions refuse before compilation. Shared X169–X172 and
both native drivers cover these paths, companion resources and Undo/Redo.

The combined gate passes 222 backend and seven IntelliJ unit tests, without failures/errors/skips.
VS Code `run-TjE279` and IntelliJ `run-14394987299477639656` pass X161/X163/X169–X172 (plus
IntelliJ START), with no editor/internal-error log markers. Initial IntelliJ X169 failed because
LSP4IJ left edited closed buffers unsaved; UP21 now persists only affected closed documents at the
end of apply/Undo/Redo. Assertions check disk contents and diagnostics, not only editor buffers.
Formatting checks pass. This is selected acceptance, not a full-catalog run or complete L62.

The same validation exposed [compiler issue #667](https://github.com/xtclang/xvm/issues/667),
reproduced through the CLI on clean master `7a4e29e57`. Unconditional duplicate type declarations
became an ambiguous CompositeComponent and crashed a ClassStructure cast. Separate commit
`2793efdd9` reports existing COMPILER-148 during structure registration; it adds no LSP/partial
AST hooks or embedding API. The unit test has two failing cases on master and all four pass with
the fix, including conditional and distinct-scope controls; 108 selected master Java tests pass.
Nine targeted Java and 46 affected LSP tests also pass on errs after this compiler repair.

The integration plan maps the four implementation checkpoints, required correction `494a18f6c`
and IntelliJ correction `2ce6029cf` for future PR extraction. Broader L62 exclusions remain
explicit, including uncaptured incoming resources: a fresh reverse refactoring can refuse while
Undo still restores its previously proven transaction. No changes have been pushed in this batch.


### L62 empty-package and trivia continuation (2026-10-04)

The compiler adapter now plans same-module type moves into captured empty package directories
and preserves comments/whitespace in qualified import, type and call names. Replay must prove
every moved declaration's destination identity and preserve all existing bindings, dispatch and
resources. Cross-module ownership, uncaptured/nonexistent directories, class-owned destinations,
specialized names and overlapping companion moves remain unsupported. No new AST/embedding
API is required. Shared X173–X176 cover empty nested packages, aliases, closed consumers,
companions/resources, prefix removal, inline collisions, interacting batches and Undo/Redo in
both editor drivers. Catalog: 181 scenarios. Selected acceptance is recorded below.

The independent compiler duplicate-declaration repair is [PR #668](https://github.com/xtclang/xvm/pull/668).
Errs includes its corrected three Java tests and five CLI manual cases, wired to the manual
check/sequential/parallel tasks. The previous artificial conditional-parser test is removed.

Selected acceptance passes X169/X171/X173–X176 in VS Code `run-ElUMIl` and IntelliJ
`run-13159045223223510909` (plus START), with zero editor errors. The combined gate passes
155 LSP tests, 103 Java tests and five manual CLI scenarios with no failures or skips; the
manual task reuses configuration cache. Both drivers compile. These are selected receipts,
not a full 181-case run. The extraction map and remaining exclusions are recorded in
[the integration plan](errs-integration-plan.md#l62-empty-destination-batch-acceptance-and-extraction).


### L63 typed initializers and unused locals (2026-10-04)

The compiler adapter now extracts whole explicitly typed local initializers and inlines a local
into an adjacent, single-use typed initializer with the same written expected type. It can also
remove an unused plain local whose initializer the compiler proves constant and free of side
effects. Complete-graph compilation and binding/dispatch proof remain mandatory. Comments and
contextual numeric/function types are preserved; inference, changed type contexts, intervening
statements, runtime initialization and Ref/Var annotations remain refusals. The deletion facts
are immutable source locations captured on the compiler worker, with no retained AST/constants
or new Java AST API. Shared X177–X180 cover native application and Undo/Redo plus installed
refusal checks. All 129 backend tests pass without failures or skips; X156/X157/X177–X180 pass
in VS Code `run-nDHZOH` and IntelliJ `run-232740531754002686` (plus START, zero IDE failures).
These are selected runs from the 185-case catalog. The independent ordinary-compiler `&& False`
code-generation repair has a failing-master CLI reproduction and 22 passing manual runtime
checks; keep its two commits separate in the extraction map. L63 remains partial for wider contexts,
extract-method, missing declarations, broader inline and global safe delete.


### L63 private expression helpers (2026-10-04)

Complete return expressions and explicitly typed local initializers can be extracted to private
helpers in the same type. Stable locals/parameters become explicit typed inputs; compiler
identities prove their types, moved bindings/calls, argument-to-parameter mapping and unchanged
existing dispatch. The compiler's effectively-final/non-reference register evidence prevents
reading mutable captured values early. Written expected types and exact moved expression text
are preserved. Generic owners and ordinary implicit instance calls are supported; method formals,
mutable/reference-backed captures, lambda/anonymous-class creation, async calls, conditional returns
and general statement extraction remain refused.

The implementation stays in Kotlin. Existing `Register.isEffectivelyFinal()`, `isVar()` and type
APIs suffice; there are no new Java AST fields, accessors, parent mutation or embedding entry points.
A local ownership map is necessary because a fresh parser tree has not adopted parent links. The
existing local-extraction reader also now skips typeless method-formal parser parameters instead
of dereferencing their missing type. Only detached type identities/source locations survive the
compiler worker. All 151 selected backend tests pass without failures/errors/skips. X156/X177/
X181–X184 pass in VS Code `run-ZhuPaV` and IntelliJ `run-5839432120705984532` (plus START,
zero IDE failures); both verify exact edits, diagnostics and Undo/Redo, while X184 verifies refusal
through the installed connection. These are selected runs from the 189-case catalog, not full
suite reruns. The integration plan records the failed first run, fixes and extraction commit map.


### L63 missing-method declaration evidence (2026-10-04)

The next repair action creates a private method in the caller's module/class from a fresh
successful declaration analysis. It never asks failed-validation TypeInfo for a signature.
Enclosing method parameters/returns provide resolved compiler types; supported literals use
compiler implicit types within the same attempt. Immutable action facts retain only rendered
text and positions. Full proposed compilation, known binding/dispatch preservation, selected-call
target and existing current-input guards protect publication. Both editors gain shared
X185–X188 with initial error, repair and Undo/Redo diagnostics. The combined gate passes 180
backend and 89 IntelliJ unit tests; X122/X181/X185–X188 pass in VS Code `run-dzR5d9` and IntelliJ
`run-16733856986922464734`, zero editor failures. These are selected runs. Native acceptance also
exposed and fixed UP07 equal-full diagnostic reports retaining canceled quick fixes in unchanged
annotations. The client-only correction and failure receipts are in the integration plan.

No Java AST or embedding API change is needed. At that checkpoint, receiver/closure/overload
contexts, method formals, inferred/computed/named arguments and ambiguous numeric literal
representations remained refusals; the continuation below adds compiler-established locals.
See [the current scope and commit map](errs-integration-plan.md#l63-missing-method-quick-fixes-2026-10-04).


### L63 local argument and initializer evidence (2026-10-04)

Missing private-method creation now accepts prior typed block locals and compiler-inferred
`var`/`val` locals whose initializers have validated. An explicitly typed local initializer supplies
the new method's result type. Resolved register types are copied to detached strings/locations;
failed validation is never resumed and its TypeInfo is never requested. Fresh declaration analysis
still checks owner/member eligibility. The complete proposed graph must prove each local argument
binds to its original declaration, as well as preserving known bindings and resolving the call to
the inserted method. No Java AST or embedding API/state was added.

All 198 selected backend tests pass, including 47 missing-method cases/proofs. X181/X185/X189–X192
pass in VS Code `run-mRmX6Y` and IntelliJ `run-1236802406694405307` (plus START), zero editor
errors. Both use the 197-case catalog; these are selected runs. New cases verify exact edits,
diagnostics and Undo/Redo plus inferred-result refusal. The commit map, build interruption and
acceptance receipts are tracked in the [integration plan](errs-integration-plan.md#l63-local-arguments-and-typed-initializer-repairs-2026-10-04).
Cross-owner creation, named/computed arguments, generic/conditional signatures, inferred result
contexts and broader missing-declaration generation remain open. The continuations below add
same-owner instance receivers and named class qualifiers.


### L63 same-owner receiver evidence (2026-10-04)

Missing private-method creation now accepts `this`, `this:private` and plain parameter/local
receivers whose validated compiler type has the exact enclosing class identity. Body evidence is
copied into immutable Kotlin inputs alongside the local type strings; no compiler object escapes,
failed validation is not resumed and failed TypeInfo is not queried. The generated method remains
an instance method even when the caller is static. Full proposed compilation must preserve the
receiver binding and resolve the selected leaf token to the inserted declaration. A compiling
change from `peer` to `this` is rejected by binding proof.

Shared X193–X196 cover explicit `this`, an inferred local receiver in a static caller, another-owner
refusal and public-view refusal. All 219 selected backend tests pass. X181/X185/X190/X193–X196
pass in VS Code `run-nvquYD` and IntelliJ `run-15968094076069950349` (plus START), zero editor
errors. Both use the 201-case catalog; these are selected runs. Acceptance receipts and extraction
commits are in the [integration plan](errs-integration-plan.md#l63-same-owner-receiver-repairs-2026-10-04).
No Java AST/embedding API or plugin production change was needed. The continuations below add
type-qualified static calls and bounded same-module destinations. Computed/chained receivers and
broader missing declarations remain open.


### L63 class qualifier and dispatch evidence (2026-10-04)

Missing-method creation now accepts named enclosing-class qualifiers, including fully qualified
names. A validated class constant must equal the enclosing class identity; runtime Class/Type
values and class-name spelling alone are insufficient. Detached evidence records instance/static
dispatch, and the completed proposed graph must bind the call to the inserted declaration with
that dispatch. A class qualifier creates a static helper even in an instance caller; a parameter
shadowing the class name still creates an instance helper. No compiler objects escape, failed
TypeInfo is not queried, and no Java AST/embedding API or plugin production change is required.

X197–X200 add class/qualified-class acceptance, runtime-Type refusal and shadowed-name instance
dispatch to both shared drivers. The combined backend gate passes 232 tests, including four
repair proofs; an otherwise compiling call redirected to another class is rejected. The next continuation adds
bounded cross-owner creation. Computed/chained receivers, runtime Class/Type values, explicit
generic/singleton qualifiers and broader missing declarations remain open. The current commit map and native
acceptance are in the [integration plan](errs-integration-plan.md#l63-class-qualified-static-repairs-2026-10-04).


X181/X185/X193/X197–X200 pass in VS Code `run-3Vm6C9` and IntelliJ
`run-11432411846060652594` (plus START), zero editor errors. Both use the 205-case catalog; these
are selected runs. IntelliJ Ultimate is disabled. Root/LSP/IntelliJ read-only Spotless passes.


### L63 cross-owner destination evidence (2026-10-04)

The adapter now proposes an explicitly public method in another writable ordinary source class
of the same module, including a closed companion. Compiler identities select the owner; detached
source locations carry that evidence into fresh declaration analysis. The final graph must prove
the inserted target, public access, instance/static dispatch and exact signature type identities,
as well as retaining known bindings and current inputs. A return-type substitution that still
compiles is rejected. Server regressions verify the destination URI and its open/closed version.

Temporary signature constants remain worker/attempt-owned and are detached by the existing
identity collector. No Java AST field/API, clone obligation or embedding entry point was added.
Native companion testing exposed a report-copy bug in the existing IntelliJ diagnostic bridge;
the corrected copy preserves decoded related reports (UP06/UP07). X201–X204 extend both shared drivers with source/companion edits
and refusal controls. The next continuation adds cross-owner locals/typed initializers;
generic/interface destinations and other configured modules remain separate tasks. See the [current scope and commit map](errs-integration-plan.md#l63-cross-owner-source-destinations-2026-10-04).


Final selected acceptance: **256 backend tests** and **90 IntelliJ unit tests** pass without
failures/errors/skips. X181/X185/X195/X197/X201–X204 pass in VS Code `run-hOIsOh` and IntelliJ
`run-18176000411609180309` (plus START), zero editor failures; Ultimate is disabled. The shared
catalog has 209 cases. The native report-copy failure, its regression and the separate open UP22
lifecycle defect are documented in the [upstream register](errs-upstream-issues.md#up06up07-companion-report-copy-correction-2026-10-04).
These are selected acceptance runs; no full-catalog rerun or new packaged-stdio gate is claimed.


### L63 detached local types across source owners (2026-10-04)

Public same-module missing-method repairs now accept earlier compiler-typed local arguments and
whole explicitly typed local initializers, including companion destinations. The failed attempt's
register types are detached by the existing identity collector before fresh declaration analysis;
its compiler constants never cross that boundary. Final compilation proves the exact parameter/
return identities, original local binding, public access, destination and dispatch. Compiling
parameter widening, result narrowing and argument rebinding are regression counterexamples.
No Java AST/API or plugin production change is needed. X205–X208 extend the shared catalog to 213.
Cross-module destinations, generic/computed receivers and broader missing declarations remain open.
See the [scope and validation record](errs-integration-plan.md#l63-cross-owner-local-arguments-and-initializer-results-2026-10-04).


Selected acceptance: **275 backend tests** pass without failures/errors/skips. X181/X185/X190/
X202/X205–X208 pass in VS Code `run-RpTVz1` and IntelliJ `run-14627770467027596087` (plus START),
zero editor failures. Both use the 213-case catalog; Ultimate is disabled. Root/LSP/IntelliJ
read-only Spotless passes. These are selected runs, not a full-catalog or new packaged-protocol gate.
The [integration plan](errs-integration-plan.md#l63-cross-owner-local-arguments-and-initializer-results-2026-10-04)
records the proof boundary, receipts and commit extraction map.


L63 cross-module continuation: public missing-method repairs now select reachable, writable
configured source dependencies, including closed companions, and render types using the destination's
existing imports. Source indexes alone do not authorize edits; reverse dependency needs remain a
refusal. The proof verifies the exact destination signature across module-local symbol IDs, complete
graph compilation and preservation of existing bindings. Shared X209–X212 cover editor behavior;
validation and extraction status are tracked in the [integration plan](errs-integration-plan.md#l63-cross-module-missing-method-destinations-2026-10-04).
No Java AST/embedding API or plugin production change is introduced.

Acceptance for this continuation: 296 backend tests and X201/X202/X205/X206/X209–X212 in both
editors pass. Receipts: VS Code `run-B54Y1R`, IntelliJ `run-14965910820534049086` (plus START,
Ultimate disabled), zero editor failures. The first IntelliJ attempt exposed cancellation during
synchronous native popup discovery; the driver now uses the ordinary UI action queue and retains
its no-edit-replay guard. UP07 tracks the evidence. These are selected 217-catalog receipts;
no fresh packaged-protocol or IntelliJ production-unit run is claimed.


L63 destination-import continuation adds atomic imports and missing methods only for dependencies
already available to the destination. Alias selection reserves source names across companions;
required imports travel with detached local type spelling. Signature proof accounts for preceding
import edits. Package imports go in the module root; a companion method and its root import share
one atomic versioned workspace edit. X213–X215 cover editor acceptance; the [integration plan](errs-integration-plan.md#l63-destination-import-insertion-2026-10-04)
records validation and consolidates the eight remaining L63 work areas. No Java AST or embedding
API change is introduced.

Validation: **310 backend tests pass**, zero failures/errors/skips. VS Code `run-u5kDXk` and IntelliJ
`run-4588144426201480586` pass **X122/X209–X215** (IntelliJ also START, Ultimate disabled), zero
editor failures, using the same 220-case catalog. X214 proves the import in the module root and
method in its closed companion are one native Undo/Redo transaction. IntelliJ fixture isolation
and graph-before-open setup avoid unrelated case churn; UP07's broader production transition
remains open. These are selected receipts, not full-catalog or fresh packaged-stdio acceptance.


The L62 closure batch now covers cross-module source ownership, ordered overlapping moves,
captured incoming resources and broader detached annotation/receiver proof. Shared X216–X220
are implemented in both drivers (225 cases total). Acceptance and the UP23 host exception are
recorded below. The [closure record](errs-integration-plan.md#l62-ownership-and-relocation-closure-batch-2026-10-04)
contains the four commit groups, support boundaries and compiler header-expression limitation.
No Java AST or embedding API change is introduced. L63 now resumes.

L62 closure validation: 178 backend/protocol tests and 7 IntelliJ unit tests passed; the final
affected backend rerun passed 58 tests. Selected IntelliJ X169/X173/X216–X220 all pass. VS Code
passes six; X218 still fails on native overlapping-move Undo (UP23), whose failing assertion stays.
The user explicitly accepted carrying that host limitation and continuing to L63. See the
[integration receipt](errs-integration-plan.md#l62-ownership-and-relocation-closure-batch-2026-10-04).


### L63 eight-area implementation batch (2026-10-04)

The current batch adds exact generic-owner/method-formal and conditional missing signatures,
compiler-validated receivers/named or computed arguments, missing class/instance-property stubs,
statement/nested-expression extraction, wider constant-local and selected private member inline,
and configured-graph safe deletion of unused private methods/constants. No compiler AST state or
embedding API has been added. Source type spelling uses actual module imports plus the compiler's
implicit module constants; both source and binary dependency types are covered.

The implementations retain explicit ownership, initialization, control-flow/capture, type and
binding/dispatch refusals. They are bounded refactorings, not arbitrary source transformations or
proof of unknown external consumers. Shared X221–X242 bring the catalog to **247 scenarios** and
pass selected acceptance in both editors. The final combined backend/protocol gate passes
**498 tests**, zero failures/errors/skips. L63 is closed within its documented supported/refused
forms; this does not claim arbitrary refactorings or a full-catalog rerun. UP23 remains open.
See the [closure and acceptance record](errs-integration-plan.md#l63-bounded-closure-and-acceptance-2026-10-04).
