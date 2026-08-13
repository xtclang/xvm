# XTC v2 Format and Method IR Notes

Investigation date: 2026-08-13 (second pass)

This chapter adds resolution to the recommendation shared by [ast-vs-xtc-feasibility.md](ast-vs-xtc-feasibility.md) and [runtime-port-self-hosting-study.md](runtime-port-self-hosting-study.md): "XTC v2 = typed module tables + method IR." The first pass named the destination but left the artifact underspecified and missed two facts that change the plan: the `.xtc` format already double-encodes method bodies, and the Kotlin compiler already achieves byte-level v1 convergence but has no module model other than the v1 binary itself.

Related notes:

- AST vs XTC feasibility: [ast-vs-xtc-feasibility.md](ast-vs-xtc-feasibility.md)
- Kotlin compiler/runtime boundary: [kotlin-compiler-runtime-boundary.md](kotlin-compiler-runtime-boundary.md)
- Second-pass review: [second-opinion-review.md](second-opinion-review.md)

## Fact 1: `.xtc` Already Ships Two Body Encodings, and Execution Uses the Lower One

Every compiled method body is serialized twice in today's format:

- the `Op[]` stream, consumed by the interpreter (`Op.process`) and by the Java JIT (`Op.build` via `BuildContext`), and
- a **BinaryAST** (BAST) — a typed, tree-shaped body encoding with 53 node classes under `javatools/src/main/java/org/xvm/asm/ast/`, lazily deserialized by `MethodStructure.getAst()`.

The only consumer of the BAST in the execution stack is `org.xvm.compiler.EvalCompiler` (debugger/eval). The JIT reconstructs control flow, type flow, and guard/finally regions from the op stream (`BuildContext.preprocess`) while a higher-level encoding of the same body sits unread in the same file.

This reframes the "neutral method IR" work. The first-pass plans propose deriving the IR from `Op[]` by CFG reconstruction and type recovery. The cheaper and semantically richer seed is the BAST: it preserves expression structure, call shapes, and typed constructs that the ops have already flattened away. The realistic plan is:

1. Audit BAST coverage and fidelity (is every op-stream construct representable? are there bodies with ops but no BAST or vice versa?).
2. Define the method IR ("XIR") as a *lowering of BAST*, with an op-stream fallback deriver only for legacy bodies whose BAST is missing or deficient.
3. In XTC v2, ship exactly one body encoding — the versioned, verifiable XIR — and retire the double encoding. The interpreter tier then interprets XIR; every backend lowers XIR.

Keeping two body encodings in the deployed format is pure cost: two serializers, two verification surfaces, and a standing invitation for the encodings to disagree.

## Fact 2: The Kotlin Compiler's Boundary Artifact Is v1 Itself

The research fork's Kotlin compiler (see [second-opinion-review.md](second-opinion-review.md) for the full survey) writes v1 `.xtc` binaries directly and has achieved method-for-method bytecode equivalence with the Java compiler across the entire XDK corpus (10,587/10,587 methods, zero unmapped semantic differences as of 2026-08-11). That is a far stronger convergence position than the first-pass docs assumed. But it also means the *only* module model that exists today is the v1 on-disk format: `XtcModule`/`XtcConstant`/`ConstantPool` in the fork's `compiler/module/xtc/` package are a faithful model of the v1 encoding, not an abstract compiler↔runtime contract.

Consequence: the "frozen module model" in [kotlin-compiler-runtime-boundary.md](kotlin-compiler-runtime-boundary.md) is not an extraction task; it is a design-and-build task, and XTC v2 *is* that model's serialization. The two workstreams are one workstream. Designing "the frozen model" and "the v2 format" separately would produce two artifacts to reconcile.

## Design Precedent: ECMA-335, Deliberately

The recommended shape — typed tables + verified method IR — is the ECMA-335 (.NET metadata + CIL) architecture, which has carried a large ecosystem for 25 years through JIT rewrites, AOT compilers (ReadyToRun, NativeAOT), trimming/linking, and reflection. Borrow its structure consciously:

- **Sectioned, table-per-entity layout.** Fixed-width rows per entity kind (module, type, member, signature), with row indices as intra-module references. Sorted tables where lookup patterns demand it.
- **Separate heaps for variable-length data**: string heap, blob heap (signatures, XIR bodies, debug maps), GUID/hash heap. Rows point into heaps; heaps are deduplicated.
- **Signatures as canonical blobs**, not as object graphs. Two references to the same type/signature are equal iff their canonical encodings are equal — this kills the recursive-identity problem of the v1 constant pool, where identity is pool position and pool position depends on global serialization order.
- **A verifier over the body IR** as a first-class deliverable. Ecstasy's container/security story requires that a loaded module cannot violate the type system; with native codegen in the pipeline, the verifier is the security boundary, and it must run before any backend consumes a body. (This also gives the self-hosting plan its ideal first compiler-side component — see [ecstasy-self-hosting-study.md](ecstasy-self-hosting-study.md).)

Also worth studying: Android's dex (aggressive deduplication via sorted string/type/proto tables) and Swift's serialized SIL (a lowered IR shipped for cross-module inlining — precedent for shipping XIR rather than source-shaped AST).

## Identity: Stable Declared IDs Plus Content Hashes

The v1 pool conflates three things that v2 should separate:

1. **Nominal identity** — what makes `ecstasy.collections.Map` the same type across compilations. This must be a *declared, stable* identifier (module id + qualified path + disambiguator), never a table position and never a content hash (nominal types can change body without changing identity, and can be mutually recursive).
2. **Structural identity** — signatures, anonymous/relational types. Canonical-encoding equality (as above).
3. **Version/content identity** — "is this the same *build* of this member?" Here, content-addressing (a hash of the canonicalized member record and body) is the right tool, and it is the piece none of the first-pass documents consider. Unison demonstrates the payoff at language scale: content-hashed definitions give perfect incremental invalidation, deduplicated storage, and safe distributed caching. For XTC v2 the modest version is enough:
   - each member row carries a content hash of its canonical encoding;
   - dependents record the hashes they compiled against;
   - incremental compilation, incremental linking, JIT-code caching, and distributed build caches all key on those hashes instead of timestamps or whole-module versions.

This directly serves the Kotlin compiler's incremental ambitions (its current side-channel identity model is the weakest part of its architecture) and gives the native code cache its invalidation key ([performance-runtime-strategy.md](performance-runtime-strategy.md) needs one and does not name one).

## One IR, Two Producers — an Invariant, Not an Accident

The first-pass documents describe two IR-producing paths without noticing they must be the same IR:

- [llvm-compiler-scope-plan.md](llvm-compiler-scope-plan.md) Phase 2/3 derives a neutral IR from v1 `Op[]` (now: preferably from BAST).
- [ast-vs-xtc-feasibility.md](ast-vs-xtc-feasibility.md) has the Kotlin compiler lowering semantic facts to a method IR.

If these drift into two dialects, every backend needs two front doors and the conformance story collapses. State it as an invariant:

> There is exactly one method IR. It has a written spec, a verifier, and a reference interpretation. The v1 adapter (BAST/ops → XIR) and the Kotlin compiler (semantic model → XIR) are two producers of the same verified language, and a conformance corpus asserts that both producers yield semantically equivalent XIR for identical sources.

The corpus already exists in embryo: the fork's convergence harness diffs Java-emitted vs Kotlin-emitted v1 bytecode method-by-method across the XDK. Repointing that harness at XIR when XIR exists is the natural evolution.

## What XIR Needs That Neither BAST Nor Ops Have

- explicit safepoint/suspension points (the fiber, GC, deopt, and OSR contract — one mechanism, see [alternative-backends-and-precedents.md](alternative-backends-and-precedents.md))
- explicit representation facts (the `JitFlavor` vocabulary, made backend-neutral)
- explicit exception/finally region structure (BAST has the source shape; XIR needs the normalized form)
- call descriptors with dispatch kind, argument/return layout, and specialization key
- verifier-checkable typing rules
- a debug/source map section designed for compiled frames, not interpreter frames

## Sequencing Correction

The first-pass migration plan ordered v2 late (step 7 of 9 in [ast-vs-xtc-feasibility.md](ast-vs-xtc-feasibility.md)). Given Fact 2, pull the *schema* work forward: the frozen module model, the XIR spec, and the v2 format are one design artifact, and both the runtime port and the Kotlin-compiler productization are blocked on it. The *serialization* can land late; the *schema* cannot.

1. Specify XIR + module-model schema (tables, heaps, identity rules, verifier obligations).
2. Build the v1→model adapter (reader exists in two implementations already — Java `javatools` and the fork's `XtcReader`; pick one as canonical).
3. Repoint the Kotlin compiler's emit at the model; keep v1 emission as a serializer of the model (the convergence gates then verify the adapter for free).
4. Interpreter-over-XIR as the reference tier; backends follow.
5. v2 serialization when the schema stops moving.
