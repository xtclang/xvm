# Exception and error signalling: an architectural report

*Measured on branch `lagergren/lazy-instance`, 2026-09-08. Read-only: this report changed no source.*

The question this answers, in the words the code itself uses, is at
`javatools/src/main/java/org/xvm/tool/Launcher.java:1017-1021`:

```java
/**
 * RuntimeException thrown upon a launcher failure.
 * TODO: Ideally this should not be a runtime exception. We can have throws declaration and do more
 *   processing and recovery attempts if we explicitly declare how to handle LauncherException in
 *   subclasses in code.
 */
static public class LauncherException extends RuntimeException {
```

So: could the tree signal failure through declared, checked exceptions handled at a consistent
layer — the way [`docs/errorlistener/README.md`](../errorlistener/README.md) threaded one diagnostic
sink explicitly instead of relying on ambient nullable state?

The short answer, defended in §6: **partly, and the feasible part is not the part that looks like
adding `throws`.** At least 58% of what is counted as "error signalling" here — 558 of the 959
`IllegalStateException` sites — is not signalling at all: it is switch exhaustiveness (459) and
unsupported-operation declarations (99). Most of the 214 guarded precondition checks are contracts
rather than failures too, which pushes the figure toward three quarters. Each of those categories
wants a *different* fix from the others, and none of them wants a `throws` clause.

The residue that genuinely is "an operation failed and someone upstream must decide" is small, is
already funnelled through three handling nets that exist today, and would be improved by a **sealed,
cause-carrying, unchecked** hierarchy with a *declared* boundary — not by checked exceptions. And the interpreter cannot use Java exceptions for
language-level failure at all, for a reason that is representational rather than performance-related
(§5.2).

---

## 0. Method, so the numbers can be re-derived

Every count below was computed against **tracked** `.java` files only:

```bash
git ls-files -z '*.java' > /tmp/javafiles.z      # 1206 files
xargs -0 rg -n --no-heading '<pattern>' < /tmp/javafiles.z
```

This matters: the repo contains agent worktrees under `.claude/worktrees/` that duplicate the whole
tree. A naive `rg` over the working directory inflates every count roughly 14x. Restricting to
`git ls-files` output gives the same totals as a `.gitignore`-respecting `rg` over the repo root
(both give 959 `throw new IllegalStateException`), which is the cross-check that the file set is right.

Classification of the 959 `IllegalStateException` sites was done by script over the source lines
themselves (`switch`/`case` arm detection by looking at the throw line and the preceding non-blank
line; "whole method body" by the preceding line ending in `{` with a non-control-flow keyword and the
following non-blank line being `}`). The rules are stated at each table so a disagreement can be
checked rather than argued.

**Where I am uncertain, §8 says so explicitly.** Two subagent investigations contributed the
`CompilerException`/`LauncherException` and interpreter-protocol sections; their citations were
spot-checked against the files (`ServiceContext.java:556-600`, `Op.java:78-100`,
`Launcher.java:1015-1045`) and matched.

---

## 1. The precedent, stated as rules

The `ErrorListener` campaign (E32 / E34 / E35 / E47, and `docs/errorlistener/README.md`) established
four properties. They are worth restating as *rules*, because the test of this report is whether the
same rules apply to exceptions.

| # | Rule | How it was enforced |
|---|---|---|
| R1 | **Ownership is a parameter, never ambient.** | The `FileStructure` listener field deleted; `ConstantPool`/`Container` own the sink; `errs` threaded (~590 parameters). |
| R2 | **The sink is non-null, so "absent" stops being a decision each caller re-makes.** | Zero `errs == null` paths; seven coalescings deleted; `requireNonNull` at every boundary where a listener enters or is stored (README §9.1, §9.2). |
| R3 | **A question is distinguishable from a discard.** | `BLACKHOLE` split into `PROBE` (136 sites) and `BLACKHOLE` (13); `typeInfo()` vs `ensureTypeInfo(errs)`. |
| R4 | **Recording and aborting are separate questions.** | `log` returns `void`; `isAbortDesired()` is the only control-flow question; the detecting code decides to stop (README §5.2). |

R4 is the one that matters most here, and it is the one people misread. The campaign did **not**
conclude "route failures through exceptions". It concluded the opposite: *the listener must not
decide control flow*. `ErrorListener.RUNTIME` used to throw `IllegalStateException` from inside
`log()` at ERROR and above, so the *type* of exception a site produced depended on which listener was
installed. That was removed. Whatever this report recommends must not put it back.

A fifth property, from README §8.4, is the one that generalises furthest:

> R5 — **Diagnostics are recorded on the result and replayed to whoever asserts**, so memoization
> does not lose them (`TypeInfo.diagnostics()` / `replayDiagnostics(errs)`).

R5 is a *result-type* move, not an exception move. Keep it in mind for §7.

---

## 2. What the tree actually throws

### 2.1 Census of every `throw new`

1206 tracked `.java` files, **1818** `throw new` sites:

| type | n | share |
|---|---:|---:|
| `IllegalStateException` | **959** | 52.7% |
| `UnsupportedOperationException` | 359 | 19.7% |
| `IllegalArgumentException` | 259 | 14.2% |
| `IOException` | 40 | |
| `RuntimeException` | 37 | |
| `ArithmeticException` | 30 | |
| `AssertionError` | 21 | |
| `CompilerException` | 18 | |
| `NoSuchElementException` | 17 | |
| `LauncherException` | 9 | |
| `UTFDataFormatException` | 7 | |
| `OutOfBounds` (jitbridge, an Ecstasy value) | 6 | |
| `NumberFormatException` | 6 | |
| `UncheckedIOException` | 4 | |
| `SegFault` | 4 | |
| `ModuleLoadException` | 3 | |
| everything else (≤3 each) | ~39 | |

**Three generic JDK unchecked types account for 1577 of 1818 sites — 86.7%.** There are exactly
**seven** custom throwable types in the entire Java tree:

| type | file:line | extends | checked? |
|---|---|---|---|
| `Launcher.LauncherException` | `javatools/src/main/java/org/xvm/tool/Launcher.java:1022` | `RuntimeException` | no |
| `CompilerException` | `javatools/src/main/java/org/xvm/compiler/CompilerException.java:10` | `LauncherException` | no |
| `ModuleLoadException` | `javatools/src/main/java/org/xvm/asm/ModuleLoadException.java:18` | `RuntimeException` | no |
| `SegFault` | `javatools/src/main/java/org/xvm/runtime/gc/SegFault.java:6` | `Error` | no |
| `Decimal.RangeException` | `javatools/src/main/java/org/xvm/type/Decimal.java:869` | `ArithmeticException` | no |
| `ObjectHandle.ExceptionHandle.WrapperException` | `javatools/src/main/java/org/xvm/runtime/ObjectHandle.java:1012` | `java.lang.Exception` | **yes** |
| `nException` | `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nException.java:6` | `RuntimeException` | no |

Not throwables, despite the names: `org.xtclang.ecstasy.Exception` and its subtypes (`IOException`,
`ReadOnly`, `OutOfBounds`, `TypeMismatch`, `Unsupported`, `Number.IllegalMath`) extend `nConst`, i.e.
they are Ecstasy *values*. `import org.xtclang.ecstasy.io.IOException` silently shadows
`java.io.IOException` inside that package — a real hazard, noted here and not pursued further.

### 2.2 The 959 `IllegalStateException` sites, classified

By module and source set:

| module/sourceset | n |
|---|---:|
| `javatools/main` | 874 |
| `javatools/test` | 38 |
| `javatools_utils/main` | 18 |
| `javatools_utils/test` | 12 |
| `plugin/main` | 9 |
| `javatools_jitbridge/main` | 8 |

The 874 in `javatools/src/main`, by package:

| package | n | KLOC | per KLOC |
|---|---:|---:|---:|
| `org.xvm.asm` (`constants` 185, root 124, `op` 73, `ast` 11) | 393 | 121.5 | 3.23 |
| `org.xvm.runtime` (core 104, `template/**` 101) | 205 | 70.6 | 2.90 |
| `org.xvm.compiler` (`ast` 174, root 8) | 182 | 69.2 | 2.63 |
| `org.xvm.javajit` (root 66, `builders` 11) | 77 | 20.5 | 3.76 |
| `org.xvm.api` | 14 | 2.2 | 6.25 |
| `org.xvm.tool` | 2 | 8.2 | 0.24 |
| `org.xvm.type` | 1 | 2.3 | 0.43 |

**By intent** (script rules stated in §0; the four buckets partition all 959):

| bucket | n | share | example |
|---|---:|---:|---|
| **1. `switch`/`case` exhaustiveness arm** — "this enum value or R_ code cannot occur" | **459** | 47.9% | `ServiceContext.java:745` `default: throw new IllegalStateException("Invalid code: " + iPC);` |
| **2. Whole method body** — "this subtype does not implement this operation" | **99** | 10.3% | `Register.java:78` `markInPlace()`; `runtime/template/Proxy.java:53` `construct(...)`; `asm/constants/DeferredValueConstant.java:90` `assemble(...)` |
| **3. Guarded by an `if`** — a precondition or invariant check | **214** | 22.3% | `compiler/ast/Statement.java:178` `if (m_ctx == null) throw …`; `runtime/NestedContainer.java:73` `"cannot register host resources on a guest container"` |
| **4. Unconditional tail / other** | **187** | 19.5% | `asm/constants/UnresolvedTypeConstant.java:419` (fallthrough after the resolved case) |

Cross-cutting measurements on the same 959:

- **458 (47.8%) carry no message at all** — `throw new IllegalStateException();`. Of those, 314 are
  bucket 1 and 144 are not.
- 413 begin with a string literal; 88 pass an expression; **25 attach a cause**.
- **38 are thrown from inside or immediately after a `catch`**; 25 of those attach the cause, 13 do not.
- 65 use the `"identifier=" + value` state-dump idiom (`op=`, `arg=`, `format=`, `id=`).
- **20 are documented with an `@throws IllegalStateException` javadoc tag — 2.1%.**

That last figure is the sharpest single number in this report. Compare `IOException`: 901
declaration-site `throws IOException` clauses and 127 `@throws IOException` javadoc tags (14.1%). The
compiler forces the declaration for a checked exception and the documentation follows it; nothing
forces either for an unchecked one, and neither happens.

The tests have compensated. `assertThrows(IllegalStateException.class, …)` appears **55** times —
more than for any other type (`IllegalArgumentException` 32, `UnsupportedOperationException` 27,
`NullPointerException` 25, `LauncherException` 12, `ModuleLoadException` 5, `CompilerException` 3,
`IOException` 3). Grouping adjacent matches into 43 context blocks, **38 of the 43 assert on the
message text** within six lines. The type carries so little information that the test suite
string-matches the prose instead — the stringly-typed dispatch that
[E11](plans/master-enhancement-submissions.md) names as an anti-pattern elsewhere in the tree.

### 2.3 A second, parallel assertion mechanism

**1776 Java `assert` statements**, 1614 of them in `javatools/src/main`. Assertions are enabled in
this repo's own builds and launchers — `-ea` at
`build-logic/common-plugins/src/main/kotlin/org.xtclang.build.java.gradle.kts:171`,
`xdk/build.gradle.kts:155`, and as the plugin's `DEFAULT_JVM_ARGS` at
`plugin/src/main/java/org/xtclang/plugin/internal/DefaultXtcLauncherTaskExtension.java:28`.

So the tree expresses "internal invariant" two ways: 1614 `assert`s that vanish in an embedding
host's JVM, and 214 + 187 always-on `IllegalStateException` guards. Nothing in the source says which
belongs where. That is a genuine architectural inconsistency, and it is *upstream* of the
checked-versus-unchecked question: an invariant that only holds under `-ea` is not a contract.

### 2.4 Checked exceptions: the tree does use them, at two boundaries

Declaration-site `throws` clauses (lines outside javadoc, excluding `@throws` tags), by type:
**`IOException` 901** — of which 894 declare it alone and 7 pair it with another checked type —
`Exception` 191, `InterruptedException` 12, `ExceptionHandle.WrapperException` 9,
`ReflectiveOperationException` 7, `NoSuchFieldException` 6, `NoSuchMethodException` 5, `SegFault` 3,
everything else ≤3.

**Boundary one — binary serialization.** The 901 `throws IOException` declaration lines split:

| module/sourceset | n |
|---|---:|
| `javatools/main` | 666 |
| `javatools/test` | 154 |
| `javatools_utils/main` | 27 |
| `plugin/test` | 23 |
| `javatools_utils/test` | 11 |
| `javatools_unicode/main` | 10 |
| `xdk/test` | 8 |
| `plugin/main` | 2 |

(E47's table records 776 for this figure. I cannot reconcile the difference: my definition is
"non-javadoc line containing the token `throws IOException`", which gives 901 across all source sets
and 705 across main source sets only. Neither is 776, so one of the two definitions differs in a way
I did not identify. The *shape* of the finding — that the tree does not avoid checked exceptions —
is unaffected.)

Of the 666 in `javatools/src/main`, **633 (95.0%) are in `org.xvm.asm`** — `assemble`/`disassemble`
of the module structure. The remainder: `runtime` 10, `type` 7, `tool` 7, `compiler` 7, `api` 2. This
is a single coherent checked-exception domain, correctly chosen, and it works.

Crucially, the leak from checked to unchecked is **narrow**, which is the opposite of what one would
guess. Of 104 `catch (…IOException…)` sites across the tree, classified by what the handler does
within six lines:

| disposition | n |
|---|---:|
| other (return `null`, fall back, rethrow as `IOException`) | 67 |
| **converted to an unchecked type** | **22** |
| discarded (empty block) | 13 |
| reported to a listener or console | 10 |

The 22 conversions, in full: `api/XtcEngine.java:832`, `api/XtcEngine.java:1146`,
`asm/Component.java:822`, `asm/FileStructure.java:1151`, `asm/MethodStructure.java:706, 1141, 2271,
2812`, `compiler/Source.java:91` (9 in `javatools/src/main`); 8 in `javatools_utils`
(`ConstBitSet`, `ConstOrdinalList` — in-memory codecs where `IOException` cannot occur);
`plugin/.../XtcTestTask.java:182`; and 4 in tests. Seven of the ten "reported" sites are in
`org.xvm.tool` — the boundary layer doing exactly its job.

**Boundary two — `ExceptionHandle.WrapperException`, the only custom checked exception.** 9
declaration-site `throws`, and **116 `catch` sites in `javatools/src/main`**, of which **94 are in
`org.xvm.asm.op`**. It exists to carry an Ecstasy exception across a Java call boundary where the int
protocol (§5) cannot reach — lambdas, `CompletableFuture` continuations — and it is converted back
within a few lines every time (`return frame.raiseException(e)`). It is a non-static inner class of
`ExceptionHandle`, so it holds an implicit reference to its handle; that is how `getExceptionHandle()`
works.

**`throws Exception` is a test idiom, not an API.** 191 of the 203 occurrences are in
`javatools/test`; there are 11 in `javatools/src/main` (4 in `Frame`, 3 in `IndexSupport`, and one
each in `xTuple`, `xArray`, `xRTType`, `NativeContainer`) and 1 in `plugin/test`. So the tree does
not have a "declare `Exception` and give up" habit in production code.

### 2.5 `CompilerException` / `LauncherException`: control flow, error report, or both

Both. And the split is by throw site, not by type — which is the defect.

`CompilerException extends LauncherException extends RuntimeException`. Neither carries a diagnostic
code, a severity, or a source position; the diagnostic content lives in the `ErrorListener` and the
exception message is human prose that nothing parses. `LauncherException` adds a `boolean error` and
a derived `exitCode` (`error ? 1 : 0`); every `CompilerException` is `error == true`.

**18 `throw new CompilerException`** sites: `Parser` 13, `Lexer` 2, `compiler/Compiler.java:128`,
`asm/constants/UnresolvedNameConstant.java:200`, `asm/constants/UnresolvedTypeConstant.java:126`.
Three distinct uses:

1. **Abort unwinding** — `Parser.log` at `Parser.java:5648-5663` and `Lexer.log` at
   `Lexer.java:2513-2522`: the diagnostic is already in the listener, and the throw exists to escape
   deep recursive descent when `errs.isAbortDesired()`. Pure `goto`.
2. **Speculative backtracking** — `Parser.SafeLookAhead.log` at `Parser.java:5673-5679` captures the
   `ErrorInfo` into a field and **never logs it**, throwing purely to unwind to the three
   `catch (CompilerException _) {}` sites at `Parser.java:1724, 3096, 3446`. This is the proof that
   the type is control flow: the error deliberately never reaches a listener.
3. **Log-then-throw** — the other ten `Parser` sites log on the preceding line, e.g. `expect()` at
   `Parser.java:5586-5601`. The throw is forced by `expect()` returning `Token` rather than `Token?`;
   the whole grammar is written against a non-null contract.

Only `compiler/Compiler.java:128` ("failed to create module") and the two `Unresolved*Constant` sites
carry information that is not already in a listener — and nothing catches `Compiler.java:128`.

**9 `throw new LauncherException`**: 7 in `runtime/NativeContainer.java` (bootstrap and native
linkage), 2 in `Launcher.java` itself. The literal count understates reach: `Launcher.report(FATAL, …)`
at `Launcher.java:486-494` throws unconditionally and has 17 call sites, and
`checkErrors()`/`checkErrors(String)` throws when `isAbortDesired()` and is called from five files
(`tool/Compiler.java` 8, `tool/Runner.java` 9, `Bundler.java` 6, `Launcher.java` 5,
`Disassembler.java` 2, `xRTCompiler.java` 5). None of that is visible in any signature, because the
type is unchecked. **That is the concrete cost of unchecked here, and it is measurable.**

The inheritance is a defect on its own terms: because `CompilerException extends LauncherException`,
an escaped parser abort is caught by the CLI's `catch (LauncherException e)` at `Launcher.java:363`
and printed as `console.log(ERROR, e.getMessage())` — the cause dropped, parser prose presented as a
user-facing tool error. And the runtime (`NativeContainer`) throws the CLI's exception type for
linkage failures, with no handler anywhere in `org.xvm.runtime`.

Two abort predicates exist and disagree. `ErrorList.isAbortDesired()` (`ErrorList.java:126-130`) is
severity + count; `Launcher.isAbortDesired()` (`Launcher.java:649-651`, overridden in
`tool/Compiler.java:613-623`) is a severity threshold that under `--strict=Stickler` fires on a
single WARNING. So the launcher can want to abort while the `ErrorList` does not, and the parser
keeps going until the next `checkErrors()` boundary.

### 2.6 Failures with nowhere to go — current state, not re-derived

E47 audited these individually; these are the counts as of today, for continuity:

| | now | E47's figure when written |
|---|---:|---:|
| `System.err` occurrences | 34 (5 commented out; 12 in main outside `ErrorListener`/`Console`/trace paths; the rest tests, comments, plugin stream plumbing) | 22 |
| `printStackTrace` | 11 | 9 |
| truly empty `catch` blocks | 44 — **20 in `javatools/src/main`**, 24 in tests (16 of which are one `TestNumber` overflow-probe pattern) | 31 |

Of the 44, thirty-eight are `catch (…) {}` on one line and six span two lines. **The main-source
figure of 19 same-line empties reconciles exactly with README §6's "19 empty `catch` blocks remain,
audited individually"**; the twentieth is the two-line
`ConstantPool.java:655-658`, a `catch (RuntimeException _)` around `getPathString()` inside the
formatter that runs while the registration guard is mid-throw — one of the "diagnostic helpers must
not throw while describing a failure" cases the same section names.

The 19 same-line live ones in main are the audited set: three speculative parses in `Parser` inside
`try (SafeLookAhead …)`, `closeQuietly`-style cleanups (`xRawOSFileChannel:271`, `xRTSocket:406`,
`xTerminalConsole:272, 379`, `DebugConsole:353`), best-effort probes returning null
(`ModuleInfo:1475, 1479`, `xRTFileTemplate:98`, `NativeTypeSystem:202`, `Disassembler:407`,
`FileStructure:198`, `JitConnector:154`), and `xRTNameService:265` / `xBit:100` /
`ArrayAccessExpression:1125` / `EvalCompiler:219`. The catch parameters are `_`, so a genuinely
ignored exception is visually distinct — that is R3 applied to catches, and it is the shape this
report wants more of.

### 2.7 `null` as a sentinel

**729 `return null;` statements** (bare, at statement start), 673 of them in `javatools/src/main`:
`compiler` 362, `asm` 213, `runtime` 62, `javajit` 19, `tool` 14, `api` 3.

The compiler's 362 are mostly *not* error signalling — they are `TypeFit.NoFit`-adjacent answers to
"does this fit?", the same speculation R3 named. `Optional<…>` appears 29 times in main source and
`@Nullable` only 6 times against 345 `@NotNull`. So the annotation campaign chose to mark the
non-null side and leave nullable implicit, and the null-returning idiom is deliberate and pervasive
in the compiler. **Converting it to `Optional` would be a much larger and much less valuable change
than anything else in this report**, and I do not recommend it (§7.4).

---

## 3. Layer analysis: is this one level, or many?

### 3.1 The packages are not layers

Cross-package `import org.xvm.*` edges, `javatools/src/main`:

| from | to (count) |
|---|---|
| `org.xvm.asm` | `asm` 1676, **`runtime` 893**, `util` 588, **`javajit` 432**, `compiler` 39, `type` 16 |
| `org.xvm.compiler` | `asm` 1288, `compiler` 244, `util` 138, `runtime` 5, `tool` 4 |
| `org.xvm.runtime` | `runtime` 1891, `asm` 1062, `util` 107, `type` 17, `compiler` 14, `tool` 5 |
| `org.xvm.javajit` | `asm` 209, `javajit` 157, `util` 26, `type` 3, `api` 1 |
| `org.xvm.tool` | `asm` 63, `util` 47, `tool` 23, `compiler` 13, `api` 3, `runtime` 1, `javajit` 1 |
| `org.xvm.api` | `asm` 36, `runtime` 20, `compiler` 11, `util` 3, `tool` 1 |

`asm → runtime` is 893 edges and `runtime → asm` is 1062. **The dependency is cyclic, and 210 of the
asm→runtime files are `org.xvm.asm.op`** — 215 files that are the interpreter's instruction set,
structurally filed under `asm`. `asm.constants` also imports `compiler` (for the diagnostic codes).

This is the single most important structural fact for the question asked. "Handle failures at a
consistent layer" presupposes layers. What exists is a model package (`asm`) that contains both the
serializable structure tree *and* the executable opcodes, with the opcodes depending on the runtime
that `asm` is otherwise beneath.

### 3.2 Origination is uniform; handling is a funnel

The per-KLOC column in §2.2 is the answer to "are these failures happening at many different levels,
or at roughly the same layer?" — **`asm` 3.23, `runtime` 2.90, `compiler` 2.63, `javajit` 3.76.**
Within a factor of 1.5 across four packages that between them are 281 KLOC. Failure origination is
uniformly distributed. It is not concentrated anywhere.

All `throw new` sites, `javatools/src/main`: `asm` 732, `runtime` 345, `compiler` 234, `javajit` 173,
`type` 25, `api` 16, `tool` 15. Genuine rethrows (`throw x;`, not `throw new`): `compiler` 29,
`runtime` 14, `asm` 7, `tool` 3 — **53 in the whole of `javatools/src/main`.** Failures are almost
never re-raised after inspection; they are raised once and caught once.

`catch` sites, `javatools/src/main` — 451 total, and excluding the 116 `WrapperException` catches
(which are the interpreter's own protocol, not Java failure handling):

| package | all catches | non-`WrapperException` | `throw new` | throw : catch |
|---|---:|---:|---:|---:|
| `org.xvm.runtime` | 181 | 172 | 345 | 2.0 |
| `org.xvm.asm` | 157 | 50 | 732 | 14.6 |
| `org.xvm.compiler` | 51 | 50 | 234 | 4.7 |
| `org.xvm.tool` | 34 | 34 | 15 | 0.4 |
| `org.xvm.javajit` | 19 | 19 | 173 | 9.1 |
| `org.xvm.api` | 6 | 6 | 16 | 2.7 |
| `org.xvm.type` | 3 | 3 | 25 | 8.3 |

`org.xvm.tool` is the only package that catches more than it throws. It is the handling layer, and it
already behaves like one — seven of the ten `IOException` catches that report to a listener are there.

### 3.3 The nets that exist today

There are exactly three outermost handlers, and each is a different shape:

1. **CLI** — `Launcher.java:361-372` (`catch (LauncherException) → console.log + exit code`, then
   `catch (Throwable) → "Unhandled exception"`), backed by `Launcher.java:174-179`
   (`catch (LauncherException e) { System.exit(e.getExitCode()); }`).
2. **Embedding API** — `api/BuildDriver.java:195-198`:
   ```java
   } catch (RuntimeException e) {
       return record(outcomes, module, Status.FAILED, 1, 0, Duration.between(start, Instant.now()),
               e.toString());
   }
   ```
   plus `XtcEngine.java:1066` (`CompletableFuture.failedFuture(e)`) and `XtcEngine.java:841`
   (`catch (CompilerException) → return null` for `parseModule`, with a comment about half-typed LSP
   buffers).
3. **Gradle plugin** — `plugin/.../IsolatedDirectExecutor.java:161`
   (`catch (RuntimeException e) { logger.error("Compilation failed: {}", e.toString()); return 1; }`)
   and `DirectStrategy.java:45/56/67`. **The plugin never names `CompilerException` or
   `LauncherException` anywhere** — verified by `rg -l` over all 53 plugin `.java` files.

So the answer to "is a consistent upstream handling boundary feasible?" is: **the boundary already
exists and is already consistent in placement — three nets, each at the right altitude.** What is not
consistent is what they can *say*. All three catch `RuntimeException` or `Throwable` and reduce it to
a string, because every failure below them has the same Java type. `BuildDriver` is the clearest
case: an embedding host cannot distinguish "your module path is wrong" from "the compiler has a bug"
from "the disk is full", because all three arrive as `e.toString()` of an `IllegalStateException`.

---

## 4. The public API is already a result type

This is easy to miss and it changes the whole calculus.

`org.xvm.api.XtcEngine` — the embedding API — **declares no checked exception on any compile or run
method**, and does not signal failure by throwing:

```java
public @NotNull CompileResult compile(@NotNull ErrorListener errsCaller, @NotNull SourceUnit… units)
public @NotNull CompletableFuture<ObjectHandle> run(@NotNull CompileResult result, @NotNull String sModuleName)
public @NotNull RunControl start(@NotNull CompileResult result, @NotNull String sModuleName)

public record CompileResult(@NotNull List<ModuleConstant> modules,
                            @NotNull List<Diagnostic> diagnostics, …) {
    public boolean isSuccess() { … }
    public @NotNull List<File> writeTo(@NotNull File dir) throws IOException { … }   // the one checked throw
}
public record Diagnostic(@NotNull Severity severity, @NotNull String code, @NotNull String message, …)
```

`RunControl` answers `Optional<Instant> whenStopped()`, `Optional<Long> result()`,
`Optional<Throwable> error()`. Records, `Optional`, `CompletableFuture`, `@NotNull` throughout, and a
diagnostic list on the result — this is R5 (record on the result, replay to whoever asserts) applied
at the API boundary, and it is already modern.

**Consequence for the question asked.** A campaign that made the interior throw checked exceptions
would be adding declarations to hundreds of intermediate methods so that `XtcEngine` and
`BuildDriver` could catch them and put them into `List<Diagnostic>`. The plumbing would be
transporting information to a place that has already decided not to throw. The place where the API
falls short is not that it lacks `throws`; it is that the **14 `IllegalStateException` throws that
escape `org.xvm.api` itself** are indistinguishable:

```
api/XtcEngine.java:279   "cannot prepare a library without the turtle module (…)"
api/XtcEngine.java:753   "storing module …"                      (wraps a cause)
api/XtcEngine.java:833   "assembling module …"                   (wraps an IOException)
api/XtcEngine.java:1123  <the XTC program's own failure text>
api/XtcEngine.java:1147  "unable to prepare …"                   (wraps a cause)
api/XtcEngine.java:1152  "missing dependency: …"
api/XtcEngine.java:1531  "cannot persist a failed compilation: …"
api/XtcEngine.java:1612  …
api/InterpreterConnector.java:65   "Connector is already activated"
api/InterpreterConnector.java:70   "Unable to load module \"…\""
api/InterpreterConnector.java:76   "Unable to load module \"…\""
api/InterpreterConnector.java:107  "The container has not been started"
api/InterpreterConnector.java:196  "the connector has not been started"
api/InterpreterConnector.java:201  …
```

Four different categories — misuse of the API (`already activated`, `has not been started`),
environment (`missing dependency`, `cannot prepare a library`), internal defect (`assembling module`),
and *the running program's own failure* (`:1123`) — one Java type, and a host that wants to react
differently has to parse English.

---

## 5. The runtime: why checked exceptions are impossible there, precisely

### 5.1 The protocol

`Op.process` (`javatools/src/main/java/org/xvm/asm/Op.java:92-100`):

```java
/**
 * Process this op.
 *
 * @param frame  current execution frame
 * @param iPC    instruction pointer
 *
 * @return a positive iPC or a negative {@code R_}* value
 */
public abstract int process(Frame frame, int iPC);
```

The return is a union: `>= 0` is a jump target, `< 0` is one of ten codes declared at `Op.java:2257-2306`
— `R_NEXT`, `R_RETURN`, `R_EXCEPTION`, `R_RETURN_EXCEPTION`, `R_CALL`, `R_RETURN_CALL`, `R_REPEAT`,
`R_BLOCK`, `R_PAUSE`, `R_RESET`. An Ecstasy exception is a **value in a frame field**,
`Frame.java:131`:

```java
public ExceptionHandle m_hException;   // an exception
```

raised by `Frame.raiseException(…)` (`Frame.java:1343-1358`, each overload preceded by
`// return R_EXCEPTION`) and unwound by an explicit pointer walk in `ServiceContext.java:657-720`
using `frame.findGuard(hException)` and `frame.f_framePrev`.

Scale: **318 `.raiseException(` call sites in `org.xvm.runtime`** (250 of them `return
frame.raiseException(`), plus **124 more in `org.xvm.asm`**; 140 `return Op.R_EXCEPTION` in the
runtime. `ExceptionHandle` never calls `fillInStackTrace` anywhere in `javatools/src/main` — the
Ecstasy stack trace is built from the frame chain.

### 5.2 The reason is representational, not (only) performance

The tempting argument is "int codes are faster than exceptions". That is probably true and it is not
the load-bearing argument. The load-bearing argument is in `R_CALL`'s own javadoc:

> `R_CALL` — "call the frame placed in `frame.m_frameNext`."

**The interpreter is non-recursive.** An Ecstasy call does not become a Java call; it becomes a
heap-allocated `Frame` and a return to the dispatch loop. Therefore the Java stack does not represent
the Ecstasy stack. A Java `throw` unwinds Java frames, and there is no correspondence between those
and the Ecstasy frames that a `try`/`catch` in Ecstasy source must unwind. `R_EXCEPTION` +
`m_hException` + `findGuard` is not an optimisation of Java exceptions; it is the only shape
consistent with heap frames and migratable fibers.

Corroborating design statements, quoted rather than inferred:

- `Frame.java:73-83` (class javadoc): *"frames are deliberate hot-path structs, and their fields are
  context-confined rather than synchronized"* — `m_hException` is in that list.
- `ServiceContext.java:573-576`, the hot loop's own comment:
  ```java
  } catch (RuntimeException | Error e) {
      // Op.process() does not declare natural XTC exceptions. Op implementations
      // convert those to R_EXCEPTION themselves; unchecked Java failures here are
      // VM/runtime defects that must reach the host failure boundary.
      throw unexpectedOpFailure(frame, op, iPCLast, e);
  }
  ```
- [`exception-hygiene-audit.md:471-475`](exception-hygiene-audit.md) states the same contract, and
  `:495` records the allocation budget: *"there is no hot-path allocation beyond one local `op`
  reference."*

Note carefully what that `catch` is: it is a **defect** catch, not a control-flow catch. It never
produces an Ecstasy exception. It replaced an older `catch (Throwable e)` that turned VM defects into
a user-catchable XTC `"Run-time error"` — the audit's reasoning at
[`exception-hygiene-audit.md:478-484`](exception-hygiene-audit.md) is that *"a bad owner assertion
should not be caught by user code as if it were an ordinary language exception."* That is R4 in the
runtime: recording a defect and choosing to stop are separate, and the *detecting* code decides.

`unexpectedOpFailure` (`ServiceContext.java:758-761`) builds an `IllegalStateException` carrying
service name, pc, op and `frame.getStackTrace()`; it lands in `ServiceContext.drainWork`'s
`catch (Throwable e)` at `:340-345` and is handed to `Container.recordRuntimeFailure`
(`Container.java:766-793`), which CASes it into `f_runtimeFailure`, reports `RT_INTERNAL_FAILURE`
through the container's `ErrorListener`, and prints the trace. `InterpreterConnector.join()`
(`:148, :152`) rethrows it. **That is a complete, deliberate, single-channel failure path for VM
defects, built on this branch, and it is the strongest existing evidence that the architecture the
question asks about is achievable — it just was not achieved with `throws`.**

### 5.3 The JIT uses the opposite protocol, and that is fine

`org.xvm.javajit` generates bytecode that signals an Ecstasy exception with a real `athrow` of a
`RuntimeException` subclass (`nException`, paired with the Ecstasy `Exception` value). `Builder.throwException`
(`Builder.java:1594-1602`) ends in `.athrow()`; `CatchStart.build` (`:158-175`) and `FinallyStart.build`
(`:126-141`) emit JVM exception-table entries. `javajit/Ctx` has no exception field at all.

The single `Throw` op (`javatools/src/main/java/org/xvm/asm/op/Throw.java`) shows both in one file:
`process(…)` at `:73-84` returns `frame.raiseException(…)`; `build(…)` at `:102-109` emits
`getfield($exception).athrow()`.

The implication for this report: **the runtime's non-exception protocol is a property of the
interpreter, not of the language or of the VM design.** When the JIT owns execution, the JVM's own
exception machinery is used directly. So "the interpreter cannot use exceptions" is not a permanent
constraint on the whole runtime — it is a constraint on one of two execution engines, and the other
already does the opposite.

---

## 6. Verdict

### 6.1 The strongest case FOR the redesign

1. **The type carries nothing.** 959 `IllegalStateException` throws; 458 with no message; 20 with a
   javadoc contract; 25 with a cause. 38 tests string-match the message because the type cannot tell
   them what failed.
2. **The boundary can't act.** All three outermost nets reduce every failure to a string
   (`BuildDriver.java:197`, `IsolatedDirectExecutor.java:161`, `Launcher.java:365`). An embedding
   host has no programmatic way to distinguish user error, environment failure and compiler defect.
   E47 already identified this as *the* reason to care.
3. **Unchecked hides real reach.** `Launcher.report(FATAL, …)` throws from 17 call sites and
   `checkErrors()` from ~35 more, and no signature says so.
4. **The hierarchy is wrong in a way that produces bad output.** `CompilerException extends
   LauncherException` means an escaped parser abort is printed by the CLI as a user-facing error with
   the cause dropped; `NativeContainer` throws the CLI's type for runtime linkage failures.
5. **The precedent worked.** The `ErrorListener` campaign proved that this tree can absorb a
   cross-cutting ownership change if it is staged and gated, and it left behind exactly the
   machinery (`Severity`, `ErrorInfo`, codes, `Origin`, `Site`, branch/merge) that a typed exception
   would want to carry.
6. **`recordRuntimeFailure` is a working proof of concept.** One named channel, first-wins, cause
   preserved, `addSuppressed` for the rest, observable by a host through `join()` *and* through the
   listener. Three callers. That is what the rest should look like.

### 6.2 The strongest case AGAINST — specifically against *checked* exceptions

I want to state this as strongly as it deserves, because I think it wins on the checked question.

1. **Checked exceptions thread an obligation, not a value; the ErrorListener analogy breaks here.**
   R1 worked because a sink is *data*: you pass it, and only the endpoints care. A `throws` clause is
   a constraint on every intermediate frame, and it propagates transitively through overrides. With
   throws distributed at ~3 per KLOC across 281 KLOC of `asm` + `compiler` + `runtime` + `javajit`
   (§3.2), a checked failure type introduced anywhere near the bottom lands on essentially every
   method in `javatools`. That is not the same shape of change as E32 at all.

2. **The type system's own units are sealed families with hundreds of members.**
   `javatools/src/main/java/org/xvm/asm/op` holds **215** class declarations, all `Op` descendants
   through a dozen intermediate abstract bases (`OpCallable` 38, `OpInvocable` 17, `OpCondJump` 17,
   `OpVar` 16, `OpTest` 14, `OpGeneral` 14, `Op` directly 12, …); **68 files declare a `sealed`
   type** (125 `sealed` tokens, plus 7 `non-sealed`). Adding a `throws` to a base method is
   a several-hundred-file change with zero behavioural difference and a permanent conflict surface
   against master. E35's step 3 already faced this exact trade — renaming `ensureTypeInfo` would have
   touched 142 call sites — and chose *a gate instead of a rename*, explicitly because "pure churn
   and a large future conflict surface" is not worth a distinction the call site already makes. The
   same reasoning applies here with a bigger multiplier.

3. **Checked exceptions do not compose with the three functional shapes the tree depends on.**
   `Frame.Continuation` lambdas, `CompletableFuture` chains, and `Op.process`'s int protocol all
   require a non-throwing signature. The tree already has the wrapper this forces —
   `ExceptionHandle.WrapperException`, caught 116 times, 94 of them in `asm/op`, converted straight
   back to `R_EXCEPTION`. Every new checked type would need its own equivalent, and the existing one
   is already a wart (a non-static inner class holding an implicit reference to its handle).

4. **E47 step 3 produced a hard counter-example.** Four constant-folding sites (`CmpExpression`,
   `RelOpExpression`, `UnaryMinusExpression`, `UnaryComplementExpression`) could not have their
   `catch (RuntimeException)` narrowed, because the *expected* failure set —
   `ArithmeticException`, `UnsupportedOperationException`, `IllegalStateException` — is not
   distinguishable by type from a genuine defect. Making those checked would not fix it; it would
   relocate the ambiguity into the signature. **What actually fixed them was reporting**
   (`COMPILER-210` at INFO through the `errs` already in scope). That is a direct data point that in
   this codebase, for this class of problem, the diagnostic channel beats the type channel.

5. **The API boundary has already chosen result types.** §4. Checked exceptions in the interior would
   be plumbing toward a boundary that catches and discards the mechanism.

6. **The interpreter is excluded on principle, not on taste.** §5.2. That removes
   `asm/op` (73 `IllegalStateException` sites across 215 classes) and `runtime` core (104) from any
   conversion — 177 sites, 18% of the total, permanently.

7. **Three quarters of the sites are not failures.** 459 exhaustiveness arms + 99 unsupported-operation
   bodies = 558 of 959 (58%). Adding `throws` to those would be actively wrong: an exhaustiveness arm
   should become *unreachable by construction*, not declared.

**Conclusion on the narrow question: checked exceptions are the wrong answer for `org.xvm.asm`,
`org.xvm.compiler`, `org.xvm.runtime` and `org.xvm.javajit`.** They are the right answer where they
already are — `IOException` across the 633 `asm` serialization declarations, which is a genuine
environmental failure that the *immediate* caller can act on, and which is already leaking to
unchecked at only 9 sites in `javatools/src/main`.

### 6.3 What the answer actually is

Not "checked vs unchecked". The real finding is that **the tree has one exception type doing five
jobs**, and the fix is to stop overloading it — mostly by *deleting* uses, and for the residue by
naming them.

| what the site means | n (approx.) | right mechanism | why |
|---|---:|---|---|
| this enum/code cannot occur | 459 | **delete** — sealed + exhaustive switch (E2/E18) | the compiler should prove it, not the runtime |
| this subtype does not implement this operation | 99 | `UnsupportedOperationException` **with a message** | already the JDK's meaning; E8 did this once for the runtime |
| the caller passed something wrong | subset of 214 | `IllegalArgumentException` / `Objects.requireNonNull` | caller error is a distinct contract |
| an internal invariant broke | rest of 214 + part of 187 | `IllegalStateException`, message mandatory | the only use that should keep the name |
| an operation the caller asked for failed | part of 187 + the 14 in `api` + the `LauncherException` family | **a sealed unchecked hierarchy with a cause and a declared handling boundary** | this is the only bucket where a handling layer helps |

Feasibility, plainly: **yes for the first four rows, which are the large majority and are individually
mechanical. Yes for the fifth as a sealed unchecked hierarchy. No for checked exceptions anywhere
except where they already are.**

---

## 7. What a modern Java equivalent looks like

### 7.1 Sealed exception hierarchies — yes, and the tree is already set up for it

68 files already declare a `sealed` type; `TypeInfo` is `sealed permits TypeInfoReal`;
`SealedAstFamiliesTest` already gates a sealed family. The shape that fits:

```java
public sealed abstract class XvmException extends RuntimeException
        permits XvmUsageException,      // the caller misused the API
                XvmEnvironmentException, // module path, unreadable file, missing dependency
                XvmDefectException,      // an XVM invariant broke; a bug in this codebase
                LauncherException {      // process-exit control flow; CLI only
    // code + params, so a host can switch, and a message can be formatted the way ErrorInfo does
    public abstract String code();
    public abstract Object[] params();
}
```

Why sealed and not just abstract: a boundary that catches `XvmException` and switches over the
permitted subtypes is exhaustively checked, which is exactly what `BuildDriver.java:195` cannot do
today. Why unchecked: §6.2.

`ModuleLoadException` (3 throws) is already `XvmEnvironmentException` under a different name and its
javadoc already explains the distinction it draws. `SegFault extends Error` in `runtime/gc` is
correct as-is and should stay outside the hierarchy.

The one inheritance change that is worth making on its own is **`CompilerException` must stop
extending `LauncherException`** (§2.5). They model different things and the accidental catch at
`Launcher.java:363` is a real output defect.

### 7.2 Exceptions carrying causes — yes, and this is the cheapest win

25 of 959 `IllegalStateException` throws carry a cause; 38 are thrown from a catch and 13 of those
drop it. E47 already found the shape of the resulting bug twice: `MainContainer.invoke0` wrapped a
startup failure in a cause-less `RuntimeException`, and `Parser` reported "no such directory or file"
for a file that existed and could not be read, because the `IOException` had been discarded. Both are
fixed; the pattern is not.

The `Container.recordRuntimeFailure` idiom — first-wins CAS, `addSuppressed` for subsequent failures
— is the right modern shape for a channel that can receive more than one failure, and it is already
implemented and tested here.

### 7.3 Result types — yes, and they are already the API's answer

`CompileResult`/`Diagnostic`/`RunControl` (§4) are the modern shape and are in place. The
generalisable rule is R5: **the result carries the diagnostics its own construction produced, and a
caller that asserts replays them.** `TypeInfo.diagnostics()` / `replayDiagnostics(errs)` is that
already, inside the type system.

Where a result type would *newly* help: `ModuleRepository.loadModule` returning `null` for both "not
present" and "present and unreadable" is the failure E47 row 49 fixed the hard way. That is a
two-valued answer wearing a one-valued type, and it is the kind of place a small sealed result
(`Found` / `Absent` / `Unreadable(cause)`) is genuinely better than either null or an exception.

### 7.4 `Optional` — mostly no, and this needs saying

29 uses in main source, 729 `return null;` statements, 362 of them in the compiler where "null" means
`NoFit` — the compiler's speculative answer. Converting those to `Optional` would allocate on the
hottest speculation paths in the compiler, touch hundreds of sites, and buy very little, because the
"is this call a question or an assertion?" distinction has already been made *by other means* (R3:
`typeInfo()` vs `ensureTypeInfo(errs)`, `PROBE` vs `BLACKHOLE`). `Optional` on **new public API
return types** — as `RunControl` already does — is right; a sweep of the interior is not.

### 7.5 What must NOT be adopted

- **Do not let any exception type depend on which listener is installed.** That was
  `ErrorListener.RUNTIME` throwing from `log()`, and README §5.1 records exactly why it was removed.
- **Do not introduce a checked exception into `Op.process` or any `Frame.Continuation`.** §5.
- **Do not convert the exhaustiveness arms into typed exceptions.** They should become unreachable,
  not better-named.

---

## 8. Concrete staged steps

In the E32/E35 shape: measured count, what each buys, what it depends on, how it is gated. Every step
is independently reviewable and none depends on a later one.

### X1 — Delete the exhaustiveness throws that a sealed family makes unnecessary
**Count:** 459 switch/case arms, of which 314 carry no message.
**Depends on:** E2 ("seal the hierarchies and make dispatch exhaustive") and E18 ("close the switches
that CAN be closed"). This step is the *consumer* of those, not a prerequisite.
**Buys:** the largest single reduction, and it removes a runtime failure rather than renaming one. For
an exhaustive switch over a sealed type or a complete enum, javac proves the arm unreachable and the
arm goes away.
**Gate:** the existing `SealedAstFamiliesTest` pattern, per family.
**Uncertainty:** I did not measure how many of the 459 are over a sealed type or complete enum versus
over an `int` (`R_` codes, op-codes) or a `Format` with legitimately unhandled members. Some — the
`default: throw new IllegalStateException("Invalid code: " + iPC)` at `ServiceContext.java:745` — are
over an int union and can never be closed. **This step's true size is unknown and must be measured
before it is scheduled.**

### X2 — Every remaining message-less throw gets a message
**Count:** 458 today; after X1, the remainder (144 are already non-switch).
**Depends on:** nothing.
**Buys:** the difference between a stack trace that names a line and one that says what was violated.
This is precisely what E8 already did once (`dc3e0d90d`, "give every message-less
`UnsupportedOperationException` in the runtime a real message"), so the precedent and the review
appetite both exist.
**Gate:** a source-shape test — `throw new IllegalStateException()` with an empty argument list
appears zero times — in the style of `TypeInfoModeIsExplicitTest`. Mutation-check by reintroducing one.

### X3 — Reclassify by intent, so the type name means one thing
**Count:** 99 whole-method-body sites → `UnsupportedOperationException`; the caller-error subset of
the 214 guarded sites → `IllegalArgumentException` / `Objects.requireNonNull`; the rest stay
`IllegalStateException`.
**Depends on:** X2 (a message is what lets you classify a site at all).
**Buys:** `grep IllegalStateException` becomes the list of internal invariants, the way
`grep PROBE` became the list of speculative paths. It also makes the 55 `assertThrows(ISE)` tests
meaningful.
**Gate:** per-file; the count of `IllegalStateException` in `javatools/src/main` is the measure.
**Note:** this is where the `assert`-versus-`throw` inconsistency (§2.3) has to be decided. 1614
`assert`s and ~400 always-on guards express the same idea with different survival properties in an
embedding host. Deciding the rule is a prerequisite for X3 being coherent, and it is a design
decision, not a mechanical one.

### X4 — The sealed hierarchy, introduced at the boundary and pushed down only where it pays
**Count:** the 14 `org.xvm.api` sites, the 9 `LauncherException` sites (+17 `report(FATAL, …)` call
sites and ~35 `checkErrors()` sites reaching them), the 3 `ModuleLoadException` sites, and
`unexpectedOpFailure`.
**Depends on:** X3 having established what "internal defect" means.
**Buys:** `BuildDriver.java:195` can `switch` instead of calling `e.toString()`; a host can tell
misuse from environment from defect. Also unhooks `CompilerException` from `LauncherException`.
**Gate:** a test that every exception reaching each of the three nets is an `XvmException`, i.e. the
`catch (RuntimeException)` at `BuildDriver.java:195` becomes `catch (XvmException)` with a separate,
failing-loudly branch for anything else.
**Deliberately not:** a sweep converting the interior 900 sites. The hierarchy exists so the boundary
can act; the interior converts only where a *specific* caller needs to distinguish something.

### X5 — Close the checked-to-unchecked conversions that lose information
**Count:** 9 in `javatools/src/main` (`XtcEngine:832, 1146`; `Component:822`; `FileStructure:1151`;
`MethodStructure:706, 1141, 2271, 2812`; `Source:91`), plus 13 discarding empty `catch (IOException)`
blocks (already audited by E47 and mostly correct).
**Depends on:** nothing.
**Buys:** the smallest, most defensible step. `Source(InputStream)` converting `IOException` to a bare
`RuntimeException` (`Source.java:91-93`) is already on the exception-hygiene backlog. Note that
`MethodStructure:2156` deliberately does **not** convert — it reports `FATAL` then throws, which is
the README §7 rule — so this step is about the ones that do it silently.
**Gate:** per site; `throw new RuntimeException(e)` inside a `catch (IOException)` reaches zero in
`javatools/src/main`.

### X6 — Make the three nets say what they caught
**Count:** 3 handlers (`Launcher.java:361-372`, `BuildDriver.java:195` + `XtcEngine.java:1066`,
`IsolatedDirectExecutor.java:161` + `DirectStrategy.java:45/56/67`).
**Depends on:** X4.
**Buys:** the actual product-visible outcome. Today a Gradle user sees a one-line `toString()` with
the cause dropped for a compiler defect and for a missing module alike.
**Gate:** an integration test per net asserting that a synthetic defect and a synthetic user error
produce distinguishable output.

**Ordering.** X2 and X5 can start immediately and are independently reviewable. X3 needs the
`assert` policy decided. X1 is gated on E2/E18 and needs sizing first. X4 and X6 are the pair that
delivers the user-visible improvement and should be done together, after X3.

**What this does not include, deliberately:** any `throws` clause added to `Op`, `Frame`,
`Continuation`, `Constant`, `XvmStructure`, `AstNode` or any of their subtypes; any `Optional` sweep;
any change to the `R_*` protocol.

---

## 9. Would the result look like a modern Java API?

Yes, and the honest form of that answer is that **the outermost layer already does** (§4: records,
`Optional`, `CompletableFuture`, `@NotNull` at 345 sites, a diagnostic-carrying result). What an
open-source consumer currently hits is not an old-fashioned API surface; it is a modern surface with
one blind spot — every failure below it has the same type and arrives as a string.

After X2–X6 the surface would be: a sealed `XvmException` a host can switch over; every diagnostic
still delivered through `ErrorListener` and `CompileResult` (unchanged); causes preserved;
`IOException` still checked exactly where it is environmental and the caller can act; and the
interpreter's int protocol untouched, because it is correct.

What it would *not* look like is a tree of `throws` declarations. That is the part of the original
proposition that the measurements do not support, and §6.2 is the argument.

---

## 10. Uncertainties, stated

1. **X1's true size is unmeasured.** I counted 459 switch/case arms but did not classify them by
   whether the switch is over a sealed type, a complete enum, an int union, or a `Format` with
   legitimately open members. The convertible fraction could plausibly be anywhere from 30% to 80%.
2. **Bucket 3/4 boundary in §2.2 is heuristic.** "Guarded by an `if`" is detected from the preceding
   non-blank line. A guard split across lines, or one whose condition is on an earlier line, lands in
   bucket 4. I spot-checked ~35 sites across all buckets and found no misclassification, but the
   split between 214 and 187 should be treated as ±20 rather than exact.
3. **"Caller error" vs "internal invariant" was not counted.** I did not classify the 214 guarded
   sites into those two, because doing it honestly requires reading each one against its callers.
   §6.3 gives the rule; X3 is where the count gets produced.
4. **`System.err` counts differ from E47's** (34 vs 22). I did not re-derive E47's figure, so I
   cannot say how much is drift and how much is a different regex — mine counts the token
   `System.err` including comments, tests and the plugin's stream plumbing; E47 counted
   `System.err.println` call sites. E47's classification stands; treat §2.6 as a re-count under a
   stated definition, not a contradiction. The empty-catch figure, by contrast, **does** reconcile:
   19 same-line empties in `javatools/src/main` is exactly README §6's number.
5. **`RuntimeFailurePropagationTest` does not exist in this tree**, though
   `exception-hygiene-audit.md` cites it as the guard for the op-loop change. `rg` finds zero `.java`
   files matching that name. Either it was renamed or the doc is stale; worth resolving, because it
   is the named regression guard for §5.2's contract.
6. **I did not run the build or the test suite.** This is a read-and-analyse report, as scoped.

---

## 11. Sources

- [`docs/errorlistener/README.md`](../errorlistener/README.md) — §§2.3, 5, 6, 7, 8.4, 9 (the precedent).
- [`docs/reentrancy/plans/master-enhancement-submissions.md`](plans/master-enhancement-submissions.md) —
  rows E2, E8, E11, E18, E32, E34, E35, E47.
- [`docs/reentrancy/plans/master-issue-submissions.md`](plans/master-issue-submissions.md) —
  rows 47-52.
- [`docs/reentrancy/exception-hygiene-audit.md`](exception-hygiene-audit.md) — the site-by-site audit
  this report sits on top of; §§ "Intended Propagation Model", "Typed Exceptions Worth Adding",
  "Inappropriate `RuntimeException` Use".
- [`docs/reentrancy/logging-diagnostics-audit.md`](logging-diagnostics-audit.md) — the diagnostics
  half of the same problem.
