# Is the `IsEq` composition cache worth building?

**One number decides it:** how often does `TypeConstant.callEquals` reach its composition
selection and find `clz1.getType() == this` *by identity* — the case where the existing
`TypeConstant.equals` short-circuit already makes the lookup free, and a cache would buy nothing?

**Measured answer: 0.20 %.** The identity short-circuit essentially never fires. It cannot fire,
for a structural reason given below. Everything else pays either a full `equals` or a full
`ensureClass`.

---

## What was instrumented, and how

Temporary static counters (`LongAdder`, so contention cannot lose counts) were placed in:

- `TypeConstant.callEquals` — the selection was expanded from the ternary chain into an explicit
  if/else with the *identical* branch conditions, one counter per outcome. `t.equals(this)` already
  begins with `obj == this`, so splitting it into `t == this` then `t.equals(this)` is semantically
  a no-op; it only makes the two cases countable.
- the six overriding `callEquals` implementations (`AnnotatedTypeConstant`, `UnionTypeConstant`,
  `IntersectionTypeConstant`, `DifferenceTypeConstant`, `RecursiveTypeConstant`,
  `RelationalTypeConstant`).
- the six op sites that call it: `IsEq`, `IsNotEq`, `JumpEq`, `JumpNotEq`, `JumpVal`, `JumpVal_N`.
  These also ran a **simulation of the proposed cache**: one entry per op site, keyed on
  (frame-resolved type, container, `hValue1` composition, `hValue2` composition), counting hit/miss.

A thread-local marker set at the op site and consumed by whichever `callEquals` is entered first
separates *direct op-site entries* from *entries reached by delegation* (an override forwarding to
`Utils.callEqualsSequence`, `xConst` comparing a property, `xRTDelegate` comparing an array element).

All of it has been removed; see "Instrumentation removed" at the end.

### Workload

`InterpreterProfileTest` (already in the tree, `@Tag("heavy")`), driving the `manualTests` modules
through one warm `XtcEngine`:

```bash
OUT=/tmp/prof
./gradlew :javatools:test --tests "org.xvm.api.InterpreterProfileTest.triage"     -PincludeHeavyTests -PtestMaxHeap=8g -Dxvm.profile.out=$OUT
./gradlew :javatools:test --tests "org.xvm.api.InterpreterProfileTest.profile"    -PincludeHeavyTests -PtestMaxHeap=8g -Dxvm.profile.out=$OUT
./gradlew :javatools:test --tests "org.xvm.api.InterpreterProfileTest.profileSteadyState" -PincludeHeavyTests -PtestMaxHeap=8g -Dxvm.profile.out=$OUT
```

Both measured runs reported `tests="1" skipped="0"` in
`javatools/build/test-results/test/TEST-org.xvm.api.InterpreterProfileTest.xml` — they were not
silently skipped on missing XDK outputs. The `compile-cold` and `compile-warm` phases recorded
**zero** counts, which is the expected sanity check: `callEquals` is a runtime-only path, so nothing
in the numbers below is compiler traffic.

**Excluded from the sweep** (26 of 45 sources survive triage):

| reason | modules |
|---|---|
| `assert:debug` — sets the process-global `debuggerActive` flag that changes the interpreter loop for every later run | `ConstOrdinalListTest`, `TestDec28`, `TestFizzBuzz` |
| hangs; wedges the singleton `TaskRegistry` for every later run (25 s timeout) | `StringBufferTest` |
| missing host resource (crypto / network / filesystem / CLI args) | `AesRawKeyRepro`, `TestSimple`, `TestTcpClient`, `TestCrypto`, `TestContained`, `TestCompiler`, `TestRun`, `SimpleApp` |
| fails on its own | `FailProbe` (deliberate), `NumericConversions`, `TestRanges`, `TestRegularExpressions`, `xunit_demo` |
| does not compile in this harness | `TestContainer`, `TestCompilerErrors` |

---

## The four counts

Steady-state pass of the sweep (`run-warm3`, taken as a delta over `run-warm2`, so no cold-start
work is included). Percentages are of the 5 383 867 entries into the **base** implementation from an
op site. The six buckets sum exactly to that total — asserted, not eyeballed.

| | count | share |
|---|---:|---:|
| **(d)** `hValue1 == hValue2` — exits before any selection | 17 410 | **0.32 %** |
| **(a)** `clz1.getType() == this` by IDENTITY — cache buys nothing | 10 562 | **0.20 %** |
| **(b)** `clz1` false by identity, TRUE by `equals` — full `compareDetails` | 522 978 | **9.71 %** |
| (a2) same, via `clz2` identity | 1 | 0.00 % |
| (b2) same, via `clz2` equals | 39 | 0.00 % |
| **(c)** both miss, `ensureClass(frame)` runs | 4 832 877 | **89.77 %** |

Cumulative over all four run passes (21 431 018 base entries) the shape is identical: (d) 0.48 %,
(a) 0.47 %, (b) 9.76 %, (c) 89.29 %. The cold pass is also identical in shape ((a) 1.28 %,
(b) 9.55 %, (c) 88.26 %). A second, independent execution of the whole sweep reproduced (b) within
1 % and (c) within 4 %.

**99.48 % of calls take a path the cache would eliminate.** The distribution is the opposite of the
one that would kill the idea.

### Base vs. override

| receiver class | count | share | base impl? |
|---|---:|---:|---|
| `TerminalTypeConstant` | 5 379 840 | 99.849 % | yes |
| `UnionTypeConstant` | 4 105 | 0.076 % | **overrides** |
| `ParameterizedTypeConstant` | 3 757 | 0.070 % | yes |
| `ImmutableTypeConstant` | 278 | 0.005 % | yes |
| `VirtualChildTypeConstant` | 2 | 0.000 % | yes |
| `PropertyClassTypeConstant` | 1 | 0.000 % | yes |

**99.92 % of op-site calls land on the base implementation.** `UnionTypeConstant` is the only
override that ever executes; `AnnotatedTypeConstant`, `IntersectionTypeConstant`,
`DifferenceTypeConstant`, `RecursiveTypeConstant` and `RelationalTypeConstant` were **never entered
from an op site at all** in any run. The override hazard is real but it is 0.08 % of the traffic —
it constrains the design, it does not shrink the prize.

Delegated entries (`xConst` field-by-field, `Utils.callEqualsSequence`, array/tuple elements) are
4 232 against 5 383 867 from op sites — **0.08 %**. An op-level cache is therefore not leaving a
large second population unserved; in this workload the six ops *are* the traffic.

### Would the cache actually hit?

Simulating one entry per op site, over the 5 369 321 calls that survive the `hValue1 == hValue2`
fast path:

| | count | share |
|---|---:|---:|
| hit | 5 362 281 | **99.87 %** |
| miss (site went polymorphic) | 5 878 | 0.11 % |
| cold (first execution of that site) | 1 162 | 0.02 % |

1 162 distinct op sites carry 5.4 M executions. A monomorphic one-entry cache is enough; nothing
here argues for a polymorphic one.

**A soundness result fell out of the simulation.** Keying on the composition pair alone — the
obvious key — would have returned a *stale* composition 2 426 times (0.045 %), because the same op
site can execute with a different frame-resolved type (`OpTest.calculateCommonType` calls
`frame.resolveType`, which resolves generics per frame) or in a different container. Wrong
`TypeComposition` means dispatching to the wrong template's `equals`: a correctness bug, not a slow
path. The key must include the resolved type and the container. That is what the 99.87 % above was
measured with.

---

## Second workload: the steady-state loops

`HotArith` / `HotCall` / `HotVirtual` — tens of seconds each inside one fiber, so the numbers are
the inner loop and nothing else. Deltas per phase:

| | `HotArith` | `HotCall` | `HotVirtual` |
|---|---:|---:|---:|
| calls | 80 000 315 | 20 000 329 | 20 000 335 |
| (a) identity hit | 22 | 22 | 22 |
| (b) equals-but-not-identical | 80 000 206 (**99.9999 %**) | 20 000 221 | 20 000 227 |
| (c) `ensureClass` | 63 | 64 | 64 |
| cache hit rate | 100.0000 % | 100.0000 % | 100.0000 % |
| distinct op sites | 4 | 4 | 4 |

In a pure interpreter loop the identity short-circuit fires 22 times in 80 million. Every single
iteration pays `getFormat()` + `compareDetails()` + `Constant.compareTo`.

### Why the identity check never fires

Counting the pools of the two constants in case (b):

```
equalsOnly.differentPool  120,005,949
equalsOnly.samePool                 0
```

**100 %, in both workloads, in every phase.** The op's compile-time type comes from the method's
constant array (the *module's* `ConstantPool`); `clz1.getType()` is the composition's revealed type,
registered in the *container's* pool. They are equal and can never be identical, so `obj == this`
in `TypeConstant.equals` is dead code on this path by construction. This is not a workload
accident — it is why `callEquals` is expensive at all.

---

## Is the ~10 % estimate real?

Yes, and it is conservative for the sweep. From the JFR recordings of the instrumented runs, with
deep stacks (`jfr print --stack-depth 128`; the default 5-frame print is useless here and will lie
to you), splitting `callEquals` inclusive time into *selection* (below `ensureClass`/`resolveClass`,
or below `equals`) versus the actual comparison:

| phase | `callEquals` incl. | selection | actual comparison |
|---|---:|---:|---:|
| sweep `run-warm2` | 15.27 % | **14.30 %** (93.6 % of it) | 0.97 % |
| sweep `run-warm3` | 16.28 % | **15.20 %** (93.4 % of it) | 1.08 % |
| `HotArith` | 15.11 % | **10.22 %** (67.6 % of it) | 4.89 % |

`callCompare` — byte-for-byte the same selection code — adds another 0.50 % in the sweep.

So the recoverable share is **~14–15 % of interpreter CPU in the manualTests sweep** and
**~10 % in the arithmetic loop**, against the profile doc's "~10 % recoverable". The W6 estimate was
right and, for a mixed workload, understated.

Two corroborating details: `Constant.compareTo` is still the hottest self frame (10.21 %), and
`Container.resolveClass` is 21.27 % inclusive of which 13.47 points sit under `callEquals` — the
89.77 % case (c) paying `pool.register` + `getTemplate` + `normalizeParameters` +
`ensureClassComposition`, three-plus hash lookups keyed on a `TypeConstant`, per comparison.

---

## Verdict

**Build it.** The measurement was meant to be able to kill the idea and did the opposite:

- the cheap case the cache cannot help — (a), identity — is **0.20 %**, and structurally so;
- the expensive cases it does help are **99.48 %**, dominated by the *worst* one (`ensureClass`, 89.8 %);
- **99.92 %** of calls reach the base implementation, so overrides do not shrink the prize;
- a one-entry monomorphic cache hits **99.87 %** (100 % in the hot loops);
- the selection is **93 %** of `callEquals` inclusive time, so a hit converts almost the whole cost
  into four reference comparisons.

Design constraints the measurement produced, both non-obvious:

1. **The key must include the frame-resolved type and the container.** A composition-pair key is not
   merely lossy, it is *wrong* — measured, 0.045 % of the time.
2. **Guard the override receivers.** 0.08 % of traffic (only `UnionTypeConstant` in practice), but
   the cached composition is meaningless for them.

`callCompare` should get the same treatment in the same change; it is the identical code.

**A cheaper alternative worth pricing first.** Since case (b) is 100 % cross-pool and case (c) ends
in `pool.register` anyway, canonicalizing the op's type into the container's pool *once* — at op
resolution rather than per execution — would make the existing identity check fire and need no new
cache, no new key, and no override hazard. It is a smaller change and would collapse case (b)
outright. Whether it also collapses case (c) was not measured, and case (c) is the 89.8 %; that is
the next thing to find out. Do not skip it on the strength of this report.

## Confidence, and what would change the answer

**High** on the counts. They are exact, not sampled; buckets sum to their totals by assertion;
`LongAdder` cannot lose increments; compile phases correctly recorded zero; two independent
executions agree within 4 %; and two structurally different workloads (26-module sweep, pure loops)
give the same verdict from opposite directions ((c)-dominated vs (b)-dominated).

**Medium** on the CPU percentages. They are JFR sampling, taken from runs carrying the
instrumentation itself — 1.5 % of samples in the sweep, 12.6 % in `HotArith` (a `ConcurrentHashMap`
lookup per op site). That dilutes every other percentage and perturbs JIT inlining of the hot path,
which is the likely reason `HotArith` reads 15.11 % here against 21.33 % in the clean profile. The
*ratio* selection : comparison is far more robust than the absolute shares, and it is the ratio the
verdict rests on.

What would change the answer:

- **A workload with a different mix.** These 26 modules are loop- and primitive-heavy. A `const`-
  heavy or collection-heavy program routes more traffic through `xConst.callEquals` and
  `xRTDelegate` element comparison, which arrive by *delegation* (0.08 % here) and which an op-level
  cache does not serve at all. If delegation were the majority, the recoverable share would fall
  even though (a)/(b)/(c) stayed the same.
- **Real polymorphism at hot sites.** 99.87 % monomorphic is what makes the one-entry design work.
  A program mixing types at a hot `IsEq` would degrade it, and per W7 an inline cache that thrashes
  is *worse than none* — that is exactly what `setOpInfo` does in `HotVirtual`.
- **The pool-canonicalization alternative succeeding.** If registering the op's type in the
  container's pool at resolution time also collapses case (c), most of this 15 % is available
  without a cache, and the cache should not be built.
- **A cache-hit path that is not near-free.** The 93 % figure assumes a hit costs four reference
  comparisons. Route it through anything resembling `f_mapOpInfo` (a `WeakHashMap`, per W7, at
  4.78 % self for `getOpInfo` alone) and a large part of the win is spent before it is banked.

## Instrumentation removed

All counters, the six op-site hooks, the six override hooks and the temporary `EqStats` class have
been removed. `git diff` over
`javatools/src/main/java/org/xvm/asm/constants/`, `javatools/src/main/java/org/xvm/asm/op/` and
`javatools/src/test/java/org/xvm/api/InterpreterProfileTest.java` is empty, `javatools` compiles
clean, and no file in the tree references `EqStats`. This report is the only artifact.
