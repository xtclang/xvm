# I1–I3 and C1–C3 branch report

Snapshot: 2026-10-08. This report covers the six prepared **20261008** review branches, not the older undated extraction branches or the full `lagergren/errs` branch.

File lists and line counts come directly from the pinned Git revisions below. Every review branch contains one incremental commit. Counts are relative to its intended review base: C2 excludes inherited C1 changes, and C3 excludes inherited C1/C2 changes.

The six incremental diffs contain **133 file entries across 114 distinct paths**: 19 additions and 114 modifications, with no deletions or renames. A path changed in multiple slices appears in each relevant list. These are diff counts, not a measure of semantic complexity.

| Slice | Scope and purpose | Review base | Files | Added lines | Removed lines |
| --- | --- | --- | ---: | ---: | ---: |
| I1 | Prevent different errors or unsaved documents from being collapsed during diagnostic deduplication. | Pinned master | 4 | 132 | 3 |
| I2 | Make audited constant and method inspection work on host threads without a current constant pool. | Pinned master | 12 | 200 | 19 |
| I3 | Test the compiler from a language-tooling consumer and reject missing or skipped consumer results in CI. | Pinned master | 7 | 162 | 8 |
| C1 | Give embedding hosts a usable listener contract with explicit reporting, state and abort decisions. | Pinned master | 18 | 1102 | 71 |
| C2 | Propagate the caller's listener and make deliberate diagnostic suppression visible at call sites. | C1 | 74 | 837 | 512 |
| C3 | Keep speculative diagnostics only when accepted and restore temporary listeners/contexts after completion or exceptions. | C2 | 18 | 875 | 159 |

## Branch relationships and review basis

The common master revision is `f442aced626085bba6623d8345dda9d73c3397d4`. I1, I2, I3 and C1 each start directly there. C2 starts at C1; C3 starts at C2. I1–I3 are independently reviewable and have no prerequisite on one another or the C-series stack.

```text
master @ f442aced6
├── I1
├── I2
├── I3
└── C1 → C2 → C3
```

Independent branches can still edit the same file: I1 and C1/C2 touch ErrorListener.java; I2 and C2 touch TypeConstant.java. Their independence refers to prerequisites and acceptance, not disjoint file ownership.

The later rebase of the integrated branch did not move these review tips. Use the pinned base below rather than whichever commit a local or remote `master` currently names.

File links use repository-relative paths so they can open from IntelliJ. They show the **current checkout**, which may contain later integrated changes. The patch link in each section shows that slice's exact change. I1's NamedSourceDiagnosticsTest.java is absent from this checkout; its link opens an exact source snapshot exported from I1 under `build/errs-review-2026-10-08/sources/`.

In the file tables, **A** means added and **M** means modified relative to that slice's review base. The # column enumerates every touched file.

## I1 — Preserve distinct diagnostics and name in-memory sources

- Branch: `errs/i1-diagnostic-identity-20261008`
- Tip: `e649179e7b0e899b16327068a931ef0016874e04`
- Review base: `master` at `f442aced626085bba6623d8345dda9d73c3397d4`
- Exact diff: [I1.patch](build/errs-review-2026-10-08/I1.patch)
- Size: 4 files (2 added, 2 modified), +132 / −3 lines.

**Purpose.** The existing diagnostic identity repeated the start position where the end position belonged and reduced message parameters to a 32-bit hash. Different spans or values could therefore suppress one another. Unnamed in-memory documents also lacked distinct source labels.

**Scope.**

- Correct the diagnostic key to use the actual end position and rendered parameter values.
- Add Source(String text, String name), preserving a caller-supplied, non-null label for an in-memory source. A URI can be used as the label; the constructor does not access a file.
- Add regressions for different span endpoints, colliding Java parameter hashes, exact duplicates, named-source reporting and multiple named buffers.

**Compatibility and limits.** This is an additive source-constructor change plus a deduplication bug fix. Existing listener signatures and file-backed source behavior remain. The key still uses rendered values, so it is not a complete structural-equality redesign for arbitrary diagnostic objects.

**Previously recorded validation.** The recorded independent run passed all five new regressions and two enabled SourceTest cases; three pre-existing SourceTest cases remained disabled. SpotlessCheck passed.

**Exact files touched.**

| # | Status | File | + lines | − lines |
| ---: | :---: | --- | ---: | ---: |
| 1 | M | [javatools/src/main/java/org/xvm/asm/ErrorListener.java](javatools/src/main/java/org/xvm/asm/ErrorListener.java) | 5 | 2 |
| 2 | M | [javatools/src/main/java/org/xvm/compiler/Source.java](javatools/src/main/java/org/xvm/compiler/Source.java) | 23 | 1 |
| 3 | A | [javatools/src/test/java/org/xvm/asm/ErrorDeduplicationTest.java](javatools/src/test/java/org/xvm/asm/ErrorDeduplicationTest.java) | 66 | 0 |
| 4 | A | [javatools/src/test/java/org/xvm/compiler/NamedSourceDiagnosticsTest.java](build/errs-review-2026-10-08/sources/I1/javatools/src/test/java/org/xvm/compiler/NamedSourceDiagnosticsTest.java) | 38 | 0 |

## I2 — Use owning constant pools when no ambient pool is bound

- Branch: `errs/i2-ambient-pools-20261008`
- Tip: `6c6720fa4e1ce34d95153e3b851df25406cd62f2`
- Review base: `master` at `f442aced626085bba6623d8345dda9d73c3397d4`
- Exact diff: [I2.patch](build/errs-review-2026-10-08/I2.patch)
- Size: 12 files (2 added, 10 modified), +200 / −19 lines.

**Purpose.** Embedding hosts, debugger watches and tests can inspect compiler objects outside a thread-local constant-pool scope. Some existing readers dereferenced the missing pool, including MethodBody inspection and display. A deliberately bound pool must still take precedence during compiler operations.

**Scope.**

- Add ConstantPool.currentOr(fallback) and Constant.poolInUse() to centralize the rule: use the currently bound pool, otherwise the relevant object's owner.
- Apply the fallback to the affected numeric, identity and type constants; method and property metadata; and InterpreterConnector.
- Add tests for unbound inspection, owner fallback and precedence/restoration of an explicitly bound alternate pool.

**Compatibility and limits.** The helpers are additive and do not bind a pool or alter its lifetime. This is a targeted set of reader fixes, not a replacement for ambient ownership or a guarantee of compiler/runtime thread safety. A null fallback can still produce null when no pool is bound.

**Previously recorded validation.** The recorded independent run passed 11 tests with no skips, built and installed all 24 XDK modules, and passed SpotlessCheck. The refreshed I2 run did not repeat the earlier normalized module-byte comparison.

**Exact files touched.**

| # | Status | File | + lines | − lines |
| ---: | :---: | --- | ---: | ---: |
| 1 | M | [javatools/src/main/java/org/xvm/api/InterpreterConnector.java](javatools/src/main/java/org/xvm/api/InterpreterConnector.java) | 3 | 1 |
| 2 | M | [javatools/src/main/java/org/xvm/asm/Constant.java](javatools/src/main/java/org/xvm/asm/Constant.java) | 13 | 0 |
| 3 | M | [javatools/src/main/java/org/xvm/asm/ConstantPool.java](javatools/src/main/java/org/xvm/asm/ConstantPool.java) | 27 | 1 |
| 4 | M | [javatools/src/main/java/org/xvm/asm/constants/ByteConstant.java](javatools/src/main/java/org/xvm/asm/constants/ByteConstant.java) | 6 | 6 |
| 5 | M | [javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java](javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java) | 1 | 1 |
| 6 | M | [javatools/src/main/java/org/xvm/asm/constants/IntConstant.java](javatools/src/main/java/org/xvm/asm/constants/IntConstant.java) | 4 | 4 |
| 7 | M | [javatools/src/main/java/org/xvm/asm/constants/MethodBody.java](javatools/src/main/java/org/xvm/asm/constants/MethodBody.java) | 11 | 2 |
| 8 | M | [javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java](javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java) | 1 | 1 |
| 9 | M | [javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java](javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java) | 1 | 1 |
| 10 | M | [javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java](javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java) | 2 | 2 |
| 11 | A | [javatools/src/test/java/org/xvm/asm/ConstantPoolAmbientTest.java](javatools/src/test/java/org/xvm/asm/ConstantPoolAmbientTest.java) | 67 | 0 |
| 12 | A | [javatools/src/test/java/org/xvm/asm/constants/MethodBodyAmbientPoolTest.java](javatools/src/test/java/org/xvm/asm/constants/MethodBodyAmbientPoolTest.java) | 64 | 0 |

## I3 — Require a real compiler consumer in language-tooling CI

- Branch: `errs/i3-compiler-consumer-tests-20261008`
- Tip: `21e8d6b2cb7eaeb8c4842df53705e44dc722af45`
- Review base: `master` at `f442aced626085bba6623d8345dda9d73c3397d4`
- Exact diff: [I3.patch](build/errs-review-2026-10-08/I3.patch)
- Size: 7 files (2 added, 5 modified), +162 / −8 lines.

**Purpose.** A compiler can build successfully while its embedding consumer fails to resolve modules or use the API. Tests that silently skip when XDK artifacts are missing cannot prove that boundary works. Compiler-only changes also need to invalidate the relevant language-tooling checks.

**Scope.**

- Add a required Kotlin smoke test that compiles a small Ecstasy module through the existing Java embedding API and verifies the resulting module and diagnostics.
- Resolve compiled Ecstasy/native-bridge module variants as declared Gradle test inputs. The fixture requires the system, bootstrap and native modules instead of assuming an installed XDK.
- Extend the shared CI classifier and core-check fingerprint to compiler inputs, and require nonempty JUnit results with zero failures, errors or skips before recording the core-check success marker.
- Make IntelliJ and VS Code packaging opt-in for the aggregate lang lifecycle through separate attachment flags. Explicit plugin tasks and root composite-inclusion defaults retain their roles.

**Compatibility and limits.** This adds test/build/CI infrastructure, not a production compiler-backed LSP implementation. The required gate initially covers one smoke-test suite in the existing Ubuntu core lane. Valid Gradle cached results remain reusable; this is not a new cross-platform IDE acceptance matrix.

**Previously recorded validation.** The recorded consumer ran twice with one test and zero failures/errors/skips, storing then reusing the configuration cache. Root/LSP formatting, 11 CI classifier/fingerprint controls and six result-gate controls passed. Workflow lint added no findings relative to the 80 existing findings on the base. These were local checks, not a remote CI run.

**Exact files touched.**

| # | Status | File | + lines | − lines |
| ---: | :---: | --- | ---: | ---: |
| 1 | M | [.github/scripts/ci-changes.py](.github/scripts/ci-changes.py) | 18 | 6 |
| 2 | M | [.github/workflows/commit.yml](.github/workflows/commit.yml) | 15 | 0 |
| 3 | M | [lang/build.gradle.kts](lang/build.gradle.kts) | 27 | 2 |
| 4 | M | [lang/lsp-server/README.md](lang/lsp-server/README.md) | 25 | 0 |
| 5 | M | [lang/lsp-server/build.gradle.kts](lang/lsp-server/build.gradle.kts) | 28 | 0 |
| 6 | A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerConsumerTest.kt](lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerConsumerTest.kt) | 20 | 0 |
| 7 | A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerTestSupport.kt](lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerTestSupport.kt) | 29 | 0 |

## C1 — Separate diagnostic reporting from listener abort policy

- Branch: `errs/c1-listener-contract-20261008`
- Tip: `dfe64261448836a95fc09670bb6650be746c0660`
- Review base: `master` at `f442aced626085bba6623d8345dda9d73c3397d4`
- Exact diff: [C1.patch](build/errs-review-2026-10-08/C1.patch)
- Size: 18 files (5 added, 13 modified), +1102 / −71 lines.

**Purpose.** Reporting a diagnostic previously doubled as a control-flow decision, and a bare callback did not maintain the severity/code state queried by compiler callers. Branching, merging, forwarding and error budgets need predictable behavior for custom host listeners.

**Scope.**

- Change ErrorListener.log and the affected forwarding helpers to return void. Compiler callers report first and consult isAbortDesired() explicitly where required.
- Add Site.In, Site.At and Site.None, severity helpers and trailing varargs for message parameters.
- Add collecting and tee helpers; preserve listener state, parent abort requests and configured budgets through branching and forwarding. collecting forwards every report; ErrorList performs deduplication.
- Add a default ErrorList constructor and named error-budget policies, plus explicit PROBE/CASCADE/DISCARD silence helpers for the next migration step.
- Make the RUNTIME listener record an abort request instead of throwing during reporting. Add contract/migration regressions and migration documentation.

**Compatibility and limits.** This intentionally breaks Java source and binary compatibility: return-type-only compatibility overloads are impossible. Clients/listener implementations must migrate and recompile for the planned 0.5.0 release. This slice retains the legacy blackhole names and null/ambient listener policy until subsequent slices. It does not make listeners concurrent.

**Previously recorded validation.** The recorded independent run executed 410 Java tests with zero failures/errors; 42 skips were recorded. All added tests executed. SpotlessCheck and a forced XDK rebuild passed; all 24 modules matched a rebuilt master baseline after normalizing only creation timestamps.

**Exact files touched.**

| # | Status | File | + lines | − lines |
| ---: | :---: | --- | ---: | ---: |
| 1 | M | [javatools/README.md](javatools/README.md) | 41 | 0 |
| 2 | M | [javatools/src/main/java/org/xvm/asm/Annotation.java](javatools/src/main/java/org/xvm/asm/Annotation.java) | 2 | 1 |
| 3 | M | [javatools/src/main/java/org/xvm/asm/ErrorList.java](javatools/src/main/java/org/xvm/asm/ErrorList.java) | 47 | 8 |
| 4 | M | [javatools/src/main/java/org/xvm/asm/ErrorListener.java](javatools/src/main/java/org/xvm/asm/ErrorListener.java) | 441 | 34 |
| 5 | M | [javatools/src/main/java/org/xvm/asm/XvmStructure.java](javatools/src/main/java/org/xvm/asm/XvmStructure.java) | 2 | 2 |
| 6 | M | [javatools/src/main/java/org/xvm/asm/constants/ImmutableTypeConstant.java](javatools/src/main/java/org/xvm/asm/constants/ImmutableTypeConstant.java) | 2 | 1 |
| 7 | M | [javatools/src/main/java/org/xvm/compiler/Lexer.java](javatools/src/main/java/org/xvm/compiler/Lexer.java) | 2 | 1 |
| 8 | M | [javatools/src/main/java/org/xvm/compiler/Parser.java](javatools/src/main/java/org/xvm/compiler/Parser.java) | 6 | 3 |
| 9 | M | [javatools/src/main/java/org/xvm/compiler/Token.java](javatools/src/main/java/org/xvm/compiler/Token.java) | 2 | 5 |
| 10 | M | [javatools/src/main/java/org/xvm/compiler/ast/AstNode.java](javatools/src/main/java/org/xvm/compiler/ast/AstNode.java) | 6 | 9 |
| 11 | M | [javatools/src/main/java/org/xvm/tool/Launcher.java](javatools/src/main/java/org/xvm/tool/Launcher.java) | 1 | 3 |
| 12 | M | [javatools/src/main/java/org/xvm/tool/ModuleInfo.java](javatools/src/main/java/org/xvm/tool/ModuleInfo.java) | 2 | 2 |
| 13 | A | [javatools/src/test/java/org/xvm/asm/ErrorListenerAbortTest.java](javatools/src/test/java/org/xvm/asm/ErrorListenerAbortTest.java) | 80 | 0 |
| 14 | A | [javatools/src/test/java/org/xvm/asm/ErrorListenerBranchTest.java](javatools/src/test/java/org/xvm/asm/ErrorListenerBranchTest.java) | 144 | 0 |
| 15 | A | [javatools/src/test/java/org/xvm/asm/ErrorListenerMigrationTest.java](javatools/src/test/java/org/xvm/asm/ErrorListenerMigrationTest.java) | 91 | 0 |
| 16 | A | [javatools/src/test/java/org/xvm/asm/ErrorListenerSilenceTest.java](javatools/src/test/java/org/xvm/asm/ErrorListenerSilenceTest.java) | 111 | 0 |
| 17 | A | [javatools/src/test/java/org/xvm/asm/ErrorListenerSiteTest.java](javatools/src/test/java/org/xvm/asm/ErrorListenerSiteTest.java) | 121 | 0 |
| 18 | M | [javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java](javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java) | 1 | 2 |

## C2 — Require explicit listeners and explicit reasons for silence

- Branch: `errs/c2-explicit-listeners-20261008`
- Tip: `cb4483dabcebdd104ed22d4fc64f5d1c0a29399f`
- Review base: `errs/c1-listener-contract-20261008` at `dfe64261448836a95fc09670bb6650be746c0660`
- Exact diff: [C2.patch](build/errs-review-2026-10-08/C2.patch)
- Size: 74 files (1 added, 73 modified), +837 / −512 lines.

**Purpose.** Implicit discard listeners and accepted nulls made an omitted diagnostic destination indistinguishable from intentional silence. The contract needs to reach compiler boundaries and the many AST call sites that report, speculate or suppress cascading errors.

**Scope.**

- Require non-null listeners at the embedding, ModuleCompiler, launcher, compiler, parser/lexer and other explicitly migrated boundaries, rejecting missing listeners before work begins.
- Propagate listeners through compiler, AST, type and tool call sites. Migrate reporting to the site/severity API introduced by C1.
- Use named silence reasons: PROBE for speculative fit checks, CASCADE for consequences of incomplete results, and DISCARD for intentional omission. Retained validation branches still merge their diagnostics.
- Remove BLACKHOLE/BlackholeErrorListener and the silent default from ResolutionCollector.getErrorListener(). Remove the listener parameter from the deliberately silent Expression.testFitAsType probe.
- Deprecate but retain the older positional reporting overloads. Add boundary regressions and update launcher tests and migration documentation.

**Compatibility and limits.** C2 depends on C1. Null rejection, removed blackhole names, the collector default removal and the testFitAsType signature change add compatibility breaks at the planned 0.5.0 boundary. The 74-file spread follows the listener call graph. File/pool ambient-listener ownership and the existing XvmStructure.log null fallback remain for later work.

**Previously recorded validation.** The recorded independent run executed 418 Java tests with zero failures/errors; 42 skips were recorded. All added tests executed. SpotlessCheck and a forced XDK rebuild passed; all 24 modules matched the normalized master baseline.

**Exact files touched.**

| # | Status | File | + lines | − lines |
| ---: | :---: | --- | ---: | ---: |
| 1 | M | [javatools/README.md](javatools/README.md) | 34 | 8 |
| 2 | M | [javatools/src/main/java/org/xvm/api/EmbeddingSupport.java](javatools/src/main/java/org/xvm/api/EmbeddingSupport.java) | 18 | 16 |
| 3 | M | [javatools/src/main/java/org/xvm/api/InterpreterControl.java](javatools/src/main/java/org/xvm/api/InterpreterControl.java) | 3 | 5 |
| 4 | M | [javatools/src/main/java/org/xvm/api/ModuleCompiler.java](javatools/src/main/java/org/xvm/api/ModuleCompiler.java) | 17 | 19 |
| 5 | M | [javatools/src/main/java/org/xvm/asm/ClassStructure.java](javatools/src/main/java/org/xvm/asm/ClassStructure.java) | 4 | 1 |
| 6 | M | [javatools/src/main/java/org/xvm/asm/Component.java](javatools/src/main/java/org/xvm/asm/Component.java) | 10 | 5 |
| 7 | M | [javatools/src/main/java/org/xvm/asm/ComponentResolver.java](javatools/src/main/java/org/xvm/asm/ComponentResolver.java) | 6 | 3 |
| 8 | M | [javatools/src/main/java/org/xvm/asm/ErrorListener.java](javatools/src/main/java/org/xvm/asm/ErrorListener.java) | 7 | 31 |
| 9 | M | [javatools/src/main/java/org/xvm/asm/MethodStructure.java](javatools/src/main/java/org/xvm/asm/MethodStructure.java) | 7 | 4 |
| 10 | M | [javatools/src/main/java/org/xvm/asm/XvmStructure.java](javatools/src/main/java/org/xvm/asm/XvmStructure.java) | 3 | 1 |
| 11 | M | [javatools/src/main/java/org/xvm/asm/constants/TypeCollector.java](javatools/src/main/java/org/xvm/asm/constants/TypeCollector.java) | 8 | 7 |
| 12 | M | [javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java](javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java) | 57 | 34 |
| 13 | M | [javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java](javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java) | 4 | 1 |
| 14 | M | [javatools/src/main/java/org/xvm/asm/constants/UnresolvedTypeConstant.java](javatools/src/main/java/org/xvm/asm/constants/UnresolvedTypeConstant.java) | 3 | 2 |
| 15 | M | [javatools/src/main/java/org/xvm/compiler/Compiler.java](javatools/src/main/java/org/xvm/compiler/Compiler.java) | 7 | 4 |
| 16 | M | [javatools/src/main/java/org/xvm/compiler/EvalCompiler.java](javatools/src/main/java/org/xvm/compiler/EvalCompiler.java) | 2 | 3 |
| 17 | M | [javatools/src/main/java/org/xvm/compiler/Lexer.java](javatools/src/main/java/org/xvm/compiler/Lexer.java) | 54 | 24 |
| 18 | M | [javatools/src/main/java/org/xvm/compiler/Parser.java](javatools/src/main/java/org/xvm/compiler/Parser.java) | 9 | 5 |
| 19 | M | [javatools/src/main/java/org/xvm/compiler/Token.java](javatools/src/main/java/org/xvm/compiler/Token.java) | 6 | 2 |
| 20 | M | [javatools/src/main/java/org/xvm/compiler/ast/AnnotationExpression.java](javatools/src/main/java/org/xvm/compiler/ast/AnnotationExpression.java) | 4 | 1 |
| 21 | M | [javatools/src/main/java/org/xvm/compiler/ast/AnonInnerClass.java](javatools/src/main/java/org/xvm/compiler/ast/AnonInnerClass.java) | 3 | 1 |
| 22 | M | [javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java](javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java) | 35 | 33 |
| 23 | M | [javatools/src/main/java/org/xvm/compiler/ast/AsExpression.java](javatools/src/main/java/org/xvm/compiler/ast/AsExpression.java) | 5 | 2 |
| 24 | M | [javatools/src/main/java/org/xvm/compiler/ast/AssignmentStatement.java](javatools/src/main/java/org/xvm/compiler/ast/AssignmentStatement.java) | 6 | 3 |
| 25 | M | [javatools/src/main/java/org/xvm/compiler/ast/AstNode.java](javatools/src/main/java/org/xvm/compiler/ast/AstNode.java) | 10 | 7 |
| 26 | M | [javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java](javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java) | 8 | 5 |
| 27 | M | [javatools/src/main/java/org/xvm/compiler/ast/CmpChainExpression.java](javatools/src/main/java/org/xvm/compiler/ast/CmpChainExpression.java) | 4 | 1 |
| 28 | M | [javatools/src/main/java/org/xvm/compiler/ast/CmpExpression.java](javatools/src/main/java/org/xvm/compiler/ast/CmpExpression.java) | 4 | 1 |
| 29 | M | [javatools/src/main/java/org/xvm/compiler/ast/Context.java](javatools/src/main/java/org/xvm/compiler/ast/Context.java) | 11 | 9 |
| 30 | M | [javatools/src/main/java/org/xvm/compiler/ast/ElseExpression.java](javatools/src/main/java/org/xvm/compiler/ast/ElseExpression.java) | 4 | 1 |
| 31 | M | [javatools/src/main/java/org/xvm/compiler/ast/ElvisExpression.java](javatools/src/main/java/org/xvm/compiler/ast/ElvisExpression.java) | 8 | 5 |
| 32 | M | [javatools/src/main/java/org/xvm/compiler/ast/Expression.java](javatools/src/main/java/org/xvm/compiler/ast/Expression.java) | 17 | 12 |
| 33 | M | [javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java](javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java) | 7 | 4 |
| 34 | M | [javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java](javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java) | 18 | 20 |
| 35 | M | [javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java](javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java) | 12 | 14 |
| 36 | M | [javatools/src/main/java/org/xvm/compiler/ast/ListExpression.java](javatools/src/main/java/org/xvm/compiler/ast/ListExpression.java) | 4 | 1 |
| 37 | M | [javatools/src/main/java/org/xvm/compiler/ast/MapExpression.java](javatools/src/main/java/org/xvm/compiler/ast/MapExpression.java) | 4 | 1 |
| 38 | M | [javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java](javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java) | 7 | 3 |
| 39 | M | [javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java](javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java) | 12 | 11 |
| 40 | M | [javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java](javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java) | 1 | 2 |
| 41 | M | [javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java](javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java) | 4 | 1 |
| 42 | M | [javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java](javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java) | 7 | 9 |
| 43 | M | [javatools/src/main/java/org/xvm/compiler/ast/NonBindingExpression.java](javatools/src/main/java/org/xvm/compiler/ast/NonBindingExpression.java) | 4 | 1 |
| 44 | M | [javatools/src/main/java/org/xvm/compiler/ast/NotNullExpression.java](javatools/src/main/java/org/xvm/compiler/ast/NotNullExpression.java) | 6 | 3 |
| 45 | M | [javatools/src/main/java/org/xvm/compiler/ast/ParenthesizedExpression.java](javatools/src/main/java/org/xvm/compiler/ast/ParenthesizedExpression.java) | 7 | 5 |
| 46 | M | [javatools/src/main/java/org/xvm/compiler/ast/RelOpExpression.java](javatools/src/main/java/org/xvm/compiler/ast/RelOpExpression.java) | 13 | 10 |
| 47 | M | [javatools/src/main/java/org/xvm/compiler/ast/ReturnStatement.java](javatools/src/main/java/org/xvm/compiler/ast/ReturnStatement.java) | 7 | 4 |
| 48 | M | [javatools/src/main/java/org/xvm/compiler/ast/SequentialAssignExpression.java](javatools/src/main/java/org/xvm/compiler/ast/SequentialAssignExpression.java) | 4 | 1 |
| 49 | M | [javatools/src/main/java/org/xvm/compiler/ast/StageMgr.java](javatools/src/main/java/org/xvm/compiler/ast/StageMgr.java) | 10 | 6 |
| 50 | M | [javatools/src/main/java/org/xvm/compiler/ast/StatementBlock.java](javatools/src/main/java/org/xvm/compiler/ast/StatementBlock.java) | 5 | 4 |
| 51 | M | [javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java](javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java) | 8 | 6 |
| 52 | M | [javatools/src/main/java/org/xvm/compiler/ast/TemplateExpression.java](javatools/src/main/java/org/xvm/compiler/ast/TemplateExpression.java) | 5 | 2 |
| 53 | M | [javatools/src/main/java/org/xvm/compiler/ast/TernaryExpression.java](javatools/src/main/java/org/xvm/compiler/ast/TernaryExpression.java) | 11 | 8 |
| 54 | M | [javatools/src/main/java/org/xvm/compiler/ast/TraceExpression.java](javatools/src/main/java/org/xvm/compiler/ast/TraceExpression.java) | 4 | 2 |
| 55 | M | [javatools/src/main/java/org/xvm/compiler/ast/TupleExpression.java](javatools/src/main/java/org/xvm/compiler/ast/TupleExpression.java) | 5 | 2 |
| 56 | M | [javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java](javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java) | 14 | 17 |
| 57 | M | [javatools/src/main/java/org/xvm/compiler/ast/TypeExpression.java](javatools/src/main/java/org/xvm/compiler/ast/TypeExpression.java) | 7 | 4 |
| 58 | M | [javatools/src/main/java/org/xvm/compiler/ast/UnaryComplementExpression.java](javatools/src/main/java/org/xvm/compiler/ast/UnaryComplementExpression.java) | 4 | 1 |
| 59 | M | [javatools/src/main/java/org/xvm/compiler/ast/UnaryMinusExpression.java](javatools/src/main/java/org/xvm/compiler/ast/UnaryMinusExpression.java) | 4 | 1 |
| 60 | M | [javatools/src/main/java/org/xvm/runtime/template/_native/lang/src/xRTCompiler.java](javatools/src/main/java/org/xvm/runtime/template/_native/lang/src/xRTCompiler.java) | 3 | 4 |
| 61 | M | [javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java](javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java) | 6 | 4 |
| 62 | M | [javatools/src/main/java/org/xvm/tool/Bundler.java](javatools/src/main/java/org/xvm/tool/Bundler.java) | 5 | 5 |
| 63 | M | [javatools/src/main/java/org/xvm/tool/Compiler.java](javatools/src/main/java/org/xvm/tool/Compiler.java) | 11 | 9 |
| 64 | M | [javatools/src/main/java/org/xvm/tool/Disassembler.java](javatools/src/main/java/org/xvm/tool/Disassembler.java) | 7 | 6 |
| 65 | M | [javatools/src/main/java/org/xvm/tool/Initializer.java](javatools/src/main/java/org/xvm/tool/Initializer.java) | 5 | 5 |
| 66 | M | [javatools/src/main/java/org/xvm/tool/Launcher.java](javatools/src/main/java/org/xvm/tool/Launcher.java) | 34 | 24 |
| 67 | M | [javatools/src/main/java/org/xvm/tool/ModuleInfo.java](javatools/src/main/java/org/xvm/tool/ModuleInfo.java) | 10 | 7 |
| 68 | M | [javatools/src/main/java/org/xvm/tool/Runner.java](javatools/src/main/java/org/xvm/tool/Runner.java) | 7 | 7 |
| 69 | M | [javatools/src/main/java/org/xvm/tool/TestRunner.java](javatools/src/main/java/org/xvm/tool/TestRunner.java) | 5 | 5 |
| 70 | A | [javatools/src/test/java/org/xvm/api/ErrorListenerBoundaryTest.java](javatools/src/test/java/org/xvm/api/ErrorListenerBoundaryTest.java) | 127 | 0 |
| 71 | M | [javatools/src/test/java/org/xvm/asm/ErrorListenerMigrationTest.java](javatools/src/test/java/org/xvm/asm/ErrorListenerMigrationTest.java) | 3 | 2 |
| 72 | M | [javatools/src/test/java/org/xvm/tool/BundlerTest.java](javatools/src/test/java/org/xvm/tool/BundlerTest.java) | 7 | 6 |
| 73 | M | [javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java](javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java) | 29 | 26 |
| 74 | M | [javatools/src/test/java/org/xvm/tool/LauncherVersionTest.java](javatools/src/test/java/org/xvm/tool/LauncherVersionTest.java) | 6 | 3 |

## C3 — Scope parser diagnostics and restore validation state on every exit

- Branch: `errs/c3-reporting-scopes-20261008`
- Tip: `4cfd442eb71132ac2dcd6afab80be2362f02bfb5`
- Review base: `errs/c2-explicit-listeners-20261008` at `cb4483dabcebdd104ed22d4fc64f5d1c0a29399f`
- Exact diff: [C3.patch](build/errs-review-2026-10-08/C3.patch)
- Size: 18 files (7 added, 11 modified), +875 / −159 lines.

**Purpose.** Parser lookahead and resolver callbacks temporarily redirect diagnostics. Loop and try/finally validation also retain context for lazy label-variable callbacks. Unscoped or separately managed state can leak discarded diagnostics, lose warnings, or survive exceptional exits.

**Scope.**

- Add Reporting scopes that restore the previous diagnostic destination through try-with-resources, and use them in the parser and name resolver.
- Replace Parser.SafeLookAhead with Parser.attempt()/Attempt.keep(). Accepted attempts merge buffered diagnostics, including warnings; discarded attempts restore token and recovery state. Nested attempts observe parent abort requests.
- Represent each active label-variable context/listener pair as an immutable ValidationScope, restoring the previous value in loop and try/finally statements. Restore the common Statement validation context on every exit.
- Give EvalCompiler and ModuleInfo.Node final diagnostic buffers from construction onward.
- Add tests for nested scopes, abort propagation, token rollback, failed callbacks, exceptional validation exits and diagnostic-buffer availability; document the parser migration.

**Compatibility and limits.** C3 depends on C2 and transitively C1. Removing the public SafeLookAhead class requires source migration/recompilation at the same planned 0.5.0 boundary. Reporting permits a null inactive destination; NameResolver borrows a non-null listener only during resolve. The scopes control lifetime, not thread safety. File/pool listener ownership remains outside this slice.

**Previously recorded validation.** The recorded independent run executed 439 Java tests with zero failures/errors; 42 skips were recorded. All added tests executed. SpotlessCheck, a forced XDK rebuild and normalized comparison of all 24 modules passed. The existing loop.x and exceptions.x exercises also compiled and ran without failure markers.

**Exact files touched.**

| # | Status | File | + lines | − lines |
| ---: | :---: | --- | ---: | ---: |
| 1 | M | [javatools/README.md](javatools/README.md) | 29 | 2 |
| 2 | A | [javatools/src/main/java/org/xvm/asm/Reporting.java](javatools/src/main/java/org/xvm/asm/Reporting.java) | 67 | 0 |
| 3 | M | [javatools/src/main/java/org/xvm/compiler/EvalCompiler.java](javatools/src/main/java/org/xvm/compiler/EvalCompiler.java) | 4 | 4 |
| 4 | M | [javatools/src/main/java/org/xvm/compiler/Parser.java](javatools/src/main/java/org/xvm/compiler/Parser.java) | 152 | 84 |
| 5 | M | [javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java](javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java) | 17 | 8 |
| 6 | M | [javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java](javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java) | 19 | 10 |
| 7 | M | [javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java](javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java) | 29 | 12 |
| 8 | M | [javatools/src/main/java/org/xvm/compiler/ast/Statement.java](javatools/src/main/java/org/xvm/compiler/ast/Statement.java) | 7 | 2 |
| 9 | M | [javatools/src/main/java/org/xvm/compiler/ast/TryStatement.java](javatools/src/main/java/org/xvm/compiler/ast/TryStatement.java) | 18 | 9 |
| 10 | A | [javatools/src/main/java/org/xvm/compiler/ast/ValidationScope.java](javatools/src/main/java/org/xvm/compiler/ast/ValidationScope.java) | 24 | 0 |
| 11 | M | [javatools/src/main/java/org/xvm/compiler/ast/WhileStatement.java](javatools/src/main/java/org/xvm/compiler/ast/WhileStatement.java) | 20 | 11 |
| 12 | M | [javatools/src/main/java/org/xvm/tool/ModuleInfo.java](javatools/src/main/java/org/xvm/tool/ModuleInfo.java) | 8 | 17 |
| 13 | A | [javatools/src/test/java/org/xvm/asm/ReportingTest.java](javatools/src/test/java/org/xvm/asm/ReportingTest.java) | 43 | 0 |
| 14 | A | [javatools/src/test/java/org/xvm/compiler/EvalCompilerTest.java](javatools/src/test/java/org/xvm/compiler/EvalCompilerTest.java) | 13 | 0 |
| 15 | A | [javatools/src/test/java/org/xvm/compiler/ParserAttemptTest.java](javatools/src/test/java/org/xvm/compiler/ParserAttemptTest.java) | 181 | 0 |
| 16 | A | [javatools/src/test/java/org/xvm/compiler/ast/NameResolverReportingTest.java](javatools/src/test/java/org/xvm/compiler/ast/NameResolverReportingTest.java) | 102 | 0 |
| 17 | A | [javatools/src/test/java/org/xvm/compiler/ast/ValidationScopeTest.java](javatools/src/test/java/org/xvm/compiler/ast/ValidationScopeTest.java) | 116 | 0 |
| 18 | M | [javatools/src/test/java/org/xvm/tool/ModuleInfoTest.java](javatools/src/test/java/org/xvm/tool/ModuleInfoTest.java) | 26 | 0 |

## Acceptance record and remaining scope

The validation summaries above reproduce the [October 8 extraction record](docs/errs-integration-plan.md#refreshed-foundation-slices) and its C-series follow-up. No compiler/build tests were rerun to generate this report. Report generation verified each local branch tip and parent against the [saved index](build/errs-review-2026-10-08/index.json), and all six exported patches against their Git diffs.

The combined acceptance checkout at `029c93def` joined C3 with I1, I2 and I3; its required compiler consumer passed with one test and no failures or skips. It is retained at `backup/six-slice-acceptance-20261008`. This integration result does not change the individual branch bases.

These six slices establish diagnostic identity, safe audited pool lookups, consumer test wiring, the listener contract, explicit propagation and scoped reporting lifetimes. They do not complete the broader embedding/LSP work. In the extraction plan, E1 carries richer compilation results and C4 carries the remaining file/pool listener and TypeInfo diagnostic ownership work; later compiler and LSP slices depend on those foundations. The planned breaking release boundary is 0.5.0; these branches do not themselves bump the release version.

Marketplace account setup and publication automation are postponed. They are outside all six diffs.

The existing [PR preparation script](create-foundation-prs.py) can export all six diffs, but its publication mode currently creates only the I1–I3 PRs. It does not create C1–C3 PRs. This report was generated using local Git reads; no branch was changed, committed or pushed, and no PR was opened.

To reproduce a file list, substitute the exact base and tip from the relevant section:

```sh
git diff --name-status <base-commit> <tip-commit> --
git diff --numstat <base-commit> <tip-commit> --
```
