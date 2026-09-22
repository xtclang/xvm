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
| `ModuleInfo.loadBinaryFile` / `extractModuleName` | Discovery probes, with invalid/unknown state or no name as the result. Caller error attribution still needs dedicated file-compilation cases. |
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
| Array accessor validation, assignment-operator lookup, constructor-super lookup, property target metadata | Potential reporting consumers. Highest-priority source-reproducer backlog; no blanket conversion based only on listener availability. |
| `NameExpression` bound-function/atomic BAST and `ToIntExpression` conversion metadata | Post-validation code generation. Keep failures visible at the embedding boundary; establish a missing-diagnostic reproducer before altering replay. |
| `TypeInfoReal`, property metadata, virtual-child fallback, mixin annotations | Shared recursive metadata paths; some run during provisional composition. Need caller/stage evidence. |
| `EvalCompiler`, initializer/delegation generation, JIT descriptors and runtime op lookups | Include debugger/runtime consumers; their location in `compiler` or `asm` does not make them LSP validation sites. |

The earlier sample of three out of seventy suppressed messages remains a sample. This pass does
not close that survey or claim every silence is justified. It fixes the reproduced repository loss
and makes the next TypeInfo investigation narrower: selected validation consumers first, with
source-level evidence and explicit checks against provisional-composition cascades.
