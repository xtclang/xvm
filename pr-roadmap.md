# Compiler foundations and LSP PR roadmap

Updated **2026-10-09**. The agreed strategy is to review complete compiler/build capabilities first, then the complete LSP and editor product. There are **four existing open PRs** and **five prepared review branches with GitHub comparison links**. No PRs have been opened for those five branches; they are available for inspection before submission.

Recovery, cursor queries, incomplete-call/constructor fitting and declaration/header analysis are **one combined partial-analysis PR**. The LSP server, IntelliJ plugin and VS Code extension are **one product PR** after their foundations. The Gradle exporter and Boolean operand-effects correction are independent changes that can land against master.

The LSP needs more than the initial embedding API: #685 provides the host compilation lifecycle; semantic facts provide source identities and resolved compiler decisions; partial analysis provides compiler answers for code being edited. The implemented adapter calls all three layers.

## Current review branches and merge order

These are the five proposed PRs. Counts are incremental against each recorded comparison base, excluding inherited work. Each title links to its description, prerequisites, file summary, validation evidence and complete clickable file inventory below.

| Proposed PR | Files | Must merge first | GitHub comparison |
| --- | ---: | --- | --- |
| [Compilation-owned syntax and semantic facts](#semantic-facts) | **36** | #685 and #687; #683 through #685. | [View diff](https://github.com/xtclang/xvm/compare/errs/compiler-review-base-20261009...errs/compiler-semantic-facts-20261009) |
| [Incomplete-source, cursor and declaration analysis](#partial-analysis) | **40** | Semantic facts, inheriting #683/#685/#687. | [View diff](https://github.com/xtclang/xvm/compare/errs/compiler-semantic-facts-20261009...errs/compiler-partial-analysis-20261009) |
| [Gradle compiler inputs for tooling](#gradle-model) | **8** | **None — independent against master.** | [View diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...errs/gradle-project-model-20261009) |
| [Boolean operand-effects correction](#boolean-effects) | **2** | **None — independent against master.** | [View diff](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...errs/compiler-conditional-effects-20261009) |
| [Compiler-backed LSP and editor integrations](#lsp-product) | **648** | Semantic facts, partial analysis, Gradle model and #686; inherits #683/#685/#687. Preserve or reconcile the Boolean fix included in its tested base. | [View diff](https://github.com/xtclang/xvm/compare/errs/lsp-review-base-20261009...errs/lsp-editors-20261009) |

**Compiler merge sequence:** #683 → #685; #685 + #687 → semantic facts → partial analysis → LSP/editor product.

**Independent work:** #686, #687, the Gradle exporter and the Boolean correction can progress against master without waiting for the embedding stack. The product waits for the applicable foundations above. The Boolean correction is part of its prepared compiler baseline, not an API prerequisite.

**Next compiler PR to submit, when authorized:** compilation-owned syntax and semantic facts. The two independent prepared branches can be submitted alongside it. Later stacked comparisons can be reviewed now; their final bases must be reconciled against the actual merged prerequisites before landing. No submission is authorized by this document itself.

## Existing open PRs

GitHub state checked on 2026-10-09: all four are open and target `master`. These PRs already exist; they are not additional proposed branches to create.

| Existing PR | Purpose | Prerequisites and current revision |
| --- | --- | --- |
| [#683 — Repository read failures and retry behavior](https://github.com/xtclang/xvm/pull/683) | Keep mixed-version/platform lookup tolerant while retaining I/O failure details for an embedding host to inspect after a required dependency fails. | **None.** `errs/repository-read-failures-20261009` at `902460194`. Land before #685. |
| [#685 — Compiler embedding and diagnostic ownership](https://github.com/xtclang/xvm/pull/685) | One complete host-compilation lifecycle: explicit listeners, suppression and reporting scopes, speculative attempts, restoration, diagnostic replay, cancellation, source snapshots, outcomes and partial progress. | **#683.** `errs/embedding-api-combined-20261009` at `c1a98a677`, already rebased over merged #684. Reconcile against #683 when it lands. |
| [#686 — Empty transient-thread-local retention](https://github.com/xtclang/xvm/pull/686) | Prevent empty thread-local probes from retaining short-lived compiler state in long-lived workers. | **None.** `errs/transient-locals-20261009` at `81f032cc0`. Two files; standalone transient-local test removed as requested, compiler-consumer coverage retained. |
| [#687 — Compiler types through fitting and code generation](https://github.com/xtclang/xvm/pull/687) | Preserve bound generic callable types, argument-fit failures and concrete atomic result/receiver types in emitted and serialized code. | **None.** `errs/compiler-type-correctness-20261009` at `f29cf28c0`. Six files; semantic facts builds on these corrected compiler decisions. |

**Merged/superseded history:** #677 and #679 are merged foundations. #678 merged as documentation only; its withdrawn owner-pool fallback must not return. #684 is merged and its diagnostic fixes must remain. #680–#682 were closed and superseded by #685; do not reopen them or split the combined embedding lifecycle back into those historical pieces.

## Reading the comparisons

The recorded master baseline is `da07a0be8`, which includes #684. The compiler comparison base `errs/compiler-review-base-20261009` combines #685 and #687 and inherits #683. The product comparison base `errs/lsp-review-base-20261009` combines the partial-analysis stack, Gradle exporter, #686 and the Boolean correction. **These two base refs are comparison aids, not PRs to submit.**

All five review branches and both comparison bases are published. The Boolean branch has a later two-line console-output removal in its local worktree, already committed to the integration branch; its GitHub comparison still shows the earlier fixture. Its section calls out that difference explicitly.

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

[Open this branch in GitHub’s compare view](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...errs/gradle-project-model-20261009). [Full local patch](build/reviews/remaining-branches-20261009/gradle-model.patch). No PR has been opened.

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

Branch `errs/compiler-conditional-effects-20261009` at `4ad9f29f9`; base `da07a0be8525962391f7e46215e4a8f277bd0b14` at `da07a0be8`.

[Open this branch in GitHub’s compare view](https://github.com/xtclang/xvm/compare/da07a0be8525962391f7e46215e4a8f277bd0b14...errs/compiler-conditional-effects-20261009). [Full local patch](build/reviews/remaining-branches-20261009/conditional-effects.patch). No PR has been opened.

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
| **Total** | **2** | No `lang/` or Gradle plugin changes. |

**Review-branch follow-up:** the console-free fixture is mirrored in `lagergren/errs`. The Boolean review worktree also contains the two-line removal, but that review-branch update is not yet committed or pushed. The GitHub comparison and line counts below still describe its published commit; locally the fixture is 79 lines and the branch change is +93/−1. All assertions are unchanged.

**2 files; +95/−1.** The TestConditionalEffects runtime fixture passed all 22 checks. Formatting passed.

### Review and acceptance focus

The runtime fixture verifies exactly-once left-operand evaluation for both Boolean operand values. It covers values, branch conditions, negated branches and call arguments, with the other short-circuit forms as controls. A constant result must not erase the operand's observable effects.

<details>
<summary>Exact changed files</summary>

| Status | File | + / − |
| --- | --- | ---: |
| M | [javatools/src/main/java/org/xvm/compiler/ast/CondOpExpression.java](/private/tmp/xvm-compiler-conditional-effects-20261009/javatools/src/main/java/org/xvm/compiler/ast/CondOpExpression.java) | 14 / 1 |
| A | [manualTests/src/main/x/conditionalEffects.x](/private/tmp/xvm-compiler-conditional-effects-20261009/manualTests/src/main/x/conditionalEffects.x) | 81 / 0 |

</details>

<a id="lsp-product"></a>

## Compiler-backed LSP and editor integrations

Branch `errs/lsp-editors-20261009` at `2dc91cf70`; base `errs/lsp-review-base-20261009` at `3f8aad03e`.

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

The largest areas are `lang/lsp-server`, `lang/intellij-plugin` and `lang/vscode-extension`. Supporting changes update grammar/highlighting generation, shared fixtures, build/test integration and XDK library packaging. Focused consumer tests prove that the extracted product compiles against the extracted compiler APIs; the full native-editor acceptance run and complete product suite remain required before claiming release acceptance.

### Files changed

| Area | Files | What changes |
| --- | ---: | --- |
| `lang/lsp-server` | 345 | Compiler adapter, server features, semantic models, protocol handling, tests and documentation. |
| `lang/intellij-plugin` | 177 | IntelliJ integration, configuration, project import, editor actions and acceptance harness. |
| `lang/vscode-extension` | 88 | VS Code client, settings/commands, import, feature integration and tests. |
| `lang/dsl` | 15 | Grammar/highlighting model and generators with their tests. |
| `lang/test-fixtures` | 8 | Shared compiler/editor/highlighting/workload fixtures. |
| Other `lang/` files | 7 | Language build/docs, generated TextMate example, workload script and Tree-sitter build/docs. |
| Outside `lang/` | 8 | CI workflow, four common build helpers, root Gradle properties/version catalog and XDK packaging. |
| **Total** | **648** | Incremental product changes; compiler/Gradle prerequisites are excluded from this comparison. |

**648 files; +110,180/−5,375.** 123 focused adapter and direct-API tests passed with no skips. The server, IntelliJ plugin and their test sources compile; VS Code TypeScript compilation passes. Root/server/IntelliJ formatting checks pass. Full native editor acceptance and the complete product suite have not been rerun for this extracted branch.

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
| M | [gradle.properties](/private/tmp/xvm-lsp-editors-20261009/gradle.properties) | 13 / 10 |
| M | [gradle/libs.versions.toml](/private/tmp/xvm-lsp-editors-20261009/gradle/libs.versions.toml) | 16 / 2 |
| M | [lang/README.md](/private/tmp/xvm-lsp-editors-20261009/lang/README.md) | 49 / 4 |
| M | [lang/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/build.gradle.kts) | 60 / 43 |
| M | [lang/doc/manual-test-plan.md](/private/tmp/xvm-lsp-editors-20261009/lang/doc/manual-test-plan.md) | 3207 / 97 |
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
| M | [lang/intellij-plugin/README.md](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/README.md) | 323 / 16 |
| M | [lang/intellij-plugin/TESTING.md](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/TESTING.md) | 240 / 38 |
| M | [lang/intellij-plugin/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/build.gradle.kts) | 120 / 10 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CachedIdeInstaller.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CachedIdeInstaller.kt) | 55 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientDiagnostics.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientDiagnostics.kt) | 151 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientProtocol.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientProtocol.kt) | 216 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientTrace.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ClientTrace.kt) | 195 / 0 |
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerPlaybook.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/CompilerPlaybook.kt) | 1989 / 0 |
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
| A | [lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityPlatform.kt](/private/tmp/xvm-lsp-editors-20261009/lang/intellij-plugin/src/integrationTest/kotlin/org/xtclang/idea/playbook/ParityPlatform.kt) | 1225 / 0 |
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
| M | [lang/lsp-server/README.md](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/README.md) | 495 / 77 |
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
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/SemanticModelBuilder.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/SemanticModelBuilder.kt) | 2089 / 0 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt) | 2448 / 69 |
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
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkProjectQueries.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkProjectQueries.kt) | 1506 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkQualifiedName.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkQualifiedName.kt) | 87 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRefactoringSyntax.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRefactoringSyntax.kt) | 53 / 0 |
| A | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRename.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkRename.kt) | 1229 / 0 |
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
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServer.kt) | 1049 / 290 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServerLauncher.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcLanguageServerLauncher.kt) | 99 / 42 |
| M | [lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcTextDocumentService.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/main/kotlin/org/xvm/lsp/server/XtcTextDocumentService.kt) | 1557 / 230 |
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
| A | [lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkStdioTest.kt](/private/tmp/xvm-lsp-editors-20261009/lang/lsp-server/src/test/kotlin/org/xvm/lsp/server/XdkStdioTest.kt) | 2079 / 0 |
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
| A | [lang/test-fixtures/compiler-playbook/scenarios.json](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/compiler-playbook/scenarios.json) | 8837 / 0 |
| A | [lang/test-fixtures/compiler-workload/platform.json](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/compiler-workload/platform.json) | 18 / 0 |
| A | [lang/test-fixtures/semantic-highlighting/SemanticColors.x](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/semantic-highlighting/SemanticColors.x) | 29 / 0 |
| A | [lang/test-fixtures/semantic-highlighting/semantic-colors.code-workspace](/private/tmp/xvm-lsp-editors-20261009/lang/test-fixtures/semantic-highlighting/semantic-colors.code-workspace) | 16 / 0 |
| M | [lang/tree-sitter/build.gradle.kts](/private/tmp/xvm-lsp-editors-20261009/lang/tree-sitter/build.gradle.kts) | 965 / 866 |
| M | [lang/tree-sitter/doc/functionality.md](/private/tmp/xvm-lsp-editors-20261009/lang/tree-sitter/doc/functionality.md) | 1 / 1 |
| M | [lang/vscode-extension/.gitignore](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/.gitignore) | 1 / 0 |
| M | [lang/vscode-extension/README.md](/private/tmp/xvm-lsp-editors-20261009/lang/vscode-extension/README.md) | 259 / 11 |
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
