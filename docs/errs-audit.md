# Failures with nowhere to go

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
and output/lifetime checks before enabling it. Property rename remains disabled.

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
