# Alternative Backends and Industry Precedents

Investigation date: 2026-08-13 (second pass)

This chapter answers a challenge raised in [for-agents.md](../for-agents.md): is LLVM actually the best native backend for XVM, or would Cranelift, MLIR, Graal/Truffle, a custom baseline tier, or the JVM itself be better? It also supplies something the first-pass documents lacked: the documented history of other VMs that tried exactly what the LLVM study proposes.

The first-pass conclusion ("LLVM is feasible, staged behind a neutral IR") survives review, but it is incomplete in three ways:

1. It does not confront the strongest historical counter-evidence: production VM teams that adopted LLVM as a JIT tier and then abandoned it.
2. It does not price LLVM's in-process footprint against the same document set's "minimum footprint" requirement. These two goals contradict each other and the contradiction needs an explicit resolution.
3. It treats "the JVM path" as a bridge to be retired rather than as a competing end-state whose ceiling is rising every year (Loom, Valhalla, generational ZGC, Leyden). A rational plan must measure that ceiling before funding a native runtime to beat it.

Related notes:

- Main LLVM study: [llvm-jit-study.md](llvm-jit-study.md)
- Performance and fiber runtime strategy: [performance-runtime-strategy.md](performance-runtime-strategy.md)
- Memory/GC alternatives: [memory-fibers-gc-alternatives.md](memory-fibers-gc-alternatives.md)
- Risk matrix and decision gates: [risk-matrix-and-decision-gates.md](risk-matrix-and-decision-gates.md)
- Second-pass review: [second-opinion-review.md](second-opinion-review.md)

## What Other VM Teams Learned About LLVM as a JIT

The LLVM study cites LLVM's own documentation. That documentation is accurate but it is marketing-neutral; it does not tell you what happens when a latency-sensitive VM adopts LLVM as a JIT tier. The record does:

### JavaScriptCore: adopted LLVM, then replaced it

WebKit's FTL JIT shipped in 2014 with LLVM as the fourth (top) tier. In 2016 the team replaced LLVM with a purpose-built backend, B3, citing compile latency and memory as the decisive problems: LLVM compile times were high enough that many functions never reached the top tier before the page finished its work. B3 compiled several times faster and recovered almost all of the throughput. The team that did this had deep LLVM expertise and still concluded that LLVM was the wrong tool for an in-process, latency-sensitive JIT tier.

Lesson for XVM: an interactive `xtc run` has the same shape as a page load. If LLVM is the only compiled tier, most methods will execute in the interpreter while LLVM chews on the hot ones.

### HHVM: evaluated LLVM, kept its custom backend

Facebook's HHVM team ran a serious LLVM backend experiment around 2015. LLVM-generated code did not beat their custom backend by enough to justify the integration and compile-time costs, and the experiment was retired.

### Azul Falcon: LLVM in production, with caveats that matter here

Azul's Falcon compiler replaced C2 with LLVM in a production JVM and succeeded. But the preconditions are instructive:

- Falcon is a *top* tier; a fast lower tier (C1-style) and interpreter absorb all cold and warming code.
- Compilation runs on background threads in server processes where compile latency is amortized over hours of uptime.
- Azul maintains substantial LLVM patches and contributed much of the statepoint/GC infrastructure upstream; they staff LLVM specialists permanently.

Lesson: LLVM as a JIT works when (a) there is a cheap tier below it, (b) latency can be hidden, and (c) the organization budgets for permanent LLVM maintenance. All three must be planned, not assumed.

### Julia: LLVM-only execution and the latency tax

Julia compiles everything through LLVM and spent a decade fighting "time to first plot." Their mitigations — an interpreter, precompilation, native code caching (Julia 1.9+) — are exactly the tiering/AOT-cache work the XVM plans defer to late phases. Adopting LLVM without an interpreter/baseline tier from day one reproduces Julia's most publicized weakness.

### .NET: the architecture the AST-vs-XTC document reinvented

The recommendation in [ast-vs-xtc-feasibility.md](ast-vs-xtc-feasibility.md) — typed module tables plus a verified method IR — is, almost exactly, ECMA-335: metadata tables plus CIL, consumed by a tiered JIT (tier-0 quick code, tier-1 optimizing with dynamic PGO) plus AOT images (ReadyToRun, NativeAOT). This is good news: the design is proven at industrial scale, and .NET's tiering policy, generic-sharing strategy (shared code for reference-typed instantiations, specialized code for value-typed ones — precisely the `JitFlavor` split), and OSR design are directly reusable prior art. XTC v2 design work should crib from ECMA-335 deliberately rather than converge on it accidentally; see [xtc-v2-format-and-method-ir.md](xtc-v2-format-and-method-ir.md).

## Alternatives the First Pass Did Not Weigh

### Cranelift

Cranelift (Rust; the JIT used by Wasmtime) is the strongest challenger for the *in-process JIT* role:

- Compile speed roughly an order of magnitude faster than LLVM -O2; designed for JIT latency budgets.
- Small footprint (single-digit MB vs. LLVM's tens of MB), memory-safe implementation.
- Has what the XVM plans need and feared LLVM might not deliver cheaply: stack maps for reference types (built for WasmGC), tail calls, and (recently) exception-handling support driven by Wasm EH.
- E-graph-based mid-end (ISLE rules) with a respectable optimization ceiling: typically within 10–40% of LLVM on integer workloads, worse on heavy FP/vectorization.
- Targets x86-64, aarch64, riscv64, s390x — enough for XVM's plausible deployment matrix.

Weaknesses: less mature vectorization, no PGO story, fewer targets, and the project is governed by Bytecode Alliance priorities (Wasmtime first).

The right question is not "LLVM or Cranelift" but "which tier is each for." A defensible split: Cranelift (or a template JIT) as the resident tier-1, LLVM as the tier-2/AOT compiler that may even run out of process. That resolves the footprint contradiction below.

### GraalVM / Truffle

The first pass never mentions Truffle, which is an omission worth correcting because the cheapest possible experiment lives there. A Truffle interpreter written around the existing `Op` semantics (or, better, the neutral method IR) would inherit speculation, deoptimization, inline caches, safepoints, GC integration, and native-image AOT from the Graal stack — the entire "hard runtime work" list from the LLVM study — without building any of it. Espresso (JVM bytecode on Truffle) demonstrates the pattern at full language scale.

Costs: a hard dependency on the Graal ecosystem; licensing texture (Community Edition is GPLv2+CPE; Oracle GraalVM is under GFTC); and it deepens the JVM commitment rather than escaping it. As a *destination* it conflicts with the native-runtime goal; as a *measurement instrument* ("what does XTC look like with a real speculative JIT?") it is two orders of magnitude cheaper than the LLVM sidecar and would produce better data about where XTC's dynamic overheads actually are.

### MLIR

MLIR is attractive if the project expects to maintain several lowering levels (XIR → mid-level → LLVM) for a long time, and it is the natural home for future accelerator/vector subsets. But it adds a large C++ toolchain surface, does not remove LLVM from the bottom of the CPU pipeline, and does nothing for JIT latency. Verdict unchanged from the first pass, now with a sharper boundary: a small custom method IR first; consider an MLIR dialect only when/if the AOT pipeline grows enough optimization levels to justify it, and never as the JIT's hot path.

### WebAssembly (+ WasmGC) as a backend target

This is a genuinely new direction the first pass missed. Compiling the neutral method IR to Wasm gives:

- A sandboxed, capability-based execution model that aligns almost perfectly with Ecstasy's container/injection philosophy — no ambient authority, explicit imports. Ecstasy's security story and Wasm's are the same story.
- WasmGC provides struct/array types with engine-owned GC, eliminating the entire native-GC workstream for this backend.
- Deployment reach: browsers, edge runtimes, embedded engines (WAMR), plus server engines (Wasmtime, V8).
- The stack-switching proposal (and JSPI in browsers) maps to the fiber model.

Limits: WasmGC has no interior pointers, casts have real cost, weak refs/finalization are constrained, and the threads+GC combination is still settling; the performance ceiling sits below tuned native code. Verdict: not the primary execution engine, but a high-leverage third backend and possibly the best *distribution* story for untrusted Ecstasy modules. A leaf-method-to-Wasm experiment costs weeks, not quarters, once the neutral IR exists.

### The JVM-max path as a competing end-state ("Plan B")

The first-pass documents treat the JVM as scaffolding. That underprices three JDK trajectories that attack exactly the weaknesses the native runtime is meant to fix:

- **Loom (final since JDK 21).** Virtual threads are fibers whose stacks are copied to the heap on park: a blocked fiber costs roughly its live frames, no OS thread, no reserved stack. This is the *same* memory shape the performance strategy demands from compact continuation frames — already engineered, tested, and shipped. The repo baseline is JDK 25, so mapping Ecstasy fibers onto virtual threads in the Java-JIT runtime is available today, and no first-pass document mentions it.
- **Valhalla.** Value classes with flattening would remove most of the boxing and multi-slot pain that `JitFlavor` exists to manage. Timeline risk is real, but the direction is committed.
- **Generational ZGC + Leyden.** Sub-millisecond pauses at large heaps, and AOT-cached startup respectively, erode the "Java GC pauses" and "JVM startup" arguments.

The native runtime is still justified by things the JVM will not give: exact per-container memory accounting with hard-kill semantics, object headers and layouts under XVM control, tiny embedded footprint, and no host-VM dependency. But the plan must name those as the *reasons*, measure the JVM-max ceiling first, and set numeric triggers for funding the native tier — otherwise the project risks spending years beating a 2026 JVM only to lose to a 2029 JVM. Concrete gates live in [risk-matrix-and-decision-gates.md](risk-matrix-and-decision-gates.md).

## The Footprint Contradiction and Its Resolution

[performance-runtime-strategy.md](performance-runtime-strategy.md) demands a "small native kernel" and a footprint report split into base kernel / metadata / fibers / code cache. The LLVM plan puts ORC LLJIT *inside* that kernel. libLLVM is tens of megabytes of code and tens of megabytes of working memory under compilation load. Both statements cannot hold.

Resolutions, in order of preference:

1. **AOT-first kernel.** The production kernel ships AOT-compiled core libraries plus a method-IR interpreter and (optionally) a small template/baseline JIT. LLVM never loads in production processes.
2. **Out-of-process compile service.** Hot methods are shipped (as method IR) to a compile daemon; object code returns over a socket/shared memory and is mapped in. Precedents: Android dex2oat, iOS AOT-only, Leyden condensers. This also isolates LLVM crashes from the runtime and simplifies W^X/MAP_JIT hardening.
3. **Cranelift in-process.** If a resident optimizing JIT is required, Cranelift's footprint fits the kernel budget where LLVM's does not.

The LLVM-in-process configuration remains fine for development and for long-lived server deployments that opt into it. It should be a configuration, not the architecture.

## Tiering Recommendation (Revised)

Combining the precedents:

```text
Tier 0: method-IR interpreter (native runtime) / existing interpreter (JVM host)
Tier 1: baseline JIT - template JIT or Cranelift; inline caches, counters, fast compile
Tier 2: optimizing compiler - LLVM (in-process on servers, out-of-process or AOT elsewhere)
AOT:    LLVM object emission for kernel + core libraries + shipped applications
```

Two additions the first-pass plans omit entirely:

- **On-stack replacement.** Long-running loops never return; without OSR they stay in tier 0/1 forever. The plans never mention OSR. The clean way to get it here: reuse the fiber safepoint machinery. A loop safepoint that can spill live state into a compact continuation frame for *suspension* can equally spill it for *tier-up*, resuming in the better code's matching safepoint entry. Suspend, deopt, OSR, and GC-poll should be one mechanism — a single "frame externalization" contract — not four. This unification is cheaper than any of the four built separately and should be a named requirement of the method IR.
- **Compile-latency budget as a gate.** Every tier must have a stated compile-time budget (e.g., tier 1 ≤ 1 ms/method typical, tier 2 background-only). JSC and Julia teach that this is not an implementation detail; it decides whether the architecture works.

## Verdict

- LLVM: keep, but demoted from "the JIT" to "the top tier and AOT compiler," with a hard rule that production kernels do not require it in-process.
- Cranelift: adopt as the candidate resident JIT tier when the native runtime exists; evaluate seriously before writing a custom template JIT.
- Truffle: run as a cheap measurement experiment on the JVM host; not a destination.
- MLIR: deferred; revisit only for the AOT pipeline.
- Wasm/WasmGC: new lateral direction worth a scoped experiment; best alignment with Ecstasy's security model of any backend.
- JVM-max (Loom + Valhalla + ZGC + Leyden): promote from "bridge" to "Plan B end-state"; measure its ceiling before funding the native runtime past the prototype gate.
