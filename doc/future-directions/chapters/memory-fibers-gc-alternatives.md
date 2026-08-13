# Memory, Fibers, and GC: Alternatives and Sharpened Design

Investigation date: 2026-08-13 (second pass)

This chapter answers four challenges from [for-agents.md](../for-agents.md): should native GC use MMTk or a custom collector; is exact GC mandatory from the start; is stackless continuation lowering the right fiber model; and are service-local heaps compatible with Ecstasy sharing semantics. It also fixes two structural gaps in the first-pass plans: the missing deoptimization-across-heaps analysis, and the missing precedent survey (Erlang/BEAM and Pony's ORCA are the closest existing systems to what these documents propose, and neither was cited).

Related notes:

- Performance and fiber runtime strategy: [performance-runtime-strategy.md](performance-runtime-strategy.md)
- LLVM object ABI notes: [llvm-object-abi-notes.md](llvm-object-abi-notes.md)
- Alternative backends: [alternative-backends-and-precedents.md](alternative-backends-and-precedents.md)
- Second-pass review: [second-opinion-review.md](second-opinion-review.md)

## Service-Local Heaps: Stronger Than the First Pass Claimed

The first pass treated per-service heaps as a promising idea to validate against language semantics. Verification against the repo shows it is much better than that — it is the *documented design intent* of the XVM, and the runtime already enforces the invariant that makes it sound:

- `doc/x.md` ("On Processor Performance") specifies services as independent von Neumann machines "within which all allocations occur," states that "only immutable state can escape the execution scope of a service," and draws the explicit conclusion: garbage collection is "performed entirely within the context of individual services, without requiring the coordination of other services or containers." It even specifies the two cross-service collection exceptions (escaped immutables; the services/containers themselves) and the container "yank" property — discarding a whole container is cheap because nothing outside can point in.
- The interpreter enforces the boundary today in `ServiceContext.validatePassThroughArgs`: an argument crossing a service boundary is passed by reference iff immutable (or itself a service), frozen in place if `@AutoFreezable`, and otherwise **proxied** — every subsequent access to a proxy is a cross-service message, not a shared pointer.

That last point deserves emphasis because it is what makes the Erlang comparison exact rather than aspirational: in the XVM model, a mutable object never escapes its service by reference. Proxies are remote references. Therefore a native per-service heap needs no read barriers for foreign mutable state, no cross-heap mutable pointer graphs, and no global stop-the-world for service-local collection. The design questions reduce to: (1) where do escaped immutables live, and (2) how are proxies and service handles traced.

### Precedents the plans should cite and mine

- **Erlang/BEAM.** Per-process heaps, message copying, and a shared refcounted binary heap for large immutables. Thirty years of production evidence that per-actor heaps deliver: pause isolation, exact per-process memory accounting (`max_heap_size` even supports kill-on-limit — precisely the container hard-limit semantics [performance-runtime-strategy.md](performance-runtime-strategy.md) wants), and trivially cheap process teardown. BEAM's main tax — copying every message — is one Ecstasy mostly avoids, because immutables pass by reference.
- **Pony/ORCA.** An actor language whose type system (reference capabilities) statically guarantees data-race freedom, letting the runtime run per-actor heaps with *no* stop-the-world phase and deferred distributed reference counting for cross-actor references. Ecstasy's immutability + proxy rules give a coarser but similar static guarantee. ORCA's key lesson: when the type system already polices sharing, the GC can exploit it aggressively; its second lesson is that cross-heap accounting protocols (ORCA's message-based ref counts) are subtle and need model-level testing.
- **Shared immutable space.** Both precedents converge on the same shape the XVM docs propose: service-local young space plus a shared region for escaped immutables. For Ecstasy, freezing (`freeze()`, `@AutoFreezable`) is the natural promotion point: freezing an object graph can *relocate* it into the shared space, since `doc/x.md` explicitly licenses copying immutables ("one cannot determine whether an object is being passed by reference or by value if that object is immutable").

### Resolution

Adopt per-service heaps + shared immutable space as the committed design, not an option under study. The open engineering questions worth listing in the plans are narrower than the first pass implied:

1. Promotion policy: freeze-time copy vs. lazy promotion on first cross-service pass.
2. Shared-space collection: refcounting (BEAM binaries), immutable-friendly mark-region, or replication (immutables can be duplicated safely).
3. Proxy/service handle tracing: a cross-service reference registry per service pair, ORCA-style, with an explicit protocol test suite.
4. Accounting: allocation charges to the owning service at nursery-chunk granularity; escaped immutables charge the shared space with per-container attribution.

## MMTk vs. Custom Collector

The first-pass plans assume a hand-written collector ("simple exact mark/sweep or copying first, generational later"). Writing a competitive generational collector, with barriers, TLABs, large-object spaces, weak refs, and finalization, is a multi-year specialist effort — this is among the most common ways runtime projects stall.

**MMTk** (the memory-management toolkit, now a Rust framework with production bindings for OpenJDK, Julia, V8, and Ruby) provides exactly the layer the plans describe: allocation fast paths (bump-pointer in Immix), write barriers, per-mutator TLABs (which map naturally onto per-service/per-worker nurseries), and a menu of collectors (MarkSweep, SemiSpace, Immix, GenImmix, StickyImmix, MarkCompact) behind one object-model/scanning interface. Adopting it means implementing: an object model (header encoding, size queries), a scanning trait (enumerate reference fields — driven by the layout tables the ABI already requires), and root enumeration (fiber frames, safepoint maps). Everything else is inherited.

Two costs are real: MMTk pulls Rust into the kernel-language decision (see the open decision in [runtime-port-scope-plan.md](runtime-port-scope-plan.md) — this weighs it toward Rust), and the per-service-heap topology is not an off-the-shelf MMTk plan, so the service model would live above MMTk spaces rather than falling out of it.

**Resolution:** default to MMTk for the native prototype (Immix or GenImmix), with the custom collector as the fallback if per-service topology proves inexpressible. Do not staff a from-scratch generational collector before an MMTk spike has failed. Note also that the repo's experimental `runtime/gc` package (`GcSpace`/`MarkAndSweepGcSpace`, long-address handles over `long[]` object storage, soft/hard byte limits — currently dead code, unwired since 2023) is best read as a *shape prototype* for the object-model/scanning interface, not as the seed of a production collector.

## Exact vs. Conservative Roots

The first pass mandates exact GC from the start ("the collector must never conservatively scan arbitrary native stacks as the main strategy"). The handoff asks whether that is actually mandatory for the first native prototype. It is not, and the industry moved on this point recently:

- V8 itself adopted **conservative stack scanning** (for unified heap collection with Oilpan) — in one of the most performance-scrutinized VMs in existence.
- Ruby has always scanned stacks conservatively; SBCL and many production Lisps likewise.
- The standard hybrid — **exact heap tracing + conservative stack scanning + pinning** — requires only that (a) the collector can pin conservatively-referenced objects (Immix handles pinning naturally; semi-space copying does not), and (b) `xvm_ref` values on stacks are distinguishable word-aligned pointers.

This hybrid removes the largest single dependency of the early native milestones: compiled safepoint stack maps. LLVM's exact-root machinery (statepoints/`RewriteStatepointsForGC`) is precisely the piece with the worst maintenance record — few production users (Azul being the significant one), known optimization-inhibiting effects, and bitrot risk — so making milestone 1 depend on it is scheduling the riskiest component first.

**Resolution:** first native prototype uses exact heap metadata + conservative stack scanning + a pinning collector (another point for Immix/MMTk). Exact stack maps arrive later, and *only* on the paths that need them: compaction of directly-stack-referenced objects and the compact-continuation fiber tier. The ABI rule that keeps the door open is cheap: never derive `xvm_ref`s that point outside their object (no interior pointers in compiled code), and keep references in identifiable slots across helper calls. Update to [performance-runtime-strategy.md](performance-runtime-strategy.md)'s root-reporting section: "shadow stack first" is replaced by "conservative scan first, shadow stack only if a non-pinning collector is forced, stack maps last."

## Fiber Models: Three Options, Not One

The first pass commits to stackless continuation lowering: every safepoint carries live-value spill maps; suspension materializes compact continuation frames; resume goes through generated stubs. That is the highest-compiler-effort option on the menu, and committing to it *before any native code runs* inverts the risk curve. The menu:

**(A) Stackless continuation lowering** (first-pass choice; async/await-style frame externalization).
- Best suspended-fiber density (bytes ≈ live values). Worst compiler complexity: safepoint spill maps, resume stubs, function splitting; every optimization pass must preserve the maps. This is roughly the hardest part of a production async runtime, scheduled in the first phases.

**(B) Per-fiber small stacks with lazy commit** (wasmtime-style fibers; one mmap region per fiber, guard page, madvise-shrink on suspend).
- Engineering cost: near zero — a context switch primitive and a stack pool. Density: RSS proportional to *touched* pages (~4-16 KB typical), virtual address space is free on 64-bit. Compiled code needs no safepoint spill metadata to suspend; any call point can block. 100k fibers ≈ 0.4–1.6 GB RSS worst case, fine for a prototype and for most server workloads; not fine for the million-fiber embedded ambition.
- This is also what keeps FFI simple: a fiber blocked in native/host code just keeps its stack.

**(C) Copy-on-suspend contiguous stacks** (Loom/Go model: run on a real stack, copy live portion to heap when parking).
- Density comparable to (A) at suspension (bytes ≈ live stack), cheap straight-line code. But copying/relocating stacks requires precise knowledge of every pointer into and within the stack — Go and HotSpot can do it because they own their compilers' stack maps end-to-end. With LLVM-generated code this is effectively unavailable. Viable only for the JVM-hosted path (where Loom already does it — see below) or a far-future fully-owned code generator.

**Resolution:** ship (B) first for the native runtime — it decouples "fibers work, scheduler works, services work" from "compiler emits perfect live-value maps." Graduate hot services to (A) once the method IR and its safepoint metadata have matured *for other reasons* (deopt, OSR, exact stack maps — note these are the same mechanism; see the frame-externalization argument in [alternative-backends-and-precedents.md](alternative-backends-and-precedents.md)). Restate the performance gate honestly: not "no OS stack per suspended fiber" but "suspended-fiber RSS proportional to live state, with a path to sub-KB frames for hot services."

**JVM-hosted corollary.** On the Java-JIT path, model (C) already exists as virtual threads, and the `Ctx` TODO comments in `org.xvm.javajit` ("park this virtual thread and schedule a different fiber") show the JIT is *already designed around Loom* — a fact none of the first-pass documents recorded. The interpreter's hand-rolled fiber multiplexing (heap `Frame` chains over a fixed `ThreadPoolExecutor`) and the JIT's implied Loom model are two different concurrency architectures; the boundary documents should name the Loom-based one as the JVM-side plan of record and test per-fiber footprint under it.

## Deoptimization Across Object Worlds — the Missing Analysis

The first-pass plans repeatedly say compiled code "falls back to the interpreter." Verification shows there is no such path even inside today's Java JIT (an unsupported op aborts class generation; there is no mixed-mode execution, no deopt, and the two runtimes cannot exchange objects). For the native runtime the problem is qualitatively worse: once objects live in a native XVM heap, "fall back to the Java interpreter" would require migrating or proxying arbitrary object graphs between heaps mid-method. That is not a fallback; it is a distributed-systems problem.

**Resolution — the fallback tier must live in the same object world as the compiled code:**

- JVM-hosted phase: compiled (classfile-JIT) code and interpreter share the JVM heap; cross-tier calls at *method granularity* are plausible but require bridging `nObject`↔`ObjectHandle`, which today does not exist. Honest statement: in this phase, fallback = "don't compile that method," decided *before* execution, never mid-method.
- Native phase: the native runtime carries its own tier-0 **interpreter over the method IR** (which the plans want anyway as the reference tier). Deopt from native code targets the native IR interpreter — same heap, same object model, no world crossing. The Java runtime's role shrinks to differential-testing oracle over program-level inputs/outputs, never a runtime fallback target.

This resolves a latent contradiction between [llvm-jit-study.md](llvm-jit-study.md) (interpreter as fallback) and [performance-runtime-strategy.md](performance-runtime-strategy.md) (production loads no Java): both are right, in different phases, and the pivot point is "first native heap object."

## The `xvm_ref` Shape Problem: References Are (Identity, Type-View) Pairs

One more finding that tightens [llvm-object-abi-notes.md](llvm-object-abi-notes.md): Ecstasy references are *conceptually a two-tuple of identity and exposed type* (`doc/x.md`, "On References"). Masking (`maskAs`) is always allowed; revealing is container-gated; and the runtime today implements a re-typed reference by **cloning the handle** (`ObjectHandle.cloneAs`). A native `xvm_ref` that is a bare pointer to an object header cannot represent "the same object, seen through a narrower type" without either:

- fat references (pointer + type-view word) — doubles reference size everywhere, or
- runtime-allocated view wrappers (what the interpreter effectively does) — allocation per mask/re-type, or
- proving via the frozen type tables that for the vast majority of references the static type of the *use site* carries the view, so the dynamic pair is only needed where `maskAs`/`revealAs`/reflection actually occur — making view wrappers a rare boxed case rather than the representation.

The third option is almost certainly the right one, but it is a *type-system obligation on the module model* (use-site view information must be preserved into method IR), not a codegen detail. It belongs in the object-ABI contract now, before `xvm_ref` hardens into "it's just a pointer."

## Memory Accounting: Correct the Baseline

First-pass docs describe today's accounting as "approximate." Verification shows it is absent: `bytesReserved`/`bytesAllocated`/`backlogDepth` have no implementation; `cpuTime`/`upTime` raise "unknown native property"; `Container.Control.gc()` has no handler; the JIT's `Ctx.alloc/allocated/realloc/free` are empty TODOs; the only enforcement anywhere is a queue-depth constant. The only working quota code in the repository is in the dead `runtime/gc` experiment (soft/hard byte limits with OOM on hard limit).

This matters for prioritization: per-container accounting is a *headline language promise* (`doc/x.md`: "exactly measure and meter — in real time") with zero implementation on any path. It is also the single capability that most cleanly justifies the native runtime over the JVM-max path, since JVM-hosted accounting tops out at TLAB/JFR approximations per carrier thread. The plans should promote "first real accounting implementation" (even coarse, chunk-granularity, on the JVM-hosted path via `Ctx.alloc`) from TODO to an early milestone — it derisks the design and delivers a visible language feature before any native code exists.
