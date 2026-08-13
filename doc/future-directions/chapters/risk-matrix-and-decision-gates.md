# Risk Matrix and Decision Gates

Investigation date: 2026-08-13 (second pass)

The first-pass scope plans list tasks and acceptance criteria but no likelihoods, no numeric targets, and no decision points at which the program changes course. This chapter supplies those. It responds to the handoff request for "a risk matrix for native GC, service-local heaps, self-hosting bootstrap, and XTC v2 migration" and extends it with the risks the second pass surfaced.

Related notes:

- Runtime port scope plan: [runtime-port-scope-plan.md](runtime-port-scope-plan.md)
- LLVM compiler scope plan: [llvm-compiler-scope-plan.md](llvm-compiler-scope-plan.md)
- Alternative backends: [alternative-backends-and-precedents.md](alternative-backends-and-precedents.md)
- Memory/fiber/GC alternatives: [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md)
- Minimal cleanroom runtime: [minimal-cleanroom-runtime-study.md](minimal-cleanroom-runtime-study.md)

## Risk Matrix

Likelihood/impact are coarse (L/M/H). "Trigger" is the observable event that says the risk is materializing — the thing to actually watch.

| # | Risk | L | I | Trigger | Mitigation |
| --- | --- | :-: | :-: | --- | --- |
| 1 | **Native GC underestimation** — collector, barriers, weak refs, finalization consume years | H | H | GC workstream still pre-alpha two quarters after native objects exist | MMTk spike before any custom collector; conservative-stack + pinning first; exact stack maps deferred (see [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md)) |
| 2 | **Dual/multi-runtime semantic divergence** — interpreter, Java JIT, Kotlin runtime, native runtime drift apart | H | H | Any conformance suite that only runs on one backend | One conformance corpus, run on every backend in CI from day one; differential harness (fork precedent) |
| 3 | **LLVM maintenance churn** — ORC/statepoint API instability, version upgrades | M | M | First painful LLVM major-version bump | Isolate behind C ABI sidecar; keep Cranelift as understudy; avoid statepoints (conservative roots) |
| 4 | **In-process LLVM contradicts footprint goals** | H (if unaddressed) | M | Kernel RSS budget blown by libLLVM | AOT-first kernel; out-of-process compile service; Cranelift in-process ([alternative-backends-and-precedents.md](alternative-backends-and-precedents.md)) |
| 5 | **FFM/JNI upcall overhead poisons sidecar data** — Java-hosted LLVM prototype measures boundary costs, not architecture, and misleads go/no-go decisions | H | M | Sidecar "object-heavy" benchmarks look catastrophically slow | Label sidecar as ABI-validation only; make perf go/no-go decisions only on native-hosted measurements |
| 6 | **JVM-max erosion** — Loom/Valhalla/ZGC/Leyden close the gap the native runtime was funded to open | M | H | Annual JVM-max benchmark shows shrinking delta | Maintain the JVM-max baseline (Loom fibers + classfile JIT) as Plan B; re-run decision gate G3 yearly |
| 7 | **XTC v2 migration splits the ecosystem** — v1 and v2 toolchains coexist too long | M | M | Tools that read one format but not the other | v2 = serialization of the frozen model; v1 adapter is a permanent reader, never a second source of truth ([xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md)) |
| 8 | **Service-local heap semantics mismatch** — an overlooked sharing path (proxies, futures, injected resources) breaks heap isolation | L | H | A cross-service mutable reference that is not a proxy | Language rules already enforce isolation at `validatePassThrough`; add a runtime assertion mode that verifies no cross-heap mutable edges during GC |
| 9 | **Self-hosting bootstrap stalls** — Ecstasy compiler passes blocked on runtime performance/stability | M | M | Stage-2 verifier in Ecstasy is 10x too slow to run in CI | Keep self-hosting behind execution maturity; verifier-first ordering (it is small, pure, and parallelizable) |
| 10 | **Spec-by-implementation** — every new runtime re-derives undocumented Java behavior | H | M | Conformance failures that require reading `javatools` to adjudicate | Fund the written spec as a deliverable (format spec exists in fork; runtime-behavior spec does not); every differential-test discrepancy becomes a spec sentence |
| 11 | **Kotlin compiler governance** — the semantic-owner-to-be lives on a personal fork, alpha, non-incremental, runtime lane unproven | M | H | Six more months without upstreaming or a second maintainer | Upstream the fork; make the incremental/architecture migration a tracked project, not aspiration ([second-opinion-review.md](second-opinion-review.md)) |
| 12 | **Silent-stub hazard in the current JIT** — non-allowlisted methods return default values instead of failing | H (today) | M | Any Java-JIT run outside the allowlist silently computes wrong answers | Make stubbed methods throw or log loudly; never ship a mode where unimplemented = wrong-but-quiet |
| 13 | **Team bandwidth / bus factor** — plans describe several engineer-years across compiler, runtime, GC, and backends | H | H | More than two of these workstreams active with fewer than N owners | The gates below exist to keep at most one expensive bet in flight at a time |

## Numeric Targets (Replacing Adjectives)

The first-pass gates say "fast," "compact," "low footprint." Proposed concrete targets — set them deliberately, then hold reviews against them:

- **Suspended fiber footprint**: ≤ 4 KB RSS median at tier B (mmap stacks, lazy commit); ≤ 512 B + live values for hot services at the continuation-frame tier. (Reference points: Go ≈ 2–4 KB min stack; parked Loom vthreads ≈ live frames.)
- **Fiber scale**: 1M suspended fibers ≤ 4 GB RSS; suspend/resume ≤ 200 ns hot path.
- **Compile latency budgets**: tier-1 ≤ 1 ms/method typical; tier-2 background-only; interpreter-to-tier-1 warmup visible in first 10 ms of a CLI run.
- **Startup**: native AOT hello-world ≤ 20 ms; JVM-hosted ≤ 300 ms with Leyden-style caching.
- **Primitive loop throughput**: within 1.5x of C for the numeric kernels in `manualTests/jit` at tier 2; within 3x at tier 1.
- **Accounting**: per-container `bytesAllocated` within 10% of true allocation volume at chunk granularity; hard-limit kill deterministic and container-scoped.
- **Code cache**: bounded per-module budget with measured eviction; specialization count per generic root capped and reported.

## Decision Gates

Each gate names the cheapest experiment that produces the information, and what changes on each outcome.

### G0 — Baseline measurement (now; weeks)

Measure today's interpreter and Java JIT on a fixed corpus: throughput, per-fiber memory, startup, allocation rate. Nothing else in this table is meaningful without the baseline. *Output:* the corpus + harness that every later gate reuses.

### G1 — Loom fiber experiment (JVM host; weeks)

Map Ecstasy fibers onto virtual threads in the Java-JIT runtime (the `Ctx` design already assumes this). Measure fiber density and suspend/resume against targets. *If it meets targets:* the JVM-hosted path's fiber story is done; the native fiber work is justified by footprint/accounting only. *If not:* quantifies exactly what the native fiber tier must beat.

### G2 — Cleanroom reference runtime to tier B ([minimal-cleanroom-runtime-study.md](minimal-cleanroom-runtime-study.md); 3–6 months)

*Pass:* a second runtime exists; runtime behavior spec grows out of differential failures; Kotlin compiler runtime lane gets bisected. *This gate does not block G1 or G3 and should run in parallel with compiler upstreaming.*

### G3 — JVM-max ceiling vs. native promise (after G0+G1; re-run yearly)

With Loom fibers, classfile JIT, and (when available) Valhalla value classes measured: is the remaining gap to targets dominated by things the JVM cannot give (exact container accounting, hard-kill semantics, embedded footprint, no-host-VM deployment)? *If yes:* fund the native runtime past prototype. *If no:* the native runtime is deferred, and investment goes to the classfile JIT + XTC v2 + compiler productization. This is the program's largest capital-allocation decision and it should be made on G0/G1 data, not on architecture documents — including these.

### G4 — Neutral IR proven by two producers (with XIR spec; months)

The v1/BAST adapter and the Kotlin compiler both emit XIR; the convergence harness passes on the XDK corpus; the XIR verifier rejects mutants. *Pass:* backends may now be built against XIR. *Fail:* backend work stays parked — building LLVM lowering against an unstable IR is how risk #2 becomes permanent.

### G5 — Native object + GC spike (MMTk; one quarter, timeboxed)

Native heap for arrays/strings/simple objects, MMTk Immix, conservative stacks, per-service nurseries, chunk-level accounting. *Pass criteria:* allocation + collection under differential test; accounting within target; no cross-heap mutable edges under assertion mode. *Fail after timebox:* re-scope to hybrid (native arrays/strings only) or hold at JVM-max while the model is redesigned.

### G6 — Backend bake-off (after G4; weeks per backend)

Same XIR corpus lowered via (a) classfile backend, (b) Cranelift, (c) LLVM out-of-process. Compare throughput, compile latency, and footprint against the numeric targets. Choose the tier-1/tier-2 assignment from data. A Wasm/WasmGC lowering can join as (d) at low cost for portability information.

## Sequencing Consequence

The gate structure inverts one first-pass assumption: the LLVM sidecar (Phase 1 of [llvm-compiler-scope-plan.md](llvm-compiler-scope-plan.md)) is no longer the first mover. G0/G1 (measurement, Loom) and G2 (reference runtime) are cheaper, run in parallel, and produce the data G3 needs to decide whether the expensive native bets should be placed at all. The sidecar retains value as ABI rehearsal, but it should be built after the XIR exists (G4), not before — otherwise it validates a lowering of `Op[]` that the program intends to retire.
