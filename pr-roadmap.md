# Compiler foundations and LSP PR roadmap

<a id="outside-lang-comparison"></a>

## Outside-`lang/` comparison branch

**Comparison branch only — no PR has been opened.** The full-reference branch also has no PR. This extra view isolates the changes to established compiler and build code so reviewers can examine them without the server/editor implementation. It does not replace or close any existing PR.

Branch: `errs/compiler-foundations-reference-20261009` at `cfc0d06d4`, based directly on master `da07a0be8`.

[Open the 165-file GitHub diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...cfc0d06d451060da90df8399a31ea61554a26fb5) · [Browse the branch](https://github.com/xtclang/xvm/tree/errs/compiler-foundations-reference-20261009) · [Compare with the full compiler-backed LSP](https://github.com/xtclang/xvm/compare/master...errs/full-testable-lsp-20261009).

### Scope and purpose

This is the exact outside-`lang/` portion of the full reference at `c63a56010`: **165 files, comprising 109 modified files and 56 new files; +13,084/−1,340 lines**. Every included file is byte-for-byte identical to the full reference. The entire `lang/` tree remains identical to the recorded master baseline; it is not deleted. No planning documents or generated build outputs were added to this branch.

The combined scope includes repository read failures, embedding and diagnostic ownership, cancellation and compilation outcomes, typed AST copying and semantic facts, incomplete-source/cursor/declaration analysis, compiler correctness fixes, transient-local retention, Gradle compiler-input export, and the shared build changes needed by the complete product. This shows how much of the final implementation touches the established compiler and build outside the language-tooling tree.

### Relationship to the other review units

The comparison includes the applicable code from #683, #685, #686, #687, #688 and #689, plus the prepared semantic-facts and partial-analysis branches and shared product build changes. Those are overlapping views of the same implementation, not additional independent changes to merge after this branch. The compiler prerequisites are included in this comparison; its remaining dependencies are the omitted language-side consumers described below.

**This exact comparison is not independently mergeable as it stands.** Removing paths by directory does not remove dependencies between those paths. Before turning this into a standalone foundation PR, defer or adapt the coupled build settings and test resources, or include their matching language-side changes. The currently prepared comparison deliberately preserves the final file contents so its diff remains faithful to the full reference.

### Dependencies exposed by the directory split

- `gradle.properties` enables language builds and selects the compiler adapter, while master's unchanged language tree still contains the placeholder compiler adapter.
- `build-logic/common-plugins/src/main/kotlin/IntellijRunIdeSupport.kt` changes log-helper properties to the new directory-based interface. The unchanged master IntelliJ build still calls `lspLogFile` and `logFile`; default Gradle configuration fails at those calls.
- `plugin/src/test/java/org/xtclang/plugin/XtcLspModelTest.java` uses the importer resource `lang/gradle/compiler-model.init.gradle`. The strict path filter omits that file, so the composite-import test fails to load it.
- `.github/workflows/commit.yml` adds the packaged stdio task and compiler test-suite checks supplied by the full language-side changes. Those checks cannot be treated as a passing CI gate on this directory-filtered branch.

These are extraction boundaries, not newly introduced differences from the tested full reference. The complete reference retains the matching consumers and resources.

### Files by area

| Area | Existing files modified | New files added | Total |
| --- | ---: | ---: | ---: |
| `javatools/` — compiler, embedding and Java regressions | 99 | 48 | 147 |
| `plugin/` — Gradle compiler-input export | 3 | 4 | 7 |
| `build-logic/` — log handling, report retention and tests | 1 | 3 | 4 |
| `manualTests/` — Boolean-effects fixture and suite registration | 1 | 1 | 2 |
| `javatools_utils/` — transient-local retention | 1 | 0 | 1 |
| `.github/` — compiler CI checks | 1 | 0 | 1 |
| `gradle/` — dependency versions and shared XDK library bundle | 1 | 0 | 1 |
| `xdk/` — use the shared library bundle | 1 | 0 | 1 |
| Root `gradle.properties` — build defaults and memory | 1 | 0 | 1 |
| **Total outside `lang/`** | **109** | **56** | **165** |

The complete file-by-file lists appear below in [existing files modified](#full-reference-modified-files) and the final [new files added](#full-reference-new-files) section. For this comparison, read the Compiler and embedding API and Gradle plugin subsections, together with the outside-`lang/` entries in Shared build, CI, runtime regression and documentation. The per-file contents and line counts are identical in both comparisons.

### Validation of this comparison

- XDK distribution build passed with `-PincludeBuildLang=false -PincludeBuildAttachLang=false`, and the configuration cache was stored. Root `spotlessCheck` passed.
- Fresh Java compiler tests: **541 passed, 42 skipped, zero failures/errors**. Utility tests: **118 passed, 2 skipped, zero failures/errors**.
- Fresh Gradle plugin tests: **49 passed, 10 skipped, 1 failure**. The failure is the composite-import test's missing excluded importer resource. It passed in the complete reference, where that resource exists.
- Default `./gradlew help` reproduced the two unresolved log-helper properties in the unchanged master IntelliJ build. The comparison therefore does not claim default-build or standalone-merge readiness.
- Verified the exact 165-path scope, file contents against the full reference, the unchanged `lang/` tree, and `git diff --check`. [Local verification receipt and archived JUnit XML](build/reviews/compiler-foundations-reference-20261009/validation.json).

<!-- full-reference-inventory-summary:start -->
## Complete reference branch: file inventory

This inventory covers the complete compiler-backed LSP reference, `errs/full-testable-lsp-20261009` at `c63a56010`, compared with master `da07a0be8`. It includes the compiler/build prerequisites and the LSP/editor product together. Earlier per-branch inventories below record their extraction snapshots; use this inventory for the final assembled reference.

[Open the complete pinned GitHub diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88) · [Existing files modified](#full-reference-modified-files) · [New files added — final section](#full-reference-new-files)

**812 files: 248 existing files modified and 564 new files added.** Existing-file changes are listed first; every new file appears separately at the end under the same area headings. Within each area, files are grouped by their repository directory. Filename links open the tested reference checkout locally; the adjacent GitHub diff links open the individual file in the pinned comparison. Line counts are additions/deletions against the recorded master baseline.

| Area | Existing files modified | New files added | Total |
| --- | ---: | ---: | ---: |
| Compiler and embedding API | [99](#full-reference-modified-compiler) | [48](#full-reference-new-compiler) | 147 |
| LSP server | [68](#full-reference-modified-server) | [283](#full-reference-new-server) | 351 |
| IntelliJ plugin | [30](#full-reference-modified-intellij) | [147](#full-reference-new-intellij) | 177 |
| VS Code extension | [20](#full-reference-modified-vscode) | [68](#full-reference-new-vscode) | 88 |
| Shared fixtures | [0](#full-reference-modified-fixtures) | [8](#full-reference-new-fixtures) | 8 |
| Grammar and syntax tooling | [18](#full-reference-modified-grammar) | [0](#full-reference-new-grammar) | 18 |
| Gradle plugin | [3](#full-reference-modified-gradle-plugin) | [4](#full-reference-new-gradle-plugin) | 7 |
| Shared build, CI, runtime regression and documentation | [10](#full-reference-modified-shared) | [6](#full-reference-new-shared) | 16 |
| **Total** | **248** | **564** | **812** |

Of these files, **415 are test sources** and **9 are shared/runtime fixtures**. The other 388 are production sources, build configuration or documentation. No build outputs, packaged binaries, dependency directories or newly added Markdown planning files are included.

The foundation comparison contains 165 files and the product comparison contains 650 files. Three paths occur in both comparisons, so the combined inventory contains **165 + 650 − 3 = 812 unique paths**. Files are listed once below, using their final status against master.
<!-- full-reference-inventory-summary:end -->

Updated **2026-10-09**. The agreed strategy is to review complete compiler/build capabilities first, then the complete LSP and editor product. There are **six existing open PRs** and **three prepared review branches awaiting PR submission**. The full reference and outside-`lang/` reference are additional comparison branches only; neither has a PR.

Recovery, cursor queries, incomplete-call/constructor fitting and declaration/header analysis are **one combined partial-analysis PR**. The LSP server, IntelliJ plugin and VS Code extension are **one product PR** after their foundations. The Gradle exporter and Boolean operand-effects correction are independent changes that can land against master.

The LSP needs more than the initial embedding API: #685 provides the host compilation lifecycle; semantic facts provide source identities and resolved compiler decisions; partial analysis provides compiler answers for code being edited. The implemented adapter calls all three layers.

## Current review branches and merge order

These are the five extracted review units; the Gradle exporter and Boolean correction are now published as #688 and #689, while the other three still await submission. Counts are incremental against each recorded comparison base, excluding inherited work. Each title links to its description, prerequisites, file summary, validation evidence and complete clickable file inventory below.

| Review unit | Files | Must merge first | GitHub comparison |
| --- | ---: | --- | --- |
| [Compilation-owned syntax and semantic facts](#semantic-facts) | **36** | #685 and #687; #683 through #685. | [View diff](https://github.com/xtclang/xvm/compare/errs/compiler-review-base-20261009...errs/compiler-semantic-facts-20261009) |
| [Incomplete-source, cursor and declaration analysis](#partial-analysis) | **40** | Semantic facts, inheriting #683/#685/#687. | [View diff](https://github.com/xtclang/xvm/compare/errs/compiler-semantic-facts-20261009...errs/compiler-partial-analysis-20261009) |
| [Gradle compiler inputs for tooling — #688](#gradle-model) | **8** | **None — independent against master.** | [View diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...errs/gradle-project-model-20261009) |
| [Boolean operand-effects correction — #689](#boolean-effects) | **3** | **None — independent against master.** | [View diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...errs/compiler-conditional-effects-20261009) |
| [Compiler-backed LSP and editor integrations](#lsp-product) | **650** | Semantic facts, partial analysis, Gradle model and #686; inherits #683/#685/#687. Preserve or reconcile the Boolean fix included in its tested base. | [View diff](https://github.com/xtclang/xvm/compare/errs/lsp-review-base-20261009...errs/lsp-editors-20261009) |

**Compiler merge sequence:** #683 → #685; #685 + #687 → semantic facts → partial analysis → LSP/editor product.

**Independent work:** #686, #687, the Gradle exporter and the Boolean correction can progress against master without waiting for the embedding stack. The product waits for the applicable foundations above. The Boolean correction is part of its prepared compiler baseline, not an API prerequisite.

**Next compiler PR to submit, when authorized:** compilation-owned syntax and semantic facts. The two independent branches have already been submitted as #688 and #689. Later stacked comparisons can be reviewed now; their final bases must be reconciled against the actual merged prerequisites before landing. No submission is authorized by this document itself.

## Existing open PRs

GitHub state checked while preparing the comparison: all six are open and target `master`. These PRs already exist; the two comparison branches above are not PR submissions.

| Existing PR | Purpose | Prerequisites and current revision |
| --- | --- | --- |
| [#683 — Repository read failures and retry behavior](https://github.com/xtclang/xvm/pull/683) | Keep mixed-version/platform lookup tolerant while retaining I/O failure details for an embedding host to inspect after a required dependency fails. | **None.** `errs/repository-read-failures-20261009` at `902460194`. Land before #685. |
| [#685 — Compiler embedding and diagnostic ownership](https://github.com/xtclang/xvm/pull/685) | One complete host-compilation lifecycle: explicit listeners, suppression and reporting scopes, speculative attempts, restoration, diagnostic replay, cancellation, source snapshots, outcomes and partial progress. | **#683.** `errs/embedding-api-combined-20261009` at `c1a98a677`, already rebased over merged #684. Reconcile against #683 when it lands. |
| [#686 — Empty transient-thread-local retention](https://github.com/xtclang/xvm/pull/686) | Prevent empty thread-local probes from retaining short-lived compiler state in long-lived workers. | **None.** `errs/transient-locals-20261009` at `81f032cc0`. Two files; standalone transient-local test removed as requested, compiler-consumer coverage retained. |
| [#687 — Compiler types through fitting and code generation](https://github.com/xtclang/xvm/pull/687) | Preserve bound generic callable types, argument-fit failures and concrete atomic result/receiver types in emitted and serialized code. | **None.** `errs/compiler-type-correctness-20261009` at `f29cf28c0`. Six files; semantic facts builds on these corrected compiler decisions. |
| [#688 — Evaluated Gradle compiler inputs](https://github.com/xtclang/xvm/pull/688) | Export evaluated source sets, dependency edges and resource paths for tooling. | **None.** `errs/gradle-project-model-20261009` at `7a19d684c`; eight files. |
| [#689 — Boolean operand effects](https://github.com/xtclang/xvm/pull/689) | Preserve required operand evaluation even when a Boolean result is constant; include the 22-check runtime regression in the standard suite. | **None.** `errs/compiler-conditional-effects-20261009` at `6d550fea1`; three files. |

**Merged/superseded history:** #677 and #679 are merged foundations. #678 merged as documentation only; its withdrawn owner-pool fallback must not return. #684 is merged and its diagnostic fixes must remain. #680–#682 were closed and superseded by #685; do not reopen them or split the combined embedding lifecycle back into those historical pieces.

## Reading the comparisons

The recorded master baseline is `da07a0be8`, which includes #684. The compiler comparison base `errs/compiler-review-base-20261009` combines #685 and #687 and inherits #683. The product comparison base `errs/lsp-review-base-20261009` combines the partial-analysis stack, Gradle exporter, #686 and the Boolean correction. **These two base refs are comparison aids, not PRs to submit.**

All five review branches and both comparison bases are published. The Boolean branch now includes the console-free fixture and standard runtime-suite registration. The LSP review branch and full reference both point to `c63a56010`; their trees are identical. The outside-`lang/` comparison at `cfc0d06d4` is published separately. Neither comparison has a PR.

The extraction source was preserved at `errs/review-source-20261009` (`c9d16c50d`). Subsequent integration synchronization is committed and pushed as `374d9c16e` on `lagergren/errs`, including the new compiler-consumer tests and roadmap. Nothing was removed from the integration implementation merely to simplify a review branch. Remaining differences requiring a disposition are listed at the end.

Local review artifacts: [branch refs and exact inventories](build/reviews/remaining-branches-20261009/branches.json), [focused validation receipts](build/reviews/remaining-branches-20261009/validation.json). These local build artifacts and absolute worktree file links are intended for the local checkout; the GitHub comparison links work remotely.

<a id="semantic-facts"></a>

## Compilation-owned syntax and semantic facts

Branch `errs/compiler-semantic-facts-20261009` at `4a3d998dd`; base `errs/compiler-review-base-20261009` at `3af705576`.

[Open this branch in GitHub’s compare view](https://github.com/xtclang/xvm/compare/errs/compiler-review-base-20261009...errs/compiler-semantic-facts-20261009). [Full local patch](build/reviews/remaining-branches-20261009/semantic-facts.patch). No PR has been opened.

### Proposed PR description

**Title: [LSP] Expose compilation-owned syntax and resolved semantic facts**

#### Why?

- Embedding a compiler is not sufficient for an editor if the host receives diagnostics and output modules but cannot inspect the source-level decisions that produced them. Navigation, references, signature help and safe refactoring need declaration identities, resolved types and selected calls.
- Recovering these facts from source text would duplicate compiler rules and lose important distinctions: overload selection, generic substitutions, named argument order, captured variables and initializers that have already been folded.
- Hosts need an explicit ownership and traversal contract for syntax. Retaining arbitrary internal nodes or copying them through scattered casts makes analysis fragile as the compiler evolves.

#### How?

- Extend compilation results with source trees and binding snapshots collected during the compiler's existing resolution and validation stages. Expose facts the compiler actually established rather than asking the host to reconstruct them.
- Record selected method, function and constructor calls with their substituted signatures and source arguments. Preserve initializer provenance, declaration associations, lambda captures and anonymous-class bindings.
- Add typed `copyTree()` APIs, consistent child traversal and source metadata handling. Snapshot maps preserve node identity and cannot be structurally mutated by consumers.

#### Implications

**Must merge first:** [#685 — compiler embedding and diagnostic ownership](https://github.com/xtclang/xvm/pull/685) and [#687 — compiler type correctness](https://github.com/xtclang/xvm/pull/687). The former supplies compilation results, reporting, snapshots and cancellation; the latter corrects types used by the exposed binding facts. [#683 — repository read failures](https://github.com/xtclang/xvm/pull/683) is an inherited prerequisite through the prepared embedding branch. No partial-analysis, Gradle model or editor implementation PR must land before this one.

This is the ordinary-compilation foundation for compiler-backed editor features. It does not yet recover incomplete source or implement an LSP server. Immutable result collections do not make their compiler objects thread-safe: syntax and constants remain owned by their compilation, and a host must respect compiler/pool ownership when extracting a detached model. Public embedding result changes belong to the planned 0.5.0 API boundary; this description does not claim binary compatibility for earlier embedding consumers.

#### Changes

`EmbeddingSupport` exposes the collected results; `InvocationBinding` and `InitializerBinding` define the call and initializer facts. AST declarations and expressions publish their resolved associations, while the traversal/copy contract makes those trees usable by a host. Tests cover node identity, source provenance, traversal, copying and calls observed through the actual embedding API.

### Files changed

| Area | Files | What changes |
| --- | ---: | --- |
| `javatools/src/main/java` | 30 | Embedding results, compiler binding collectors, AST access/copy/traversal and method metadata. |
| `javatools/src/test/java` | 5 | Identity, provenance, composition, traversal and tree-copy regressions. |
| `lang/lsp-server/src/test` | 1 | A direct compiler API consumer test; no production LSP code. |
| **Total** | **36** | Exact file paths follow below. |

**36 files; +1,931/−163.** 20 focused tests passed with no skips; compilation and formatting passed. The later public register accessor is also compiled by the actual LSP consumer.

### Review and acceptance focus

The review should establish that source identities survive nested compilation, shadowing and narrowing; named/default/generic call facts reflect the compiler's selection; and failed calls or discarded trial copies do not publish successful bindings. Copies must own their children and preserve source metadata. No retained validation context, global declaration registry or new public context cache is part of this contract.

<details>
<summary>Exact changed files</summary>

| Status | File | + / − |
| --- | --- | ---: |
| M | [javatools/src/main/java/org/xvm/api/EmbeddingSupport.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/api/EmbeddingSupport.java) | 150 / 31 |
| M | [javatools/src/main/java/org/xvm/asm/constants/MethodBody.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/asm/constants/MethodBody.java) | 5 / 1 |
| M | [javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java) | 24 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/Compiler.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/Compiler.java) | 27 / 18 |
| A | [javatools/src/main/java/org/xvm/compiler/InitializerBinding.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/InitializerBinding.java) | 85 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/InvocationBinding.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/InvocationBinding.java) | 244 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/AnonymousClassBindings.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/AnonymousClassBindings.java) | 54 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/AstNode.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/AstNode.java) | 116 / 10 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/CompositionNode.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/CompositionNode.java) | 6 / 3 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/Context.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/Context.java) | 11 / 3 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/Expression.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/Expression.java) | 8 / 3 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/ImportStatement.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/ImportStatement.java) | 22 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java) | 63 / 1 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/LambdaBindings.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/LambdaBindings.java) | 76 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java) | 32 / 10 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java) | 28 / 8 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java) | 26 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java) | 14 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java) | 40 / 6 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java) | 75 / 22 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/Parameter.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/Parameter.java) | 23 / 2 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/PropertyDeclarationStatement.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/PropertyDeclarationStatement.java) | 23 / 4 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/StageMgr.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/StageMgr.java) | 34 / 13 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/Statement.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/Statement.java) | 5 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/StatementBlock.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/StatementBlock.java) | 49 / 9 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java) | 57 / 17 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/TypeExpression.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/TypeExpression.java) | 5 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/TypedefStatement.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/TypedefStatement.java) | 7 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/VariableDeclarationStatement.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/compiler/ast/VariableDeclarationStatement.java) | 3 / 2 |
| M | [javatools/src/main/java/org/xvm/tool/ModuleInfo.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/main/java/org/xvm/tool/ModuleInfo.java) | 25 / 0 |
| A | [javatools/src/test/java/org/xvm/api/BindingIdentitySnapshotTest.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/test/java/org/xvm/api/BindingIdentitySnapshotTest.java) | 144 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ast/AstChildTraversalTest.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/test/java/org/xvm/compiler/ast/AstChildTraversalTest.java) | 131 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ast/CompositionSourceTest.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/test/java/org/xvm/compiler/ast/CompositionSourceTest.java) | 36 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ast/DeclarationProvenanceTest.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/test/java/org/xvm/compiler/ast/DeclarationProvenanceTest.java) | 151 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ast/OrdinaryAstTreeCopyTest.java](/private/tmp/xvm-compiler-semantic-facts-20261009/javatools/src/test/java/org/xvm/compiler/ast/OrdinaryAstTreeCopyTest.java) | 42 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerBindingFactsTest.kt](/private/tmp/xvm-compiler-semantic-facts-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerBindingFactsTest.kt) | 90 / 0 |

</details>

<a id="partial-analysis"></a>

## Incomplete-source, cursor and declaration analysis

Branch `errs/compiler-partial-analysis-20261009` at `777a0bd4b`; base `errs/compiler-semantic-facts-20261009` at `4a3d998dd`.

[Open this branch in GitHub’s compare view](https://github.com/xtclang/xvm/compare/errs/compiler-semantic-facts-20261009...errs/compiler-partial-analysis-20261009). [Full local patch](build/reviews/remaining-branches-20261009/partial-analysis.patch). No PR has been opened.

### Proposed PR description

**Title: [LSP] Analyze incomplete source, cursor scopes and declarations through the compiler API**

#### Why?

- Source in an editor is routinely incomplete: the user has just typed a receiver and a dot, started a call, or changed a declaration header. Abandoning analysis at the first syntax error leaves completion and signature help unavailable exactly when they are needed.
- Useful suggestions require the real lexical scope, narrowed types, receiver and overload-fitting rules. A syntax-only approximation cannot reliably reproduce those compiler decisions.
- A broken method body should not prevent a host from inspecting recoverable declarations elsewhere. Equally, recovering information for an editor must not turn invalid source into a successful executable compilation.

#### How?

- Add explicit incomplete-source and declaration-analysis entry points to `EmbeddingSupport`, including cursor-aware analysis of source/module trees.
- Preserve recoverable structure with bounded parser recovery and dedicated partial AST nodes. Expose cursor scope, readable locals, shadowing, narrowing, receiver types and declaration facts.
- Fit incomplete calls and constructions with compiler rules, including named/default arguments and generic types. Return candidates and proposals as partial information, distinct from a call successfully selected during normal compilation.
- Keep recovery, cursor queries and declaration/header analysis together because they share parser modes, tree ownership and result contracts. This makes the public feature usable as one complete addition.

#### Implications

**Must merge first:** the proposed [compilation-owned syntax and semantic-facts PR](#semantic-facts). Its transitive prerequisites are [#685](https://github.com/xtclang/xvm/pull/685), [#687](https://github.com/xtclang/xvm/pull/687), and the repository work in [#683](https://github.com/xtclang/xvm/pull/683). This branch relies on that ownership/binding contract and on the embedding foundation's reporting, snapshots and cancellation. It has no prerequisite on the Gradle exporter or either editor integration.

Normal compilation still rejects invalid input; partial results are analysis results, not executable artifacts. Cancellation and failed analysis must not manufacture a valid semantic graph. The later LSP adapter directly consumes these APIs, so merging only the initial embedding foundation would leave its incomplete-source features without their compiler implementation.

#### Changes

`Parser` and the partial AST classes retain recoverable syntax. `CursorBinding`/`CursorScope` describe the cursor context; `PartialQueries`, `PartialCallResolver` and `PartialConstructionResolver` answer semantic queries. The tool/compiler stages add declaration-only analysis. Regression tests exercise recovery boundaries, copying, scopes, candidate fitting and public API results.

### Files changed

| Area | Files | What changes |
| --- | ---: | --- |
| `javatools/src/main/java` | 29 | Parser modes, partial AST nodes, cursor/candidate queries and embedding/compiler entry points. |
| `javatools/src/main/resources` | 1 | Diagnostic resource adjustment. |
| `javatools/src/test/java` | 8 | Recovery, declaration names, cursor bindings, partial syntax, ownership/copying and module-tree tests. |
| `lang/lsp-server/src/test` | 2 | Direct embedding API tests for partial facts and declaration analysis; no production LSP code. |
| **Total** | **40** | Incremental over semantic facts; inherited files are not counted again. |

**40 files; +5,652/−316.** 157 Java tests and 8 direct compiler API tests passed, with no skips. These cover parser recovery, partial syntax, scope, incomplete calls/construction, cancellation and declaration-only analysis.

### Review and acceptance focus

The combined API must cover UTF-16 cursor positions, unsaved module members, bounded delimiter recovery and declarations after a damaged expression. Call/constructor proposals must use ordinary compiler fitting without leaking trial diagnostics, mutating retained source, or emitting synthetic operations merely to answer a query. Declaration analysis must preserve real registered identities and reject invalid declarations. Ordinary valid compilation, failure paths, budgets and cancellation remain controls.

<details>
<summary>Exact changed files</summary>

| Status | File | + / − |
| --- | --- | ---: |
| M | [javatools/src/main/java/org/xvm/api/EmbeddingSupport.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/api/EmbeddingSupport.java) | 322 / 7 |
| M | [javatools/src/main/java/org/xvm/asm/ConstantPool.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/asm/ConstantPool.java) | 5 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/Compiler.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/Compiler.java) | 11 / 4 |
| A | [javatools/src/main/java/org/xvm/compiler/CursorBinding.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/CursorBinding.java) | 326 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/Parser.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/Parser.java) | 1073 / 243 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/AstNode.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/AstNode.java) | 7 / 1 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/Context.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/Context.java) | 7 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/CursorScope.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/CursorScope.java) | 466 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java) | 2 / 2 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java) | 3 / 3 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java) | 49 / 8 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/PartialArgument.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/PartialArgument.java) | 49 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/PartialCallResolver.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/PartialCallResolver.java) | 452 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/PartialConstructionResolver.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/PartialConstructionResolver.java) | 139 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/PartialQueries.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/PartialQueries.java) | 77 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/PropertyDeclarationStatement.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/PropertyDeclarationStatement.java) | 11 / 1 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/StageMgr.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/StageMgr.java) | 19 / 0 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/Statement.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/Statement.java) | 7 / 1 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/StatementBlock.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/StatementBlock.java) | 19 / 1 |
| M | [javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java) | 4 / 3 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteDeclarationStatement.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteDeclarationStatement.java) | 136 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteExpression.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteExpression.java) | 93 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteLocalDeclaration.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteLocalDeclaration.java) | 87 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteStatement.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteStatement.java) | 271 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteTypeCompositionStatement.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteTypeCompositionStatement.java) | 143 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/partial/PartialSyntax.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/PartialSyntax.java) | 94 / 0 |
| A | [javatools/src/main/java/org/xvm/compiler/ast/partial/ProposedLiteralToken.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/ProposedLiteralToken.java) | 23 / 0 |
| M | [javatools/src/main/java/org/xvm/tool/Compiler.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/tool/Compiler.java) | 20 / 8 |
| M | [javatools/src/main/java/org/xvm/tool/ModuleInfo.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/java/org/xvm/tool/ModuleInfo.java) | 38 / 9 |
| M | [javatools/src/main/resources/errors.properties](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/main/resources/errors.properties) | 1 / 1 |
| M | [javatools/src/test/java/org/xvm/api/BindingIdentitySnapshotTest.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/test/java/org/xvm/api/BindingIdentitySnapshotTest.java) | 21 / 2 |
| A | [javatools/src/test/java/org/xvm/compiler/CursorBindingTest.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/test/java/org/xvm/compiler/CursorBindingTest.java) | 133 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ParserDeclarationNameTest.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/test/java/org/xvm/compiler/ParserDeclarationNameTest.java) | 172 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ParserRecoveryTest.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/test/java/org/xvm/compiler/ParserRecoveryTest.java) | 672 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ast/AstTreeCopyTest.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/test/java/org/xvm/compiler/ast/AstTreeCopyTest.java) | 134 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ast/PartialSyntaxTest.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/test/java/org/xvm/compiler/ast/PartialSyntaxTest.java) | 233 / 0 |
| A | [javatools/src/test/java/org/xvm/compiler/ast/partial/RegisteredChildFieldsTest.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/test/java/org/xvm/compiler/ast/partial/RegisteredChildFieldsTest.java) | 143 / 0 |
| M | [javatools/src/test/java/org/xvm/tool/ModuleInfoTest.java](/private/tmp/xvm-compiler-partial-analysis-20261009/javatools/src/test/java/org/xvm/tool/ModuleInfoTest.java) | 41 / 22 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerDeclarationAnalysisTest.kt](/private/tmp/xvm-compiler-partial-analysis-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerDeclarationAnalysisTest.kt) | 80 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerPartialFactsTest.kt](/private/tmp/xvm-compiler-partial-analysis-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerPartialFactsTest.kt) | 69 / 0 |

</details>

<a id="gradle-model"></a>

## Gradle compiler inputs for tooling

Branch `errs/gradle-project-model-20261009` at `7a19d684c`; base `da07a0be8525962391f7e46215e4a8f277bd0b14` at `da07a0be8`.

[Open this branch in GitHub’s compare view](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...errs/gradle-project-model-20261009). [Full local patch](build/reviews/remaining-branches-20261009/gradle-model.patch). Published as [#688](https://github.com/xtclang/xvm/pull/688).

### Proposed PR description

**Title: [LSP] Export evaluated Gradle compiler inputs for tooling**

#### Why?

- An editor must analyze the same sources, libraries and resources as the build. Scanning folders or interpreting build scripts independently loses evaluated source sets, project dependencies, module-path ordering and resource processing.
- IntelliJ and VS Code need a common import contract, including multi-project and composite builds. Keeping that contract in the Gradle plugin lets Gradle remain the authority for compiler inputs.
- Reading metadata should not silently trigger resource preparation or a full compilation. Tooling needs to request preparation explicitly when it needs generated/processed inputs.

#### How?

- Add per-source-set reports and `exportXtcLspModel`, producing an evaluated JSON model of source ownership, project dependencies, module paths and resource locations.
- Provide `prepareXtcLspModel` for resource preparation, and a shared init script that aggregates models across included builds, including workspace roots without the Ecstasy plugin.
- Publish source archive variants for source attachment. Replace aggregate model files atomically so removed projects do not remain as stale entries, and reject missing or conflicting composite reports.

#### Implications

**Must merge first: none.** This branch is independent and based directly on master `da07a0be8`. It uses existing Gradle/plugin/XDK machinery and does not require any pending embedding, compiler correctness, partial-analysis or LSP PR.

The export is useful to any tooling consumer; it does not start an LSP server or change compiler language semantics. The later editor product consumes the model and supplies its UI/import lifecycle. XDK library-bundle distribution wiring remains in that later product change. Task inputs and outputs are declared for Gradle, and functional tests demonstrate configuration-cache storage and reuse.

#### Changes

`XtcLspModelIntegration` wires the tasks from the existing project delegate. `XtcLspSourceSetTask` writes individual input reports and `XtcLspModelTask` combines them. The init script performs workspace/composite aggregation. Functional tests use real consumer builds rather than checking source-code spelling.

### Files changed

| Area | Files | What changes |
| --- | ---: | --- |
| `plugin/src/main/java` | 5 | Task registration, source variants, compile-task access and the two report task implementations. |
| `plugin/src/test/java` | 1 | Functional export, resource preparation and configuration-cache tests. |
| `plugin/build.gradle.kts` | 1 | Test/build support for the exporter. |
| `lang/gradle/compiler-model.init.gradle` | 1 | Shared workspace/composite model import script. |
| **Total** | **8** | Seven files in `plugin/`, one in `lang/`; no `javatools` changes. |

**8 files; +533/−1.** All 3 functional tests passed, including configuration-cache reuse inside real consumer builds; formatting passed.

### Review and acceptance focus

Review the exported model against a real multi-project/composite consumer, including project ownership, dependency ordering, resource transformations, source attachment, removed projects and failed imports. Metadata export and resource preparation are distinct operations. Configuration-cache reuse is part of the contract.

<details>
<summary>Exact changed files</summary>

| Status | File | + / − |
| --- | --- | ---: |
| A | [lang/gradle/compiler-model.init.gradle](/private/tmp/xvm-gradle-project-model-20261009/lang/gradle/compiler-model.init.gradle) | 78 / 0 |
| M | [plugin/build.gradle.kts](/private/tmp/xvm-gradle-project-model-20261009/plugin/build.gradle.kts) | 4 / 0 |
| A | [plugin/src/main/java/org/xtclang/plugin/XtcLspModelIntegration.java](/private/tmp/xvm-gradle-project-model-20261009/plugin/src/main/java/org/xtclang/plugin/XtcLspModelIntegration.java) | 87 / 0 |
| M | [plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java](/private/tmp/xvm-gradle-project-model-20261009/plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java) | 20 / 0 |
| M | [plugin/src/main/java/org/xtclang/plugin/tasks/XtcCompileTask.java](/private/tmp/xvm-gradle-project-model-20261009/plugin/src/main/java/org/xtclang/plugin/tasks/XtcCompileTask.java) | 1 / 1 |
| A | [plugin/src/main/java/org/xtclang/plugin/tasks/XtcLspModelTask.java](/private/tmp/xvm-gradle-project-model-20261009/plugin/src/main/java/org/xtclang/plugin/tasks/XtcLspModelTask.java) | 50 / 0 |
| A | [plugin/src/main/java/org/xtclang/plugin/tasks/XtcLspSourceSetTask.java](/private/tmp/xvm-gradle-project-model-20261009/plugin/src/main/java/org/xtclang/plugin/tasks/XtcLspSourceSetTask.java) | 96 / 0 |
| A | [plugin/src/test/java/org/xtclang/plugin/XtcLspModelTest.java](/private/tmp/xvm-gradle-project-model-20261009/plugin/src/test/java/org/xtclang/plugin/XtcLspModelTest.java) | 197 / 0 |

</details>

<a id="boolean-effects"></a>

## Boolean operand-effects correction

Branch `errs/compiler-conditional-effects-20261009` at `6d550fea1`; base `da07a0be8525962391f7e46215e4a8f277bd0b14` at `da07a0be8`.

[Open this branch in GitHub’s compare view](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...errs/compiler-conditional-effects-20261009). [Full local patch](build/reviews/remaining-branches-20261009/conditional-effects.patch). Published as [#689](https://github.com/xtclang/xvm/pull/689).

### Proposed PR description

**Title: Preserve Boolean operand effects when the final result is constant**

#### Why?

- An expression such as `probe() && False` has a constant result, but evaluating `probe()` still matters. Discarding that evaluation can remove state changes or other observable behavior from an otherwise ordinary program.
- Both value-producing code and conditional branches must preserve evaluation exactly once. A fix confined to one emission path would leave the same expression behaving differently depending on where it appears.

#### How?

- Preserve left-operand evaluation in the affected `CondOpExpression` value and conditional-jump generation paths while still producing the known Boolean result.
- Correct the assignment classification to inspect the right operand instead of inspecting the left operand twice.
- Add a runtime fixture that checks call counts and results for both runtime operand values, across assignment, branches, negated branches and argument evaluation, with other short-circuit forms as controls.

#### Implications

**Must merge first: none.** This is a standalone compiler correctness fix based directly on master `da07a0be8`. It does not depend on #687, the embedding APIs, Gradle tooling or the LSP. It benefits ordinary compiled programs and introduces no runtime API.

The prepared LSP comparison base includes this correction so that it uses the corrected compiler. That is a tested-baseline choice, not a new API dependency of the language server.

#### Changes

The production change is confined to `CondOpExpression.java`. `conditionalEffects.x` supplies 22 assertion-based runtime checks. The fixture needs no injected console or success-message logging; failures are expressed by assertions.

### Files changed

| Area | Files | What changes |
| --- | ---: | --- |
| `javatools/src/main/java/org/xvm/compiler/ast/CondOpExpression.java` | 1 | Preserve operand evaluation and correct operand assignment classification. |
| `manualTests/src/main/x/conditionalEffects.x` | 1 | Runtime assertions for constant-result Boolean expressions and control cases. |
| `manualTests/build.gradle.kts` | 1 | Register the regression in the standard runtime suite. |
| **Total** | **3** | No `lang/` or Gradle plugin changes. |

**Synchronization:** the console-free fixture and its runtime-suite registration are committed and pushed on the Boolean branch and are present in the integration and complete-reference branches.

**3 files; +94/−1.** The TestConditionalEffects runtime fixture passed all 22 checks. Formatting passed.

### Review and acceptance focus

The runtime fixture verifies exactly-once left-operand evaluation for both Boolean operand values. It covers values, branch conditions, negated branches and call arguments, with the other short-circuit forms as controls. A constant result must not erase the operand's observable effects.

<details>
<summary>Exact changed files</summary>

| Status | File | + / − |
| --- | --- | ---: |
| M | [javatools/src/main/java/org/xvm/compiler/ast/CondOpExpression.java](/private/tmp/xvm-compiler-conditional-effects-20261009/javatools/src/main/java/org/xvm/compiler/ast/CondOpExpression.java) | 14 / 1 |
| M | [manualTests/build.gradle.kts](/private/tmp/xvm-compiler-conditional-effects-20261009/manualTests/build.gradle.kts) | 1 / 0 |
| A | [manualTests/src/main/x/conditionalEffects.x](/private/tmp/xvm-compiler-conditional-effects-20261009/manualTests/src/main/x/conditionalEffects.x) | 79 / 0 |

</details>

<a id="lsp-product"></a>

## Compiler-backed LSP and editor integrations

Branch `errs/lsp-editors-20261009` at `c63a56010`; base `errs/lsp-review-base-20261009` at `3f8aad03e`.

[Open this branch in GitHub’s compare view](https://github.com/xtclang/xvm/compare/errs/lsp-review-base-20261009...errs/lsp-editors-20261009). [Full local patch](build/reviews/remaining-branches-20261009/lsp-editors.patch). No PR has been opened.

### Proposed PR description

**Title: [LSP] Deliver compiler-backed language tooling for IntelliJ and VS Code**

#### Why?

- The compiler foundation becomes useful to users when editors consume its source identities, resolved types, call bindings and incomplete-source queries. A parser fallback alone cannot provide equivalent semantic accuracy for navigation, overloads or refactoring.
- An interactive compiler host must also manage cancellation, stale documents, dependency changes and compiler ownership. Connecting request handlers directly to mutable compiler state would leave correctness and responsiveness problems outside the API's guarantees.
- The server and both clients need to ship as a coherent product: project import, settings, adapter switching, source attachments and diagnostics must agree across the protocol boundary.

#### How?

- Add the compiler adapter and detached semantic models, with serialized compiler access, cancellation, source/dependency tracking and invalidation. Use the compiler adapter by default while retaining the selectable Tree-sitter fallback.
- Build editor features on compiler facts: navigation and references, hierarchies, completion/signature help, rename and refactoring, formatting, theme-based semantic highlighting, inlay hints and CodeLens.
- Integrate IntelliJ and VS Code with the evaluated Gradle model, library sources, adapter selection/restart, configuration and support logs.
- Add shared scenarios, packaged-server tests, native editor acceptance harnesses, timeout handling and retained CI reports so the implementation can be exercised through real clients.

#### Implications

**Must merge first:** the proposed [semantic-facts PR](#semantic-facts), then the [partial-analysis PR](#partial-analysis); the independent [Gradle compiler-model PR](#gradle-model); and [#686 — transient thread-local lifecycle correction](https://github.com/xtclang/xvm/pull/686). Their inherited compiler/repository prerequisites are [#683](https://github.com/xtclang/xvm/pull/683), [#685](https://github.com/xtclang/xvm/pull/685) and [#687](https://github.com/xtclang/xvm/pull/687). Semantic facts and partial analysis supply APIs called directly by the adapter; the Gradle model supplies real project inputs; #686 addresses lifecycle behavior in the long-lived host.

The prepared base also contains the independent [Boolean operand-effects correction](#boolean-effects). Land or explicitly reconcile that correction before updating this product branch so its tested compiler baseline is preserved; it does not add an API needed to compile the LSP. The two published comparison-base refs are bookkeeping for review, not additional PRs to merge.

This branch intentionally delivers the complete server/editor product after the separately reviewable compiler foundations. It uses released LSP4J/LSP4IJ dependencies and does not require private forks. Public color-library design, notebooks, runtime/DAP work and Microsoft Marketplace authentication/publication setup remain deferred. It does not absorb every unassigned compiler cleanup listed later in this roadmap.

#### Changes

The largest areas are `lang/lsp-server`, `lang/intellij-plugin` and `lang/vscode-extension`. Supporting changes update grammar/highlighting generation, shared fixtures, build/test integration and XDK library packaging. Focused consumer tests prove that the extracted product compiles against the extracted compiler APIs. The complete-reference validation now includes the backend/protocol suite and both native editors; the remaining native failures and partial cases are listed below.

### Files changed

| Area | Files | What changes |
| --- | ---: | --- |
| `lang/lsp-server` | 345 | Compiler adapter, server features, semantic models, protocol handling, tests and documentation. |
| `lang/intellij-plugin` | 177 | IntelliJ integration, configuration, project import, editor actions and acceptance harness. |
| `lang/vscode-extension` | 88 | VS Code client, settings/commands, import, feature integration and tests. |
| `lang/dsl` | 15 | Grammar/highlighting model and generators with their tests. |
| `lang/test-fixtures` | 8 | Shared compiler/editor/highlighting/workload fixtures. |
| Other `lang/` files | 7 | Language build/docs, generated TextMate example, workload script and Tree-sitter build/docs. |
| Outside `lang/` | 10 | CI workflow, four common build helpers, root Gradle properties/version catalog, XDK packaging and two runtime-fixture/suite updates. |
| **Total** | **650** | Incremental product changes; compiler/Gradle prerequisites are excluded from this comparison. |

**650 files; +109,469/−5,383.** The tree is identical to the tested full reference. Backend/protocol validation: 2,257 tests passed with two disabled; 82 packaged stdio tests passed. IntelliJ native acceptance: 282 feature cases passed and two are partial, with zero IDE failures; all separate lifecycle/settings modes passed. VS Code: 279 native cases passed and five failed (two Explorer/Undo cases and three optional color-picker cases); 55 smoke tests and all separate lifecycle/settings modes passed. Root/language formatting, 1,032 parser-corpus files and both plugin packages passed. [Complete validation receipt](build/reviews/full-testable-lsp-20261009/validation-summary.json).

### Review and acceptance focus

Before release acceptance, run the packaged server and both native editor paths. Unexpected timeouts, hanging dialogs and server-failure popups must fail the scenario; intentional negative cases must assert their expected outcome. Keep known client/platform limitations explicit. Reconcile the product against the actual merged compiler and Gradle revisions, and keep any newly discovered general compiler defect visible as a separate review decision.

<details>
<summary>Exact changed files</summary>

| Status | File | + / − |
| --- | --- | ---: |
| M | [.github/workflows/commit.yml](/private/tmp/xvm-lsp-editors-20261009/.github/workflows/commit.yml) | 32 / 4 |
| M | [build-logic/common-plugins/src/main/kotlin/IntellijRunIdeSupport.kt](/private/tmp/xvm-lsp-editors-20261009/build-logic/common-plugins/src/main/kotlin/IntellijRunIdeSupport.kt) | 74 / 33 |
| A | [build-logic/common-plugins/src/main/kotlin/PruneTestReportsTask.kt](/private/tmp/xvm-lsp-editors-20261009/build-logic/common-plugins/src/main/kotlin/PruneTestReportsTask.kt) | 86 / 0 |
| A | [build-logic/common-plugins/src/test/kotlin/ServerLogTailsTest.kt](/private/tmp/xvm-lsp-editors-20261009/build-logic/common-plugins/src/test/kotlin/ServerLogTailsTest.kt) | 51 / 0 |
| A | [build-logic/common-plugins/src/test/kotlin/TestReportRetentionTest.kt](/private/tmp/xvm-lsp-editors-20261009/build-logic/common-plugins/src/test/kotlin/TestReportRetentionTest.kt) | 70 / 0 |
| M | [gradle.properties](/private/tmp/xvm-lsp-editors-20261009/gradle.properties) | 14 / 11 |
| M | [gradle/libs.versions.toml](/private/tmp/xvm-lsp-editors-20261009/gradle/libs.versions.toml) | 16 / 2 |
| M | [lang/README.md](/private/tmp/xvm-lsp-editors-20261009/lang/README.md) | 35 / 5 |
| M | [lang/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/build.gradle.kts) | 60 / 43 |
| M | [lang/doc/manual-test-plan.md](/private/tmp/xvm-lsp-editors-20261009/lang/doc/manual-test-plan.md) | 2,799 / 101 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/LanguageModelCli.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/LanguageModelCli.kt) | 4 / 4 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/XtcLanguage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/XtcLanguage.kt) | 60 / 16 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/EmacsGenerator.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/EmacsGenerator.kt) | 16 / 8 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/GeneratorUtils.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/GeneratorUtils.kt) | 15 / 24 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/SublimeSyntaxGenerator.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/SublimeSyntaxGenerator.kt) | 4 / 3 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TextMateBundleManifestGenerator.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TextMateBundleManifestGenerator.kt) | 2 / 1 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TextMateGenerator.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TextMateGenerator.kt) | 49 / 11 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TreeSitterGenerator.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TreeSitterGenerator.kt) | 20 / 23 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/VimGenerator.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/VimGenerator.kt) | 16 / 12 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/model/LanguageModel.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/model/LanguageModel.kt) | 72 / 158 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/CCodeDsl.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/CCodeDsl.kt) | 4 / 11 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/ScannerCGeneratorDsl.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/ScannerCGeneratorDsl.kt) | 99 / 43 |
| M | [lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/ScannerSpec.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/ScannerSpec.kt) | 6 / 9 |
| M | [lang/dsl/src/test/kotlin/org/xtclang/tooling/DslPowerShowcaseTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/test/kotlin/org/xtclang/tooling/DslPowerShowcaseTest.kt) | 4 / 1 |
| M | [lang/dsl/src/test/kotlin/org/xtclang/tooling/LanguageModelTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/dsl/src/test/kotlin/org/xtclang/tooling/LanguageModelTest.kt) | 30 / 11 |
| M | [lang/generated-examples/xtc.tmLanguage.json](/private/tmp/xvm-lsp-editors-20261009/lang/generated-examples/xtc.tmLanguage.json) | 1 / 1 |
| M | [lang/intellij-plugin/README.md](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/README.md) | 199 / 17 |
| M | [lang/intellij-plugin/TESTING.md](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/TESTING.md) | 139 / 37 |
| M | [lang/intellij-plugin/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/build.gradle.kts) | 120 / 10 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CachedIdeInstaller.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CachedIdeInstaller.kt) | 55 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientDiagnostics.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientDiagnostics.kt) | 151 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientProtocol.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientProtocol.kt) | 216 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientTrace.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientTrace.kt) | 195 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerPlaybook.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerPlaybook.kt) | 1,989 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerPlaybookTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerPlaybookTest.kt) | 385 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerSettingsPage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerSettingsPage.kt) | 27 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompletionActions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompletionActions.kt) | 219 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/DiagnosticProbePlugin.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/DiagnosticProbePlugin.kt) | 47 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/EditorFocus.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/EditorFocus.kt) | 180 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/FocusRecovery.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/FocusRecovery.kt) | 120 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/IdeServices.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/IdeServices.kt) | 148 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LanguageServicePage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LanguageServicePage.kt) | 49 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LargeFileEditing.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LargeFileEditing.kt) | 92 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LocalLsp4ijPlugin.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LocalLsp4ijPlugin.kt) | 57 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/MemberActionScenarios.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/MemberActionScenarios.kt) | 83 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NativeDiagnostics.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NativeDiagnostics.kt) | 33 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NativeSemanticViews.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NativeSemanticViews.kt) | 312 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NavigationChooserTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NavigationChooserTest.kt) | 69 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityColors.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityColors.kt) | 157 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityCompilerImports.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityCompilerImports.kt) | 315 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityDependencies.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityDependencies.kt) | 150 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityDocumentation.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityDocumentation.kt) | 37 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityGraph.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityGraph.kt) | 313 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityHighlighting.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityHighlighting.kt) | 147 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityInlineCompletion.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityInlineCompletion.kt) | 96 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityLibrarySettings.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityLibrarySettings.kt) | 131 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityModules.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityModules.kt) | 271 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityNavigation.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityNavigation.kt) | 329 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityPlatform.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityPlatform.kt) | 1,225 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityProgress.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityProgress.kt) | 123 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityReferenceLenses.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityReferenceLenses.kt) | 77 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityReliability.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityReliability.kt) | 285 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityRename.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityRename.kt) | 215 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityRuntimeSettings.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityRuntimeSettings.kt) | 178 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityScenarios.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityScenarios.kt) | 56 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParitySemantics.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParitySemantics.kt) | 348 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityTypeMoves.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityTypeMoves.kt) | 205 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityWorkspace.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityWorkspace.kt) | 514 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookFailure.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookFailure.kt) | 15 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookFailureTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookFailureTest.kt) | 34 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookProgress.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookProgress.kt) | 22 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ProjectLifecycle.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ProjectLifecycle.kt) | 208 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/RefactoringActions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/RefactoringActions.kt) | 296 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ReferenceActions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ReferenceActions.kt) | 139 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/RenameFamilyScenarios.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/RenameFamilyScenarios.kt) | 143 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SettingsPersistence.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SettingsPersistence.kt) | 89 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SharedScenarios.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SharedScenarios.kt) | 264 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SignatureHints.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SignatureHints.kt) | 143 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/StartupEditing.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/StartupEditing.kt) | 144 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/StructureViews.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/StructureViews.kt) | 175 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UiWaits.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UiWaits.kt) | 62 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UiWaitsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UiWaitsTest.kt) | 71 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UpstreamReplacementGates.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UpstreamReplacementGates.kt) | 192 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/WorkspaceFolders.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/WorkspaceFolders.kt) | 50 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CodeLensUi.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CodeLensUi.kt) | 48 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ColorUi.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ColorUi.kt) | 165 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerImportPage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerImportPage.kt) | 103 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerLibrariesPage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerLibrariesPage.kt) | 114 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerReportPage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerReportPage.kt) | 87 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerSettingsPage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerSettingsPage.kt) | 173 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/EditorDiagnostics.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/EditorDiagnostics.kt) | 33 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/EditorUi.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/EditorUi.kt) | 154 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/FileTreeOperations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/FileTreeOperations.kt) | 116 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/HighlightingUi.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/HighlightingUi.kt) | 34 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/InlineCompletionUi.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/InlineCompletionUi.kt) | 30 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LanguageServicePage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LanguageServicePage.kt) | 182 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LargeFileProbe.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LargeFileProbe.kt) | 112 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/NavigationChooser.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/NavigationChooser.kt) | 36 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/PartialResults.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/PartialResults.kt) | 57 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/PlaybookProgress.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/PlaybookProgress.kt) | 84 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ProgressUi.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ProgressUi.kt) | 68 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ProjectLifecycle.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ProjectLifecycle.kt) | 96 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/RefreshRequests.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/RefreshRequests.kt) | 122 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ServerRuntimePage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ServerRuntimePage.kt) | 96 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/SettingsPersistencePage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/SettingsPersistencePage.kt) | 56 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/StartupEdits.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/StartupEdits.kt) | 86 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/WorkspaceEdits.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/WorkspaceEdits.kt) | 29 / 0 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/PluginPaths.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/PluginPaths.kt) | 28 / 18 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcCommenter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcCommenter.kt) | 11 / 11 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcFileType.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcFileType.kt) | 4 / 4 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcIconProvider.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcIconProvider.kt) | 3 / 4 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcIntelliJLanguage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcIntelliJLanguage.kt) | 8 / 9 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcTextMateBundleProvider.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcTextMateBundleProvider.kt) | 8 / 16 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt) | 21 / 21 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor/XtcEditorStartupActivity.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor/XtcEditorStartupActivity.kt) | 5 / 3 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor/XtcEnterHandlerDelegate.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor/XtcEnterHandlerDelegate.kt) | 16 / 6 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ClosedRefactoringDocuments.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ClosedRefactoringDocuments.kt) | 81 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CodeActionMessages.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CodeActionMessages.kt) | 28 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerBuildModel.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerBuildModel.kt) | 310 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerBuildUpdates.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerBuildUpdates.kt) | 73 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerImport.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerImport.kt) | 143 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerImportService.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerImportService.kt) | 57 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerLibrariesPanel.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerLibrariesPanel.kt) | 108 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerProjectConfigurable.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerProjectConfigurable.kt) | 476 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerRootWatches.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerRootWatches.kt) | 199 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerSettings.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerSettings.kt) | 33 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerWorkspaceModels.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerWorkspaceModels.kt) | 77 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConfigurationJson.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConfigurationJson.kt) | 39 / 0 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConnectionLifetime.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConnectionLifetime.kt) | 8 / 2 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixes.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixes.kt) | 74 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticReportJson.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticReportJson.kt) | 40 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessages.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessages.kt) | 110 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMoves.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMoves.kt) | 101 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DocumentStartupMessages.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DocumentStartupMessages.kt) | 275 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/EditorRefresh.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/EditorRefresh.kt) | 90 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ExportServerLogsAction.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ExportServerLogsAction.kt) | 75 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/FileMoveTargets.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/FileMoveTargets.kt) | 41 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageAdapter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageAdapter.kt) | 23 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurable.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurable.kt) | 212 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfiguration.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfiguration.kt) | 144 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServicePreferences.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServicePreferences.kt) | 24 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceSettings.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceSettings.kt) | 33 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LibraryConfiguration.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LibraryConfiguration.kt) | 110 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/OrderedPaths.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/OrderedPaths.kt) | 103 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/PreflightedRenames.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/PreflightedRenames.kt) | 69 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SelectLanguageAdapterAction.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SelectLanguageAdapterAction.kt) | 32 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerJvmOptions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerJvmOptions.kt) | 54 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerRuntimeConfigurable.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerRuntimeConfigurable.kt) | 107 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerRuntimeSettings.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerRuntimeSettings.kt) | 62 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerSupportLogs.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerSupportLogs.kt) | 159 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerWorkspaceEdit.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerWorkspaceEdit.kt) | 209 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SourceGraphConfiguration.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SourceGraphConfiguration.kt) | 185 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SourceGraphEdit.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SourceGraphEdit.kt) | 126 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt) | 158 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileMoveHandler.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileMoveHandler.kt) | 150 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileOperations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileOperations.kt) | 78 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileRenameHandler.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileRenameHandler.kt) | 99 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFormattingService.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFormattingService.kt) | 138 / 0 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt) | 207 / 31 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageServer.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageServer.kt) | 43 / 0 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt) | 120 / 44 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcParameterInfoHandler.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcParameterInfoHandler.kt) | 53 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameEdit.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameEdit.kt) | 358 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameHandler.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameHandler.kt) | 259 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcResolveCodeAction.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcResolveCodeAction.kt) | 24 / 0 |
| A | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcServerLogAction.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcServerLogAction.kt) | 34 / 0 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project/XtcNewProjectWizard.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project/XtcNewProjectWizard.kt) | 4 / 3 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project/XtcNewProjectWizardStep.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project/XtcNewProjectWizardStep.kt) | 14 / 8 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfiguration.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfiguration.kt) | 5 / 8 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfigurationProducer.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfigurationProducer.kt) | 4 / 4 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfigurationType.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfigurationType.kt) | 4 / 7 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/style/XtcCodeStyleSettings.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/style/XtcCodeStyleSettings.kt) | 6 / 6 |
| M | [lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/style/XtcLanguageCodeStyleSettingsProvider.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/style/XtcLanguageCodeStyleSettingsProvider.kt) | 4 / 4 |
| M | [lang/intellij-plugin/src/main/resources/META-INF/plugin.xml](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/resources/META-INF/plugin.xml) | 62 / 3 |
| M | [lang/intellij-plugin/src/main/resources/liveTemplates/XTC.xml](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/main/resources/liveTemplates/XTC.xml) | 1 / 1 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ClientCapabilitiesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ClientCapabilitiesTest.kt) | 96 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CodeActionMessagesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CodeActionMessagesTest.kt) | 42 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerBuildModelTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerBuildModelTest.kt) | 65 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerBuildUpdatesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerBuildUpdatesTest.kt) | 18 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerImportServiceTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerImportServiceTest.kt) | 87 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerImportTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerImportTest.kt) | 168 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerRootWatchesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerRootWatchesTest.kt) | 92 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConfigurationJsonTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConfigurationJsonTest.kt) | 53 / 0 |
| M | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConnectionLifetimeTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConnectionLifetimeTest.kt) | 13 / 6 |
| M | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConnectionProcessTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConnectionProcessTest.kt) | 16 / 14 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixesTest.kt) | 117 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticReportJsonTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticReportJsonTest.kt) | 79 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessagesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessagesTest.kt) | 210 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMovesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMovesTest.kt) | 66 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DocumentStartupMessagesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DocumentStartupMessagesTest.kt) | 272 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/FileMoveTargetsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/FileMoveTargetsTest.kt) | 61 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurationTest.kt) | 94 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LibraryConfigurationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LibraryConfigurationTest.kt) | 49 / 0 |
| M | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LspServerJarResolutionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LspServerJarResolutionTest.kt) | 9 / 11 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/PreflightedRenamesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/PreflightedRenamesTest.kt) | 152 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerJvmOptionsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerJvmOptionsTest.kt) | 40 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerSettingsPersistenceTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerSettingsPersistenceTest.kt) | 19 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerSupportLogsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerSupportLogsTest.kt) | 67 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerWorkspaceEditTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerWorkspaceEditTest.kt) | 29 / 0 |
| A | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/SourceGraphConfigurationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/SourceGraphConfigurationTest.kt) | 219 / 0 |
| M | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/BundledResourcesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/BundledResourcesTest.kt) | 14 / 14 |
| M | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/LiveTemplateRegistrationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/LiveTemplateRegistrationTest.kt) | 12 / 11 |
| M | [lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/PluginManifestTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/PluginManifestTest.kt) | 57 / 14 |
| M | [lang/lsp-server/README.md](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/README.md) | 468 / 77 |
| M | [lang/lsp-server/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/build.gradle.kts) | 134 / 25 |
| M | [lang/lsp-server/editor-setup.md](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/editor-setup.md) | 5 / 5 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AbstractAdapter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AbstractAdapter.kt) | 63 / 37 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/Adapter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/Adapter.kt) | 338 / 162 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterCapability.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterCapability.kt) | 33 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterCodeActions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterCodeActions.kt) | 50 / 31 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterFormatter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterFormatter.kt) | 132 / 150 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterFutures.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterFutures.kt) | 31 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterModels.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterModels.kt) | 68 / 57 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterTree.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterTree.kt) | 8 / 9 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/DocumentColor.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/DocumentColor.kt) | 24 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/FormattingConfig.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/FormattingConfig.kt) | 11 / 14 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/LanguageConstants.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/LanguageConstants.kt) | 22 / 12 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/mock/MockAdapter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/mock/MockAdapter.kt) | 89 / 39 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/treesitter/TreeSitterAdapter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/treesitter/TreeSitterAdapter.kt) | 395 / 221 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerAnnotationIdentity.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerAnnotationIdentity.kt) | 115 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerCallableIdentity.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerCallableIdentity.kt) | 66 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerCalls.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerCalls.kt) | 53 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerDeclarations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerDeclarations.kt) | 49 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerDispatch.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerDispatch.kt) | 183 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerImplementations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerImplementations.kt) | 231 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerImportAliases.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerImportAliases.kt) | 99 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMemberAction.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMemberAction.kt) | 174 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMemberSignature.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMemberSignature.kt) | 235 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMethodRelations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMethodRelations.kt) | 60 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMissingMethod.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMissingMethod.kt) | 591 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerPrimaryParameters.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerPrimaryParameters.kt) | 19 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerPropertyRelations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerPropertyRelations.kt) | 69 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerQueueTrace.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerQueueTrace.kt) | 213 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerReceiverIdentity.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerReceiverIdentity.kt) | 85 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerRenameFacts.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerRenameFacts.kt) | 558 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerResourceValues.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerResourceValues.kt) | 95 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceLinks.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceLinks.kt) | 46 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceTypes.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceTypes.kt) | 35 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceUsage.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceUsage.kt) | 63 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerTypeNames.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerTypeNames.kt) | 108 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/LibraryDeclaration.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/LibraryDeclaration.kt) | 70 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/PartialSemanticModel.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/PartialSemanticModel.kt) | 176 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/SemanticModel.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/SemanticModel.kt) | 566 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/SemanticModelBuilder.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/SemanticModelBuilder.kt) | 2,089 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt) | 2,448 / 69 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAst.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAst.kt) | 179 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAttachedSources.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAttachedSources.kt) | 111 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAutoImports.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAutoImports.kt) | 145 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkBuildModel.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkBuildModel.kt) | 190 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkCalls.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkCalls.kt) | 88 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkColorPrototype.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkColorPrototype.kt) | 179 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkCursorQueries.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkCursorQueries.kt) | 336 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDependency.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDependency.kt) | 170 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDiagnosticIndex.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDiagnosticIndex.kt) | 33 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDiagnostics.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDiagnostics.kt) | 96 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDocumentation.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDocumentation.kt) | 56 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkFileChanges.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkFileChanges.kt) | 76 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkHierarchy.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkHierarchy.kt) | 86 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkImports.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkImports.kt) | 108 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkInlineCompletions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkInlineCompletions.kt) | 52 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLexical.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLexical.kt) | 331 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibraries.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibraries.kt) | 85 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibrarySources.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibrarySources.kt) | 196 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalDeclarations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalDeclarations.kt) | 59 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalExtraction.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalExtraction.kt) | 140 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalInline.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalInline.kt) | 203 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalRemoval.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalRemoval.kt) | 73 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMemberActions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMemberActions.kt) | 138 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMemberInline.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMemberInline.kt) | 144 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMethodExtraction.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMethodExtraction.kt) | 245 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMissingDeclarations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMissingDeclarations.kt) | 162 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMissingMethods.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMissingMethods.kt) | 183 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMonikers.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMonikers.kt) | 132 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveImports.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveImports.kt) | 37 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveInputs.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveInputs.kt) | 40 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveOperations.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveOperations.kt) | 30 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkNavigationIndex.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkNavigationIndex.kt) | 53 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkPresentation.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkPresentation.kt) | 252 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkProjectQueries.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkProjectQueries.kt) | 1,506 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkQualifiedName.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkQualifiedName.kt) | 87 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRefactoringSyntax.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRefactoringSyntax.kt) | 53 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRename.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRename.kt) | 1,229 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRenameProposal.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRenameProposal.kt) | 44 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkResourceMoves.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkResourceMoves.kt) | 80 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkResources.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkResources.kt) | 104 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSafeDelete.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSafeDelete.kt) | 106 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSourceModule.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSourceModule.kt) | 163 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSourceMoves.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSourceMoves.kt) | 104 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSources.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSources.kt) | 223 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSymbols.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSymbols.kt) | 168 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSyntaxCompletions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSyntaxCompletions.kt) | 506 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkTypeMoves.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkTypeMoves.kt) | 229 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkWorkspaceDiscovery.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkWorkspaceDiscovery.kt) | 167 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkWorkspaceNavigation.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkWorkspaceNavigation.kt) | 267 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/IndexedSymbol.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/IndexedSymbol.kt) | 6 / 6 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/WorkspaceIndex.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/WorkspaceIndex.kt) | 23 / 22 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/WorkspaceIndexer.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/WorkspaceIndexer.kt) | 147 / 70 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/lexer/TemplateScanner.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/lexer/TemplateScanner.kt) | 14 / 11 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/lexer/TemplateScannerToken.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/lexer/TemplateScannerToken.kt) | 9 / 8 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/CompilationResult.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/CompilationResult.kt) | 11 / 3 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Diagnostic.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Diagnostic.kt) | 4 / 6 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Location.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Location.kt) | 1 / 3 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Lsp4jConversions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Lsp4jConversions.kt) | 13 / 4 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/SymbolInfo.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/SymbolInfo.kt) | 4 / 5 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientPresentation.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientPresentation.kt) | 153 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientRefresh.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientRefresh.kt) | 88 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientTrace.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientTrace.kt) | 60 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CodeLensSettings.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CodeLensSettings.kt) | 14 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CompilerConfiguration.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CompilerConfiguration.kt) | 126 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CompilerLibraries.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CompilerLibraries.kt) | 143 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ConnectionProgress.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ConnectionProgress.kt) | 236 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DiagnosticReports.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DiagnosticReports.kt) | 171 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DocumentSynchronization.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DocumentSynchronization.kt) | 52 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DocumentText.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DocumentText.kt) | 73 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/EditorFormattingState.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/EditorFormattingState.kt) | 78 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/FileChangeSnapshots.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/FileChangeSnapshots.kt) | 40 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/LspJsonOptions.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/LspJsonOptions.kt) | 66 / 23 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/PartialResults.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/PartialResults.kt) | 93 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ProtocolLifecycle.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ProtocolLifecycle.kt) | 129 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ProtocolTrace.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ProtocolTrace.kt) | 160 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ReadOnlyDocuments.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ReadOnlyDocuments.kt) | 109 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/RenameProposal.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/RenameProposal.kt) | 36 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ResolveReports.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ResolveReports.kt) | 68 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ResourceFileWatchers.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ResourceFileWatchers.kt) | 250 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/SemanticTokenReports.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/SemanticTokenReports.kt) | 127 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/SourceRootResolver.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/SourceRootResolver.kt) | 15 / 18 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt) | 1,049 / 290 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServerLauncher.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServerLauncher.kt) | 99 / 42 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcTextDocumentService.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcTextDocumentService.kt) | 1,557 / 230 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcWorkspaceService.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcWorkspaceService.kt) | 83 / 31 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/Platform.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/Platform.kt) | 10 / 6 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/SemanticTokenEncoder.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/SemanticTokenEncoder.kt) | 44 / 27 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/TreeSitterLibraryLookup.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/TreeSitterLibraryLookup.kt) | 2 / 2 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcNode.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcNode.kt) | 205 / 243 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcParser.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcParser.kt) | 175 / 175 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcQueries.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcQueries.kt) | 17 / 25 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcQueryEngine.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcQueryEngine.kt) | 91 / 71 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcTree.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcTree.kt) | 66 / 70 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ExecutionTrace.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ExecutionTrace.kt) | 121 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ProgressLabels.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ProgressLabels.kt) | 34 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ServerLogs.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ServerLogs.kt) | 221 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/TraceProcessId.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/TraceProcessId.kt) | 12 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/WorkInProgress.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/WorkInProgress.kt) | 4 / 2 |
| M | [lang/lsp-server/src/main/resources/logback.xml](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/resources/logback.xml) | 27 / 8 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/AdapterFuturesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/AdapterFuturesTest.kt) | 55 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CodeActionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CodeActionTest.kt) | 6 / 6 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CodeLensTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CodeLensTest.kt) | 1 / 3 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerBoundaryRequirementsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerBoundaryRequirementsTest.kt) | 249 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerCallSiteTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerCallSiteTest.kt) | 366 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerCallableProofTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerCallableProofTest.kt) | 221 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerConsumerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerConsumerTest.kt) | 7 / 2 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerDispatchRoutesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerDispatchRoutesTest.kt) | 238 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerEmissionAuditTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerEmissionAuditTest.kt) | 263 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerExtractMethodFactsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerExtractMethodFactsTest.kt) | 57 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerMethodExtractionProofTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerMethodExtractionProofTest.kt) | 106 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerMissingMethodProofTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerMissingMethodProofTest.kt) | 404 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerPoolScopeTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerPoolScopeTest.kt) | 102 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerProjectTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerProjectTest.kt) | 383 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerRelocationProofTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerRelocationProofTest.kt) | 76 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerRenameRequirementsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerRenameRequirementsTest.kt) | 103 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerTestSupport.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerTestSupport.kt) | 3 / 22 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompletionTest.kt) | 48 / 45 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/DocumentFormattingTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/DocumentFormattingTest.kt) | 13 / 15 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/EmbeddingDiagnosticsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/EmbeddingDiagnosticsTest.kt) | 17 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/FoldingRangeTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/FoldingRangeTest.kt) | 15 / 11 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/FormattingTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/FormattingTest.kt) | 13 / 13 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/InlayHintTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/InlayHintTest.kt) | 16 / 48 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/LinkedEditingRangeTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/LinkedEditingRangeTest.kt) | 2 / 2 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/ManualCompositionFixture.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/ManualCompositionFixture.kt) | 26 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/MockAdapterTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/MockAdapterTest.kt) | 17 / 14 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/NavigationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/NavigationTest.kt) | 81 / 60 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/OnTypeFormattingTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/OnTypeFormattingTest.kt) | 12 / 11 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/PrepareLibraryPlaybook.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/PrepareLibraryPlaybook.kt) | 38 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/RenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/RenameTest.kt) | 14 / 16 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SelectionRangeTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SelectionRangeTest.kt) | 34 / 22 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SemanticModelTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SemanticModelTest.kt) | 390 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SemanticTokenTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SemanticTokenTest.kt) | 33 / 21 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SignatureHelpTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SignatureHelpTest.kt) | 91 / 59 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TreeSitterAdapterTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TreeSitterAdapterTest.kt) | 165 / 210 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TreeSitterTestBase.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TreeSitterTestBase.kt) | 9 / 10 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TypeInfoDiagnosticsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TypeInfoDiagnosticsTest.kt) | 1 / 1 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TypeInfoFinalCompositionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TypeInfoFinalCompositionTest.kt) | 333 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAdapterLifecycleTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAdapterLifecycleTest.kt) | 159 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAdapterTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAdapterTest.kt) | 486 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAnonymousConstructorTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAnonymousConstructorTest.kt) | 264 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArgumentCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArgumentCompletionTest.kt) | 387 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArgumentContextTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArgumentContextTest.kt) | 75 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArrayDimensionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArrayDimensionTest.kt) | 183 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkBuildModelTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkBuildModelTest.kt) | 218 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCallHierarchyTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCallHierarchyTest.kt) | 165 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCallableBreadthTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCallableBreadthTest.kt) | 45 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCandidateProbeTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCandidateProbeTest.kt) | 169 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkColorValueTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkColorValueTest.kt) | 182 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCompletionSignatureTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCompletionSignatureTest.kt) | 201 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCompoundHeaderTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCompoundHeaderTest.kt) | 195 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkConditionalRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkConditionalRenameTest.kt) | 155 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkConfigurationReplacementTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkConfigurationReplacementTest.kt) | 217 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCrossModuleMissingMethodsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCrossModuleMissingMethodsTest.kt) | 381 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCrossOwnerMissingMethodsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCrossOwnerMissingMethodsTest.kt) | 497 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCursorPresentationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCursorPresentationTest.kt) | 84 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCursorRequestTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCursorRequestTest.kt) | 328 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCyclicRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCyclicRenameTest.kt) | 44 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationHeaderTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationHeaderTest.kt) | 225 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationProvenanceTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationProvenanceTest.kt) | 132 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationTest.kt) | 121 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDelegatedRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDelegatedRenameTest.kt) | 62 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDelimiterRecoveryTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDelimiterRecoveryTest.kt) | 246 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDependencyTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDependencyTest.kt) | 407 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDiagnosticIndexTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDiagnosticIndexTest.kt) | 150 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDiagnosticTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDiagnosticTest.kt) | 149 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDocumentationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDocumentationTest.kt) | 93 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkEditingTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkEditingTest.kt) | 129 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkEnclosingValueCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkEnclosingValueCompletionTest.kt) | 130 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkExternalRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkExternalRenameTest.kt) | 200 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFileChangesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFileChangesTest.kt) | 130 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFileOperationsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFileOperationsTest.kt) | 214 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFoldingTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFoldingTest.kt) | 40 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFormattingBreadthTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFormattingBreadthTest.kt) | 127 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkGenericHeaderTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkGenericHeaderTest.kt) | 241 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkGraphMoveTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkGraphMoveTest.kt) | 457 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkHeaderSlotsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkHeaderSlotsTest.kt) | 153 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkImportCompletionLifecycleTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkImportCompletionLifecycleTest.kt) | 68 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkImportCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkImportCompletionTest.kt) | 103 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteCallTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteCallTest.kt) | 144 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteConstructorTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteConstructorTest.kt) | 95 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteFunctionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteFunctionTest.kt) | 128 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIndexLifecycleTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIndexLifecycleTest.kt) | 132 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInferredPresentationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInferredPresentationTest.kt) | 78 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInitializerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInitializerTest.kt) | 64 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInlineCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInlineCompletionTest.kt) | 142 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkL63SharedActionsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkL63SharedActionsTest.kt) | 95 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkL65ClosureTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkL65ClosureTest.kt) | 106 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLambdaRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLambdaRenameTest.kt) | 129 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLexicalTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLexicalTest.kt) | 41 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLibrariesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLibrariesTest.kt) | 101 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLibraryDocumentsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLibraryDocumentsTest.kt) | 62 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLinkedEditingTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLinkedEditingTest.kt) | 106 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiteralCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiteralCompletionTest.kt) | 136 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiteralRecoveryTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiteralRecoveryTest.kt) | 185 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiveWorkspaceTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiveWorkspaceTest.kt) | 177 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalExtractionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalExtractionTest.kt) | 143 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalInitializerActionsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalInitializerActionsTest.kt) | 219 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalInlineTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalInlineTest.kt) | 146 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalRemovalTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalRemovalTest.kt) | 137 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkManualCompositionRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkManualCompositionRenameTest.kt) | 132 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkManualCompositionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkManualCompositionTest.kt) | 64 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMemberActionsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMemberActionsTest.kt) | 641 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMemberInlineTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMemberInlineTest.kt) | 148 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMethodExtractionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMethodExtractionTest.kt) | 381 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingDeclarationNameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingDeclarationNameTest.kt) | 134 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingDeclarationsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingDeclarationsTest.kt) | 119 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingMethodsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingMethodsTest.kt) | 688 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkModuleSessionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkModuleSessionTest.kt) | 284 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMonikerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMonikerTest.kt) | 212 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMoveInputsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMoveInputsTest.kt) | 30 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkNavigationIndexTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkNavigationIndexTest.kt) | 233 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkNavigationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkNavigationTest.kt) | 521 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkOperandCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkOperandCompletionTest.kt) | 117 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkParameterRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkParameterRenameTest.kt) | 187 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPartialAnalysisTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPartialAnalysisTest.kt) | 952 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPlatformRegressionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPlatformRegressionTest.kt) | 194 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPresentationScaleTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPresentationScaleTest.kt) | 86 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPresentationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPresentationTest.kt) | 247 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPrimaryParameterRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPrimaryParameterRenameTest.kt) | 112 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectGraphTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectGraphTest.kt) | 99 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectProofMemoryTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectProofMemoryTest.kt) | 104 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectQueryLifecycleTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectQueryLifecycleTest.kt) | 301 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectQueryTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectQueryTest.kt) | 573 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectTest.kt) | 302 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPropertyArgumentCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPropertyArgumentCompletionTest.kt) | 270 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkQualifiedHeaderTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkQualifiedHeaderTest.kt) | 240 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRecoveryTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRecoveryTest.kt) | 145 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkReferenceLensTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkReferenceLensTest.kt) | 117 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRenameBoundaryTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRenameBoundaryTest.kt) | 116 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRenameTest.kt) | 228 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkResourceInputsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkResourceInputsTest.kt) | 159 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkResourceRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkResourceRenameTest.kt) | 317 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRetentionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRetentionTest.kt) | 194 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSafeDeleteTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSafeDeleteTest.kt) | 108 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkScopeCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkScopeCompletionTest.kt) | 187 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSemanticColorTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSemanticColorTest.kt) | 313 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSemanticLookupTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSemanticLookupTest.kt) | 758 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSourceLinksTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSourceLinksTest.kt) | 125 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSpecializedConstructorTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSpecializedConstructorTest.kt) | 216 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkStructuralTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkStructuralTest.kt) | 68 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSyntaxCompletionTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSyntaxCompletionTest.kt) | 204 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTeachingWorkspaceTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTeachingWorkspaceTest.kt) | 237 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTypeHeaderTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTypeHeaderTest.kt) | 195 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTypeMoveTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTypeMoveTest.kt) | 650 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkUnionDelegationRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkUnionDelegationRenameTest.kt) | 150 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkUnionRenameTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkUnionRenameTest.kt) | 323 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkValueTemplateTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkValueTemplateTest.kt) | 54 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceDiscoveryTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceDiscoveryTest.kt) | 108 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceNavigationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceNavigationTest.kt) | 98 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceRefactoringTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceRefactoringTest.kt) | 349 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWrittenFormalTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWrittenFormalTest.kt) | 212 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/index/WorkspaceIndexTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/index/WorkspaceIndexTest.kt) | 17 / 5 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/index/WorkspaceIndexerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/index/WorkspaceIndexerTest.kt) | 142 / 13 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/lexer/TemplateScannerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/lexer/TemplateScannerTest.kt) | 6 / 6 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/AdapterBackendTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/AdapterBackendTest.kt) | 42 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CapabilityContractTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CapabilityContractTest.kt) | 100 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CapabilityNegotiationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CapabilityNegotiationTest.kt) | 406 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ClientNotificationsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ClientNotificationsTest.kt) | 130 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ClientPresentationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ClientPresentationTest.kt) | 165 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CodeLensSettingsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CodeLensSettingsTest.kt) | 18 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ColorValueProtocolTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ColorValueProtocolTest.kt) | 138 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CompilerConfigurationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CompilerConfigurationTest.kt) | 450 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CompilerLibrariesTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CompilerLibrariesTest.kt) | 115 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ConnectionProgressTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ConnectionProgressTest.kt) | 324 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/DiagnosticPresentationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/DiagnosticPresentationTest.kt) | 107 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/DocumentSynchronizationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/DocumentSynchronizationTest.kt) | 287 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/EditorFormattingStateTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/EditorFormattingStateTest.kt) | 50 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ExecutionTraceTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ExecutionTraceTest.kt) | 213 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/FileChangeSnapshotsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/FileChangeSnapshotsTest.kt) | 44 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/FormattingConfigRoundTripTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/FormattingConfigRoundTripTest.kt) | 49 / 37 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/IndexingProgressTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/IndexingProgressTest.kt) | 109 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/InlineCompletionProtocolTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/InlineCompletionProtocolTest.kt) | 149 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LibraryContentProtocolTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LibraryContentProtocolTest.kt) | 166 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LspIntegrationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LspIntegrationTest.kt) | 43 / 38 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LspProcessLifecycleTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LspProcessLifecycleTest.kt) | 42 / 30 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/MonikerProtocolTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/MonikerProtocolTest.kt) | 114 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/PartialResultsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/PartialResultsTest.kt) | 168 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ProgressLabelsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ProgressLabelsTest.kt) | 21 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ProtocolLifecycleTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ProtocolLifecycleTest.kt) | 112 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/RequestOwnershipTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/RequestOwnershipTest.kt) | 86 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ResolveReportsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ResolveReportsTest.kt) | 29 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ResourceFileWatchersTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ResourceFileWatchersTest.kt) | 232 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/SemanticTokenReportsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/SemanticTokenReportsTest.kt) | 71 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/SourceRootResolverTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/SourceRootResolverTest.kt) | 20 / 14 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/StartupRegistrationTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/StartupRegistrationTest.kt) | 50 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCallbackServerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCallbackServerTest.kt) | 183 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCursorServerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCursorServerTest.kt) | 373 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCursorWatchTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCursorWatchTest.kt) | 103 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkDeclarationServerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkDeclarationServerTest.kt) | 62 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkFeatureResolveProtocolTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkFeatureResolveProtocolTest.kt) | 282 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkLanguageServerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkLanguageServerTest.kt) | 430 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkModuleServerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkModuleServerTest.kt) | 269 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkProjectServerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkProjectServerTest.kt) | 467 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkPullDiagnosticsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkPullDiagnosticsTest.kt) | 444 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkRenameServerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkRenameServerTest.kt) | 729 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkResolveProtocolTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkResolveProtocolTest.kt) | 220 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkSemanticTokenProtocolTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkSemanticTokenProtocolTest.kt) | 146 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkStdioTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkStdioTest.kt) | 2,079 / 0 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkSyntaxCompletionProtocolTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkSyntaxCompletionProtocolTest.kt) | 143 / 0 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XtcLanguageServerTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XtcLanguageServerTest.kt) | 47 / 28 |
| M | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/treesitter/SemanticTokensVsTextMateTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/treesitter/SemanticTokensVsTextMateTest.kt) | 119 / 106 |
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/util/ServerLogsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/util/ServerLogsTest.kt) | 79 / 0 |
| A | [lang/lsp-server/src/test/resources/platform/CircularBuffer.x](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/resources/platform/CircularBuffer.x) | 132 / 0 |
| A | [lang/scripts/compiler-workload.py](/private/tmp/xvm-lsp-editors-20261009/lang/scripts/compiler-workload.py) | 504 / 0 |
| A | [lang/test-fixtures/color-values/ColorPrototype.x](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/color-values/ColorPrototype.x) | 17 / 0 |
| A | [lang/test-fixtures/compiler-playbook/compiler-import/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/compiler-playbook/compiler-import/build.gradle.kts) | 49 / 0 |
| A | [lang/test-fixtures/compiler-playbook/compiler-import/gradle.properties](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/compiler-playbook/compiler-import/gradle.properties) | 4 / 0 |
| A | [lang/test-fixtures/compiler-playbook/compiler-import/settings.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/compiler-playbook/compiler-import/settings.gradle.kts) | 1 / 0 |
| A | [lang/test-fixtures/compiler-playbook/scenarios.json](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/compiler-playbook/scenarios.json) | 8,837 / 0 |
| A | [lang/test-fixtures/compiler-workload/platform.json](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/compiler-workload/platform.json) | 18 / 0 |
| A | [lang/test-fixtures/semantic-highlighting/SemanticColors.x](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/semantic-highlighting/SemanticColors.x) | 29 / 0 |
| A | [lang/test-fixtures/semantic-highlighting/semantic-colors.code-workspace](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/semantic-highlighting/semantic-colors.code-workspace) | 16 / 0 |
| M | [lang/tree-sitter/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/tree-sitter/build.gradle.kts) | 965 / 866 |
| M | [lang/tree-sitter/doc/functionality.md](/private/tmp/xvm-lsp-editors-20261009/lang/tree-sitter/doc/functionality.md) | 1 / 1 |
| M | [lang/vscode-extension/.gitignore](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/.gitignore) | 1 / 0 |
| M | [lang/vscode-extension/README.md](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/README.md) | 220 / 11 |
| M | [lang/vscode-extension/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/build.gradle.kts) | 256 / 170 |
| M | [lang/vscode-extension/package-lock.json](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/package-lock.json) | 82 / 26 |
| M | [lang/vscode-extension/package.json](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/package.json) | 369 / 64 |
| M | [lang/vscode-extension/scripts/bundle.cjs](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/scripts/bundle.cjs) | 2 / 2 |
| M | [lang/vscode-extension/scripts/run-vscode-tests.cjs](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/scripts/run-vscode-tests.cjs) | 2 / 2 |
| A | [lang/vscode-extension/src/adapter-selection.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/adapter-selection.ts) | 23 / 0 |
| A | [lang/vscode-extension/src/build-model.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/build-model.ts) | 84 / 0 |
| M | [lang/vscode-extension/src/commands.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/commands.ts) | 15 / 1 |
| A | [lang/vscode-extension/src/compiler-import.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/compiler-import.ts) | 81 / 0 |
| A | [lang/vscode-extension/src/compiler-paths.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/compiler-paths.ts) | 246 / 0 |
| A | [lang/vscode-extension/src/compiler-task.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/compiler-task.ts) | 43 / 0 |
| M | [lang/vscode-extension/src/debug-adapter.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/debug-adapter.ts) | 1 / 1 |
| A | [lang/vscode-extension/src/editor-settings.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/editor-settings.ts) | 30 / 0 |
| M | [lang/vscode-extension/src/extension.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/extension.ts) | 86 / 20 |
| M | [lang/vscode-extension/src/java.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/java.ts) | 4 / 1 |
| A | [lang/vscode-extension/src/library-configuration.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/library-configuration.ts) | 46 / 0 |
| A | [lang/vscode-extension/src/library-settings.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/library-settings.ts) | 79 / 0 |
| M | [lang/vscode-extension/src/lsp-client.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/lsp-client.ts) | 181 / 49 |
| A | [lang/vscode-extension/src/rename-proposal.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/rename-proposal.ts) | 154 / 0 |
| A | [lang/vscode-extension/src/runtime-settings.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/runtime-settings.ts) | 46 / 0 |
| A | [lang/vscode-extension/src/service-notifications.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/service-notifications.ts) | 11 / 0 |
| A | [lang/vscode-extension/src/service-settings.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/service-settings.ts) | 35 / 0 |
| A | [lang/vscode-extension/src/settings-report.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/settings-report.ts) | 33 / 0 |
| A | [lang/vscode-extension/src/source-graph-configuration.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/source-graph-configuration.ts) | 46 / 0 |
| M | [lang/vscode-extension/src/status-bar.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/status-bar.ts) | 11 / 10 |
| A | [lang/vscode-extension/src/support-logs.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/support-logs.ts) | 105 / 0 |
| A | [lang/vscode-extension/src/test/archive.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/archive.ts) | 19 / 0 |
| A | [lang/vscode-extension/src/test/explorer-move-trace.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/explorer-move-trace.ts) | 64 / 0 |
| A | [lang/vscode-extension/src/test/explorer-move.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/explorer-move.ts) | 43 / 0 |
| A | [lang/vscode-extension/src/test/explorer-probe/index.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/explorer-probe/index.ts) | 75 / 0 |
| A | [lang/vscode-extension/src/test/lifecycle/index.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/lifecycle/index.ts) | 113 / 0 |
| A | [lang/vscode-extension/src/test/native-focus.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/native-focus.ts) | 23 / 0 |
| A | [lang/vscode-extension/src/test/playbook/advanced.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/advanced.ts) | 530 / 0 |
| A | [lang/vscode-extension/src/test/playbook/colors.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/colors.ts) | 156 / 0 |
| A | [lang/vscode-extension/src/test/playbook/compilerImports.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/compilerImports.ts) | 238 / 0 |
| A | [lang/vscode-extension/src/test/playbook/completion.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/completion.ts) | 174 / 0 |
| A | [lang/vscode-extension/src/test/playbook/dependencies.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/dependencies.ts) | 173 / 0 |
| A | [lang/vscode-extension/src/test/playbook/documentation.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/documentation.ts) | 32 / 0 |
| A | [lang/vscode-extension/src/test/playbook/editingClosure.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/editingClosure.ts) | 102 / 0 |
| A | [lang/vscode-extension/src/test/playbook/graph.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/graph.ts) | 237 / 0 |
| A | [lang/vscode-extension/src/test/playbook/highlighting.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/highlighting.ts) | 103 / 0 |
| A | [lang/vscode-extension/src/test/playbook/index.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/index.ts) | 88 / 0 |
| A | [lang/vscode-extension/src/test/playbook/inlineCompletion.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/inlineCompletion.ts) | 81 / 0 |
| A | [lang/vscode-extension/src/test/playbook/librarySettings.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/librarySettings.ts) | 119 / 0 |
| A | [lang/vscode-extension/src/test/playbook/liveWorkspace.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/liveWorkspace.ts) | 175 / 0 |
| A | [lang/vscode-extension/src/test/playbook/memberActions.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/memberActions.ts) | 116 / 0 |
| A | [lang/vscode-extension/src/test/playbook/modules.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/modules.ts) | 207 / 0 |
| A | [lang/vscode-extension/src/test/playbook/navigation.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/navigation.ts) | 97 / 0 |
| A | [lang/vscode-extension/src/test/playbook/platform.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/platform.ts) | 534 / 0 |
| A | [lang/vscode-extension/src/test/playbook/playbook.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/playbook.test.ts) | 125 / 0 |
| A | [lang/vscode-extension/src/test/playbook/progress.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/progress.ts) | 115 / 0 |
| A | [lang/vscode-extension/src/test/playbook/properties.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/properties.ts) | 65 / 0 |
| A | [lang/vscode-extension/src/test/playbook/referenceLenses.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/referenceLenses.ts) | 52 / 0 |
| A | [lang/vscode-extension/src/test/playbook/reliability.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/reliability.ts) | 160 / 0 |
| A | [lang/vscode-extension/src/test/playbook/rename.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/rename.ts) | 93 / 0 |
| A | [lang/vscode-extension/src/test/playbook/renameFamilies.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/renameFamilies.ts) | 177 / 0 |
| A | [lang/vscode-extension/src/test/playbook/runtimeSettings.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/runtimeSettings.ts) | 118 / 0 |
| A | [lang/vscode-extension/src/test/playbook/semantics.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/semantics.ts) | 221 / 0 |
| A | [lang/vscode-extension/src/test/playbook/shared.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/shared.ts) | 102 / 0 |
| A | [lang/vscode-extension/src/test/playbook/support.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/support.ts) | 274 / 0 |
| A | [lang/vscode-extension/src/test/playbook/syntaxCompletion.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/syntaxCompletion.ts) | 48 / 0 |
| A | [lang/vscode-extension/src/test/playbook/typeMoves.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/playbook/typeMoves.ts) | 77 / 0 |
| A | [lang/vscode-extension/src/test/progress.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/progress.ts) | 62 / 0 |
| A | [lang/vscode-extension/src/test/runLifecycle.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/runLifecycle.ts) | 154 / 0 |
| M | [lang/vscode-extension/src/test/runTest.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/runTest.ts) | 115 / 45 |
| A | [lang/vscode-extension/src/test/settings-persistence/index.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/settings-persistence/index.ts) | 46 / 0 |
| M | [lang/vscode-extension/src/test/suite/activation.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/activation.test.ts) | 17 / 0 |
| A | [lang/vscode-extension/src/test/suite/build-model.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/build-model.test.ts) | 33 / 0 |
| A | [lang/vscode-extension/src/test/suite/compiler-import.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/compiler-import.test.ts) | 94 / 0 |
| A | [lang/vscode-extension/src/test/suite/compiler-paths.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/compiler-paths.test.ts) | 41 / 0 |
| A | [lang/vscode-extension/src/test/suite/compiler-project.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/compiler-project.test.ts) | 125 / 0 |
| A | [lang/vscode-extension/src/test/suite/compiler-task.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/compiler-task.test.ts) | 108 / 0 |
| M | [lang/vscode-extension/src/test/suite/extension.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/extension.test.ts) | 2 / 5 |
| M | [lang/vscode-extension/src/test/suite/index.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/index.ts) | 6 / 1 |
| A | [lang/vscode-extension/src/test/suite/library-configuration.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/library-configuration.test.ts) | 41 / 0 |
| M | [lang/vscode-extension/src/test/suite/lsp-startup.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/lsp-startup.test.ts) | 88 / 21 |
| A | [lang/vscode-extension/src/test/suite/playbook-wait.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/playbook-wait.test.ts) | 37 / 0 |
| A | [lang/vscode-extension/src/test/suite/runtime-settings.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/runtime-settings.test.ts) | 16 / 0 |
| A | [lang/vscode-extension/src/test/suite/service-settings.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/service-settings.test.ts) | 20 / 0 |
| A | [lang/vscode-extension/src/test/suite/settings-report.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/settings-report.test.ts) | 42 / 0 |
| A | [lang/vscode-extension/src/test/suite/source-graph-configuration.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/source-graph-configuration.test.ts) | 68 / 0 |
| A | [lang/vscode-extension/src/test/suite/support-logs.test.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/suite/support-logs.test.ts) | 53 / 0 |
| M | [lang/vscode-extension/src/test/vscodeCache.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/vscodeCache.ts) | 23 / 2 |
| A | [lang/vscode-extension/src/test/wait.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/wait.ts) | 36 / 0 |
| A | [lang/vscode-extension/src/test/workbenchUi.ts](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/src/test/workbenchUi.ts) | 62 / 0 |
| M | [lang/vscode-extension/tsconfig.json](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/tsconfig.json) | 3 / 2 |
| M | [manualTests/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/manualTests/build.gradle.kts) | 1 / 0 |
| M | [manualTests/src/main/x/conditionalEffects.x](/private/tmp/xvm-lsp-editors-20261009/manualTests/src/main/x/conditionalEffects.x) | 0 / 2 |
| M | [xdk/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/xdk/build.gradle.kts) | 1 / 22 |

</details>

## Validation and release requirements

- Keep each capability's implementation, public contract, direct consumer tests and relevant documentation together. A passing isolated Java suite does not establish LSP adapter compatibility.
- Preserve the agreed **0.5.0 Java API migration boundary**. Ordinary CLI behavior and Java source/binary compatibility are different claims; removed APIs and changed result shapes require migration and recompilation.
- Reconcile stacked branches against the actual merged prerequisites, preserving accepted cleanup, compiler fixes and current Gradle versions. Do not replay an obsolete owner-pool fallback or restore a downgraded wrapper.
- Rerun relevant tests after material changes/rebases, inspect JUnit XML failures and skips, and build the XDK inputs first. Gradle changes must preserve configuration-cache support. Full native-editor acceptance remains outstanding for the extracted product branch.
- Mirror accepted branch fixes into the integration branch. Its latest synchronization passed **695 compiler/utility tests and 270 LSP compiler/embedding consumer tests**; 44 existing disabled/platform/opt-in Java skips remained, and none of the changed regressions or focused LSP tests were skipped. The Boolean runtime fixture, XDK build and root/server formatting checks also passed. This is integration evidence, not a substitute for each branch's recorded validation.
- When PR submission is authorized, request **ggleyzer, cpurdy and thegridman**. Preserve required reviews, CI and conversation resolution. A stacked parent does not inherit master's review protection automatically.

## Deferred work

Runtime execution/DAP, notebooks and a public color library remain deferred. Theme-based source highlighting is included in the LSP/editor product. Microsoft Marketplace authentication and automatic publication are postponed and do not block the compiler foundations. IntelliJ Platform and VS Code upstream changes remain deferred; released client dependencies, supported workarounds and explicit limitations are the shipping plan.

Unrelated runtime/JIT edits, broad modernization, documentation sweeps and historical review scripts are not implicitly included in the five prepared branches. The reconciliation inventory below preserves their visibility and identifies compiler follow-ups that still need a decision.

## Remaining work outside the five prepared branches

The integration snapshot and prepared stack still differ in the files below. This is a
**reconciliation inventory**, not an additional PR or a patch to apply wholesale. It includes
intentional extraction changes, newer master/review decisions, historical notes, extra tests,
and work requiring separate disposition. Everything remains preserved in the source snapshot.

- `ErrorListener` still has unassigned diagnostic-origin metadata and the parameter-hash
  collision change, with `ErrorDeduplicationTest`/`CompilerDiagnosticsTest`. These need a separate
  listener follow-up decision; neither is a prerequisite for compiling the extracted adapter.
- The lexer remainder includes removal of a duplicate cancellation check in a token-array iterator.
  Reconcile that with #685, rather than hiding it in the editor review.
- Listener field renaming, constructor-policy cleanup, documentation/inspection fixes and extra
  API/regression tests should be reconciled with #685 or a clearly named follow-up. The prepared
  branches keep #685's accepted field names and diagnostic calls.
- The source snapshot has an older Gradle wrapper/default-version setting than current master.
  The prepared stack keeps master's version; do not replay that downgrade.
- Runtime/JIT/DAP edits, unrelated modernizations and historical review plans/scripts are excluded.
  Duplicate-type and implicit-abstract regression fixtures remain available for separate review.
- New extraction-specific tests exist in the prepared stack and are mirrored into the main
  integration checkout; their absence in the earlier snapshot is intentional.

See [the reconciliation-only diff](build/reviews/remaining-branches-20261009/reconciliation-only.patch).
The counts below describe differences **from the prepared stack to the preserved snapshot**,
not forward changes proposed for publication.

<details>
<summary>Exact reconciliation inventory</summary>

| File | Snapshot difference + / − |
| --- | ---: |
| [changed-files.md](/private/tmp/xvm-review-source-20261009/changed-files.md) | 962 / 0 |
| [create-foundation-prs.py](/private/tmp/xvm-review-source-20261009/create-foundation-prs.py) | 493 / 0 |
| [demo.md](/private/tmp/xvm-review-source-20261009/demo.md) | 211 / 0 |
| [docs/errs-audit.md](/private/tmp/xvm-review-source-20261009/docs/errs-audit.md) | 2059 / 0 |
| [docs/errs-error-listeners.md](/private/tmp/xvm-review-source-20261009/docs/errs-error-listeners.md) | 744 / 0 |
| [docs/errs-integration-plan.md](/private/tmp/xvm-review-source-20261009/docs/errs-integration-plan.md) | 13312 / 0 |
| [docs/errs-lsp-process-lifecycle.md](/private/tmp/xvm-review-source-20261009/docs/errs-lsp-process-lifecycle.md) | 148 / 0 |
| [docs/errs-rebase-2026-10-05.tsv](/private/tmp/xvm-review-source-20261009/docs/errs-rebase-2026-10-05.tsv) | 591 / 0 |
| [docs/errs-upstream-issues.md](/private/tmp/xvm-review-source-20261009/docs/errs-upstream-issues.md) | 903 / 0 |
| [docs/errs.md](/private/tmp/xvm-review-source-20261009/docs/errs.md) | 3208 / 0 |
| [foundation-branches-report.md](/private/tmp/xvm-review-source-20261009/foundation-branches-report.md) | 345 / 0 |
| [gradle/wrapper/gradle-wrapper.jar](/private/tmp/xvm-review-source-20261009/gradle/wrapper/gradle-wrapper.jar) | - / - |
| [gradle/wrapper/gradle-wrapper.properties](/private/tmp/xvm-review-source-20261009/gradle/wrapper/gradle-wrapper.properties) | 1 / 1 |
| [javatools/README.md](/private/tmp/xvm-review-source-20261009/javatools/README.md) | 65 / 127 |
| [javatools/src/main/java/org/xvm/api/EmbeddingSupport.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/api/EmbeddingSupport.java) | 4 / 4 |
| [javatools/src/main/java/org/xvm/api/InterpreterControl.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/api/InterpreterControl.java) | 3 / 3 |
| [javatools/src/main/java/org/xvm/api/ModuleCompiler.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/api/ModuleCompiler.java) | 2 / 2 |
| [javatools/src/main/java/org/xvm/asm/Annotation.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/Annotation.java) | 1 / 2 |
| [javatools/src/main/java/org/xvm/asm/Argument.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/Argument.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/asm/ClassStructure.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/ClassStructure.java) | 14 / 27 |
| [javatools/src/main/java/org/xvm/asm/Component.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/Component.java) | 11 / 12 |
| [javatools/src/main/java/org/xvm/asm/ComponentResolver.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/ComponentResolver.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/asm/ConstantPool.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/ConstantPool.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/asm/DirRepository.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/DirRepository.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/asm/ErrorListener.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/ErrorListener.java) | 43 / 6 |
| [javatools/src/main/java/org/xvm/asm/FileStructure.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/FileStructure.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/asm/MethodStructure.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/MethodStructure.java) | 10 / 9 |
| [javatools/src/main/java/org/xvm/asm/OpVar.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/OpVar.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java) | 1 / 2 |
| [javatools/src/main/java/org/xvm/asm/constants/TypeCollector.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/constants/TypeCollector.java) | 2 / 2 |
| [javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java) | 22 / 26 |
| [javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/asm/constants/UnresolvedTypeConstant.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/asm/constants/UnresolvedTypeConstant.java) | 4 / 5 |
| [javatools/src/main/java/org/xvm/compiler/EvalCompiler.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/EvalCompiler.java) | 6 / 3 |
| [javatools/src/main/java/org/xvm/compiler/Lexer.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/Lexer.java) | 14 / 20 |
| [javatools/src/main/java/org/xvm/compiler/Source.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/Source.java) | 9 / 4 |
| [javatools/src/main/java/org/xvm/compiler/Token.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/Token.java) | 3 / 3 |
| [javatools/src/main/java/org/xvm/compiler/ast/AnonInnerClass.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/AnonInnerClass.java) | 3 / 4 |
| [javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java) | 3 / 3 |
| [javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/compiler/ast/CmpExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/CmpExpression.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/compiler/ast/RelOpExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/RelOpExpression.java) | 8 / 5 |
| [javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java) | 2 / 1 |
| [javatools/src/main/java/org/xvm/compiler/ast/TemplateExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/TemplateExpression.java) | 3 / 3 |
| [javatools/src/main/java/org/xvm/compiler/ast/TernaryExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/TernaryExpression.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/compiler/ast/TupleExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/TupleExpression.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/compiler/ast/TypeExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/TypeExpression.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/compiler/ast/UnaryComplementExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/UnaryComplementExpression.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/compiler/ast/UnaryMinusExpression.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/compiler/ast/UnaryMinusExpression.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/javajit/JitConnector.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/javajit/JitConnector.java) | 2 / 2 |
| [javatools/src/main/java/org/xvm/javajit/NativeTypeSystem.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/javajit/NativeTypeSystem.java) | 2 / 2 |
| [javatools/src/main/java/org/xvm/runtime/DebugConsole.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/DebugConsole.java) | 2 / 2 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/crypto/KeyStoreOperations.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/crypto/KeyStoreOperations.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/io/xTerminalConsole.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/io/xTerminalConsole.java) | 3 / 3 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/lang/src/xRTCompiler.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/lang/src/xRTCompiler.java) | 8 / 7 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTNameService.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTNameService.java) | 3 / 3 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTSocket.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/net/xRTSocket.java) | 2 / 2 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFileTemplate.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFileTemplate.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java) | 8 / 8 |
| [javatools/src/main/java/org/xvm/runtime/template/numbers/xBit.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/numbers/xBit.java) | 1 / 1 |
| [javatools/src/main/java/org/xvm/runtime/template/reflect/xModule.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/runtime/template/reflect/xModule.java) | 2 / 2 |
| [javatools/src/main/java/org/xvm/tool/Compiler.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/tool/Compiler.java) | 3 / 4 |
| [javatools/src/main/java/org/xvm/tool/Disassembler.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/tool/Disassembler.java) | 3 / 4 |
| [javatools/src/main/java/org/xvm/tool/Launcher.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/tool/Launcher.java) | 17 / 15 |
| [javatools/src/main/java/org/xvm/tool/Runner.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/tool/Runner.java) | 3 / 3 |
| [javatools/src/main/java/org/xvm/tool/XtcProjectCreator.java](/private/tmp/xvm-review-source-20261009/javatools/src/main/java/org/xvm/tool/XtcProjectCreator.java) | 1 / 1 |
| [javatools/src/test/java/org/xvm/api/EmbeddingApiCompatibilityTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/api/EmbeddingApiCompatibilityTest.java) | 160 / 0 |
| [javatools/src/test/java/org/xvm/api/EmbeddingRepositoryFailureTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/api/EmbeddingRepositoryFailureTest.java) | 70 / 0 |
| [javatools/src/test/java/org/xvm/asm/ErrorDeduplicationTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/asm/ErrorDeduplicationTest.java) | 98 / 0 |
| [javatools/src/test/java/org/xvm/asm/ErrorListenerBranchTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerBranchTest.java) | 4 / 2 |
| [javatools/src/test/java/org/xvm/asm/ErrorListenerCancelTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerCancelTest.java) | 155 / 0 |
| [javatools/src/test/java/org/xvm/asm/ErrorListenerMigrationTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerMigrationTest.java) | 2 / 2 |
| [javatools/src/test/java/org/xvm/asm/ErrorListenerSiteTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerSiteTest.java) | 3 / 1 |
| [javatools/src/test/java/org/xvm/compiler/CompilerDiagnosticsTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/compiler/CompilerDiagnosticsTest.java) | 146 / 0 |
| [javatools/src/test/java/org/xvm/compiler/CompilerDuplicateTypeTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/compiler/CompilerDuplicateTypeTest.java) | 74 / 0 |
| [javatools/src/test/java/org/xvm/compiler/LspRoundTripTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/compiler/LspRoundTripTest.java) | 153 / 0 |
| [javatools/src/test/java/org/xvm/compiler/ParserTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/compiler/ParserTest.java) | 18 / 1 |
| [javatools/src/test/java/org/xvm/compiler/ast/DeclarationProvenanceTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/compiler/ast/DeclarationProvenanceTest.java) | 1 / 1 |
| [javatools/src/test/java/org/xvm/compiler/ast/OrdinaryAstTreeCopyTest.java](/private/tmp/xvm-lsp-editors-20261009/javatools/src/test/java/org/xvm/compiler/ast/OrdinaryAstTreeCopyTest.java) | 0 / 42 |
| [javatools/src/test/java/org/xvm/runtime/TestNumber.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/runtime/TestNumber.java) | 16 / 16 |
| [javatools/src/test/java/org/xvm/tool/BundlerTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/tool/BundlerTest.java) | 5 / 5 |
| [javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java) | 3 / 3 |
| [javatools/src/test/java/org/xvm/tool/LauncherVersionTest.java](/private/tmp/xvm-review-source-20261009/javatools/src/test/java/org/xvm/tool/LauncherVersionTest.java) | 3 / 3 |
| [javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java](/private/tmp/xvm-review-source-20261009/javatools_jitbridge/src/main/java/org/xtclang/ecstasy/nType.java) | 3 / 3 |
| [javatools_utils/src/test/java/org/xvm/util/PackedIntegerTest.java](/private/tmp/xvm-review-source-20261009/javatools_utils/src/test/java/org/xvm/util/PackedIntegerTest.java) | 2 / 2 |
| [javatools_utils/src/test/java/org/xvm/util/SetTest.java](/private/tmp/xvm-review-source-20261009/javatools_utils/src/test/java/org/xvm/util/SetTest.java) | 1 / 1 |
| [lang/dap-server/src/main/kotlin/org/xvm/debug/XtcDebugServer.kt](/private/tmp/xvm-review-source-20261009/lang/dap-server/src/main/kotlin/org/xvm/debug/XtcDebugServer.kt) | 13 / 17 |
| [lang/dap-server/src/main/kotlin/org/xvm/debug/XtcDebugServerLauncher.kt](/private/tmp/xvm-review-source-20261009/lang/dap-server/src/main/kotlin/org/xvm/debug/XtcDebugServerLauncher.kt) | 3 / 6 |
| [lang/doc/plans/README.md](/private/tmp/xvm-review-source-20261009/lang/doc/plans/README.md) | 1 / 1 |
| [lang/doc/plans/idea-specific.md](/private/tmp/xvm-review-source-20261009/lang/doc/plans/idea-specific.md) | 47 / 18 |
| [lang/doc/plans/lsp-feature-tiers.md](/private/tmp/xvm-review-source-20261009/lang/doc/plans/lsp-feature-tiers.md) | 140 / 3 |
| [lang/doc/plans/plan-embedded-execution.md](/private/tmp/xvm-review-source-20261009/lang/doc/plans/plan-embedded-execution.md) | 182 / 0 |
| [lang/doc/plans/plan-ide-integration.md](/private/tmp/xvm-review-source-20261009/lang/doc/plans/plan-ide-integration.md) | 1270 / 64 |
| [lang/doc/plans/plan-tree-sitter.md](/private/tmp/xvm-review-source-20261009/lang/doc/plans/plan-tree-sitter.md) | 23 / 12 |
| [lang/doc/plans/vscode-specific.md](/private/tmp/xvm-review-source-20261009/lang/doc/plans/vscode-specific.md) | 10 / 2 |
| [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerBindingFactsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerBindingFactsTest.kt) | 0 / 90 |
| [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerPartialFactsTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerPartialFactsTest.kt) | 0 / 69 |
| [lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDocumentationTest.kt](/private/tmp/xvm-review-source-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDocumentationTest.kt) | 1 / 1 |
| [manualTests/build.gradle.kts](/private/tmp/xvm-review-source-20261009/manualTests/build.gradle.kts) | 29 / 0 |
| [manualTests/src/main/java/DuplicateTypesRunner.java](/private/tmp/xvm-review-source-20261009/manualTests/src/main/java/DuplicateTypesRunner.java) | 96 / 0 |
| [manualTests/src/main/x/archive/duplicateTypes/App.x](/private/tmp/xvm-review-source-20261009/manualTests/src/main/x/archive/duplicateTypes/App.x) | 23 / 0 |
| [manualTests/src/main/x/archive/duplicateTypes/App/util.x](/private/tmp/xvm-review-source-20261009/manualTests/src/main/x/archive/duplicateTypes/App/util.x) | 3 / 0 |
| [manualTests/src/main/x/archive/duplicateTypes/App/util/Taken.x](/private/tmp/xvm-review-source-20261009/manualTests/src/main/x/archive/duplicateTypes/App/util/Taken.x) | 1 / 0 |
| [manualTests/src/main/x/archive/duplicateTypes/DistinctScopes.x](/private/tmp/xvm-review-source-20261009/manualTests/src/main/x/archive/duplicateTypes/DistinctScopes.x) | 13 / 0 |
| [manualTests/src/main/x/archive/duplicateTypes/Inline.x](/private/tmp/xvm-review-source-20261009/manualTests/src/main/x/archive/duplicateTypes/Inline.x) | 7 / 0 |
| [manualTests/src/main/x/misc.x](/private/tmp/xvm-review-source-20261009/manualTests/src/main/x/misc.x) | 65 / 0 |
| [plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java](/private/tmp/xvm-review-source-20261009/plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java) | 6 / 6 |
| [plugin/src/main/java/org/xtclang/plugin/runtime/PluginRuntimeClassLoader.java](/private/tmp/xvm-review-source-20261009/plugin/src/main/java/org/xtclang/plugin/runtime/PluginRuntimeClassLoader.java) | 1 / 1 |
| [plugin/src/main/java/org/xtclang/plugin/runtime/impl/IsolatedDirectExecutor.java](/private/tmp/xvm-review-source-20261009/plugin/src/main/java/org/xtclang/plugin/runtime/impl/IsolatedDirectExecutor.java) | 3 / 5 |
| [plugin/src/main/java/org/xtclang/plugin/tasks/XtcCompileTask.java](/private/tmp/xvm-review-source-20261009/plugin/src/main/java/org/xtclang/plugin/tasks/XtcCompileTask.java) | 6 / 6 |
| [pr-roadmap.md](/private/tmp/xvm-review-source-20261009/pr-roadmap.md) | 404 / 0 |

</details>

## Recorded evidence for existing PRs

These details explain already-open PRs and their recorded tests. They are not another submission plan.

<details>
<summary>#683: tolerant platform lookup and host diagnostics</summary>

Gene correctly identified a regression in the first version: throwing on an incompatible
`.xtc` candidate prevented scanning a mixed-version directory or trying a later repository.
The revised PR at `902460194` instead preserves two separate responsibilities:

- **Lookup:** skip unreadable/incompatible candidates, continue searching, and return a usable
  module or ordinary absence. An obsolete binary alongside a valid dependency is not a failure
  of the successful lookup.
- **Inspection:** `ModuleRepository.getReadFailures()` exposes an immutable map of file paths
  and original I/O causes. Reading the snapshot performs no I/O; implementations without retained
  failures inherit an empty map.
- **Host policy:** #685 consumes retained issues after required-dependency resolution fails.
  Successful fallback and ordinary source errors must not acquire spurious repository errors.
  A skipped file is supporting evidence, not proof that it contains the missing module.
- **Recovery:** subsequent access retries failed candidates, including repairs with unchanged
  size/timestamp; successful retry or observed removal clears the retained issue.

The runtime bridge in
[xCoreRepository](javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xCoreRepository.java)
uses `loadModule` and translates absence into the language's conditional result. It need not
adopt a throwing lookup API or inspect the new snapshot. The failure-inspection API is additive;
retry/cache behavior and removal of stdout reporting are explicit behavior changes to review.

Recorded regression coverage: **12 repository cases passed with zero skips**, including mixed
formats, directory and single-file fallback, versioned lookup, retained causes, replacement and
removal. This proves those cases, not every possible platform workload. Keep these controls when
refreshing #683 and #685.

</details>

<details>
<summary>#685: embedding foundation and recorded rebase validation</summary>

The requested #685 rebase is complete and pushed as`c1a98a677`. Master's #684 commit is
an ancestor, the duplicate cherry-pick was dropped, and all remaining patches and the final source
tree are unchanged. The rerun passed **467 Java tests and 459 language-server tests**, with
42 and 3 existing skips respectively; the three #684 behaviors passed without skips.
XDK distribution and formatting checks passed. The current master diff is **111 files
(+5,447/−880)** while #683 remains unmerged. The old published head is preserved in local backup
branch `backup/embedding-685-before-master-rebase-20261009`.

#685 contains the listener contract, explicit destinations and named suppression, scoped reporting,
parser attempts, validation-state restoration, compilation outcomes and ordinary partial progress,
explicit structure diagnostics and cached TypeInfo replay, cancellation, and host source snapshots.
These are one complete host-compilation lifecycle; do not split them back into new PRs.

The prepared embedding-only patch is **105 files (+5,072/−844)** against its recorded prerequisite
base. That is a preparation measurement, not a claim about the live master diff after rebasing.
See the [preparation report and exact inventory](build/reviews/embedding-combined-20261009/review.md).
The report's publication-time master/PR status statements are historical; this roadmap gives the
updated submission plan.

</details>

<details>
<summary>#687: exact six-file scope and compiler regression evidence</summary>

**Published as [#687](https://github.com/xtclang/xvm/pull/687):** branch
`errs/compiler-type-correctness-20261009`, based directly on
master `da07a0be8`, in `/private/tmp/xvm-compiler-correctness-20261009`. This extraction is
independent of #683/#685 and leaves their API migrations out. Commit `f29cf28c0` is pushed; the PR
is ready for review by ggleyzer, cpurdy and thegridman.

**Exact diff: 6 files, +365/−29 lines.** Production accounts for +57/−29 across four files;
the two new direct-consumer test files add 308 lines. The compiler changes were extracted from
the existing implementation; the tests use master's current embedding API.

Review the [complete six-file patch](build/reviews/compiler-correctness-20261009/compiler-correctness.patch)
and [recorded test counts](build/reviews/compiler-correctness-20261009/validation.json).
The adjacent `test-results` directory preserves the baseline, fixed and integration JUnit XMLs.

| File | Extracted change |
| --- | --- |
| [NameExpression.java](/private/tmp/xvm-compiler-correctness-20261009/javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java) | Bind hidden generic type arguments in both the callable type and binary AST; retain concrete function register types; resolve atomic reference owners consistently for explicit, current, singleton and enclosing receivers; correct the enclosing-instance AST's owner type. |
| [InvocationExpression.java](/private/tmp/xvm-compiler-correctness-20261009/javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java) | Combine argument and return fitting validity instead of overwriting an earlier argument failure. |
| [SequentialAssignExpression.java](/private/tmp/xvm-compiler-correctness-20261009/javatools/src/main/java/org/xvm/compiler/ast/SequentialAssignExpression.java) | Preserve the validated result type and concrete atomic reference type in prefix/postfix binary ASTs. |
| [AssignmentStatement.java](/private/tmp/xvm-compiler-correctness-20261009/javatools/src/main/java/org/xvm/compiler/ast/AssignmentStatement.java) | Use the resolved atomic reference type for compound assignment AST targets. |
| [CompilerFunctionTypingTest.kt](/private/tmp/xvm-compiler-correctness-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerFunctionTypingTest.kt) | 12 cases: four bound generic function forms and serialization, complete-call controls, and direct fitting-contract checks for matching returns with valid/missing/excess/wrong-type arguments. |
| [CompilerAtomicEmissionTest.kt](/private/tmp/xvm-compiler-correctness-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerAtomicEmissionTest.kt) | 18 cases: typed serialized prefix/postfix results, enclosing owners, generic receivers, compound assignments and invalid-operation controls. |

The source-level failure controls alone cannot expose the argument-fitting defect: existing
diagnostics already reject the complete invalid program. The direct test additionally verifies
the protected fitting method's documented type-or-absence result, using actual compiler types
and literal fitting, without reflection or private field access.

**Focused evidence:** unchanged master fails 22 of these 30 cases; the extracted fixes pass all
30. The same 30 cases pass in the LSP mono-code branch. The fixes have been restored after the
baseline comparison, and the new tests are mirrored in the integration checkout.

**Full validation:** the forced Java and language-server suite run has zero failures or errors:
386 Java tests passed with 42 skipped; 462 language-server tests passed with 3 skipped. All 30
new cases ran without skips. XDK distribution assembly, root and language-server formatting
checks, and `git diff --check` passed. These checks validate compilation and serialized compiler
output; the new regressions do not execute programs in the runtime.

Switch audit controls and cached-warning replay are not production changes in this PR.

</details>

<a id="full-reference-modified-files"></a>

## Complete reference branch — existing files modified

**248 existing files changed.** These are modifications to paths already present in master. New files are excluded from this section; [review them in the final section](#full-reference-new-files).

[Inventory summary](#complete-reference-branch-file-inventory) · [Complete GitHub diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88)

<a id="full-reference-modified-compiler"></a>

### Compiler and embedding API — 99 files

#### `javatools`

| File | GitHub | + / − |
| --- | --- | ---: |
| [README.md](/private/tmp/xvm-full-testable-lsp-20261009/javatools/README.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-062915114db39781cbf4d7fa6c3e0ffe6553a19fba2a84280b4e8716ac2f43b5) | +464 / −1 |

#### `javatools/src/main/java/org/xvm/api`

| File | GitHub | + / − |
| --- | --- | ---: |
| [EmbeddingSupport.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/api/EmbeddingSupport.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9cce06fb7abee5c030ab339335af4c33b16d2714f957ea0def2f01628cddd7b3) | +872 / −29 |
| [InterpreterControl.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/api/InterpreterControl.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-10e976da8c9653688faa76e32e2e2575b339dd823b5b64d4a0d70078cb360b3f) | +3 / −5 |
| [ModuleCompiler.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/api/ModuleCompiler.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0cf0fe98900e3a9a32753bfab26fa1036de6b69663e4b64a28822bf7c5bb2725) | +47 / −20 |

#### `javatools/src/main/java/org/xvm/asm`

| File | GitHub | + / − |
| --- | --- | ---: |
| [Annotation.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/Annotation.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-45c1d785db6bbbfe1914b65061aacf3ec1af9cc5ce4f9ba0cde899bb8b636e90) | +2 / −1 |
| [ClassStructure.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/ClassStructure.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ec6ff1925935b8f61286a57d5e67ef03f6373430df7cc9e0b7a3551c86e745ac) | +4 / −1 |
| [Component.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/Component.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b1b0f53e3b2dba76bf5113f12bb66f389a5aceba097aba7b98e18c9402f53059) | +10 / −5 |
| [ComponentResolver.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/ComponentResolver.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1d1d55b2fdccb622131ee2d0155c8127105f432cc557bcf797e7b8d7820f1fd6) | +6 / −3 |
| [ConstantPool.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/ConstantPool.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-26fb572f46825df3f08f1391020819031166f5bba7a667e212dd7d603a84057b) | +5 / −0 |
| [DirRepository.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/DirRepository.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ba24f5289dcec044334cb2da78b98a1e7266657d4b066acb4669d192d5292cb2) | +32 / −7 |
| [ErrorList.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/ErrorList.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-623aa64bb788e3333c22bedacc9d1f3d51c3b4bbc21353b6b8a909fadcec4eab) | +90 / −14 |
| [ErrorListener.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/ErrorListener.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a2184675a70f5c0c4b5088110b210280a5aec465adc5c33e979fe88ef3e4a742) | +679 / −56 |
| [FileRepository.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/FileRepository.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fb6258e145dd79074c182f26a0e535dbef27892250beec12ef63b2283ae70b7d) | +40 / −27 |
| [FileStructure.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/FileStructure.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-53730e1f1f4a26ebf094d9aeab29a4a0bf88acfa40c834b6416b8daf5fd285c7) | +0 / −30 |
| [LinkedRepository.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/LinkedRepository.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c97aaddea57555ac153488ecc878257e32b6206dda90462f3f56c01de4d9b500) | +17 / −2 |
| [MethodStructure.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/MethodStructure.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-189c01938fe58a73e6e7c22816873b07dfcb9c4aa89ff990ed4011e6f1bc122b) | +9 / −4 |
| [ModuleRepository.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/ModuleRepository.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b14a8d5954910a2ac30bbb36f4d2458ed2d562aa391c0ad63b6bcf89b280a46a) | +20 / −0 |
| [XvmStructure.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/XvmStructure.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-138bbe812867d46490cb90bdcf75fa556fe84952ba6355adc7f8a5ba87005d69) | +3 / −31 |

#### `javatools/src/main/java/org/xvm/asm/constants`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ImmutableTypeConstant.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/constants/ImmutableTypeConstant.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1785c8fb64d9aad7dc0811bda67f7bbfebdad4fec7d793a45fce0c6411226025) | +2 / −1 |
| [MethodBody.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/constants/MethodBody.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bc40de99fdeba0605cebe39f144faf4aa9b97c189cce7a56861a82e54af69ed4) | +5 / −1 |
| [MethodInfo.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dfb596531d97ed1dfbc94bc564e6d3596a0eb7575dbb05a9d60a7f3379c8af6a) | +24 / −0 |
| [PropertyInfo.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-14a96d65cf23524bb86af14048f4916321af1701e588a2e623c6fa6ce01f8e0e) | +2 / −2 |
| [TypeCollector.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/constants/TypeCollector.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e044ee6d01e4d5da26fd2ab0736d8a73889d986c6cfa885aceef5539b15a225e) | +10 / −6 |
| [TypeConstant.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1cd27d8c2c1e9d56f8b9ded205226f246b9c8f3ad39704ce7a750e0e7776fdd5) | +114 / −40 |
| [TypeInfoReal.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/constants/TypeInfoReal.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-68aba073896737dd248154b94d016533396a58320665dcb13a916ab8f30ee319) | +4 / −1 |
| [UnresolvedTypeConstant.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/constants/UnresolvedTypeConstant.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8d878848e9a07e51395054baa5b1d12cd62a917b8d64b5c6fb7cc08904b36658) | +3 / −2 |

#### `javatools/src/main/java/org/xvm/compiler`

| File | GitHub | + / − |
| --- | --- | ---: |
| [Compiler.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/Compiler.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0a314d33ce9bd89d6e404dcf426cf308acc82e96e30eb5728206e4a4e987e288) | +34 / −20 |
| [EvalCompiler.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/EvalCompiler.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e6ec9c5bbf589b45ef88f1904d1022c1c9404f21c2a4f2404b61632b25ff6da2) | +6 / −7 |
| [Lexer.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/Lexer.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9f50d492eec4f7ac4babf5344942aa43963ba26d14bcf76e9f0ffb59feab8307) | +64 / −24 |
| [Parser.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/Parser.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-14a7392c5947eb672cab10c6007ceaf728b21b0947ea1c3954b7b16bfcc2daa3) | +1231 / −327 |
| [Token.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/Token.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5c064104831d494a33be66a5cab9107bae00bcc8656048dae88890441fbd15c0) | +7 / −6 |

#### `javatools/src/main/java/org/xvm/compiler/ast`

| File | GitHub | + / − |
| --- | --- | ---: |
| [AnnotationExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/AnnotationExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8e6db0af54b079e9c337784b640f5ac2d7337cb682e6dbdf5198630b4743ca04) | +4 / −1 |
| [AnonInnerClass.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/AnonInnerClass.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9bbf5ce8bb821391b18cc534f3886b8c69f7dc415d3815ae260c274053ab0778) | +3 / −1 |
| [ArrayAccessExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ArrayAccessExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e868eb052ed7efb04cd7e308b106b2fabf920346b2a3c24816567fef2df7fc97) | +43 / −33 |
| [AsExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/AsExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e8f5e8d187633ad82d741f3ee7662a02513b2e29582863543911dd884998bb07) | +5 / −2 |
| [AssignmentStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/AssignmentStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-02a0779b2b3e63ef7e65287a554f970617d126f51da6a7327243fbecd7ff4cc4) | +11 / −5 |
| [AstNode.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/AstNode.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8cc5c8089f703bac980be13f5a40345f46c1e110993564797d55ec38d6619aeb) | +129 / −17 |
| [CaseManager.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e7db7d4d708ab74220b04c6a9986c5155feacef89f857887b2f1f60d5ab8eb5f) | +10 / −5 |
| [CmpChainExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/CmpChainExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-903464ad2648be4964da5a369f798a8f92a8f9f4eb4c9d94d64af928b2c1d554) | +4 / −1 |
| [CmpExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/CmpExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-43f4f8d77d4b5515ff898d552431c8c801c27adeadda0ad6acac1f22ab6a3e6c) | +4 / −1 |
| [CompositionNode.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/CompositionNode.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-330658bc79cd107856c962afc4e39911225ce409b5095d2c0aff5e732c318934) | +6 / −3 |
| [CondOpExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/CondOpExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f84661b063c7e2c8bf6ed3076752977043a1e5925690a1d0a928955b281d51ac) | +14 / −1 |
| [Context.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/Context.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-615c0859c474d6f7da50987524e4e2948e041b89a42902855bd975f3585bc680) | +24 / −7 |
| [ElseExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ElseExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a1b926acd3ac5a972c7c1d5cb887587ecc95bdaf42983f034a3e9fb58a77c119) | +4 / −1 |
| [ElvisExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ElvisExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-55a213ae768507be4ea71762327b6cc3ff783b50c946b2541941794ffac4776f) | +10 / −5 |
| [Expression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/Expression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-61588c198efaa2abcc055e875512b98e764223ad7376f1d09617ce3e0e04f1c7) | +25 / −13 |
| [ForEachStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ForEachStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6aaba4b799b31ca9f75c31159a67e92fca433b69c35d80727057ac6d4ffc6856) | +26 / −12 |
| [ForStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ForStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dc80a02e0ed393bb8a4d725bcf2af9bd0ded1b6a1a3ac65309a5c469e77e811e) | +19 / −10 |
| [ImportStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ImportStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a07d8f5189591861ed9b58461e1ae70379086d5ff33ccb8958694c833a786874) | +22 / −0 |
| [InvocationExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/InvocationExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d7cb2bd3827e451214d2a4c2c45540d8d29b937103d0e0e1805187492855a872) | +84 / −22 |
| [LambdaExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3c45206b03db4e47ddebf5e97e5d593cea27571c46c062705b9001f94f47d23a) | +39 / −17 |
| [ListExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ListExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3ed53d877e58685b6e327806b6fea7197025bab67b957be44cbcde18508d9b19) | +4 / −1 |
| [MapExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/MapExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6749ed97e6095864422b036d5a44210474576825ebc8d992dfbce624e36d2013) | +4 / −1 |
| [MethodDeclarationStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/MethodDeclarationStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c6edadfc07522e9e8be1ddffe37c70b542c8f33d53e71bd87904255ac0d397d8) | +31 / −7 |
| [NameExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/NameExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f72e73f7df4fb0f206b19f0d654457d521f6b6a30545b9fa231274ceaa04a9b8) | +88 / −34 |
| [NameResolver.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/NameResolver.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9b08e2a17899d1186e8212b6338501fd36498228378adb3d321f6d2c7462e5c5) | +44 / −15 |
| [NamedTypeExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/NamedTypeExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f5f1a2996b24ccde91ab93011d707dd0006b67b828c379b104377a4dd99227f6) | +42 / −5 |
| [NewExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e5123f0dd62c15b0b572013ec550cd1cef5c806c40a0ba5c05aebca9f32a7060) | +123 / −31 |
| [NonBindingExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/NonBindingExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2a88177e561c0588a87f769d77a29db27762108cf8157fb19697c4504f208a40) | +4 / −1 |
| [NotNullExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/NotNullExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3dc073c93a30436a6b4ed7e3d117e92b74d0e975e0f0a1dafe7ca3810cc2f4a7) | +8 / −3 |
| [Parameter.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/Parameter.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b78ebaae619b49746a8313cf402ccc1916b6b122465f32a44fe09ef43f5bf834) | +23 / −2 |
| [ParenthesizedExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ParenthesizedExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-707317b9089fa190cb558ca81267b201c929c5c5508b1fca61c74da79e8f5d1c) | +9 / −5 |
| [PropertyDeclarationStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/PropertyDeclarationStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ee9bacf1874d6c5e5fcc363a4bc71b6c60424e973c9d160b04ed9aa103337ca3) | +33 / −4 |
| [RelOpExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/RelOpExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3cabcef97144a44b7af9201b13c679009aeeaabc6035899b4c6f2c13fe07b565) | +17 / −10 |
| [ReturnStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ReturnStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f04a41511dbdc5a64a1a40494f2ffabbc6571d26c59a9692f2c3f714c9088e80) | +9 / −4 |
| [SequentialAssignExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/SequentialAssignExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ed1372883f3996994ad4ebbcaf49d3f9cd1a6c9c0653da510f5fb0b3db9b40fc) | +6 / −3 |
| [StageMgr.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/StageMgr.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-552ad0ed86944bd9363019e217c5a7de8b5b70aac741b86a101411730fdc4e6f) | +57 / −13 |
| [Statement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/Statement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a75242078b9e633cf0ddf940b69f6e10014812a605fde8cf5220aaf7c1d6f524) | +18 / −2 |
| [StatementBlock.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/StatementBlock.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b0f6a3ca4b709bab2678423eda829501347f0c04bba3aa99298a8ba7b264523f) | +73 / −14 |
| [StatementExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/StatementExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a1e43e25ba7feebdb6e9fbc281a4b121190bc894e38dd95accbdcfb644572135) | +8 / −6 |
| [TemplateExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/TemplateExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5604e089b8a08e605d2fbbfb21b1b13c1d8ebfc384bf0c276b05a3b038d66931) | +7 / −2 |
| [TernaryExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/TernaryExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f09aebd481c0222283a34de29053430d912bd321c484b07afea0de92782ee365) | +15 / −8 |
| [TraceExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/TraceExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-46ab91934d7d5ec05222618be3b0db120790eaaf3cb15dc724001f6b9b06cc5a) | +4 / −2 |
| [TryStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/TryStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f09449f48fc62d8a26bf325cb1392d8de0a611a1496446532b12588fe351ea8c) | +18 / −9 |
| [TupleExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/TupleExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a3ed22d832b04d5d1706d578d9dd3b3d642fa1c31b107f9a50fb67c8a38cc426) | +5 / −2 |
| [TypeCompositionStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/TypeCompositionStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f5106a71467868139a1759981acb0610deb497d7ac79d7164ecbf31b8428bada) | +59 / −21 |
| [TypeExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/TypeExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-20531d009244321de3b585ea67d353b032fcd51dc321523d78b740218c2e0687) | +12 / −4 |
| [TypedefStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/TypedefStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b554709a2042c02baff53b2cf6a87e895bc185bb85f9f7fe09ce88eb8f848405) | +7 / −0 |
| [UnaryComplementExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/UnaryComplementExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c8cff3146ac0290d2a3d9835752b13d9d38bc6e6dcef1367b1a9759a8cb82db2) | +4 / −1 |
| [UnaryMinusExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/UnaryMinusExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d7e695a99a819a3af07c715fa01d2867ada6cc12731fb05c2052b5c1662a03fb) | +4 / −1 |
| [VariableDeclarationStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/VariableDeclarationStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ae7912149d28a6316d851de884b7766900c49be2e2f49b7ce3d485498b4d1f76) | +3 / −2 |
| [WhileStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/WhileStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-12c37b9fcd59138dddc2ff3ebc44edb1c587057ae8cf4e934e07aadac9bed8b0) | +20 / −11 |

#### `javatools/src/main/java/org/xvm/runtime/template/_native/lang/src`

| File | GitHub | + / − |
| --- | --- | ---: |
| [xRTCompiler.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/lang/src/xRTCompiler.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5276351d6f578cf53ccd7a2daf36b532b74763d506a5e3a8bbc83af934ca4545) | +3 / −4 |

#### `javatools/src/main/java/org/xvm/runtime/template/_native/reflect`

| File | GitHub | + / − |
| --- | --- | ---: |
| [xRTType.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-89ad52b8dd4e3d293bd9c5f34cdd74649d6fa1d7776b4b720593241d01237591) | +6 / −4 |

#### `javatools/src/main/java/org/xvm/tool`

| File | GitHub | + / − |
| --- | --- | ---: |
| [Bundler.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/tool/Bundler.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0ba0da5ebad0bfc6356909bae22bea9ed9e32033006c17b336337430d767e201) | +5 / −5 |
| [Compiler.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/tool/Compiler.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-91a33482c6496c2f232d7b118e622e8fa68ad75eb941b0941af68259b2ac09f2) | +31 / −17 |
| [Disassembler.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/tool/Disassembler.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6291e429e62ef197a2f6a4bb30f1ac939064262fd5f8417e0b8e03612d26d198) | +7 / −6 |
| [Initializer.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/tool/Initializer.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d8f9112cbc6b29917d5fb4314ebbdbdcc87108261d5f2a03a3033f04bb98cf4f) | +5 / −5 |
| [Launcher.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/tool/Launcher.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c806b1b605fbd43f9b33f008a25df092edb4c71119de79853e405a7db752b4ee) | +37 / −28 |
| [ModuleInfo.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/tool/ModuleInfo.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1dc368ee86ea1556a9280603547cb09e1f0fca7222d249234c0a280bbdf4a6f5) | +193 / −40 |
| [Runner.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/tool/Runner.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b3edb45cc42935ee766dc885f210b4fd1d46d919cfcc8aaf894de33cdbbacc49) | +7 / −7 |
| [TestRunner.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/tool/TestRunner.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-aaeebfe0a143682659aa254039624d9259b6444b94e6dc3e931887a05f2e97ff) | +5 / −5 |

#### `javatools/src/main/resources`

| File | GitHub | + / − |
| --- | --- | ---: |
| [errors.properties](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/resources/errors.properties) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ebe49ce798181bbbe767fa76c5864c1cfbab7220f27259514abd48be912ff46a) | +3 / −1 |

#### `javatools/src/test/java/org/xvm/asm`

| File | GitHub | + / − |
| --- | --- | ---: |
| [FileStructureErrorListenerTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/FileStructureErrorListenerTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d44686b27de792e4cb76189a43467d8c2ac25c19c9f85862fe75ded88f014709) | +22 / −28 |

#### `javatools/src/test/java/org/xvm/compiler`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ParserTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ParserTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-12f673197a34ed3188d5e1be23822cf5e66d4dc1b52d52393cc32ec01870ed49) | +2 / −2 |

#### `javatools/src/test/java/org/xvm/tool`

| File | GitHub | + / − |
| --- | --- | ---: |
| [BundlerTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/tool/BundlerTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-db22fbd645b1b66ec3fa4a00ef41a33c51c3d3ef0d1416a26300bb70e2a4651a) | +7 / −6 |
| [LauncherErrorHandlingTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/tool/LauncherErrorHandlingTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4bf3f3b3367e02bf124708ee594987954fdbcf8082b64998f987dd62f34cb174) | +45 / −28 |
| [LauncherVersionTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/tool/LauncherVersionTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6ed005670c92c224968556822c789e82986565e272b13ef11090f276fb4965cb) | +6 / −3 |
| [ModuleInfoTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/tool/ModuleInfoTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-92ac72fe419f2a66ba43b270c5fea718bb8ccf536023217fd98505dda2807e5d) | +67 / −22 |

<a id="full-reference-modified-server"></a>

### LSP server — 68 files

#### `lang/lsp-server`

| File | GitHub | + / − |
| --- | --- | ---: |
| [README.md](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/README.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-338b69cc04cf43e021dd03bd01f080b8616e9ef7bdf8e7b4c5c277227e9cecc2) | +468 / −77 |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-de797a995e110763cd87b7c570ca667f895e1a1a58a68a79133d5428a72107f1) | +134 / −25 |
| [editor-setup.md](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/editor-setup.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-54e630bd77d93ddeb651d6b0286125173a7a039864d5bd7dc522e5cca459e6cc) | +5 / −5 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter`

| File | GitHub | + / − |
| --- | --- | ---: |
| [AbstractAdapter.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AbstractAdapter.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f48095c4881f7e8ccab32cd5976f5cc2df7fce94f44770d596e2faf9ac9e0361) | +63 / −37 |
| [Adapter.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/Adapter.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3e4a4b3f00f945b70fc3d72ce0e04532cfe2ee018cda41dd5aca226c1f5c6033) | +338 / −162 |
| [AdapterCodeActions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterCodeActions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c7583b150b13d22088d39286e5d573cf11345f3e18e508043db1c3ffc1890d10) | +50 / −31 |
| [AdapterFormatter.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterFormatter.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-514110568a1f631a0308fdfcf38b6722abc1119c494e2c9beb68505da3393f8b) | +132 / −150 |
| [AdapterModels.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterModels.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5ae9640cdfbacd9a1e0e3298ce84ed6f081a7f6806ab989703dc2f944ea284ad) | +68 / −57 |
| [AdapterTree.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterTree.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-623eda5e6b6d45d2b61c1aaa1e765be72fafc565a50857b915013f8b0d838366) | +8 / −9 |
| [FormattingConfig.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/FormattingConfig.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cfa7375d36778e8d4f7ba60d137d40751404844226869e8bd0d3c6dcae0d9691) | +11 / −14 |
| [LanguageConstants.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/LanguageConstants.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-209d219871239d9741299d79437c32f3ac4f4476890812e15e0e3135aff7473c) | +22 / −12 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/mock`

| File | GitHub | + / − |
| --- | --- | ---: |
| [MockAdapter.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/mock/MockAdapter.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2f8f8cc6cc321af40a2e6449f6f534772f323702fd35caf033f4b00e4d85409d) | +89 / −39 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/treesitter`

| File | GitHub | + / − |
| --- | --- | ---: |
| [TreeSitterAdapter.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/treesitter/TreeSitterAdapter.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4941bd77b092f52e10188fdc0a0ab107d56aff2f6dba3a3d761c6871165f1c32) | +395 / −221 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XdkAdapter.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7a8e3df9dd52456ff88cec6dddbbf8626ec65d18504253f5c1ea8fe8d75c0186) | +2448 / −69 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/index`

| File | GitHub | + / − |
| --- | --- | ---: |
| [IndexedSymbol.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/IndexedSymbol.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e4c698e0e518435de7f216a717d9bd9f1ad0e0318faca43cfcf1ba9e972910f0) | +6 / −6 |
| [WorkspaceIndex.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/WorkspaceIndex.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f88934e983c8dcb35cc500b521c87eddf46cf8ce4281ec7efe3f78b84f99b822) | +23 / −22 |
| [WorkspaceIndexer.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/index/WorkspaceIndexer.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d24df6294007740c7c773a2ad49ad13b8292c8445e3b166c4d3d6d2ce1bc3608) | +147 / −70 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/lexer`

| File | GitHub | + / − |
| --- | --- | ---: |
| [TemplateScanner.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/lexer/TemplateScanner.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8e905c0e68544ace930e89c8c3f8d8d86204b2d310426d1be185328a4535167f) | +14 / −11 |
| [TemplateScannerToken.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/lexer/TemplateScannerToken.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-68eaa31e3802ba2d31e6dcdebe6ea59be61a5e89d29fdf68db7fc33b6fbf4394) | +9 / −8 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/model`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CompilationResult.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/CompilationResult.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8aeaa4e930823a218ffe869e403278edcc80699bf946402714fe391655262f9c) | +11 / −3 |
| [Diagnostic.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Diagnostic.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c3ed512743153a2a8492398056b836fa1b88d8670e00f87e831816b96ce80213) | +4 / −6 |
| [Location.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Location.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-31dde030f061da74f3498586b6bd4936a51a4918f42be062836d2523af4df3e3) | +1 / −3 |
| [Lsp4jConversions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/Lsp4jConversions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-079fcb3186aad313be9e29bdb6cd23f1934a8b82d2ab34d053e44c2d997fd437) | +13 / −4 |
| [SymbolInfo.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/model/SymbolInfo.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3f8c7d79e2619ef9ed8346338fad000a1ac97dec60ad59115d171eb92204e9ff) | +4 / −5 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/server`

| File | GitHub | + / − |
| --- | --- | ---: |
| [LspJsonOptions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/LspJsonOptions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cf2c2619e229e06cc89eab041f48be32dad55abe3193629bb60824c948ec3a95) | +66 / −23 |
| [SourceRootResolver.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/SourceRootResolver.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4f624d86dd082733d1fa2b15c8381e43459709df4dcf32cbe94d24105ff8d354) | +15 / −18 |
| [XtcLanguageServer.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-72f6fb7a99d83027ee21457e3a2ba37af9f0ccefa0336ac76dcdd337fe097709) | +1049 / −290 |
| [XtcLanguageServerLauncher.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServerLauncher.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-72f871d3d13fe171a6d8173ee323b7a348d4ae9c44fa42db8b82785d75ee9dfc) | +99 / −42 |
| [XtcTextDocumentService.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcTextDocumentService.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dfcb0f292037294c0496697f319a198ea5a13f5bd67326be511e3194fb1292d5) | +1557 / −230 |
| [XtcWorkspaceService.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcWorkspaceService.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-60dc08abe0831632771d430ffcd3d20e9c9ded1f0931d857433d40b87292e107) | +83 / −31 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter`

| File | GitHub | + / − |
| --- | --- | ---: |
| [Platform.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/Platform.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b864095be426e4b3fb751b8034dcb73faaa7e2b99fc2619fe9d0aa4c3aa05c18) | +10 / −6 |
| [SemanticTokenEncoder.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/SemanticTokenEncoder.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e12cf3e8694e60868a83278e356510889af8d17b314272eb2502c6733f3bae9c) | +44 / −27 |
| [TreeSitterLibraryLookup.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/TreeSitterLibraryLookup.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-16e3173ae62ebfbd3a476e7d11fff68b9c96785c82f2d23c3eb389f068438d77) | +2 / −2 |
| [XtcNode.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcNode.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-465900bb85415d80bedd5613eb5db5bf6038b4d279158cdc0809a7d8cf280c2e) | +205 / −243 |
| [XtcParser.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcParser.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e235abac17632730a3dbcd865978a897c992c295011957a3b6a7c2356f4d4dd7) | +175 / −175 |
| [XtcQueries.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcQueries.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-96c750ca5adefad92084ca258ef226e8709f7823f2a9efab67b0ae533df64e75) | +17 / −25 |
| [XtcQueryEngine.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcQueryEngine.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-305af297f938c705b98509d789298e6cfa89ef994e903488a4a16e56173b87f8) | +91 / −71 |
| [XtcTree.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/treesitter/XtcTree.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-df9bb3d9c1ade8d64fcd20283635cd5b573e84d7204215e9282262f7a97af9b6) | +66 / −70 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/util`

| File | GitHub | + / − |
| --- | --- | ---: |
| [WorkInProgress.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/WorkInProgress.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-909aee032b7a0f8059fa2bf143b7284b3715520093032201038890799ecd5843) | +4 / −2 |

#### `lang/lsp-server/src/main/resources`

| File | GitHub | + / − |
| --- | --- | ---: |
| [logback.xml](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/resources/logback.xml) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9b5f613a6b8567cb8d0bff1051eb519cf0e8601308e6af3c219c384113b0de75) | +27 / −8 |

#### `lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CodeActionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CodeActionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5111a5b7dce1e6b698242f314fca3f7dd4e74d0c7c32bb6d50567ea6c9d95719) | +6 / −6 |
| [CodeLensTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CodeLensTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3420b080b236e3cea0342a9128d25826758544e64618c423552e8231ceb02bf1) | +1 / −3 |
| [CompilerConsumerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerConsumerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-28bcda4bd2395077b835b54ede5c1be208c08e098acc57926739b0313b1e2111) | +7 / −2 |
| [CompilerTestSupport.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerTestSupport.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d3299a13171682beb85d38d78463334f790f36831955088cd91838ab8c70e28c) | +3 / −22 |
| [CompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-03d1a31917923ab7952c25d67c9ae7c558758fb6939583e0c758506d17801847) | +48 / −45 |
| [DocumentFormattingTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/DocumentFormattingTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4ebebfd2cf1b819222150e7fed4a27eb4d6e074890e92f122e6eabd84fc80316) | +13 / −15 |
| [FoldingRangeTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/FoldingRangeTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f09887e4218d4200110274410864a7f056135371000d4ab2e7493e5614ea008a) | +15 / −11 |
| [FormattingTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/FormattingTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0392187d59506de4fc7cbe51e5f84f2688f6f735922390ae51523323dad7d3eb) | +13 / −13 |
| [InlayHintTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/InlayHintTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c2ee616d2e6e8e5d81837aee01a58ebb3fe5c9b02a132218bfa4ca98b9ee8dd5) | +16 / −48 |
| [LinkedEditingRangeTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/LinkedEditingRangeTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4f289f0fa12c55f924b2fc772904a6dd86f8232f19ae987025e3b1565d786104) | +2 / −2 |
| [MockAdapterTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/MockAdapterTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ebcc5661e5097aaac9c0314b40645c19b2db754337ee609d9bac1e71bc4e7466) | +17 / −14 |
| [NavigationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/NavigationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e7edf835246412e208c7bf501d1fae7dd7854cd88b2f19fdb6db9bf5dd6799f5) | +81 / −60 |
| [OnTypeFormattingTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/OnTypeFormattingTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dbe62ce83a315b3faf082d5c3f32a95bcf34d99fde8c300e722581a2b3ca3b38) | +12 / −11 |
| [RenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/RenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dea055f42598baae0b548d46312dbf287ae79fc978e4755acd99a70ede3a4bf8) | +14 / −16 |
| [SelectionRangeTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SelectionRangeTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d0630cead244cab116956342c26d52aa22b2310bc4f4511cb01a686a3bb6360a) | +34 / −22 |
| [SemanticTokenTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SemanticTokenTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4d973dabb21e6ca4dc610e29705d89dd36bf78034801a17881256306cf9a0b30) | +33 / −21 |
| [SignatureHelpTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SignatureHelpTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cbf9853afdf553cfca57d5bff8ba794081a2f6c122aa6e7890de92e2fc5035e3) | +91 / −59 |
| [TreeSitterAdapterTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TreeSitterAdapterTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-eb341d8ca224ca9af9d96932c60add462c5fc55a0db93451745134f542adb760) | +165 / −210 |
| [TreeSitterTestBase.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TreeSitterTestBase.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-11483a8ee62c0a7b17a5e1f57c93f7777ebda9bf4308de806154d4b7a708de3e) | +9 / −10 |

#### `lang/lsp-server/src/test/kotlin/org/xvm/lsp/index`

| File | GitHub | + / − |
| --- | --- | ---: |
| [WorkspaceIndexTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/index/WorkspaceIndexTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-51674457867680241c5bf474a4db36814c7ff72b78510a4298575449ebf4739d) | +17 / −5 |
| [WorkspaceIndexerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/index/WorkspaceIndexerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fab2a7d77d1f995ec010fdc22ee348a0d1a243cf665be4f8250210f8b3a57618) | +142 / −13 |

#### `lang/lsp-server/src/test/kotlin/org/xvm/lsp/lexer`

| File | GitHub | + / − |
| --- | --- | ---: |
| [TemplateScannerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/lexer/TemplateScannerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7d780cb3694b4fac54ac48e0fc31ce6966630daa8491cf83d0d134ca09e5a869) | +6 / −6 |

#### `lang/lsp-server/src/test/kotlin/org/xvm/lsp/server`

| File | GitHub | + / − |
| --- | --- | ---: |
| [FormattingConfigRoundTripTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/FormattingConfigRoundTripTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-afedd16cb2bfff1eb47f0c40385fb22b66fb174fa4652e2aa64e69e33fb3899e) | +49 / −37 |
| [LspIntegrationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LspIntegrationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dcc0003c85a6f99d716d078296a64a64391e659c5daf7a8e0e51dfea9453a2f8) | +43 / −38 |
| [LspProcessLifecycleTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LspProcessLifecycleTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fd9c0684f634a331a21882a6f57a595ebc2b9bce8feb835d1797be0fc0fc46d8) | +42 / −30 |
| [SourceRootResolverTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/SourceRootResolverTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f8cca8f5f8711980b4d371d303564f67ac73bdd825ade7ffc82a6fd36e56fbe2) | +20 / −14 |
| [XtcLanguageServerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XtcLanguageServerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8f892cfd0633c480f462fde646d8536d3db03148a06acffed12b0e4f4f22cfed) | +47 / −28 |

#### `lang/lsp-server/src/test/kotlin/org/xvm/lsp/treesitter`

| File | GitHub | + / − |
| --- | --- | ---: |
| [SemanticTokensVsTextMateTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/treesitter/SemanticTokensVsTextMateTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-18b8f4fdbc5eff6dccd49c7465d4100c18de80b085cb333b61e4ea48509295ff) | +119 / −106 |

<a id="full-reference-modified-intellij"></a>

### IntelliJ plugin — 30 files

#### `lang/intellij-plugin`

| File | GitHub | + / − |
| --- | --- | ---: |
| [README.md](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/README.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-43035f6755e4ec8ee131bd13e7c4ef35a860157929416ef4d49fd3216e1fbcb0) | +199 / −17 |
| [TESTING.md](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/TESTING.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4c79f4e1e1e1baa0f95300e4048688cb522fdc5a61d372c1a814d1e6cdcac6e7) | +139 / −37 |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-506ab153738a4379822c9fddb8bfd4529f3b061d7de5886b1823d4327b7e6776) | +120 / −10 |

#### `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea`

| File | GitHub | + / − |
| --- | --- | ---: |
| [PluginPaths.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/PluginPaths.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c7369a1c6192ebbd278dc392a1931dbbe27a8d9af54ddce8d0512f90309a869a) | +28 / −18 |
| [XtcCommenter.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcCommenter.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-addd4666e98de9381805ffb0cb4b941fcc07d32792436ef9105d811858878e46) | +11 / −11 |
| [XtcFileType.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcFileType.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b1f0171f56531dfe336af4af4cc4dfcd5edb76a481ca8bb3a7258f234d6aeb79) | +4 / −4 |
| [XtcIconProvider.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcIconProvider.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5b835c83415b53cf9a14d375106908eb415ed858e92c7c2c32c4886ddaa8b21f) | +3 / −4 |
| [XtcIntelliJLanguage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcIntelliJLanguage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-236c75c9c50a2352b584044e81452e5c4fd234582cccfcca822f6d448b1fb901) | +8 / −9 |
| [XtcTextMateBundleProvider.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/XtcTextMateBundleProvider.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e0ac74098a1fbd1cba4d487b055f940142c7bce33e10417cc9a836a0dc2fbe87) | +8 / −16 |

#### `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcDebugAdapterFactory.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/dap/XtcDebugAdapterFactory.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dddeb41a1c6341e27874991b175a3c92837bb3439c4c27ce9afae8a150478b3e) | +21 / −21 |

#### `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcEditorStartupActivity.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor/XtcEditorStartupActivity.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bbb8001c9a161114e06fa8d028da5c36f8a392196d8cdae1e24b9924c09e42e2) | +5 / −3 |
| [XtcEnterHandlerDelegate.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/editor/XtcEnterHandlerDelegate.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-104b7f8c9b153962c0375e41ea65d23a37a2a25ae8cd777badc8924fb5746778) | +16 / −6 |

#### `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ConnectionLifetime.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConnectionLifetime.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1f3202ed1e816976e50b225c36638e1e4de6e0c44f20fe9a0149653f87d48cac) | +8 / −2 |
| [XtcLanguageClient.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageClient.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6e0071e9e056787b1c5a9be6cb3e491b6d0620d7d8d6126f7b9e3f05a4f9d7c2) | +207 / −31 |
| [XtcLspServerSupportProvider.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLspServerSupportProvider.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-13bb1f546b1ca9978c6a748bf5a287f2e29f408d02ccd44ca0607f875f27b57c) | +120 / −44 |

#### `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcNewProjectWizard.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project/XtcNewProjectWizard.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c07b038940b780f385e0a480589674382c4f6763081d448d61260389a3441a0a) | +4 / −3 |
| [XtcNewProjectWizardStep.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/project/XtcNewProjectWizardStep.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fcd58aa154e21b0bb9f1a483214f6938dcea96e031f400d06e8165c6803a9626) | +14 / −8 |

#### `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcRunConfiguration.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfiguration.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-81540ba07411fea21315c125584cc942b3959f8a2350cc3d2fc584c416ef1a80) | +5 / −8 |
| [XtcRunConfigurationProducer.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfigurationProducer.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cf153e6ba4a899afb0ab66b30bbf3230014bfd3868ec1fe5b7ed442195e26b79) | +4 / −4 |
| [XtcRunConfigurationType.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/run/XtcRunConfigurationType.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8a3f838fd02810bceb55d0f58353ced04fc620a924611e3197925cc07aca03e4) | +4 / −7 |

#### `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/style`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcCodeStyleSettings.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/style/XtcCodeStyleSettings.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d84e08f54157024b7f1b04bda303cd20e5b7cf7cffd54610db28faeeea8afc29) | +6 / −6 |
| [XtcLanguageCodeStyleSettingsProvider.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/style/XtcLanguageCodeStyleSettingsProvider.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-34e325b5ecc113abfbf5164328a19e4851250783cbcd509b97c7bae63c44c689) | +4 / −4 |

#### `lang/intellij-plugin/src/main/resources/META-INF`

| File | GitHub | + / − |
| --- | --- | ---: |
| [plugin.xml](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/resources/META-INF/plugin.xml) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7f5e5b121fe0dcac93c24117a85407f493bcb382f21d0abccf23c1246ba5fb70) | +62 / −3 |

#### `lang/intellij-plugin/src/main/resources/liveTemplates`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XTC.xml](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/resources/liveTemplates/XTC.xml) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-eb270344f97d2a6a9ff2ac4bc59438820af0939b766856462e50db78f5530079) | +1 / −1 |

#### `lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ConnectionLifetimeTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConnectionLifetimeTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bdfae9f8535fdb58a57c98eb171a2bc95c9deb1ce992f90cb83cf8ff8f05dff6) | +13 / −6 |
| [ConnectionProcessTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConnectionProcessTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d26a321d31b1fa51619b4c208620d48b6845cfff35a44d48d0243d342f36e1ab) | +16 / −14 |
| [LspServerJarResolutionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LspServerJarResolutionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ad389cc283e10bfeed1831561c727d3fd793dde07d269adf624143129190f965) | +9 / −11 |

#### `lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest`

| File | GitHub | + / − |
| --- | --- | ---: |
| [BundledResourcesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/BundledResourcesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fd404b83e41e012fee33be677c97c18dc8cbb5daacdd1677c80676fc06ce7e7d) | +14 / −14 |
| [LiveTemplateRegistrationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/LiveTemplateRegistrationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ccddfcc248fa97042fd65a546ab7aced4ef74fc54150932ef8c3a65cde69f2b4) | +12 / −11 |
| [PluginManifestTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/manifest/PluginManifestTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-62b8fc2eb880c79fba1be7d628eb158ed669c9c2e11c52d58f3cf0acda9ee5a0) | +57 / −14 |

<a id="full-reference-modified-vscode"></a>

### VS Code extension — 20 files

#### `lang/vscode-extension`

| File | GitHub | + / − |
| --- | --- | ---: |
| [.gitignore](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/.gitignore) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c2a0f77005d7a0bdb5a8df4e6f1dc1585f175d79c5f3180ef40c7d67dde6b6bc) | +1 / −0 |
| [README.md](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/README.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dbf543b17125723cbc24d85986f17cfe62d33fc477c76a029cec52fc3918f29a) | +220 / −11 |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-72d70a97ba83357b6138e2ff363a126c23cbdc56138c232a546b0e50ff4e96c3) | +256 / −170 |
| [package-lock.json](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/package-lock.json) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-92210871dc49e23c48766ef7f6ce4f19629060307b0115fd15f575c8f42f3ea9) | +82 / −26 |
| [package.json](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/package.json) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-03fc9f87a7e7a8faf7d3a2a2d2d128e61f79b979a5d02938d729bffce83002f2) | +369 / −64 |
| [tsconfig.json](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/tsconfig.json) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8b49d335d26e850fe1a7e5cc74d8d3f815f1780d1da7f7252497c16bf99e5626) | +3 / −2 |

#### `lang/vscode-extension/scripts`

| File | GitHub | + / − |
| --- | --- | ---: |
| [bundle.cjs](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/scripts/bundle.cjs) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8bc53f5ec9139f97f2aaa1d63125716a0cee832571022a59a797d62aada08370) | +2 / −2 |
| [run-vscode-tests.cjs](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/scripts/run-vscode-tests.cjs) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3e3afc8b440896d39fa948aab4fdfb5374ec2987702bc6f32bfd83a476474b15) | +2 / −2 |

#### `lang/vscode-extension/src`

| File | GitHub | + / − |
| --- | --- | ---: |
| [commands.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/commands.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d7048af8a28edf5be3d6423e35871b2bd269c6a5409de781bfcf50031a31fd49) | +15 / −1 |
| [debug-adapter.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/debug-adapter.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-36f6fb15098fe88dc39eadaeff7cd7f639af6eb5bdcb9ec5a07a8ba4b89ae1c3) | +1 / −1 |
| [extension.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/extension.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b90b945dc44d9b2ea77701d1650e14989e040d463e3de52ca24427887fd11337) | +86 / −20 |
| [java.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/java.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-653779ccdd3c9cb94d87ac3c8b88f33f8a71464df57ad27164f41597923082ea) | +4 / −1 |
| [lsp-client.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/lsp-client.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-51728521745e73ceaf99e4d611762877754c83b6f0f333ca58842eb4bf862e46) | +181 / −49 |
| [status-bar.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/status-bar.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b38bf6be4f1341b8a21ed6e95c7ffd07505ca475ac057abf294422945227e592) | +11 / −10 |

#### `lang/vscode-extension/src/test`

| File | GitHub | + / − |
| --- | --- | ---: |
| [runTest.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/runTest.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-898441d8b7ddcdb8f9ec130a4844d466eaa6e1601745afd292919e793512567a) | +115 / −45 |
| [vscodeCache.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/vscodeCache.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e423067229623b16fe35421d77df1969fa2e649c961bfa8ef8667604a613301f) | +23 / −2 |

#### `lang/vscode-extension/src/test/suite`

| File | GitHub | + / − |
| --- | --- | ---: |
| [activation.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/activation.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8603169dc4d69b337c4f96144a631895b4793c1cc0f5156087c8d331513a9007) | +17 / −0 |
| [extension.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/extension.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0beb1cd46915c84621d06b7e941e5ad463da6d71be6a67d0067022d567a9bbc4) | +2 / −5 |
| [index.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/index.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6de0708c724d96eed3d0fc3ceb7b8720a43d1004831c89f25fdb91ee0523a135) | +6 / −1 |
| [lsp-startup.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/lsp-startup.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-12fa4a85e0f10e99ea5d3b8f996a2871ea920929af6df2663ced7ce55b960c78) | +88 / −21 |

<a id="full-reference-modified-fixtures"></a>

### Shared fixtures — 0 files

No files in this category.

<a id="full-reference-modified-grammar"></a>

### Grammar and syntax tooling — 18 files

#### `lang/dsl/src/main/kotlin/org/xtclang/tooling`

| File | GitHub | + / − |
| --- | --- | ---: |
| [LanguageModelCli.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/LanguageModelCli.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e267fdfac2742bc4d502d45a061a3f94558c2492a31a90e98c67a70ecb849b8f) | +4 / −4 |
| [XtcLanguage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/XtcLanguage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ed2365481e3d52589ee160ff0e9d3e95f62cadecfe27bc2c363304a49345b3ea) | +60 / −16 |

#### `lang/dsl/src/main/kotlin/org/xtclang/tooling/generators`

| File | GitHub | + / − |
| --- | --- | ---: |
| [EmacsGenerator.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/EmacsGenerator.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7271cd4c008320bca5aeeeb5b8424dad915d3ce9433c87a1063b8c6849c86148) | +16 / −8 |
| [GeneratorUtils.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/GeneratorUtils.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bb68b6786afe9feb2f4ab4c373c4c114cb5301e7e94b53e1016b0a6e5169650c) | +15 / −24 |
| [SublimeSyntaxGenerator.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/SublimeSyntaxGenerator.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5b534fd518f176f37b5569a6ce44670478e9978fd2cc697c2c1f42b3909bf236) | +4 / −3 |
| [TextMateBundleManifestGenerator.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TextMateBundleManifestGenerator.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dca81b746498daec9bfba6cd86c1e8eb1cd5f53f92d05fd3b3e3f409d87b0422) | +2 / −1 |
| [TextMateGenerator.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TextMateGenerator.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cc47b2e41da955526f8c9d35fcfed5eaf22923feeabb48c8aec4da311faf80f5) | +49 / −11 |
| [TreeSitterGenerator.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/TreeSitterGenerator.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e4ac8d627ec16ccd06a7b378fb0c2c4372f26717de7c9d51b01e46460d02b3e5) | +20 / −23 |
| [VimGenerator.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/generators/VimGenerator.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-28f20ee1cd9a30619846eb838a51e891fefed27c31dffd627ac3de3dba72f6a2) | +16 / −12 |

#### `lang/dsl/src/main/kotlin/org/xtclang/tooling/model`

| File | GitHub | + / − |
| --- | --- | ---: |
| [LanguageModel.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/model/LanguageModel.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-82c6a92ef0b2905c1b579c58fa1046dbedf70d88fb16fe47a6dda64c7878d927) | +72 / −158 |

#### `lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CCodeDsl.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/CCodeDsl.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7d1babed9e3ad5ed671515814ac2174db8ab6827f9ae3de5084d654d0f4191c6) | +4 / −11 |
| [ScannerCGeneratorDsl.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/ScannerCGeneratorDsl.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-74bc724d97a549ca6d1cd6ad9de9df7505b681a43b96b92a2b936075f2c1af67) | +99 / −43 |
| [ScannerSpec.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/main/kotlin/org/xtclang/tooling/scanner/ScannerSpec.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-413638531c81904f126a59bf71fef35f1d5214fb7cacd2cf69b4c61efabe22d2) | +6 / −9 |

#### `lang/dsl/src/test/kotlin/org/xtclang/tooling`

| File | GitHub | + / − |
| --- | --- | ---: |
| [DslPowerShowcaseTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/test/kotlin/org/xtclang/tooling/DslPowerShowcaseTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6c738bd37edbe49e1795b425fdecb1fd29665341064a42d0c3f502b5ea0f457c) | +4 / −1 |
| [LanguageModelTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/dsl/src/test/kotlin/org/xtclang/tooling/LanguageModelTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8a3c962f11cfb79d77af40d4afcb9d7e2fd0d2b3a6e4fa3e8780586f0653e4ae) | +30 / −11 |

#### `lang/generated-examples`

| File | GitHub | + / − |
| --- | --- | ---: |
| [xtc.tmLanguage.json](/private/tmp/xvm-full-testable-lsp-20261009/lang/generated-examples/xtc.tmLanguage.json) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3f70615b0cad9e264db2b52738b0c3e313aad051fb9234010631e4180387679f) | +1 / −1 |

#### `lang/tree-sitter`

| File | GitHub | + / − |
| --- | --- | ---: |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/lang/tree-sitter/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-95887c36badb17b9508a890aacbd2ef5e796d2dd1ad577be202103b875c8cab2) | +965 / −866 |

#### `lang/tree-sitter/doc`

| File | GitHub | + / − |
| --- | --- | ---: |
| [functionality.md](/private/tmp/xvm-full-testable-lsp-20261009/lang/tree-sitter/doc/functionality.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2701d72306fb7a87d7fd1ec69e7655671be71970d7b7b2165384c45ab4a46c4f) | +1 / −1 |

<a id="full-reference-modified-gradle-plugin"></a>

### Gradle plugin — 3 files

#### `plugin`

| File | GitHub | + / − |
| --- | --- | ---: |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/plugin/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-126180cb2041a63aa5868495a1fda5bff4cb7fde6e324c8d11b164a63127af0e) | +4 / −0 |

#### `plugin/src/main/java/org/xtclang/plugin`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcProjectDelegate.java](/private/tmp/xvm-full-testable-lsp-20261009/plugin/src/main/java/org/xtclang/plugin/XtcProjectDelegate.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-36da7022dea05d59d606551dc1cc30f38421bd55cafcfba3f1ba0134fa82e542) | +20 / −0 |

#### `plugin/src/main/java/org/xtclang/plugin/tasks`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcCompileTask.java](/private/tmp/xvm-full-testable-lsp-20261009/plugin/src/main/java/org/xtclang/plugin/tasks/XtcCompileTask.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0dfc70d3ba6563904322c61e71439b6444b42ba059a3d8265ebe496030bcdd07) | +1 / −1 |

<a id="full-reference-modified-shared"></a>

### Shared build, CI, runtime regression and documentation — 10 files

#### `Repository root`

| File | GitHub | + / − |
| --- | --- | ---: |
| [gradle.properties](/private/tmp/xvm-full-testable-lsp-20261009/gradle.properties) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3d103fc7c312a3e136f88e81cef592424b8af2464c468116545c4d22d6edcf19) | +14 / −11 |

#### `.github/workflows`

| File | GitHub | + / − |
| --- | --- | ---: |
| [commit.yml](/private/tmp/xvm-full-testable-lsp-20261009/.github/workflows/commit.yml) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0ef1236636e9c2c8dd2477cd2b0bf27fb0acc264224c198503c82b18dc3792e3) | +32 / −4 |

#### `build-logic/common-plugins/src/main/kotlin`

| File | GitHub | + / − |
| --- | --- | ---: |
| [IntellijRunIdeSupport.kt](/private/tmp/xvm-full-testable-lsp-20261009/build-logic/common-plugins/src/main/kotlin/IntellijRunIdeSupport.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c53034bf5110ebd35fcf5c9b57603ab4962c217682516781846f2bdd56741f37) | +74 / −33 |

#### `gradle`

| File | GitHub | + / − |
| --- | --- | ---: |
| [libs.versions.toml](/private/tmp/xvm-full-testable-lsp-20261009/gradle/libs.versions.toml) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-697f70cdd88ba88fe77eebda60c7e143f6ad1286bca75017421e93ad84fb87df) | +16 / −2 |

#### `javatools_utils/src/main/java/org/xvm/util`

| File | GitHub | + / − |
| --- | --- | ---: |
| [TransientThreadLocal.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-eed57f97c4b58102a7ec6568cd474f0e7b650d0ef823add159f5e3c63499b3e4) | +12 / −2 |

#### `lang`

| File | GitHub | + / − |
| --- | --- | ---: |
| [README.md](/private/tmp/xvm-full-testable-lsp-20261009/lang/README.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-048cc39d364fefa563880391db5b946fd31c849ba44ee8f7f429122bf2733a0e) | +35 / −5 |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/lang/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2051623f01fe1a3cacf102dc4944edc0a8d0b48cae85841c0d0023f526e581e4) | +60 / −43 |

#### `lang/doc`

| File | GitHub | + / − |
| --- | --- | ---: |
| [manual-test-plan.md](/private/tmp/xvm-full-testable-lsp-20261009/lang/doc/manual-test-plan.md) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d87406f67af682a3119a0137504d7c12d548bbeef2c9c8e2cc09970ce2e3dd30) | +2799 / −101 |

#### `manualTests`

| File | GitHub | + / − |
| --- | --- | ---: |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/manualTests/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-011d7bf962ef12d89cbb93c6818bfa7537b2fe8f2b16b9f7af0b97c2aa22673e) | +1 / −0 |

#### `xdk`

| File | GitHub | + / − |
| --- | --- | ---: |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/xdk/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4c62c5a63d80067ac8cb055601c6e1fd91ff3d85123d9a481b675781b91ae23f) | +1 / −22 |

<a id="full-reference-new-files"></a>

## Complete reference branch — new files added

**564 files added.** These paths do not exist in the recorded master baseline. The area headings match the existing-file section so additions can be reviewed separately from changes to established code.

[Inventory summary](#complete-reference-branch-file-inventory) · [Complete GitHub diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88)

<a id="full-reference-new-compiler"></a>

### Compiler and embedding API — 48 files

#### `javatools/src/main/java/org/xvm/asm`

| File | GitHub | + / − |
| --- | --- | ---: |
| [Reporting.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/asm/Reporting.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f5600a3c81bd9950472d1c2db59bcbf45b5e660d2e62362eaebc5542d60423ae) | +67 / −0 |

#### `javatools/src/main/java/org/xvm/compiler`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CursorBinding.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/CursorBinding.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9a1b7318a63efb25e3a1fab4a8662d6dc4d4fc5412c876a2b007d99b0a722756) | +326 / −0 |
| [InitializerBinding.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/InitializerBinding.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6d98b22e1b7d47e22fb8f68f8f80e0d0682be43434893cfdd0af121235926bb8) | +85 / −0 |
| [InvocationBinding.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/InvocationBinding.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7c03d0fb28af4a5fdb15bc386d950ea08683a56f0d5242b1ba52b499a08ae565) | +244 / −0 |

#### `javatools/src/main/java/org/xvm/compiler/ast`

| File | GitHub | + / − |
| --- | --- | ---: |
| [AnonymousClassBindings.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/AnonymousClassBindings.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c43990083fe98c64635ce0a2cf4e1a451162d64323a64bf047cc27238ccbb2db) | +54 / −0 |
| [CursorScope.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/CursorScope.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-17b4bd078ad906a77673e2b83ea25180618f9b4f97c584af931d4d4056cbe5bc) | +466 / −0 |
| [LambdaBindings.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/LambdaBindings.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7bc47f0867cb7105a42a01cc18a62ad8ff15f353015fe52292f278462fd60eed) | +76 / −0 |
| [PartialArgument.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/PartialArgument.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-41e26cc2956a0a608a0b8d1406597ffcc461b3c943043a4f6d91da90274b73be) | +49 / −0 |
| [PartialCallResolver.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/PartialCallResolver.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-70038b3c673ab87760bbc9359becbd7a86ea84348130b41fa50c42c2b7865f8c) | +452 / −0 |
| [PartialConstructionResolver.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/PartialConstructionResolver.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5546a827833c2444757862471b2aeac6efa0da89ed9335905890c2e567645950) | +139 / −0 |
| [PartialQueries.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/PartialQueries.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-949a1e943589961627f792d180ee262bcb9fecdd01c7c6eda41dc53259496696) | +77 / −0 |
| [ValidationScope.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/ValidationScope.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-722294827b6880f382d113b7bd7eb21c0dac268447365b4d4e6abe819e362ab7) | +24 / −0 |

#### `javatools/src/main/java/org/xvm/compiler/ast/partial`

| File | GitHub | + / − |
| --- | --- | ---: |
| [IncompleteDeclarationStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteDeclarationStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9c37ec72169223d247d9436bd2b4fe0bb98ae814afbb7a17cda6775efaace16d) | +136 / −0 |
| [IncompleteExpression.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteExpression.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5d20f20629762991f68a35396449c568a4154a6e2cedd4d9306bae07be1bdd24) | +93 / −0 |
| [IncompleteLocalDeclaration.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteLocalDeclaration.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fe6061b15ffe03f54e04535120a15d4eaafdf6d03fd8879bfc1ac32d81125054) | +87 / −0 |
| [IncompleteStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-22ef4f1ccfbabcc1032a1afda02f9987fc4e99f969eaf7a2c594c6819a6b8f0e) | +271 / −0 |
| [IncompleteTypeCompositionStatement.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/IncompleteTypeCompositionStatement.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dee324a7a7246cca7fd3e17dd29628b292a8266c7a8ce88f48fc9723bf978011) | +143 / −0 |
| [PartialSyntax.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/PartialSyntax.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2a07544bcd5cd7aee1acc764d87e76b36c40fa0194014791c3dd57b68c7f796c) | +94 / −0 |
| [ProposedLiteralToken.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/main/java/org/xvm/compiler/ast/partial/ProposedLiteralToken.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1521918a221ecc9ff912c2b33d84e810341c85cf88fd8b5f7973b617f7d3dc6d) | +23 / −0 |

#### `javatools/src/test/java/org/xvm/api`

| File | GitHub | + / − |
| --- | --- | ---: |
| [BindingIdentitySnapshotTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/api/BindingIdentitySnapshotTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a8bbe0834c6c70df3673c09957b0c55fafd53e926b79041490b2e3017d66ba1f) | +163 / −0 |
| [EmbeddingFootprintTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/api/EmbeddingFootprintTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7fce947c2979987530ea119236709e627319975eab090a0d59b2daebd4eec01b) | +35 / −0 |
| [ErrorListenerBoundaryTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/api/ErrorListenerBoundaryTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d4aa53a5725edc373159108d20d54d308c89714e8f92801aaa9dad839a5a26c2) | +127 / −0 |

#### `javatools/src/test/java/org/xvm/asm`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ConstantPoolDeferredTypeInfoTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ConstantPoolDeferredTypeInfoTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e71dfbe33bfbab3b6a8cc136613df945deb6c23ba6ed2fe3c11ce9c62d8aa03a) | +74 / −0 |
| [DirRepositoryFailureTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/DirRepositoryFailureTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b04f8b626dfd87d0fed4f50700963d6860878751dd25a40e4bd738b606ae0508) | +162 / −0 |
| [ErrorListenerAbortTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerAbortTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e12a603ed6b8b406ce5a54adfbea5610fef68d0f016efd17b3e316a8dfc2d084) | +96 / −0 |
| [ErrorListenerBranchTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerBranchTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d9c709b16dbc17750f4ff7c1a191be56859bc41a7ae90587caf7450682b3668c) | +144 / −0 |
| [ErrorListenerCancellationTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerCancellationTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-98fbb3db282157f17152ff5181bb6c1aff613676b4dc2fa36ca74e0fb9ec00ab) | +43 / −0 |
| [ErrorListenerHostRegressionTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerHostRegressionTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-303947263ae70fe850d79b1ac9291d44c2188a4e0c5d9f2cac524f9cc913e0a6) | +62 / −0 |
| [ErrorListenerMigrationTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerMigrationTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a23e193bfb1be4f942bb383340607c293486d24fec96683dd0c0f7a386793b87) | +123 / −0 |
| [ErrorListenerSilenceTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerSilenceTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8fd2ea0a713255d781b289630654a5cb1960b9c467f4afaca976fed26c87a824) | +155 / −0 |
| [ErrorListenerSiteTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ErrorListenerSiteTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3056fc0fa3024828401000fcb05bbe12d85fff0664b5fdf700206b0349fd9562) | +121 / −0 |
| [FileRepositoryFailureTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/FileRepositoryFailureTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-80c69413f8bd448dde47808f5626a97effd811c72f518d463464a574ae387b90) | +104 / −0 |
| [ReportingTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/asm/ReportingTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-863d3823c9e681b703bb8b7eaa33086d2f4fdf385047914742c138209a317130) | +43 / −0 |

#### `javatools/src/test/java/org/xvm/compiler`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CompilerCancellationTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/CompilerCancellationTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8127e698a3cc95447cdc6300c5a84828bc30dd0f2ab5e3cc30618849c5a7ea05) | +24 / −0 |
| [CursorBindingTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/CursorBindingTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-682f376d08daa9df54949cbe47b4ba50602dee955caa28167dee03c13938b702) | +133 / −0 |
| [EvalCompilerTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/EvalCompilerTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0d684fe993f83f5abdfab2ec6dcda1870291bb6c972683d0c5135baae5c3d6dc) | +13 / −0 |
| [ParserAttemptTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ParserAttemptTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-15fe785073fef955a54752bf1df00539a0d302a5911358af6533d1d07bb5ccd3) | +181 / −0 |
| [ParserDeclarationNameTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ParserDeclarationNameTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-acd393b36f9bcd7e02c1c61eb7cd0025315cc44bf920b2da3942b2f7d6381acc) | +172 / −0 |
| [ParserRecoveryTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ParserRecoveryTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b1258eba0a6820776d190829350b216aec32db0e4d1bfe3d9a6bc81598058435) | +672 / −0 |

#### `javatools/src/test/java/org/xvm/compiler/ast`

| File | GitHub | + / − |
| --- | --- | ---: |
| [AstChildTraversalTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/AstChildTraversalTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c95cd233532390fd7d8d44363e533fd23fdea6f5552bcd3f9576482f6129d8fe) | +131 / −0 |
| [AstTreeCopyTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/AstTreeCopyTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a158af3e5456283eba624d06c9a19a66709b47f51d80dcc2e2fbbd855a6fe07f) | +134 / −0 |
| [CompositionSourceTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/CompositionSourceTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fea7a0efc28790892ab856995777cfd13f0bf2c2179da007c457bdb705d09e07) | +36 / −0 |
| [DeclarationProvenanceTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/DeclarationProvenanceTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6ce63635546929d42791f4a81d74269c2e610be4e834043e2d4a2f1df757e3e6) | +151 / −0 |
| [NameResolverReportingTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/NameResolverReportingTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1b57bb6f4044d70d55f4e2ff383b355b54433d52209247f378caa3d9705dde20) | +109 / −0 |
| [OrdinaryAstTreeCopyTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/OrdinaryAstTreeCopyTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-617664fb79aad083862ae0eafd88e160b419b345cb1aebd92e0347f1bb59c9ba) | +42 / −0 |
| [PartialSyntaxTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/PartialSyntaxTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ae70a8ec583f378ba691079960e0bfafa76c61c0f1d739f96828215cc0674d2f) | +233 / −0 |
| [ValidationScopeTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/ValidationScopeTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-03a466a7a289e2ea19bcf02e97736d7cf0a164a707c3260e145ca0c30b4fd82f) | +156 / −0 |

#### `javatools/src/test/java/org/xvm/compiler/ast/partial`

| File | GitHub | + / − |
| --- | --- | ---: |
| [RegisteredChildFieldsTest.java](/private/tmp/xvm-full-testable-lsp-20261009/javatools/src/test/java/org/xvm/compiler/ast/partial/RegisteredChildFieldsTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0fa5058d2b12a4f75679af8ada3aa6699d292ad822bfccf388792f237337f089) | +143 / −0 |

<a id="full-reference-new-server"></a>

### LSP server — 283 files

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter`

| File | GitHub | + / − |
| --- | --- | ---: |
| [AdapterCapability.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterCapability.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0d8de2bf3163ef37a860596cbdc8f1e6a6d7083e8ec71a38398bf10a03aecda7) | +33 / −0 |
| [AdapterFutures.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/AdapterFutures.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-449abb0a5185bc5fb97ef1a0f7508802272216708b5659eef52647200c562faa) | +31 / −0 |
| [DocumentColor.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/DocumentColor.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-224c03b1b06fbab284074261986034b1867ca0ce89a1e5b1ea6eb65aa686a91f) | +24 / −0 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CompilerAnnotationIdentity.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerAnnotationIdentity.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ebc8c73b71c7b898435cff0cefc2b957125bc51f57970f98827e774f033c8aeb) | +115 / −0 |
| [CompilerCallableIdentity.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerCallableIdentity.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ee872eb925662f846541d2dbe0bf86d3c2caf44c9c55400fbca05ca0f8f19b6d) | +66 / −0 |
| [CompilerCalls.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerCalls.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7b95082ab720d987c8d16e64dcd9a650fe3fbb163469c64d03b9a7396e5e35b3) | +53 / −0 |
| [CompilerDeclarations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerDeclarations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4c811999e8856d8d7a161f609923ad7e5dec7e07a471c6ba960a94553e4149c7) | +49 / −0 |
| [CompilerDispatch.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerDispatch.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-eead31f27f65dfc137c457072d647afb455454aa53e2d52c19aea742bf1cee58) | +183 / −0 |
| [CompilerImplementations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerImplementations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-90b8d77717eb86387c3358b2ff2aa52800fcb4118c2353f5bbbe63d1c82d60e1) | +231 / −0 |
| [CompilerImportAliases.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerImportAliases.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a731c14f6596945bcb58c1aff0a37de6c6895b96fdb7a6be74a001996538e185) | +99 / −0 |
| [CompilerMemberAction.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMemberAction.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c9725f90434fdcd023d094543bf84d1c59bad11dd9b5597a3477541452199df7) | +174 / −0 |
| [CompilerMemberSignature.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMemberSignature.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ee257b9434ba4402caaef8bc8ce388bf80f4fe91f4da63ad374d8d262bcb7f73) | +235 / −0 |
| [CompilerMethodRelations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMethodRelations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ccf6b973a497e04c68f13a2969a0c5a57d52470a1020b44891929984a531526e) | +60 / −0 |
| [CompilerMissingMethod.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerMissingMethod.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c77e82a29202e48fb0220d978a31edaadf7f9ea24ab39d73b9c8a715625744cf) | +591 / −0 |
| [CompilerPrimaryParameters.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerPrimaryParameters.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8f28abf742638810693a67e297afb56e1e8e33dfa27bf00ac199428eb36fb703) | +19 / −0 |
| [CompilerPropertyRelations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerPropertyRelations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0d3a1616af46763e03ed8e8ad6e288937903982087f68c28230535b28d5d4c0f) | +69 / −0 |
| [CompilerQueueTrace.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerQueueTrace.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-642e2a6fc05905f31e4f44bd1ac12aeeb55d89fe32b6c6ce539578cda10f65bb) | +213 / −0 |
| [CompilerReceiverIdentity.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerReceiverIdentity.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ec6f9fd3c2d427d629a56f92b4f1d6c3e4eedbb12ab009e8ce2682b05cf3a945) | +85 / −0 |
| [CompilerRenameFacts.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerRenameFacts.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d07fe60fc4fd93f189c88a980b115ca22c50444c3fe84dee9338ef945d9f0d20) | +558 / −0 |
| [CompilerResourceValues.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerResourceValues.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1a08ba4cacf30c38f0c1fb64217f9819de834ca51ad259b595df4aed4b635e4b) | +95 / −0 |
| [CompilerSourceLinks.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceLinks.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8be03d2b5f88927a32e987dd602b7060d9ba2ab3858559fd820353fe8483d8a4) | +46 / −0 |
| [CompilerSourceTypes.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceTypes.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3b75a55e8a473c6028b66be33e4b5021d34498316b7f665e77318558808e7dde) | +35 / −0 |
| [CompilerSourceUsage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerSourceUsage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-290436a265bb89a4bba9a85670e2d29eeda807900285358abcb1cc76d42cf878) | +63 / −0 |
| [CompilerTypeNames.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/CompilerTypeNames.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4c81939934d5ff7c6a2a95e9402a2c8cad49a2c79f04e372a8d738f700159eae) | +108 / −0 |
| [LibraryDeclaration.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/LibraryDeclaration.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-438dc398ca547737da5f69b0457e9b3828eb8b52af1eb0735b7da5edf91bb888) | +70 / −0 |
| [PartialSemanticModel.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/PartialSemanticModel.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-582778ab188968505be13fe1b32c6445c5bd7f9ece6917e092ffb6b475f35f7b) | +176 / −0 |
| [SemanticModel.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/SemanticModel.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2f33dae13df56169cc76bdfb7bdea3a4dfa2dc043d8c9fe54dc2e9af81d77854) | +566 / −0 |
| [SemanticModelBuilder.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/SemanticModelBuilder.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8e365b04c2612a00f91d4312e4454548ec2cb0a54d395125e07628ac56cbec2b) | +2089 / −0 |
| [XdkAst.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAst.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2cffb865cd0327b131f4a7833f33d14e4449392027794f6adeb31b9db0d28066) | +179 / −0 |
| [XdkAttachedSources.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAttachedSources.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a028a1e6e43ebb9fb37acb7c5d491a2f627404c394b576942e11032e3d62ddda) | +111 / −0 |
| [XdkAutoImports.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAutoImports.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7259ec6ae774b420a1d6ec6fc7e5b4ab8ad5ad61ffc3583c15eb6a7272fee326) | +145 / −0 |
| [XdkBuildModel.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkBuildModel.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-03b0dcf1c012b6a83643a8548505ffc992a839ee3209043905ae545966cc0a45) | +190 / −0 |
| [XdkCalls.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkCalls.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-96da9d8affc89fd17464acff30a7365a3b428cb0563094c8ccc62271f31b49c6) | +88 / −0 |
| [XdkColorPrototype.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkColorPrototype.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5c1eb4cc44c34dccf2dc0c080ecef312e579c820ca6c3961ad75a3f1ff5864a4) | +179 / −0 |
| [XdkCursorQueries.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkCursorQueries.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1847fa1052da3a15551935f5de56a247a4ba0b6c05e2ae1a6757bfea806d268a) | +336 / −0 |
| [XdkDependency.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDependency.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3dd2102536c7b1de5a2b2d383461e6226c0baad84384f829ba1884351686d983) | +170 / −0 |
| [XdkDiagnosticIndex.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDiagnosticIndex.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-121b908a6ff24b0cc76c25757e2ed17f72a6257686cd2887339154a4f9ef617a) | +33 / −0 |
| [XdkDiagnostics.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDiagnostics.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-99b40ef5da21e337c5a251ad082d03ab725c94e0515f26e7107ce62db319633f) | +96 / −0 |
| [XdkDocumentation.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDocumentation.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4b223b78b183e6693b3409f370e4045a4a33a16876fe03ada85ffa3a168bd094) | +56 / −0 |
| [XdkFileChanges.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkFileChanges.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-98a692ba64f940d904b7905811ca8d1a9f500f1a4e9913487a654c40f595e51d) | +76 / −0 |
| [XdkHierarchy.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkHierarchy.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-794b531b601508a9c1b64f4345410d2362ba5b09acb8c9f6a2cf8de6c7d2d2da) | +86 / −0 |
| [XdkImports.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkImports.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3ff20d36467839d66f7a0a75163f408ad031c2e4034ae1b2af669257cd1ecf3f) | +108 / −0 |
| [XdkInlineCompletions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkInlineCompletions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-92c8a76fef78d5e43e12c5beded46b79b465b76c61fe3f9f7bc02884d573aecc) | +52 / −0 |
| [XdkLexical.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLexical.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-42ee46af781d8b8b529230b1d5c1524b69ec410f9a7abc033859c77d1290a882) | +331 / −0 |
| [XdkLibraries.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibraries.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f885f55fb2aa7fe3498aec263a961264e210d54f889972796d8a0ee70d3aa06b) | +85 / −0 |
| [XdkLibrarySources.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibrarySources.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fe3319bf53969956c115058d81d40121f81d69288a006096f3d8b96f95a7c9e7) | +196 / −0 |
| [XdkLocalDeclarations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalDeclarations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-46f198fb225f9a727ec19eec3e1c0b0f93f81fcc8c3b545c084e3b4dcd3c112e) | +59 / −0 |
| [XdkLocalExtraction.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalExtraction.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4eff6e33c9f4761f73023e7bfb8d8fd0b6183e1e3d7ff233d4b0853eb6a38cea) | +140 / −0 |
| [XdkLocalInline.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalInline.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-aa167403604b93ec1ceea127db830e4141def6e96770fb2abe1d6c2ce284ce00) | +203 / −0 |
| [XdkLocalRemoval.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLocalRemoval.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bc159d9bd8cae555d8db8ee86d2d074798daf49acf2acb2339a18c61b5eebc45) | +73 / −0 |
| [XdkMemberActions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMemberActions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2f2af5880b9a33cda9f083f76f4192d4257b58225af59e907d1bb9e9f983b9a9) | +138 / −0 |
| [XdkMemberInline.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMemberInline.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-491ab6d12ab9d3db4310340dd0d63beb3df9481a13c266d01e333f332c577c89) | +144 / −0 |
| [XdkMethodExtraction.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMethodExtraction.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b2497e5b80eee14ae9b8d0b38f8c75ec494c8f89d336503b3fc976ae38cc65cf) | +245 / −0 |
| [XdkMissingDeclarations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMissingDeclarations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-de9ffe1b42c8ac8ac8506ad2f2d51d8d622e7428f621105451f5203d23fcb965) | +162 / −0 |
| [XdkMissingMethods.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMissingMethods.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-70d7968dad0a0715b8534ffe88d6e0f41b1fb3eb56069ee421b8c2240cef88e8) | +183 / −0 |
| [XdkMonikers.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMonikers.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dad08616cd5bbd9e027e6e7419552e6a763da24e2e4df133f1b0e085d3888437) | +132 / −0 |
| [XdkMoveImports.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveImports.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f6ffe41ba8ad1b93d34c60720c01650c111caa27f347c4192dddc42f84ded93f) | +37 / −0 |
| [XdkMoveInputs.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveInputs.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2d2383c50a9f89077c2bbc39c471d8b5b1850b8ec0f3ce6aef313560c5462ecd) | +40 / −0 |
| [XdkMoveOperations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkMoveOperations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-285c252cc9ca688947f3515dc1748e55311992b22a5161f6e7a28dd720ed1a7a) | +30 / −0 |
| [XdkNavigationIndex.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkNavigationIndex.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a9434d0bd21312828da2a6468590bd74195c86dc5adf762abe6275eacce1b1bb) | +53 / −0 |
| [XdkPresentation.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkPresentation.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a2d712a31e4fa17e47cf9724cf7179d0ccb3905d195390ee1da04dca5223b946) | +252 / −0 |
| [XdkProjectQueries.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkProjectQueries.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4abd633c6e5373db45d93febc4a6c5ba01f5ce5aa1ad740df1412d2ef57878d7) | +1506 / −0 |
| [XdkQualifiedName.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkQualifiedName.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-eae824e1e346bbf1c9858f38005422ecce1d2e30e6bb5c9f0583790f20785b75) | +87 / −0 |
| [XdkRefactoringSyntax.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRefactoringSyntax.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-801c874240ce6dfb047a535dcc6363d11aa365a4aed59a87f7913a868155bb3a) | +53 / −0 |
| [XdkRename.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRename.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-53431e12129b160ba171d8f7cdbb53928612cee61d5aa03a9bb43c35f1c0cdcd) | +1229 / −0 |
| [XdkRenameProposal.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRenameProposal.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f9bd16a50125a7ab193304d268804eb9cfc7d1411221cfa386e4f74082e20710) | +44 / −0 |
| [XdkResourceMoves.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkResourceMoves.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-62828a5f347a9c9744b90e1bc02132428a33668366d4a9663430a6a461877267) | +80 / −0 |
| [XdkResources.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkResources.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bd52e5ec931a670a6eb2cf11bfe55439fd7a1359611d6aac865afd856c7144da) | +104 / −0 |
| [XdkSafeDelete.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSafeDelete.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-31138d9bc5f27066d59a2f7061a5a90ae967087e9f2799511ef81fe6cef71707) | +106 / −0 |
| [XdkSourceModule.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSourceModule.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ec75e1895a00e4f3553ffa9192cd07db576039e9671f6cdb2a0b2234423b1349) | +163 / −0 |
| [XdkSourceMoves.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSourceMoves.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b9b82a5854cee4517006e2df5e319d6e65f660cb0ef673a663ca597c9882dc35) | +104 / −0 |
| [XdkSources.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSources.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e1473e97b07dff81812659f2f4f5b1ad1c351b8de11523013eb18a2a57932ef6) | +223 / −0 |
| [XdkSymbols.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSymbols.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a3bd9791fb2122a15940b300a0f5c63dca3dc09395a02fd3f25e63cd0c54a9e4) | +168 / −0 |
| [XdkSyntaxCompletions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkSyntaxCompletions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4bfdb228464bd98ac95373581bda5926c15b8e62b4782537617aa99920806800) | +506 / −0 |
| [XdkTypeMoves.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkTypeMoves.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4478f4278d9a6ad3d56b042c6d6f627bfdf780fbd2056d7b4cafc68893a74ddc) | +229 / −0 |
| [XdkWorkspaceDiscovery.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkWorkspaceDiscovery.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6d722ee8812541c4299f300e69a7959df65264fc2aa3591f55f28299ec8eb72a) | +167 / −0 |
| [XdkWorkspaceNavigation.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkWorkspaceNavigation.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6c5a4cd62212a021d911df0acc850812ad30370e10f71cb9965081075a346a04) | +267 / −0 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/server`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ClientPresentation.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientPresentation.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8179fa57b48799350e216fe03bfdf9ccccd68588c77c64f6a03df29c64d1e504) | +153 / −0 |
| [ClientRefresh.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientRefresh.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1874d9f3563ee88fea9458591172ebbc27d7598656898d8433d9081416031992) | +88 / −0 |
| [ClientTrace.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ClientTrace.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5c0e240eedcc137b14418c7ecd19e938b24a3088a990ecb7af43f76c829f5eec) | +60 / −0 |
| [CodeLensSettings.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CodeLensSettings.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a2519d9a221596c42cb708c3cbf37bfac81fa9d9a6d3803fd219d0ee6bf59dd8) | +14 / −0 |
| [CompilerConfiguration.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CompilerConfiguration.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-de37b06a1bb2acb8b2acf49c2f0247a3fa5b6ee272f3d579a1a0a21fd7901e1a) | +126 / −0 |
| [CompilerLibraries.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/CompilerLibraries.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f484467f4e546c4b8a7db014453a88dd4ba159a2a066b0e69859ff6318b9551f) | +143 / −0 |
| [ConnectionProgress.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ConnectionProgress.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9c38e63bb225c907101edaee0465bf8c098f2bb50cc078b28a82bb083996f39c) | +236 / −0 |
| [DiagnosticReports.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DiagnosticReports.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-37f3f11ae0a976a6795f00be1bfde69355b3b9a13acd848aa6c1c888771c4c18) | +171 / −0 |
| [DocumentSynchronization.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DocumentSynchronization.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8d0b4d5b33020ee86e0e4c435dd56cf2e403da4a1e9ac37ff8ad98cbb5c3d253) | +52 / −0 |
| [DocumentText.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/DocumentText.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-595e78561fc8b4679a1889c14bdd7a9b15ddc6e281b2aa87dde1130c8c5a96b2) | +73 / −0 |
| [EditorFormattingState.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/EditorFormattingState.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0a0de68bf1ecaaae2c4eb87f479c8eff1926f80d31cdf405e884014dfc667d5d) | +78 / −0 |
| [FileChangeSnapshots.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/FileChangeSnapshots.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-60cedfd2470c8bee42245f3f5eda225f6a75ebffea28d7f3ec6ca223b645a448) | +40 / −0 |
| [PartialResults.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/PartialResults.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3e1835804e3865e74b9e038e46a68d2c8a7104169ef33dc099e704d49e43c7d2) | +93 / −0 |
| [ProtocolLifecycle.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ProtocolLifecycle.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8561090d1e970f44e672eee5208b5fd53c83d9f05e366cd8fa9283cae85158e8) | +129 / −0 |
| [ProtocolTrace.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ProtocolTrace.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9a7fe810d4e28af797b50f30435ed4c6f88f9c215fc3da215719417ec3652f1c) | +160 / −0 |
| [ReadOnlyDocuments.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ReadOnlyDocuments.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-93f86443d761d2e277d66359aa2f7362cc70e0900a65b3cc5f55b27799d3012d) | +109 / −0 |
| [RenameProposal.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/RenameProposal.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d3ab387e7a6670600d91bd40b6fc69b11992a04905091330b7a38ec2923192d0) | +36 / −0 |
| [ResolveReports.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ResolveReports.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9ca39c8bd7d1cd8f1749c93efad661fc2bf928560642ca6f4c0defa82078ca4e) | +68 / −0 |
| [ResourceFileWatchers.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/ResourceFileWatchers.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e21ee1728d6ea82c83a297c7b27da8e133d09fe88858d2ae75644325c074fea4) | +250 / −0 |
| [SemanticTokenReports.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/SemanticTokenReports.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-24ba772d9e1663775ad5706903e8616f45a222cbc563dbc643c6a784bfaac82d) | +127 / −0 |

#### `lang/lsp-server/src/main/kotlin/org/xvm/lsp/util`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ExecutionTrace.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ExecutionTrace.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6dcb7fa61fa5ef5cbc1130c18e7852a1fa0032d427a871d6ebec9641e396a01a) | +121 / −0 |
| [ProgressLabels.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ProgressLabels.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7da403a060d61c8c34dc0e89303f6f0436adc60b3f98c88ae89677816869e461) | +34 / −0 |
| [ServerLogs.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/ServerLogs.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-37a8acdf2a77b6ca46872a5c13d2635e3a048b6029bc3181e8deffd676d51a04) | +221 / −0 |
| [TraceProcessId.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/util/TraceProcessId.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7687113235ba852c5869ac4b9e5f2ccf566e1651aae3827df2eb6c9fe75777eb) | +12 / −0 |

#### `lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter`

| File | GitHub | + / − |
| --- | --- | ---: |
| [AdapterFuturesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/AdapterFuturesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-519c26df6392fb29ea32fd2dce73818d5ea233d2816fd116842a8e21c760d553) | +55 / −0 |
| [CompilerAtomicEmissionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerAtomicEmissionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2d89515b788aa49e677e011276043a02420e07dae487686055d7f56b737cddb7) | +159 / −0 |
| [CompilerBindingFactsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerBindingFactsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d67e9029c49cc00647ce48d337bbac2f0062b6a53d7bf200def564f354d8bc98) | +90 / −0 |
| [CompilerBoundaryRequirementsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerBoundaryRequirementsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-88030356cefa20089e8f3b54819c7e4cdb8ea017c242d13d23d29de5629a754f) | +249 / −0 |
| [CompilerCallSiteTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerCallSiteTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d8ce6fb0e2a7cd78dd3c4eb4e68bb8abec85335b7864b2fc52bcbef6277c60ee) | +366 / −0 |
| [CompilerCallableProofTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerCallableProofTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bf846cf82e53832d977736ff25715ca2157a91e80735aba80ff71ebcf9fbd22c) | +221 / −0 |
| [CompilerDeclarationAnalysisTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerDeclarationAnalysisTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6f0055f710f0d1b9aae2c049ec668e8585836fc7754246a6eb7bfdd241678eda) | +80 / −0 |
| [CompilerDispatchRoutesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerDispatchRoutesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2c8164d99bba4d1661e8ca2f4a3f0c77c421ad16e86aaa7082e3b3abdf4a97ab) | +238 / −0 |
| [CompilerEmissionAuditTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerEmissionAuditTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5487ddcf69dad27edc5d4211e8db109379f991fa1a3b6634b565a2a2ad5a814c) | +263 / −0 |
| [CompilerExtractMethodFactsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerExtractMethodFactsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-441caecb3e23507e5ecc0aa689d61972618bbde2fe33607abdbd51f8be4d0644) | +57 / −0 |
| [CompilerFunctionTypingTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerFunctionTypingTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8746d9b90965bc7aa62db9d76b0f6d5a6e75fba5af3bc1592e1d8b4587d2735a) | +149 / −0 |
| [CompilerMethodExtractionProofTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerMethodExtractionProofTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-064da779f590fa8904d5c338fdb2973246c043ab111d55eb795f34e33bd915b7) | +106 / −0 |
| [CompilerMissingMethodProofTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerMissingMethodProofTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2f258621d40ca3400725df18f40b3fc29eb5c517b4f03f80e4b263ece2ce9307) | +404 / −0 |
| [CompilerPartialFactsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerPartialFactsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8f183bfc1543fbd4ded1957fbea8474a46ffa5eb649d373a150870e4b5b029d0) | +69 / −0 |
| [CompilerPoolScopeTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerPoolScopeTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c2fb39d5704e3d954fe85f60cf6dcf0e92e8eee313efeb822c347e3108ba38c5) | +102 / −0 |
| [CompilerProjectTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerProjectTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b39bec83a221097bb52496a6829930e1d332605c69629284332d7719129a20ad) | +383 / −0 |
| [CompilerRelocationProofTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerRelocationProofTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9e074b82584469dceb3d832dada6938cc4db4436b51c86f24e7d5f87b9eee308) | +76 / −0 |
| [CompilerRenameRequirementsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/CompilerRenameRequirementsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f03d262da3ec6672322962cbf59e18920503f531d3e971dd95b5b145367aafe6) | +103 / −0 |
| [EmbeddingDiagnosticsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/EmbeddingDiagnosticsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0d0991d0fa758389be0408da9e0e81bdf51e555ce6fb705b9d584e3ad69cdc39) | +195 / −0 |
| [EmbeddingSourceSnapshotTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/EmbeddingSourceSnapshotTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d6c5860ab2c31087512f5e42260a7a8190b54b491deb0cc78f5c534d49465e72) | +149 / −0 |
| [ManualCompositionFixture.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/ManualCompositionFixture.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-26a3d4132a494ae06a4eb061344dddb7e9a77af54786f1f09142c1f8b50e95c5) | +26 / −0 |
| [PrepareLibraryPlaybook.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/PrepareLibraryPlaybook.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-acef1fec25c049db4b42eb6c567e5667584accb6c6ce18249293180e0c1140da) | +38 / −0 |
| [SemanticModelTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/SemanticModelTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f13eb71a7c906a1aceda66238e31a907f3113d1f9aa8ecb30a1fea4fd175a4cf) | +390 / −0 |
| [TypeInfoDiagnosticsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TypeInfoDiagnosticsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9832de69650d35c611e89fee1d513319a6e14e2abef640ca02af25d218d27d67) | +292 / −0 |
| [TypeInfoFinalCompositionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/TypeInfoFinalCompositionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-61ffa88f25adcd2b23088a510ab8cc2454763e59a29c77a9790f2f2f2bdfdaf6) | +333 / −0 |
| [XdkAdapterLifecycleTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAdapterLifecycleTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f910a4fb82d76c0f08471d1bd0959c76edf2fc3817bf0521ccf70eb242fee729) | +159 / −0 |
| [XdkAdapterTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAdapterTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e63990b5eaad9873967979c5cabcf96002878f199dea2d564d14632912e57289) | +486 / −0 |
| [XdkAnonymousConstructorTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkAnonymousConstructorTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fbc098f14672b219456abe9cdf923c6405e41606c27cdfaa244d21ebada926be) | +264 / −0 |
| [XdkArgumentCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArgumentCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d1e98b556d4c2d0e68fd1acea76a76c70f61095b7705465ba6bb5b300d4b60fd) | +387 / −0 |
| [XdkArgumentContextTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArgumentContextTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8d814a56ee816620cd4f1233b989a80b74b1e9f45c82eda1e3a5efe2381d666a) | +75 / −0 |
| [XdkArrayDimensionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkArrayDimensionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-09f943212ea05dcda02976ff73afe1cb7bcbcf45416080b007e91f1269f56f3e) | +183 / −0 |
| [XdkBuildModelTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkBuildModelTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7791c21f075a4cb5190dadf57ae6501b73fe458972ffe61b7138d53c4d3711b7) | +218 / −0 |
| [XdkCallHierarchyTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCallHierarchyTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dcfcd54cf34a9b9ba411a5f049fee01febd7923588e3dd3e152f955cdc0ae636) | +165 / −0 |
| [XdkCallableBreadthTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCallableBreadthTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c77ba243658f00a74c81e85d23da17daff4c46ff55fec3d0979572495e4ce92a) | +45 / −0 |
| [XdkCandidateProbeTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCandidateProbeTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-05b426ea0a185d106309dec351fb74f240e77fb57b799e20023e81bd6bdd9310) | +169 / −0 |
| [XdkColorValueTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkColorValueTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-92d834b5ab1a48554bc044fc3c74c51efc72fdd5ac6dad168c11ccb3b65c4bb9) | +182 / −0 |
| [XdkCompletionSignatureTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCompletionSignatureTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-92feb07b15aeac047e0eb0ab46d973cb520ae618f5082bdb1a5ab7b10ba80bdb) | +201 / −0 |
| [XdkCompoundHeaderTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCompoundHeaderTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e4320addde5eed6d665ccfc8f447c3e9e1f099abcce98547aeb3cddebc27a5a1) | +195 / −0 |
| [XdkConditionalRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkConditionalRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2551a6fad17e3c571d3ac92b97f2fb45a75d36f24276fdc45cdaff569f4cdeaa) | +155 / −0 |
| [XdkConfigurationReplacementTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkConfigurationReplacementTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-74d706fd9612799a6e209435b17d5a7902acf96f4a1b39debd7300144debfda7) | +217 / −0 |
| [XdkCrossModuleMissingMethodsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCrossModuleMissingMethodsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-714b754861a3ecdf86c2abca170136674a1a631865049036de39b96e47030879) | +381 / −0 |
| [XdkCrossOwnerMissingMethodsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCrossOwnerMissingMethodsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cc28868fda40da2de1de4b30f2a98da281714d8918165f855d05319058ea856d) | +497 / −0 |
| [XdkCursorPresentationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCursorPresentationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ab754b42e382376216a2a057aab8e3f1c25cc9fbded9fb8f3355e4d3d68915ac) | +84 / −0 |
| [XdkCursorRequestTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCursorRequestTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3efbde9ad2958faad7fd92c94f92320cec419911f74abe6e7e64eb4f03a6ce2c) | +328 / −0 |
| [XdkCyclicRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkCyclicRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f9fe1b3e521e51be87172f0ccb19de79fcd7c35b43d09fd79727bfc29c0337f9) | +44 / −0 |
| [XdkDeclarationHeaderTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationHeaderTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-80b3ee30585078c718cf794b4da7e2f5370dbf680fe5dbccf32e3f77c6b9b792) | +225 / −0 |
| [XdkDeclarationProvenanceTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationProvenanceTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-80219dab1548de9a283280ef16a1d12a343f741c898231dcc4e1733a79427640) | +132 / −0 |
| [XdkDeclarationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDeclarationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5a71878934d74af30751b95721435854c820a8d61209d22ab0fb9fc99d46cf7c) | +121 / −0 |
| [XdkDelegatedRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDelegatedRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dbf28bb421606898d1bcda02356401e834b4b68b56c4f8692a02bcd410f3672d) | +62 / −0 |
| [XdkDelimiterRecoveryTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDelimiterRecoveryTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-db9762461e94cda43a403a99b316a6222eec99154075fd8e9b0ee49e7acb1824) | +246 / −0 |
| [XdkDependencyTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDependencyTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4d36cae87e464a57e7e9776a75f4fef295a6c8d24d69bdbf76cc5a3a509974ed) | +407 / −0 |
| [XdkDiagnosticIndexTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDiagnosticIndexTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-511a459542b7765cf5c703e034622f0b5f6eefd6bc72bca9638514006aede404) | +150 / −0 |
| [XdkDiagnosticTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDiagnosticTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c9e40ffa79d0d3f609145e9041e4b06ecdb0dadc6073e969e53e4aa55fa02f2d) | +149 / −0 |
| [XdkDocumentationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkDocumentationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0f588304b2be5fd4c7f15d86768060a4ac1e436ddda0ede6172f85766c61aa61) | +93 / −0 |
| [XdkEditingTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkEditingTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-da4a71f9f80affcf4457d536146bc7a1f58798268b279930ca64174209a02c7c) | +129 / −0 |
| [XdkEnclosingValueCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkEnclosingValueCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-be99252515ec56bf068546ae7bd2d4d542efef14a2f8ccd9fbb12a6a27ea0be1) | +130 / −0 |
| [XdkExternalRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkExternalRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-20f79720b5448d644740f23eb7c42aacf4e414ce7d4a69829e7e90817e2776d8) | +200 / −0 |
| [XdkFileChangesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFileChangesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4dcc2396039a36fe0b5dea66652762c70f81813fbf1cadb19b39f047bc39e484) | +130 / −0 |
| [XdkFileOperationsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFileOperationsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9e50c0fc2cd0283eb0d3e5e4a7e4cec04a87aa994b23c457886fc8596ba88b1f) | +214 / −0 |
| [XdkFoldingTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFoldingTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9f9d0364fdec35d648e255f636d04c978821b890510f34b93bae690746b477ce) | +40 / −0 |
| [XdkFormattingBreadthTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkFormattingBreadthTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4e991da58a459a928015cf9263982b2db5c9efc063c8d52f64f75fbddb1b01f9) | +127 / −0 |
| [XdkGenericHeaderTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkGenericHeaderTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bb23ae8fbe2a15bc0492a2e2a1c5c21bb4d1871d659e47631b7411ec898be237) | +241 / −0 |
| [XdkGraphMoveTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkGraphMoveTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f213c374a0c946f92189c05a7778f900e7535ef8353038d72df5614ed24b2a2a) | +457 / −0 |
| [XdkHeaderSlotsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkHeaderSlotsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-058fc77ce76626ff984ca1db3757a3113bed9351677265660862c900e651d864) | +153 / −0 |
| [XdkImportCompletionLifecycleTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkImportCompletionLifecycleTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e9533ee842e9345f891662118decc294947c061f178073e44f74fc9d5be8ca83) | +68 / −0 |
| [XdkImportCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkImportCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e8fb9fe94d57261fe44fd1f6837034aaf3416a4ff30cc2483f532ae7cb58f378) | +103 / −0 |
| [XdkIncompleteCallTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteCallTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a3e8e037786461b50bda4c06107a224bbef5c5695f4dc8893f5c1afc22dcf10d) | +144 / −0 |
| [XdkIncompleteConstructorTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteConstructorTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b4486730f48e432befdab19d6879b3186d3c97f93c4098d288f26b7c464a67bc) | +95 / −0 |
| [XdkIncompleteFunctionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIncompleteFunctionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-88221bbad0773b277c723ef412aefdf270f96a10499df9e4d20668e3c57ddc93) | +128 / −0 |
| [XdkIndexLifecycleTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkIndexLifecycleTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a367b4719ea33a707e3b048057e1d35642116a3c5bfdacc96b267809fbe89c70) | +132 / −0 |
| [XdkInferredPresentationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInferredPresentationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-391f754e76bca0afa6532f06a990fe0d21013adf08d672950a466269647bed03) | +78 / −0 |
| [XdkInitializerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInitializerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cace669ede9605b8d6c50ecfda4191d7db31fbcc8c7aa96bc8f332f865d17099) | +64 / −0 |
| [XdkInlineCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkInlineCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fc3cfe1fa8b198d805d4d69b8bac5cc05495f52ef8668c23175cfe1ffe008205) | +142 / −0 |
| [XdkL63SharedActionsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkL63SharedActionsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d524df869577f0d2fc247ed1a07b1b92bb2ab6ce02f693d74f6320e912a36511) | +95 / −0 |
| [XdkL65ClosureTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkL65ClosureTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c6e477f6cde89e429079911c374ceb0c2222a297a79bc76ea53b9cb40284afb3) | +106 / −0 |
| [XdkLambdaRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLambdaRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-66dcae7af4a5a5de66ddb6ab72f6c0214df45888c467a12870fa2eff0203df64) | +129 / −0 |
| [XdkLexicalTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLexicalTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-aec0a26e326dac0d94a538468e90b67475162e94310a5564721415fd00c94923) | +41 / −0 |
| [XdkLibrariesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLibrariesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-27505304aeb9656edf24e071c98d0c117987244a21e8cdef4216f82d99aba7e4) | +101 / −0 |
| [XdkLibraryDocumentsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLibraryDocumentsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f2f66e63319364763ab50f068bf0f34237762fe6f4ef25ed40a42a63f76fa950) | +62 / −0 |
| [XdkLinkedEditingTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLinkedEditingTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-429df0ff9e8f9b1cd8b1c418f91e70b887e9b3886475b3d526cbc26bbe2faa04) | +106 / −0 |
| [XdkLiteralCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiteralCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cd64494ac217418f0e84b47bc413f69bdf034bb7a7f94805645693d122be8abd) | +136 / −0 |
| [XdkLiteralRecoveryTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiteralRecoveryTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c840cd1858004564ef7709a581871b81c685779114b71f4a07fe26c3bc66a4c8) | +185 / −0 |
| [XdkLiveWorkspaceTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLiveWorkspaceTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3d03a7ce4cdb9c4b4c68446097e49e264a409fb0ddb9c3b8dc8b4cfb9ec7cc8a) | +177 / −0 |
| [XdkLocalExtractionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalExtractionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1fe2e0cb9b499c138484bbc0aa7b800ba56122a5cf9442af81a6c525c9706062) | +143 / −0 |
| [XdkLocalInitializerActionsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalInitializerActionsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-305b7b212fed3c4e625f9fac27b2a5d15cce89bf794f113bfd1c0063c3009d6a) | +219 / −0 |
| [XdkLocalInlineTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalInlineTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e322314510c6e46c4cba9e49397a8fae423a89e4cabdf0eb015ee41090d12899) | +146 / −0 |
| [XdkLocalRemovalTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkLocalRemovalTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d634c606da61849bc2580faaa80d980975ad71a3fb6cc9cd09bfabb6a47744af) | +137 / −0 |
| [XdkManualCompositionRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkManualCompositionRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d187c38b504bd8852de4ef8a69df1851e5331bc0ab324678e67e04268abdd613) | +132 / −0 |
| [XdkManualCompositionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkManualCompositionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ac6c79d871d1878496f2b7ec7b6c3def6b67fd0f71e3b97a9217565879ec8f72) | +64 / −0 |
| [XdkMemberActionsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMemberActionsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a9949279d52b6eb3cd521adcc7362a530aafbe5431e045850d6b616debaa2635) | +641 / −0 |
| [XdkMemberInlineTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMemberInlineTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a22415fb06fb99a1c7c0bb6b8d60ec4a6fe43b6236044cb7efd62cb62e377ea9) | +148 / −0 |
| [XdkMethodExtractionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMethodExtractionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-57dd13db32213fe790cb6e851ad803ae18a4fe1916f087165fb5349468d52228) | +381 / −0 |
| [XdkMissingDeclarationNameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingDeclarationNameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0994c719921d2f9e45408ac37b078db9ed4f522ffa1c733248428f22758f3de9) | +134 / −0 |
| [XdkMissingDeclarationsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingDeclarationsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-edc45bb67c47b3a13c37194f144136bec6a1eed4371dbae006656d84741d8e5f) | +119 / −0 |
| [XdkMissingMethodsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMissingMethodsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-951c7da7a9ad7c8a1db4488036044974928d825e9fc94d644dc76d3617a1c755) | +688 / −0 |
| [XdkModuleSessionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkModuleSessionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9d046eb4d20f1ac9601cdaf3c1ed8a3726699d51167176e2c20325f617288608) | +284 / −0 |
| [XdkMonikerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMonikerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-09256230e8cbf29f66ea403a9ce2f678b6653f05dee35814a2086bff336d4131) | +212 / −0 |
| [XdkMoveInputsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkMoveInputsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2996b08846735ae0927d4ec492ef14f41dcba3197c5ab7733a9de97889ba5b2d) | +30 / −0 |
| [XdkNavigationIndexTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkNavigationIndexTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b6a56f4f379fa5aa1f2c73686cccf5f4b2d8d8f1cbae52bdb95fe121f9872a24) | +233 / −0 |
| [XdkNavigationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkNavigationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7ac25dcba6a1a6f74220739ccc9467c20fc6bbc2e508b2e4a905b1cf43061d3f) | +521 / −0 |
| [XdkOperandCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkOperandCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-95f01a1dfbd6e4287e766cc72baf5951a0f25e54efe3ef4170333cb63b8cbb15) | +117 / −0 |
| [XdkParameterRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkParameterRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-792a986118b16906e60f468d7819308c5ce9151236c9d932c5f510cf54555e6e) | +187 / −0 |
| [XdkPartialAnalysisTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPartialAnalysisTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2594211d80c9ad933ab32ab10350e172a873efef7f060fed3b2b70adfcd9237b) | +952 / −0 |
| [XdkPlatformRegressionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPlatformRegressionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fc418eb000b18d3f8808feb38d39ce5a1975e5255b0e8b9acfa272ca3ff4412e) | +194 / −0 |
| [XdkPresentationScaleTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPresentationScaleTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3a237c08026441cef0044fbce630d6cca9d2efff2726f5248e6e0e803770599c) | +86 / −0 |
| [XdkPresentationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPresentationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ed69f9e18630f618a9a68f9dc56ad5b1977b138b9b300085ddd01795b8f96314) | +247 / −0 |
| [XdkPrimaryParameterRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPrimaryParameterRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-79e0a715f15d291a36a2a3bc988f961fd142ff1e7583cc352be6c5574188eba5) | +112 / −0 |
| [XdkProjectGraphTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectGraphTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-faee9ba39bf86566b9ba5a3d54d4827351937a8850c1e00d76247ea319af79a5) | +99 / −0 |
| [XdkProjectProofMemoryTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectProofMemoryTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4eb794bc21d7cc542eaac371113de0c902ef309f7c55e735c7d1d045630db429) | +104 / −0 |
| [XdkProjectQueryLifecycleTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectQueryLifecycleTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4cd2a758dc5681482c94dee3d709913db80767941acb6776616123173cb383dc) | +301 / −0 |
| [XdkProjectQueryTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectQueryTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-884fdbef4f58f8e3f50c386361fe7641407b87281d8c3f2e78d2455af7df74e3) | +573 / −0 |
| [XdkProjectTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkProjectTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a7d58a19cfd0c07fd1b5cbc2028b37836723b6f3f44306cf9e36a2edd18e7bda) | +302 / −0 |
| [XdkPropertyArgumentCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkPropertyArgumentCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7fe93aa9de86bb37c5f49949506c56c804956cbb9e0575b2ff9ad6320c447c52) | +270 / −0 |
| [XdkQualifiedHeaderTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkQualifiedHeaderTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f54e343486c65f6dbabf55e90ac979ebc31f3705fe6a695eebf1f21ff8946df6) | +240 / −0 |
| [XdkRecoveryTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRecoveryTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f12f2a60243a7100091bc1dea70fafc510fe839d51dd4ce0550512e189ca3dcf) | +145 / −0 |
| [XdkReferenceLensTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkReferenceLensTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1f6306ee6c2311365bb25915385bccd9a2b1d26fa74d01358c820336aa7dd19e) | +117 / −0 |
| [XdkRenameBoundaryTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRenameBoundaryTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c5350d6f03b49c977451a543cecbd4a590376c98db9121c1300647f5ebd78172) | +116 / −0 |
| [XdkRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e1d08518b16c77a3836d108131fd9955cff183789428d1f393140c51a49b3367) | +228 / −0 |
| [XdkResourceInputsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkResourceInputsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-64a990baa7fa9b2d2f510a68504c4da2edf4688204ef4b77cefd4721cf6ab2fd) | +159 / −0 |
| [XdkResourceRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkResourceRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ea95f54c11e9ea6cfd0d0ae4e8f24f9230a3142bf156262136af1ccbd2f4f330) | +317 / −0 |
| [XdkRetentionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkRetentionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0454423e474b4f7bcdba8c6b570a9dd8bb2516ff6b626903ee5cff513c67c811) | +194 / −0 |
| [XdkSafeDeleteTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSafeDeleteTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-74dc2ff50f42220cf4c61e7f6f6cb8d248f8a1851416eb255fc2f3a6e5cafa41) | +108 / −0 |
| [XdkScopeCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkScopeCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a2aaa0411e406c536372ed804a8fc45350b7b6aeb1aefbd7a985ee02dd7dccd4) | +187 / −0 |
| [XdkSemanticColorTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSemanticColorTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a1c2b1cb31809f6b817364e538f013578a46b2b81c4e4b8e3a241d048eabdba2) | +313 / −0 |
| [XdkSemanticLookupTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSemanticLookupTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-07d517d9c59acf1f55e99cdda02ecaf434ec5bbd7168cb6cfd643e6bdf799f91) | +758 / −0 |
| [XdkSourceLinksTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSourceLinksTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-62a84af1413db2ed9114aee23201e3726a775d69036dee062b23a3c9d51454a2) | +125 / −0 |
| [XdkSpecializedConstructorTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSpecializedConstructorTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f5a9229ca88755a39d4c4690637f220abc78c4c82f8951e4070e159e292f7e54) | +216 / −0 |
| [XdkStructuralTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkStructuralTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0a91ac33d65254022550023b8298aad17c4bc59e9a4e27c16a21b6f694bd96d7) | +68 / −0 |
| [XdkSyntaxCompletionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkSyntaxCompletionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4af43762b783c53e5b3ca3013175c996a5aee53e66b8caae8587a85a9d1fdcaa) | +204 / −0 |
| [XdkTeachingWorkspaceTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTeachingWorkspaceTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d00d150daa7519792915e387872fa16c0fbe94608541146fd8ffeaad57601ec0) | +237 / −0 |
| [XdkTypeHeaderTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTypeHeaderTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d273c1b8a206877584b11c11a7f2e8735996d7b12881408e32c88bdb975c69bd) | +195 / −0 |
| [XdkTypeMoveTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkTypeMoveTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ee863eb59836b9c097d3e50d4ca27896d28dd06b25d70399ac94e173aea5739b) | +650 / −0 |
| [XdkUnionDelegationRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkUnionDelegationRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-729f8a7cc208d19d149fba60d599c28cf51f43969f5af12ef03a6311c9dc873c) | +150 / −0 |
| [XdkUnionRenameTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkUnionRenameTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b3ecab4ba5f28efb8a90722f928515df272237d501056299c92ddc02210f8f9f) | +323 / −0 |
| [XdkValueTemplateTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkValueTemplateTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9e6d949689b43a0878d0886e081ae7c0d2c5744b8bef1bd00cb3460433b04ee7) | +54 / −0 |
| [XdkWorkspaceDiscoveryTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceDiscoveryTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c9d046859fce924d399635f1b3121c1ee88f5e283d5e90adfd792cd336fe9e2e) | +108 / −0 |
| [XdkWorkspaceNavigationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceNavigationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7ec03c9355eb9620599e40a6df97a170d40978649d3fea4e284249e26791eff7) | +98 / −0 |
| [XdkWorkspaceRefactoringTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWorkspaceRefactoringTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-223af9619a33e3fab5ad67a0c12cd9de92f233a047031184b9567cbff2aa7098) | +349 / −0 |
| [XdkWrittenFormalTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/adapter/XdkWrittenFormalTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c3d7a06e4a23139dd3aeb6c9092298ae81f12ef4723207e4795baaa7a8e67f53) | +212 / −0 |

#### `lang/lsp-server/src/test/kotlin/org/xvm/lsp/server`

| File | GitHub | + / − |
| --- | --- | ---: |
| [AdapterBackendTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/AdapterBackendTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-997065df24643cc62b1c03a7af56846783c24e7de0c4f1809f3cd04589c31f65) | +42 / −0 |
| [CapabilityContractTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CapabilityContractTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5050dea4b9d443e8efb595ed5fa04e8b81695e9500542b71f18137fad9e7a97e) | +100 / −0 |
| [CapabilityNegotiationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CapabilityNegotiationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-54e2380cc0c7b973118f214e504ce8624fa1486317523a8df73c523ffccf19d1) | +406 / −0 |
| [ClientNotificationsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ClientNotificationsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e701726c728a33ac567482fc1f6d0857fe2c42fd8d65d02165e500d836d3b6d9) | +130 / −0 |
| [ClientPresentationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ClientPresentationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-faa060931bfb111e8d2ca2ebc9bc7315849785b2eb7c8d93c3847bdbfc3d272f) | +165 / −0 |
| [CodeLensSettingsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CodeLensSettingsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1cbdf9171c85d6838d120d95f8d908ab86e3a4ba295550cd95e28c038206fc5f) | +18 / −0 |
| [ColorValueProtocolTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ColorValueProtocolTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b6f3a8a271c5f6aba92475f0e78da4d96d82b3192555028778881b7899feee19) | +138 / −0 |
| [CompilerConfigurationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CompilerConfigurationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cf6d6faca06ebeee12d368de3a9b6310e14237d0e94662dc867ee1d8cffdd689) | +450 / −0 |
| [CompilerLibrariesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/CompilerLibrariesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9c3893383172196b9eefde93ab5ea3568b447c256f0fb370159f6eb9f55f03e0) | +115 / −0 |
| [ConnectionProgressTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ConnectionProgressTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fd1d98427c302ef3c39bc32b837b94e702898c0b9d3e9245a1930311f7947a63) | +324 / −0 |
| [DiagnosticPresentationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/DiagnosticPresentationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-38fb2ee6355f35168684e9eacf429df383428472fc20eec3b3285ce485c89028) | +107 / −0 |
| [DocumentSynchronizationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/DocumentSynchronizationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0a1789595122172a0840316f3417634e7b341ccf3c05591398c7782b99360946) | +287 / −0 |
| [EditorFormattingStateTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/EditorFormattingStateTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5e3f421885ad8c5871c8a662ab2aff7c6caa60441034c9e357f7df2b4a92f991) | +50 / −0 |
| [ExecutionTraceTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ExecutionTraceTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0b6f4cc5f0142596909478adfebd39a97d5fe5b971a8383876493315f227c6dd) | +213 / −0 |
| [FileChangeSnapshotsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/FileChangeSnapshotsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-69512cc35a9363eba8e1b1fedafd9a1f54e9955d317c9318fc79c77f570f62c9) | +44 / −0 |
| [IndexingProgressTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/IndexingProgressTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-49e04ba4bdb7edb757b76f827d09a96407aa1d6e91ad418c525b46ab7161f98e) | +109 / −0 |
| [InlineCompletionProtocolTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/InlineCompletionProtocolTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-80a72ab400340db72ab6811ed949f8935173140cb8fa2db4a91b4b64e7d90fd2) | +149 / −0 |
| [LibraryContentProtocolTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/LibraryContentProtocolTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-69d8356a392dcd56e40309dbdc38c81bd76a2e89a436fa68600205444cc5b9e3) | +166 / −0 |
| [MonikerProtocolTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/MonikerProtocolTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-55ae67fdebef8545c5eab19b1eeed7749ad9b3f44119fc5feff612c0347ca8a6) | +114 / −0 |
| [PartialResultsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/PartialResultsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0949829fb6abdbbd45515f69e7aaa6e692ce6f310bf8d8a6d420aa6dc5626a08) | +168 / −0 |
| [ProgressLabelsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ProgressLabelsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-883531bc1b98e93e02af41e250aeff3b143e35411668662450b2b3bd820ccdf1) | +21 / −0 |
| [ProtocolLifecycleTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ProtocolLifecycleTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c26f655d6e03931ec9964ec09bae7af88c2ca37716e90f19c52c1fdd4a9e2a87) | +112 / −0 |
| [RequestOwnershipTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/RequestOwnershipTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cb73249628babacbc6810a8b6c9906fafe842ecb74a52bef8a1432507ce9a93d) | +86 / −0 |
| [ResolveReportsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ResolveReportsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b3bf2f99bafad452bdfdd2c0373c2688c5f19e3868e369d80c0edf115415ba7f) | +29 / −0 |
| [ResourceFileWatchersTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/ResourceFileWatchersTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-75e5e0e87ae9eb79670f022aea3d03ba8db1ef1f4d72a072d7adac0c4f2afb15) | +232 / −0 |
| [SemanticTokenReportsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/SemanticTokenReportsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-061064ff5fe18ebb420254fe4460e36615726403e8c8ec8728401da22fa791d5) | +71 / −0 |
| [StartupRegistrationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/StartupRegistrationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0c39f10cec7c13de47b13d9cb20c6e6900a4cf40f3f0d61cce815da2bd0ef023) | +50 / −0 |
| [XdkCallbackServerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCallbackServerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a6fd39f3ea83d70430d6f67011d66f71b1819782953743e0550cb1c5c9546cdd) | +183 / −0 |
| [XdkCursorServerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCursorServerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-57183d983c9aeb448760d9eed5b15aa6d5024e121737ac315d415a2df7b96af5) | +373 / −0 |
| [XdkCursorWatchTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkCursorWatchTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2791aa45e5f9095f339620eef5cc83a9a7fea6409b1de8d38d6352d0098c6ad5) | +103 / −0 |
| [XdkDeclarationServerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkDeclarationServerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ed0189eed9ceaedbf63c7356290835ecacecc20ff6159eab7ea01f6ddcd85acb) | +62 / −0 |
| [XdkFeatureResolveProtocolTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkFeatureResolveProtocolTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-870ed0cbce37b7b6c189773e283af75f356a9db6bd8e1658dfe99cf220f0c50c) | +282 / −0 |
| [XdkLanguageServerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkLanguageServerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f6524e63e107e180152c68dc9e736bf68aa07dc0c48efff36941382e1ecf3f28) | +430 / −0 |
| [XdkModuleServerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkModuleServerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dd30171eae7023557e9df4bc7b6fd68cff5ca773ec718e69a7a717e6993b69ec) | +269 / −0 |
| [XdkProjectServerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkProjectServerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-71a1727a5b72259e88a187cf944e62c7eec211cb8c0d4a326ce9f942df122745) | +467 / −0 |
| [XdkPullDiagnosticsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkPullDiagnosticsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cd67d8a921d474593d195b479938f6e9bc8c28770942c825e1d157c85e9a40ac) | +444 / −0 |
| [XdkRenameServerTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkRenameServerTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-425726b9acf77de0872630c062ffbbb2eecc693090fe259495be65cbc166cee7) | +729 / −0 |
| [XdkResolveProtocolTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkResolveProtocolTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-da300575ab0e20a3e9095daecfc55535f8129932cd115becdab9d6b2c1f7f117) | +220 / −0 |
| [XdkSemanticTokenProtocolTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkSemanticTokenProtocolTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dcdf852f922074240f8c25e95a6acc0f5f43c1125d193395db7987f117e24a52) | +146 / −0 |
| [XdkStdioTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkStdioTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-71b3a87a740997357e40019dfdeb17e15ea79c89012a656e2b472f376abe52bd) | +2079 / −0 |
| [XdkSyntaxCompletionProtocolTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkSyntaxCompletionProtocolTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b9c7e12b3a9c96f8af099cec6611ec33bff23d794522db12994ca80bdeb53b2f) | +143 / −0 |

#### `lang/lsp-server/src/test/kotlin/org/xvm/lsp/util`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ServerLogsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/util/ServerLogsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-198813b7897a657d241b5b32d676895bae5f096ea1d64512768e9a2325ed5c8d) | +79 / −0 |

#### `lang/lsp-server/src/test/resources/platform`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CircularBuffer.x](/private/tmp/xvm-full-testable-lsp-20261009/lang/lsp-server/src/test/resources/platform/CircularBuffer.x) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-105c8b9e51bab66bc74f130346c09c7b008f3a284e6afad6314c0ff87266b531) | +132 / −0 |

<a id="full-reference-new-intellij"></a>

### IntelliJ plugin — 147 files

#### `lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CachedIdeInstaller.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CachedIdeInstaller.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-90c149c458b460d0d6b7a037afe0a2c715e86a1f77d85bc5a0da05d39b32275e) | +55 / −0 |
| [ClientDiagnostics.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientDiagnostics.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e58012a29f6ba36a05b598c9fab9841bdf1de561215709493a3fcea97f76a624) | +151 / −0 |
| [ClientProtocol.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientProtocol.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-978adc20b88c066fface112b62ebd264cd3f3f00e5eda24bb8ed038dacee5f9a) | +216 / −0 |
| [ClientTrace.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientTrace.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-03af3376d2554deeee12c1160024489292d60b06c1233d35d492508c1a7a7904) | +195 / −0 |
| [CompilerPlaybook.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerPlaybook.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fcbba3b037a2057987e0e5cfc10dbb28736fa322ba4a7af212a37d336bcb19ef) | +1989 / −0 |
| [CompilerPlaybookTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerPlaybookTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-84ea676a5caba8e9b55927382b6a46a27432088f349aadcbd4b923a32521b248) | +385 / −0 |
| [CompilerSettingsPage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerSettingsPage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4017b05d7ee9ab2cbe34cc85bd3c4f6a5c017cce4d147100a0d9882cdb9b4644) | +27 / −0 |
| [CompletionActions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompletionActions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-670a3713b87c7d65b2bfe8ef555218ee68300fabdef466b811ceb248ba33278d) | +219 / −0 |
| [DiagnosticProbePlugin.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/DiagnosticProbePlugin.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-911790acdcf00a31671f26be83205e5875347fdf2f1fc20e620bc8dcffa5b056) | +47 / −0 |
| [EditorFocus.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/EditorFocus.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f3f63160fa3dc18e0168033ec047e5d08f3dba20574591911a67013dce3c72ef) | +180 / −0 |
| [FocusRecovery.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/FocusRecovery.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-02fbe493ac1ffb64f6b3b3bfb0668167a1a7b7291e1a2ccc7f1a533fb47f98ee) | +120 / −0 |
| [IdeServices.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/IdeServices.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-579337397a299c2e843c3f697612bad18e37ee65660619fd71c8719f5fbc036e) | +148 / −0 |
| [LanguageServicePage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LanguageServicePage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8c1b8180dfde768b49a9f6c54143eff2c1f6079f0911b9eead38849a6b2b69d8) | +49 / −0 |
| [LargeFileEditing.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LargeFileEditing.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-daba65a92a323f6a1d5521a774bdf60dfd9846e57ba43e8dd6d6b78c0590dadc) | +92 / −0 |
| [LocalLsp4ijPlugin.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/LocalLsp4ijPlugin.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-aaf551cb8bf4015360252deda9f33f2f41157de986681f66f5ef840761da6996) | +57 / −0 |
| [MemberActionScenarios.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/MemberActionScenarios.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-23f0aa749473bba584467750be3e7f29643f36701293480898fb080e3497bf50) | +83 / −0 |
| [NativeDiagnostics.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NativeDiagnostics.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-743756edcf622edc134055315f3bd647fe6a1754550cd01772188b70806abfd3) | +33 / −0 |
| [NativeSemanticViews.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NativeSemanticViews.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f8b4a2660322f29d44c088985077ac1bf1be5e25dad2e93c39fac7b3f5d9a855) | +312 / −0 |
| [NavigationChooserTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/NavigationChooserTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4e35fd199b01e8dd30468bcff60a831aa571549cacf48d6b266fbddd2d47b3fe) | +69 / −0 |
| [ParityColors.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityColors.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9f35f1b915dbe82f39a99e688aa112935822c14f5c8d7e4fd89f2ce028ce22c1) | +157 / −0 |
| [ParityCompilerImports.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityCompilerImports.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f1a0bd48b4f9a23b174472a9581d3b5a8806412287e1212d4e99b849d5cda478) | +315 / −0 |
| [ParityDependencies.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityDependencies.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b65d4e69d784c911bf5dfce70ffc599ecac78edc57b21e72ed7db425c980d4fc) | +150 / −0 |
| [ParityDocumentation.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityDocumentation.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-213cb4bb42d1e5166d4c5c22a50bf14a655e2e703aa08f07da4b817bae9acfc5) | +37 / −0 |
| [ParityGraph.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityGraph.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5f03d1e47d9fb5a40c9de3cf7c35b208ec69bdb5d16eb8fe18ed69d543711a68) | +313 / −0 |
| [ParityHighlighting.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityHighlighting.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0b9ddc66c19739809acc012492a4409cb881d5b6c0fd0fdacc9c214c2a3e9eb8) | +147 / −0 |
| [ParityInlineCompletion.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityInlineCompletion.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5f1728320b90f7519badfb2be4bccf4c5d5c3394ab55ea33953e52dd2dee6837) | +96 / −0 |
| [ParityLibrarySettings.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityLibrarySettings.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fed8ca2d25bee11b5884c1eeaae80989e5a2e2ba3a0a3a53fb78659416c1f8cf) | +131 / −0 |
| [ParityModules.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityModules.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5c3a49f4fb7e88ff3cabbdf060bc173dce550bf2758da4f2640950cfd0ad4e5d) | +271 / −0 |
| [ParityNavigation.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityNavigation.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-983d536c2a758ee04ae2024ec3e6f6b66adcfa4c5f65f82419f88d5f1f7ac369) | +329 / −0 |
| [ParityPlatform.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityPlatform.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e06857d0a011d3d6d87ccfe58c4891d0c1d81a96b3f0162fbc79ce9922eaac72) | +1225 / −0 |
| [ParityProgress.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityProgress.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-887c025d44c436a2c9e67cf7339275eff56a8ebd1dfe7a9a8e74efe4b51eec28) | +123 / −0 |
| [ParityReferenceLenses.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityReferenceLenses.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d59f20bc75be65e22df02ca420b62321455378def43b1149c7d19b39b3fb6120) | +77 / −0 |
| [ParityReliability.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityReliability.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2b4a420cdb802efe26fdf7cbf39f4e84acbfad4d697d4bad458f9c06d3e5ce62) | +285 / −0 |
| [ParityRename.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityRename.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f9b56f9a161292b07c82c25bba0d403ab8f48411f5b4b4cd280bc1fcd7b761ef) | +215 / −0 |
| [ParityRuntimeSettings.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityRuntimeSettings.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-50a4104a2a9e19a34e756240d7865eba583796f82298ab9dfa14c7462e6d324c) | +178 / −0 |
| [ParityScenarios.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityScenarios.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bc3d16049231ac6acae978210bb990ff8900eec47c81e31c9fe516c1e6d2f6d2) | +56 / −0 |
| [ParitySemantics.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParitySemantics.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2c1ad7a3e63f7bbb5aa29825e10572c02833ae0455c1159912e428b1a9518f4f) | +348 / −0 |
| [ParityTypeMoves.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityTypeMoves.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-59aa12c732ba6ac63d447900fe77250f5ff62d4982698a14d2784541ad4df08b) | +205 / −0 |
| [ParityWorkspace.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityWorkspace.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4f4934074055164943122f245bf5c8465a590b94088e25cfa20e083536da0e86) | +514 / −0 |
| [PlaybookFailure.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookFailure.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-397bf245081c67ead9f629cb72d6e60a2938da34c9509b3aef5c7fb3eaf575a1) | +15 / −0 |
| [PlaybookFailureTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookFailureTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-901a257f83bf7d1667084cd8b0c14cb472a3140a888775cf9d6d066f67eaa59d) | +34 / −0 |
| [PlaybookProgress.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/PlaybookProgress.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a6a6e67ae180f533e15db2ee42733fd89cba6e7cf018d70cb84c567869ba539e) | +22 / −0 |
| [ProjectLifecycle.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ProjectLifecycle.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-afe4dbee1b3b33cf50544e1ff9b8bbc6b1edcb8faa47bde316c3970b044d8a5a) | +208 / −0 |
| [RefactoringActions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/RefactoringActions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-136c012a3b1c7569379958a4ac4f8a5b66fdd6c48930a21554267102ca88af9f) | +296 / −0 |
| [ReferenceActions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ReferenceActions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-feeb17ec6dfca5ea7a205be803bb19ff866df90805c4f04ea9070425333940b0) | +139 / −0 |
| [RenameFamilyScenarios.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/RenameFamilyScenarios.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-de48eee50c0449d1c0a599df9090fe86c880dc49003ea4da9f1fec1c196aa673) | +143 / −0 |
| [SettingsPersistence.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SettingsPersistence.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-57944fe6f96daf43d8da82690222b3f0110a47420b60256d8670aa4d68e67b7f) | +89 / −0 |
| [SharedScenarios.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SharedScenarios.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-eda56be9cdc7903080c761fd112d469d56c72421afbe0137bcd70c5b738514d1) | +264 / −0 |
| [SignatureHints.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/SignatureHints.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e3e87bc1bb42ff5f002e58981aac0acfc5bae7ce60202ae0fb435fe4f15e8581) | +143 / −0 |
| [StartupEditing.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/StartupEditing.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-075e5b9c9a8bb7dab8e6a455c7767c6993dc0a8233b4cc2ab0784238f4cc3421) | +144 / −0 |
| [StructureViews.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/StructureViews.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-86dfaff1ce72a60c35f824e99aa0badf961faf869327cb714ba55338e1d4c754) | +175 / −0 |
| [UiWaits.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UiWaits.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-337154f4b7f2812a0a48424f726396a87f7f180d8388d78cdb87cd7959cb5610) | +62 / −0 |
| [UiWaitsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UiWaitsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-94f7bd4c5ae78bebaf4fcad31f9c85b8560f5e3b6a39ea023a14821c2bd620dc) | +71 / −0 |
| [UpstreamReplacementGates.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/UpstreamReplacementGates.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-df714e7423526f03718d661a60f0d612541e347667d61fccdc824481f3312680) | +192 / −0 |
| [WorkspaceFolders.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/WorkspaceFolders.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-066c730bbd8bc138c1296d034545e4de1445d7004ffd5bdbaa707d1943aa5e41) | +50 / −0 |

#### `lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe`

| File | GitHub | + / − |
| --- | --- | ---: |
| [CodeLensUi.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CodeLensUi.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-27f19ccd98baf598eae31e42c806f4dd9379ea19fce2d386953e545692391b91) | +48 / −0 |
| [ColorUi.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ColorUi.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ac84babb62a980b41278f4ec0941a245c00304075ae2293800dab61184be1b13) | +165 / −0 |
| [CompilerImportPage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerImportPage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8ab75e18264477881569fd9c5f270cedb513f2ace2c35988716741b501fa23c9) | +103 / −0 |
| [CompilerLibrariesPage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerLibrariesPage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e4520f50552820d57a2b7f512ad798f3a7f30e4825cee3587afa318f6c8f467e) | +114 / −0 |
| [CompilerReportPage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerReportPage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3e2dd3dd37f16708ed7ce4ac6c5bd9b40a8afbcab39600f11368f342715a4672) | +87 / −0 |
| [CompilerSettingsPage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/CompilerSettingsPage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a45e700eb1965896a551fa1639ea0715d488fa06d9fd5a9e9f4c73d2ead1a6d7) | +173 / −0 |
| [EditorDiagnostics.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/EditorDiagnostics.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-73190d4c4010add89411b3d7c7221fcaa418c8b4c32fa0eeb2228ab5c09ddde1) | +33 / −0 |
| [EditorUi.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/EditorUi.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-27db71665c04ac942984b5af0b952aa48bdd3d85bdbfaf7189e167cad971a6e8) | +154 / −0 |
| [FileTreeOperations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/FileTreeOperations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-46412b793512831b701bf6313e69ffd1c39b5d1fdbf2063a67cf993086ad3dce) | +116 / −0 |
| [HighlightingUi.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/HighlightingUi.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ab2e57b15f87c970f63fffb11119bf6d5898d4208261c684dd440e75a19d78e3) | +34 / −0 |
| [InlineCompletionUi.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/InlineCompletionUi.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ba662c318d38362b7409b6aa8b051489448b580f7c6136de40768dae614e582a) | +30 / −0 |
| [LanguageServicePage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LanguageServicePage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f618ef7315ec9e9a34686dae83c584f1d4df778189dbd54c5dd77794a26efad3) | +182 / −0 |
| [LargeFileProbe.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/LargeFileProbe.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-06eea97d414ef59018d24f83fa4c61fb11961478600c6768e2b7eb260de46b9d) | +112 / −0 |
| [NavigationChooser.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/NavigationChooser.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-425e243d0b3002732119f9440bffe2287fbd16bad6046156df0ba8eca84e11e1) | +36 / −0 |
| [PartialResults.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/PartialResults.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d03ad33b7fc39d9f1e5e0b9a42d2adca2a203da063ec556b5f67a635af15067d) | +57 / −0 |
| [PlaybookProgress.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/PlaybookProgress.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-af0224fdb8bd5f9618738080e1b2d295c7262b18bd8da1eb661584a39fcdd29f) | +84 / −0 |
| [ProgressUi.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ProgressUi.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-261d0d91ddade6b8b7e072f45fe71d1f70b41bf74a0e815f023706c94481ab14) | +68 / −0 |
| [ProjectLifecycle.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ProjectLifecycle.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6864c515510e8796675cf5d82140698378cae262eba2a85c0bc0ff5c557324e9) | +96 / −0 |
| [RefreshRequests.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/RefreshRequests.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e6159d7f779da8ded73759509a2c8a4ab90dd42877f2537634c1cedf8293c7f9) | +122 / −0 |
| [ServerRuntimePage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/ServerRuntimePage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9c1313e79e3c0076c9cfb9cdcf801ed429ecb460073ff829d9666f7bdc08ea4a) | +96 / −0 |
| [SettingsPersistencePage.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/SettingsPersistencePage.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b2dbba8dbe3f55522cf9f2b27b0d650b1eb00e485c1fc63592826457f5b54355) | +56 / −0 |
| [StartupEdits.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/StartupEdits.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-07bda071f9c7afbad208e2b068a0b9aed30bafa5bdc49433268bc496a38f67d9) | +86 / −0 |
| [WorkspaceEdits.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/probe/WorkspaceEdits.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-603f5299c31933785960fb3a5d393f06d282ba25d46af68daf5c59314744fc0e) | +29 / −0 |

#### `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ClosedRefactoringDocuments.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ClosedRefactoringDocuments.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1ebdd7ef29c43e7f3bc6760954936b958b2feaa1bc2a0ad1827261bed6e7ad86) | +81 / −0 |
| [CodeActionMessages.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CodeActionMessages.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7e074cf56ccfaf2fc7f1e6ba691f3a24198b754695f254c1a4cb3745f4b1bed6) | +28 / −0 |
| [CompilerBuildModel.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerBuildModel.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e3d1807061a2edb972392fc78e0ce06a83a6525bcb13b0fc096eb081d93f66d8) | +310 / −0 |
| [CompilerBuildUpdates.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerBuildUpdates.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2e6f70d787dc4191c6f0a545d9c8610685d72d3f4e0bf41cf0f8d0e2b10ad4e9) | +73 / −0 |
| [CompilerImport.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerImport.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-616c0c2fad9dfdb53cec8595f730a48a447d2a119515b94a16c23ab66a071f16) | +143 / −0 |
| [CompilerImportService.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerImportService.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-aabd93348db245580d10b052c9c4176eb386ba9e8872c515aab88e0e36796065) | +57 / −0 |
| [CompilerLibrariesPanel.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerLibrariesPanel.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b7e9505c5b1f296d549904596e8f5bec343dc25f9f05dc50cfe6a140d97ff1e1) | +108 / −0 |
| [CompilerProjectConfigurable.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerProjectConfigurable.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-49c0be245546096fdeb0fae2cd8c295a83612e7ebe586197956774ce269099b8) | +476 / −0 |
| [CompilerRootWatches.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerRootWatches.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-38f33ae1a44b799d72c017dff066abeadcf6eb0158e17186556a5d11724e8c71) | +199 / −0 |
| [CompilerSettings.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerSettings.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cdf5e6f66f0a4af7e419d02484acdf516f7883be87d0efa49938132a7b92431a) | +33 / −0 |
| [CompilerWorkspaceModels.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/CompilerWorkspaceModels.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8e3c0965aaddebe1aaf0a3bfb391994cdf47a519b59ac1bf98bee203e15ce919) | +77 / −0 |
| [ConfigurationJson.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ConfigurationJson.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-39e6b8b5caf86b0384a042e4a0c885ebc250a7b834f783de15b5eb84c11a5c55) | +39 / −0 |
| [DiagnosticQuickFixes.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixes.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dd08faa0763096e8f14d27f0d8dc17408a400859df452a5b2a5a78f60699d836) | +74 / −0 |
| [DiagnosticReportJson.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticReportJson.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-686d7c6deb471a9a41e4c638a2c5d2dd076e548631b0c06a7bb42b2bdb68a064) | +40 / −0 |
| [DiagnosticResultMessages.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessages.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-114d19de9f6ddb20643f5d637b69fbe35646363c7e95695cc18ee363f5397f4d) | +110 / −0 |
| [DirectoryDocumentMoves.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMoves.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dbb71ff2f65acbaaf2e6a4a5ef6e07eab962f53235528ccf3b7676927ccc3d3a) | +101 / −0 |
| [DocumentStartupMessages.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/DocumentStartupMessages.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ff6d57a1626891548af67a3f33a78ff15e6edcd2ddab826ff2f02d2d742e0b40) | +275 / −0 |
| [EditorRefresh.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/EditorRefresh.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-cb951b51544cdc1bdb55befa8cc4d5f91525751810968699cac7d3c242f4d0d4) | +90 / −0 |
| [ExportServerLogsAction.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ExportServerLogsAction.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9493a479c88b135cf4a9bbf285bcac028507586e842c8366c076b13eee51d13b) | +75 / −0 |
| [FileMoveTargets.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/FileMoveTargets.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f72fce37a75456639439782c01a0f9c4cee746e51fe238228534d83d77e1271f) | +41 / −0 |
| [LanguageAdapter.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageAdapter.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9f5d5bba4593393e1b067c1fe0f1ae8fe07dd447566e46d95a0d1da0e1dcebfd) | +23 / −0 |
| [LanguageServiceConfigurable.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurable.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-386b05f4fdf509c64687e88f3c69707ba1e5bc311f72f396de9a5aef30239e5c) | +212 / −0 |
| [LanguageServiceConfiguration.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceConfiguration.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e3b8b9cc034fa5a111c9314e87cc5c58a4511ca76cd52c029fd88cc66d3398b5) | +144 / −0 |
| [LanguageServicePreferences.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServicePreferences.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6d6027fc8b8058da327a27b4982350a32b00a54371f258826b7a01c750b16d40) | +24 / −0 |
| [LanguageServiceSettings.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LanguageServiceSettings.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3d14ba8eabb44a73c471a778d7807111f6a6bde78798373c84acca33f4172046) | +33 / −0 |
| [LibraryConfiguration.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/LibraryConfiguration.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1caee30883447b2beb33a80c8f3877e4d3aa4e639d37d4919efdb2ea75bbe61e) | +110 / −0 |
| [OrderedPaths.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/OrderedPaths.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8bc56c6c3c025c60ffa63051cf65b52e3984a6cb509f9676a8ab977ce0025b92) | +103 / −0 |
| [PreflightedRenames.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/PreflightedRenames.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2dcf22fc4fd63af93c512afd2cc6e0654fa37d54760a02453b61fdd3a7a9c3f7) | +69 / −0 |
| [SelectLanguageAdapterAction.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SelectLanguageAdapterAction.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f8c2c72ec444987e1e6496871c82ce18659cb9d1ec2065150c1ef575c846dea5) | +32 / −0 |
| [ServerJvmOptions.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerJvmOptions.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8a0a7f4476891f798063d971b59768f3bbcfbac69c99c174577537e4739b0515) | +54 / −0 |
| [ServerRuntimeConfigurable.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerRuntimeConfigurable.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-33a8edda31eed5496aab23a1595e75206e881550371c1b90f144e9894eef7246) | +107 / −0 |
| [ServerRuntimeSettings.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerRuntimeSettings.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7372aeebba82649317292c5572033acedbbf59da4c0caac8a08b6be46354685a) | +62 / −0 |
| [ServerSupportLogs.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerSupportLogs.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1da0643171f3fd52f8a2ed50729b54072bd22e5a5077c7464ca71d0ed9ae1682) | +159 / −0 |
| [ServerWorkspaceEdit.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/ServerWorkspaceEdit.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5e6da03a9c8addc1955498fd19c6f7319f06db25d34972742562936250eaaca6) | +209 / −0 |
| [SourceGraphConfiguration.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SourceGraphConfiguration.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ae01bfe93d7487fb61d18d43064fa3dd8fb2967d1fcf46ab58335e72ca55d690) | +185 / −0 |
| [SourceGraphEdit.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/SourceGraphEdit.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-236e0fa18e66084fc46ed45af7f86d103ad1308c3e17f9d4d1b1183816b95afb) | +126 / −0 |
| [XtcClientFeatures.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcClientFeatures.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-166f85f418b6aa3a195e047e5b338858aa8c2b71932fc789d8c95e7c199f5897) | +158 / −0 |
| [XtcFileMoveHandler.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileMoveHandler.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bd2c72cffd4fb4028f91ae8330d910a0dfe970df2535cc363e33e934f0bc6cf0) | +150 / −0 |
| [XtcFileOperations.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileOperations.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-db898e7ae061cda68c2f73a8da40889bd143d5b7c7a731b881d5207df9de0198) | +78 / −0 |
| [XtcFileRenameHandler.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFileRenameHandler.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c25a93d62b0bd77179cf24533d4bbb480e70eb5fe8f127be434f14db045baa94) | +99 / −0 |
| [XtcFormattingService.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcFormattingService.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fa7a934c2eebfecb03c5925b120f178a42c526caa6d58519ed54452bd8826eb1) | +138 / −0 |
| [XtcLanguageServer.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcLanguageServer.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-304552a12fefd34678af966bf57ac4de2a79c59d7989da59767f69d1529a235f) | +43 / −0 |
| [XtcParameterInfoHandler.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcParameterInfoHandler.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1be3a822d23e8246a9f38bc104ee8fc27321a989b4785e9d88619908b2717e4d) | +53 / −0 |
| [XtcRenameEdit.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameEdit.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a6f3d6fd3dae8fefa18c3e3f6dbd03e067339aafb89a71bc0af9c3216d120bab) | +358 / −0 |
| [XtcRenameHandler.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcRenameHandler.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-068a553940c6b95e1e1d3297229ff85f50f67fb37235b6adfc3159faa64bee24) | +259 / −0 |
| [XtcResolveCodeAction.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcResolveCodeAction.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ecbd6c0295c063d942646fe0ed6789e42a29ea59fe65dac25a91bf0c3d58cc66) | +24 / −0 |
| [XtcServerLogAction.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/lsp/XtcServerLogAction.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2f46c4f76f3fc4e84561b1de7bc2091998610034a8afd2d592ee7db10fd85af6) | +34 / −0 |

#### `lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ClientCapabilitiesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ClientCapabilitiesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-78512e8c9d1eb08727e444c9c55ae6a9b0e34c78cfdc74511f9a4a7b717854dd) | +96 / −0 |
| [CodeActionMessagesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CodeActionMessagesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-76cbc451e6983da07ae1681f88e9df8f1a89971db1795da2ba7965664c92e893) | +42 / −0 |
| [CompilerBuildModelTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerBuildModelTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7acac20a1771de9231fd9e2264ec30cb7484322b0416c2c113c5b1c702365587) | +65 / −0 |
| [CompilerBuildUpdatesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerBuildUpdatesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3475ee6161b6156ac9b26db1ada06e8041b750f2c2e04c8bfa77f4cab4a0d4ee) | +18 / −0 |
| [CompilerImportServiceTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerImportServiceTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c9f9b0a786e4b3df05c80fad9d73d3e7fac806b708c8eb049664a3129201029f) | +87 / −0 |
| [CompilerImportTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerImportTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-54517c829b398ed9ba3423d8946c2c629a7817ea5adb57ca4e47d2674c4f99e4) | +168 / −0 |
| [CompilerRootWatchesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/CompilerRootWatchesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7acdf8b4a844bdce4bfecd770080c78d03d409bf140918cbc39344092aeb7013) | +92 / −0 |
| [ConfigurationJsonTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ConfigurationJsonTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3c363bc186e1d1234fa52ecc6186e05e075deb4c5d469703c9cf11af8cf679c0) | +53 / −0 |
| [DiagnosticQuickFixesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticQuickFixesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9cbe15babbd4d5e3c2285efb021039f42c0df86d5c5968431de0ad5236792eea) | +117 / −0 |
| [DiagnosticReportJsonTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticReportJsonTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3b6b25c6e7c26448c1119a77e90b3b727483054aa013c9e5bad7c07ebe024a90) | +79 / −0 |
| [DiagnosticResultMessagesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DiagnosticResultMessagesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-51efa27c9ac2996dce8639f57518f1c11849af22c71aee47013660ed4edc0f6d) | +210 / −0 |
| [DirectoryDocumentMovesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DirectoryDocumentMovesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-31d46a34f3a72171d8f6b968cc44334af58edde27a9d3e387c16aeeec5601a6d) | +66 / −0 |
| [DocumentStartupMessagesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/DocumentStartupMessagesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a3fa1a36c2e8e63fe1831298b3ef3ee2df072baeffc7c8eebee8ea2e9a374506) | +272 / −0 |
| [FileMoveTargetsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/FileMoveTargetsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-32222ef0a003268c9d601784129bb28b2fc505b071b59fc852200d488b4cbd4e) | +61 / −0 |
| [LanguageServiceConfigurationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LanguageServiceConfigurationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4809bc59ce064cdccf1c94a15b40b1b64bf5f87514dd460307d47f24da2afb3d) | +94 / −0 |
| [LibraryConfigurationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/LibraryConfigurationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4297305dd82710b45e89dbc30469aa647b608235bce27e9148c661fefb39c724) | +49 / −0 |
| [PreflightedRenamesTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/PreflightedRenamesTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e5e215a4e3db06597adb68302a656137e124d917d408651cbdcc3e899d51422a) | +152 / −0 |
| [ServerJvmOptionsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerJvmOptionsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2301e7b03e4e7799e656d8f0c822f97e52bc1e91e776874f4e72f115e92cdafd) | +40 / −0 |
| [ServerSettingsPersistenceTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerSettingsPersistenceTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-d404a89400bdf844938718be70191f5137b7a1f52d84d5f7dcb75ddd07abfba9) | +19 / −0 |
| [ServerSupportLogsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerSupportLogsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a298c7115747c5f6c853ce690284d9f3b7bae79a33d1326b503d6810294e3de0) | +67 / −0 |
| [ServerWorkspaceEditTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/ServerWorkspaceEditTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6de641ec243f5468d004e94e4720b82ef73567f80e3ce84441456255b4f2beb7) | +29 / −0 |
| [SourceGraphConfigurationTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/lang/intellij-plugin/src/test/kotlin/org/xtclang/idea/lsp/SourceGraphConfigurationTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f04aa16b85c3c222ca7b966f9a3ccc2120f1af76556cdfc6957ec9cfb814656b) | +219 / −0 |

<a id="full-reference-new-vscode"></a>

### VS Code extension — 68 files

#### `lang/vscode-extension/src`

| File | GitHub | + / − |
| --- | --- | ---: |
| [adapter-selection.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/adapter-selection.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a9891c53fb2d33948a35a6b3bd948eebf3f6bddcb660ef20a15935785a544010) | +23 / −0 |
| [build-model.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/build-model.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3fe523f074b87d8782074962b800f87cb06b3c96665247c8dba5b43f563a9ee6) | +84 / −0 |
| [compiler-import.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/compiler-import.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a5101a3a12dcd97115923c4ae63b60f63e3378e65c37c024e6a05aa3bb1b7e4e) | +81 / −0 |
| [compiler-paths.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/compiler-paths.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-356610d8fb4d0d37971c8fcc688f4fa59d3804c75e3e8d2a2ec1791e3677f598) | +246 / −0 |
| [compiler-task.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/compiler-task.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4bc0c8bf9bc80140fe86156adbeffae6fce0e06063eb948c5c171d41f4b666f6) | +43 / −0 |
| [editor-settings.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/editor-settings.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4a1311a9875087e55a054ddef745876120f8ba70e680ba9fdf2358a9b8716c04) | +30 / −0 |
| [library-configuration.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/library-configuration.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5efd963e4afa8d65ad971884f72085132e42295e41f608ee9676339b48310b3b) | +46 / −0 |
| [library-settings.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/library-settings.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1a9575d12ffcfc75a5c2c2c82ba6705f414e7c4d72312eff9cfa20ffc9fe4962) | +79 / −0 |
| [rename-proposal.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/rename-proposal.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-37ece5de49df4b99753da79c089105b5a884eccd5d02268102ef43d4ef34f241) | +154 / −0 |
| [runtime-settings.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/runtime-settings.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-38d24c9b23783e6f288923ba7ad1cb1f9cb9b75b2bdf2c4999218f9772278202) | +46 / −0 |
| [service-notifications.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/service-notifications.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-099e4094712202da3f15d5709ece5755d80a6eb7d7447fa7758c923c8c0a53c9) | +11 / −0 |
| [service-settings.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/service-settings.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1eed89e3b555f43507cf9ede1c6db13741fecfe4cb8e2e96b4594b07e36826e5) | +35 / −0 |
| [settings-report.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/settings-report.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e64de13c3162ea155e43d2da1e39950a76d85e6503fbbb653de291478aada1a2) | +33 / −0 |
| [source-graph-configuration.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/source-graph-configuration.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-277a41422c5b340e8ccd9d748265dba70ab9b8fc908b335bd3d937cbaf2c6e11) | +46 / −0 |
| [support-logs.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/support-logs.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-97694abdd57d9307d55bd5bf1436be93fec5523fc9d554882aed7c3bb723ea3f) | +105 / −0 |

#### `lang/vscode-extension/src/test`

| File | GitHub | + / − |
| --- | --- | ---: |
| [archive.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/archive.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bcfd09847400bf6e6db7dfd4eca6cf8f3e50c899a9367d57849ab1e77073d776) | +19 / −0 |
| [explorer-move-trace.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/explorer-move-trace.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5d8b4093d2aa4478a5498222d250eb13203cd27e991b2bbc3302c0f44018a4bb) | +64 / −0 |
| [explorer-move.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/explorer-move.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-02f66d69a9c91032a3e39b1df0e1f36ffb6143bac4e609a32e1deace8369992c) | +43 / −0 |
| [native-focus.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/native-focus.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ebac0019a9533764b931d7f56354dba72eed0b12885613c281ea606654dc39a2) | +23 / −0 |
| [progress.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/progress.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-dd2a8cc1dbace6e602f5cfc21cf5f46eb925131d3188c3ac0200b5932b0c7beb) | +62 / −0 |
| [runLifecycle.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/runLifecycle.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ef7afee64867fc4a67554a54c5a9c49337f1aebceafa268d683121f3a71c157c) | +154 / −0 |
| [wait.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/wait.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-49d43b0648899eb7c916b5e7d0eccfda060d52acb0fb001a4023af3c7b14ec92) | +36 / −0 |
| [workbenchUi.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/workbenchUi.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ede5e06ea2447471b4178b2917a4c0441ace7b29e9a187247f1105e050116637) | +62 / −0 |

#### `lang/vscode-extension/src/test/explorer-probe`

| File | GitHub | + / − |
| --- | --- | ---: |
| [index.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/explorer-probe/index.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-79e2ea9fe297ecefea05d4ab0622d411f465f73fbc19547c6b774a19a53ac8e6) | +75 / −0 |

#### `lang/vscode-extension/src/test/lifecycle`

| File | GitHub | + / − |
| --- | --- | ---: |
| [index.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/lifecycle/index.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-bfa1f7d50064e8b8d022dad71e2b9567ab81114e376c00726cbcb82239cc78dd) | +113 / −0 |

#### `lang/vscode-extension/src/test/playbook`

| File | GitHub | + / − |
| --- | --- | ---: |
| [advanced.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/advanced.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ec6fe60e38db89e852598fd3ff252b9895e92768c18e8282b5134bfabf93c75e) | +530 / −0 |
| [colors.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/colors.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-33ec0f00c5266064132e6b5ad8422cd416b2f2e40d25b4196416f589a5f6fc9a) | +156 / −0 |
| [compilerImports.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/compilerImports.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c3da75ad6f8045f2a9280f1a5a88a9e1ccb510c57db3f0c54b9f17741819f1d2) | +238 / −0 |
| [completion.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/completion.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6c64afa80c100214e1ff1378e5bea613f6bf8e5906be909edb98815cb374ad71) | +174 / −0 |
| [dependencies.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/dependencies.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-38a0547e8a5248ae7bda13300fcb285106b97d6661360bc1965e38ad9c2125f6) | +173 / −0 |
| [documentation.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/documentation.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8c04864eb6ef95c58d7eb0590a1fa516f88d68ea550e2c397eceb5b9baeb1447) | +32 / −0 |
| [editingClosure.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/editingClosure.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-aac9dfed2e4d6f0b3cf2a126d2bf981bd7923ea93a72116f7b342307fbccc564) | +102 / −0 |
| [graph.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/graph.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b6c975d83bfd98d5c647c66e09f54a1f7d37ea8a715906c5d9ddcd1691f9ea48) | +237 / −0 |
| [highlighting.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/highlighting.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3a98a8e8071c7cf379c87893094ef792f8aa3fba52f17fab572285e00f61929f) | +103 / −0 |
| [index.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/index.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0e02b93266a92eac738d291f7c30183052226604671cc9c87f3e51c4cea0294b) | +88 / −0 |
| [inlineCompletion.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/inlineCompletion.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e4cf903822b5f463cd8f0cb24267ec917e3e279854b07d55eff2aabcddc1f33d) | +81 / −0 |
| [librarySettings.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/librarySettings.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-c2ca0c03bc59014f14e29b0a4c8771e531ef004486471e90ae4bdf5f99ae2719) | +119 / −0 |
| [liveWorkspace.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/liveWorkspace.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-41f799396864b049d93f6f9f9e72dfada0d69b1b15c3fd146b07f8aa16f8571b) | +175 / −0 |
| [memberActions.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/memberActions.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-5c2e332b838b18cdc10ceca783ce825e50ee24fef78336200b35fbfaa1a92cd3) | +116 / −0 |
| [modules.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/modules.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-94c794150f1e786ed23f59be41507de914fde440a9cefd1f2e0dd747d94be1c3) | +207 / −0 |
| [navigation.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/navigation.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4024e765ea491a63c421677700e49e9246c32fc438b135443278238b0a9417e6) | +97 / −0 |
| [platform.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/platform.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-af7b1f5f131894537952494de1e830653070ea8f5c051b8972fa2c5a42bdfda6) | +534 / −0 |
| [playbook.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/playbook.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b715997ddc6f62a015825673657d7070049d64d9d1bf9ac67cd572ed5f46de15) | +125 / −0 |
| [progress.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/progress.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-113db12c215820e504a60d974d11bace1c2ab81fd9b883e7a25c5cc41890a8cf) | +115 / −0 |
| [properties.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/properties.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9a896b673972e01d747ac48dd76f77d50c6cad107c4e4ee15b72e4e9831c248e) | +65 / −0 |
| [referenceLenses.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/referenceLenses.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-9badbaed5381439fa657bef6e85f41270f4e2ba3e614b64ac036fec4f2b40459) | +52 / −0 |
| [reliability.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/reliability.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-24db6950f5dedd6eb0bcddf746d019f79044a0ba749470dee600642947a6f512) | +160 / −0 |
| [rename.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/rename.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-ffbb80812c4fa320fde065459590f3916e257e8a8f997a4fd8cb2cfddacc5c6d) | +93 / −0 |
| [renameFamilies.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/renameFamilies.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a4ae2719f11aae70b2fc6d7dfbecde0a6fb8f4046d9d9479991f548392aa87bd) | +177 / −0 |
| [runtimeSettings.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/runtimeSettings.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-db5b082703d3bde3f6d4b8f27120e9b1e16f6cd30731604834522bdb25dc2df2) | +118 / −0 |
| [semantics.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/semantics.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e9c8a0d801a85b29185c150055dd7f9e9fe6336a37c26b87ba90fe6fb4d934bf) | +221 / −0 |
| [shared.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/shared.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-f67a7532065c58cdea2676ff1e1836690079555cce0d83b2710d3e95227fc24b) | +102 / −0 |
| [support.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/support.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-0c1e231027dcb97b4fe1aaa37dd637ea67a67add9e0c198f1b00c35c6df2e543) | +274 / −0 |
| [syntaxCompletion.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/syntaxCompletion.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2ed66a9cd3cfe94203fde572d4e20bed03929e446d104879268edce06a9e06d4) | +48 / −0 |
| [typeMoves.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/playbook/typeMoves.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-be7655db560b83b73ad969a9784fb08cd7c39b9833ec174c97bf25bd414c1d05) | +77 / −0 |

#### `lang/vscode-extension/src/test/settings-persistence`

| File | GitHub | + / − |
| --- | --- | ---: |
| [index.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/settings-persistence/index.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8dfdd023cda48aaf0c920aaec4aa634790890fe4a890d47ee57a301fb0fe7f03) | +46 / −0 |

#### `lang/vscode-extension/src/test/suite`

| File | GitHub | + / − |
| --- | --- | ---: |
| [build-model.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/build-model.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-330b578a61b3b1a1c7048afa5d6dcf93f6995c34e1f7c01288ec8c0a3594d096) | +33 / −0 |
| [compiler-import.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/compiler-import.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3af6fe99c6005cd1f4756b6f4db8a677af2a104aba09fd5518131ff5e3bcbaab) | +94 / −0 |
| [compiler-paths.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/compiler-paths.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-de6eacb68fa11a8b8e08cd3a6d24f2811bbaba10aa8e5c7ce09197feedb260e6) | +41 / −0 |
| [compiler-project.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/compiler-project.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a9d66641385774ccb7505ccd774fbb6aaca3e900431f950f61edbbcf81aafb0c) | +125 / −0 |
| [compiler-task.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/compiler-task.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3795b26cb84020925c8e909d4b68e572e8847cd6afcb293fb93cb3f402fa417c) | +108 / −0 |
| [library-configuration.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/library-configuration.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-53ad1b32ceaa8c91278c0674d53ab3a821b4603b4c9451fdb078712abdf56c9e) | +41 / −0 |
| [playbook-wait.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/playbook-wait.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-90483a0ea0555257a276eea998134936780a9fe066feafedc20b29150243c422) | +37 / −0 |
| [runtime-settings.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/runtime-settings.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2b6967d32c27e55649ad40bef8769ef061a82f5afbd3978b1b2338a2234f9a29) | +16 / −0 |
| [service-settings.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/service-settings.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-76b59df65faa64c44b927d08fce9d6959b54cf0504d5b7eddc5b20e900900fe0) | +20 / −0 |
| [settings-report.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/settings-report.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-88e751d10b1f799d5222337eee91748984478cb0136933d6e205655bf8a55ae0) | +42 / −0 |
| [source-graph-configuration.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/source-graph-configuration.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e56c7ad2e81030366d389f8888a6657cdd0f4d1990e45d65340208a1857a6338) | +68 / −0 |
| [support-logs.test.ts](/private/tmp/xvm-full-testable-lsp-20261009/lang/vscode-extension/src/test/suite/support-logs.test.ts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-4db3b18f8f4aaf3e9ae60041e38af3abc7481b91b166bc0e12cb61b07127471c) | +53 / −0 |

<a id="full-reference-new-fixtures"></a>

### Shared fixtures — 8 files

#### `lang/test-fixtures/color-values`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ColorPrototype.x](/private/tmp/xvm-full-testable-lsp-20261009/lang/test-fixtures/color-values/ColorPrototype.x) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8fbf56ae967287d42be9adddbd3dfda4acb2d9f4f60c49fa1e6cde5387c2547a) | +17 / −0 |

#### `lang/test-fixtures/compiler-playbook`

| File | GitHub | + / − |
| --- | --- | ---: |
| [scenarios.json](/private/tmp/xvm-full-testable-lsp-20261009/lang/test-fixtures/compiler-playbook/scenarios.json) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-8546470972a88dc4f31ddced9a12b67fe6760a5ed1a4984ece553bfac5a92a62) | +8837 / −0 |

#### `lang/test-fixtures/compiler-playbook/compiler-import`

| File | GitHub | + / − |
| --- | --- | ---: |
| [build.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/lang/test-fixtures/compiler-playbook/compiler-import/build.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-3d28709ceeb580df4b79acfe3a89469dbe7e161329a3d362c0e6034d9068e78d) | +49 / −0 |
| [gradle.properties](/private/tmp/xvm-full-testable-lsp-20261009/lang/test-fixtures/compiler-playbook/compiler-import/gradle.properties) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b2a60151ad07af179989de818a8e8209b7b19cf44388de224c1d2559bdaa35ea) | +4 / −0 |
| [settings.gradle.kts](/private/tmp/xvm-full-testable-lsp-20261009/lang/test-fixtures/compiler-playbook/compiler-import/settings.gradle.kts) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-902dc002006ddd559576949bf474f9cc6dd6f0b651c835158a0186f1ac02421c) | +1 / −0 |

#### `lang/test-fixtures/compiler-workload`

| File | GitHub | + / − |
| --- | --- | ---: |
| [platform.json](/private/tmp/xvm-full-testable-lsp-20261009/lang/test-fixtures/compiler-workload/platform.json) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2c15241aa4f0d42acd93a67c4e3dd267efdea11f6b16acf70799aa30ca8ce976) | +18 / −0 |

#### `lang/test-fixtures/semantic-highlighting`

| File | GitHub | + / − |
| --- | --- | ---: |
| [SemanticColors.x](/private/tmp/xvm-full-testable-lsp-20261009/lang/test-fixtures/semantic-highlighting/SemanticColors.x) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e3924283c6fdeb07a5ae8477e8617940932a0ee8196b153512756f9d005c205c) | +29 / −0 |
| [semantic-colors.code-workspace](/private/tmp/xvm-full-testable-lsp-20261009/lang/test-fixtures/semantic-highlighting/semantic-colors.code-workspace) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-1d009e08b1444d74ddedb5f3b076c0a702b26d1d4e7ee8c462113fa74a561930) | +16 / −0 |

<a id="full-reference-new-grammar"></a>

### Grammar and syntax tooling — 0 files

No files in this category.

<a id="full-reference-new-gradle-plugin"></a>

### Gradle plugin — 4 files

#### `plugin/src/main/java/org/xtclang/plugin`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcLspModelIntegration.java](/private/tmp/xvm-full-testable-lsp-20261009/plugin/src/main/java/org/xtclang/plugin/XtcLspModelIntegration.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-a463961113b0b0739e289df62106a41f4e2ea635814536818373c1746b4fd8e6) | +87 / −0 |

#### `plugin/src/main/java/org/xtclang/plugin/tasks`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcLspModelTask.java](/private/tmp/xvm-full-testable-lsp-20261009/plugin/src/main/java/org/xtclang/plugin/tasks/XtcLspModelTask.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-e7b990d4cb18eea3c3fd39241be360d695bd7cbd2563866c3d946b3a07a9cb32) | +50 / −0 |
| [XtcLspSourceSetTask.java](/private/tmp/xvm-full-testable-lsp-20261009/plugin/src/main/java/org/xtclang/plugin/tasks/XtcLspSourceSetTask.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-7cf528fed84b2cd8c7eb4f04decc6d6c023546ae0ead213c2debf57a9b8aca2f) | +96 / −0 |

#### `plugin/src/test/java/org/xtclang/plugin`

| File | GitHub | + / − |
| --- | --- | ---: |
| [XtcLspModelTest.java](/private/tmp/xvm-full-testable-lsp-20261009/plugin/src/test/java/org/xtclang/plugin/XtcLspModelTest.java) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b613be9915656db8ff46182fd55173303a48644316cdd226ddc7046f179435b7) | +197 / −0 |

<a id="full-reference-new-shared"></a>

### Shared build, CI, runtime regression and documentation — 6 files

#### `build-logic/common-plugins/src/main/kotlin`

| File | GitHub | + / − |
| --- | --- | ---: |
| [PruneTestReportsTask.kt](/private/tmp/xvm-full-testable-lsp-20261009/build-logic/common-plugins/src/main/kotlin/PruneTestReportsTask.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-456b3709ba9a3dcea36c760173074ad9fb4722aedbde005e4f7ab63c4436e9b4) | +86 / −0 |

#### `build-logic/common-plugins/src/test/kotlin`

| File | GitHub | + / − |
| --- | --- | ---: |
| [ServerLogTailsTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/build-logic/common-plugins/src/test/kotlin/ServerLogTailsTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-2933ec09bd0cc841be46a88c19f81f41c156158fe385f949ff74a59bf82be51f) | +51 / −0 |
| [TestReportRetentionTest.kt](/private/tmp/xvm-full-testable-lsp-20261009/build-logic/common-plugins/src/test/kotlin/TestReportRetentionTest.kt) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-b22948838215a4945e4bd54432eda2c73174ed603489d568801bf7c327bb3d5b) | +70 / −0 |

#### `lang/gradle`

| File | GitHub | + / − |
| --- | --- | ---: |
| [compiler-model.init.gradle](/private/tmp/xvm-full-testable-lsp-20261009/lang/gradle/compiler-model.init.gradle) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-fb7363745b6f56f475a8ef29dd7cd93daef651b69e31cbbfb99807b87cc2394f) | +78 / −0 |

#### `lang/scripts`

| File | GitHub | + / − |
| --- | --- | ---: |
| [compiler-workload.py](/private/tmp/xvm-full-testable-lsp-20261009/lang/scripts/compiler-workload.py) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-79aa25f527da8c29190ac1fb995e4884d203a21ca0d1f0f7f3e1d5c5675922e6) | +504 / −0 |

#### `manualTests/src/main/x`

| File | GitHub | + / − |
| --- | --- | ---: |
| [conditionalEffects.x](/private/tmp/xvm-full-testable-lsp-20261009/manualTests/src/main/x/conditionalEffects.x) | [Diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...c63a560106ebe306a22b5f80a7addcc114183b88#diff-6048053efa69641d2131de596bfc9fe719427f650676f91d59eeba02c6b7db80) | +79 / −0 |
