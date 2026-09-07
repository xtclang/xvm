# Building the XDK in-process on `XtcEngine`: is it worth it?

Asked 2026-09-07. Short answer: **yes as a correctness harness, no as a performance exercise**, and
there is a blocker that has to be faced first either way.

## 1. The dependency graph, extracted rather than assumed

22 modules, 142,390 lines of `.x`. Edges come from `package X import X.xtclang.org` in each module
root, plus the implicit dependency every module has on `ecstasy` (and `ecstasy`'s on turtle), which
is not a declared import and has to be added by hand.

| level | parallel | modules |
| --- | --- | --- |
| 0 | **1** | `lib_ecstasy` (67,859) |
| 1 | 7 | `json` 9,090 · `oodb` 3,000 · `collections` 2,512 · `xunit` 2,337 · `crypto` 1,391 · `cli` 619 · `aggregate` 614 |
| 2 | 4 | `jsondb` 14,506 · `xunit_engine` 5,250 · `net` 4,179 · `metrics` 189 |
| 3 | 2 | `convert` 1,405 · `xunit_db` 508 |
| 4 | 2 | `xml` 5,624 · `sec` 1,967 |
| 5 | **1** | `web` 7,601 |
| 6 | 4 | `xenia` 6,254 · `javatools_bridge` 5,722 · `webauth` 931 · `webcli` 387 |
| 7 | **1** | `runner` 445 |

## 2. The performance answer: 1.26x, and that is the CEILING

Serial total 142,390 lines; critical path 112,784. **Theoretical speedup 1.26x** with perfect
parallelism, unlimited cores and zero per-compile overhead.

The reason is `lib_ecstasy`: **48% of all source, in a level of its own, that everything else waits
on.** No scheduling can help with that, and the tail (`web` alone at level 5, `runner` alone at
level 7) adds two more serial points.

Lines are a crude proxy for compile cost and `ecstasy` is probably *worse* than its share, being the
core type system that every later `TypeInfo` resolution walks into. So 1.26x is optimistic.

**The corollary matters more than the number.** The XDK build is the **worst possible showcase** for
a parallel compiler, because the library is the output. The case T1-T5 actually target - many
independent compiles against a *stable* prebuilt library, which is what an LSP server or a test
runner does - has no serial prefix at all. Measuring parallel compilation on the XDK build would
understate it by design.

## 3. The blocker: the engine does not compile the same as the CLI

[engine-compile-divergence.md](engine-compile-divergence.md) is open. `xcc` compiles `lib_json`
with 0 diagnostics; `XtcEngine.compile(json.x)` gives 5, because a nullable local's declared type is
lost (`StringBuffer? buf = Null` treated as `Null`), so the narrowing after assignment never
happens. Root cause is localized to narrowing state surviving into the engine's second validation
pass; it is not fixed.

`lib_json` is **level 1** - the first thing after `ecstasy`. So an XDK build on today's engine gets
about two modules in before it stops. That is not a reason to skip the harness. It is a reason to
build the harness, because "how many of the 22 does the engine manage" is a far better progress
metric for that investigation than the current single-module reproducer.

## 4. What is worth building, in order

**Step 1 - a SEQUENTIAL in-process XDK build. Do this now.**

One engine, 22 modules in dependency order, each module's output fed into the next's input
repository. Per module: pass/fail, diagnostics, wall time, heap after. It buys three things the
current tests do not:

- **A real progress number** for the divergence investigation: modules compiled / 22.
- **Rerunnability at realistic scale.** Run the whole sequence twice on one engine and compare. That
  is exactly the shape of [T15](parallel-compiler-plan.md)'s retention leak, at 22 compiles instead
  of a microbenchmark, and it is the "rerunnable compiler" question directly.
- **Real per-module timings**, replacing the line-count proxy above, which will say whether
  `ecstasy` is 48% of the cost or 80%.

None of it needs threads, so none of it can be confounded by a race.

**Step 2 - add parallelism per level, ONLY after step 1 is green.**

Levels 1, 2 and 6 are where the width is. Per
[the plan's execution model](parallel-compiler-plan.md), it must be **one task = one thread** - a
virtual thread per compile - and explicitly **not** `parallelStream`, whose common ForkJoinPool runs
several tasks on one carrier thread and would bleed the per-call `TypeSystemThread` state between
them.

Treat step 2 as a **race detector, not a speedup**. Expect 1.26x at best; the value is that a
dependency-ordered real workload is a much better stressor than a synthetic one.

## 5. What NOT to conclude

- Do not read 1.26x as "the parallel compiler is not worth it". It says the *XDK build* is a poor
  vehicle for it. The LSP and test-runner cases have no serial prefix.
- Do not build step 2 before the divergence is fixed. Parallel wrong answers are harder to debug
  than sequential ones, and every failure would first have to be triaged against a known-open
  correctness bug.
