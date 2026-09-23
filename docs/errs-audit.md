# Failures with nowhere to go

The original audit below records findings to triage: each site needs a
judgement about whether the failure is real and where it ought to go, and those judgements belong
to whoever owns the code.

Measured on `lagergren/errs`. First taken against `origin/master` at `fbdeb86c7`; re-checked
after the branch was rebased onto `4a1eae6f7`, and the counts are unchanged - this branch
had deliberately fixed none of them. The 2026-09-22 follow-up at the end records the subsequent
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
binary-AST TODO still needs a reproducer; simple bound-function probes report normal type errors
and do not reproduce it. Track that with the call-consumer work. Debug formatting, runtime/JIT
ownership and optional CLI display fallbacks remain separate follow-ups as classified above.
