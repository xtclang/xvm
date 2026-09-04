# What this branch is doing, and why

`lagergren/lazy-instance` is an **experimental branch**, not a release candidate. Its purpose is to
find out what stops the XVM compiler and runtime from being safely reused - by a resident host that
serves many requests in one warm JVM - and to fix what can be fixed while writing down what cannot.

Nothing here is pushed anywhere without being asked for. Work that applies to `master` is prepared
as a **local branch with no PR** and written up in the submission lists below.

## The one-sentence version

The compiler is correct when one thread owns everything and exits afterwards; almost every defect
found here is a **fact about one call stack stored where every call stack can see it**, and the
per-compile clone that hides them costs about 18% of a warm compile.

## The principle everything converges on

Prefer a mechanism that is reentrant **by construction**. In order of preference:

1. **No shared mutable state** - a function of its inputs. Nothing to re-enter.
2. **State on the call stack**, passed as a parameter. Correct under any execution model; this is
   what `ErrorListener` already does.
3. **State owned by the executing thread** (`TypeSystemThread`). Correct while one task equals one
   thread - which is why a work-stealing pool or `parallelStream` is *not* usable here.
4. **A marker on shared state.** What most of the bugs were. It asks *"am I inside?"* and answers
   *"is anyone inside?"*.

Being *thread-safe* does not rescue a mis-scoped fact: `m_cRecursiveDepth` was an `AtomicInteger`,
so its count was always correct, and it was still wrong because the quantity belonged to one
descent and was shared by all of them.

The smell is greppable: `m_FVisited`, `m_fRecurseReg`, `m_cRecursiveDepth`, or any javadoc saying
*recursion check*, *in progress*, *being built*, or *not thread-safe*.

Full statement, with the ladder and the two hard-won corollaries (reset in a `finally`; state that
is not there needs no clearing): [parallel-compiler-plan.md](docs/reentrancy/plans/parallel-compiler-plan.md).

## Where to look

### The active work

| document | what it holds |
| --- | --- |
| [plans/parallel-compiler-plan.md](docs/reentrancy/plans/parallel-compiler-plan.md) | **Start here.** T1-T15: serving one prepared library to concurrent compiles. Every task carries what was measured, what was tried and rejected, and why. |
| [engine-compile-divergence.md](docs/reentrancy/engine-compile-divergence.md) | The investigation log for the engine-vs-CLI miscompile, including the reductions that were rejected and the reason. |
| [fixed-in-this-branch.md](docs/reentrancy/fixed-in-this-branch.md) | Fixes that landed here, with their red-on-master proof where one exists. |

### The lists that decide what gets done

| list | what qualifies |
| --- | --- |
| [must-audit-backlog.md](docs/reentrancy/must-audit-backlog.md) | **Must audit** - safe only because of an assumption not encoded in the API. Between must-fix and should-fix. |
| [plans/global-issue-pr-backlog.md](docs/reentrancy/plans/global-issue-pr-backlog.md) | The cross-cutting backlog: what is filed, what is prepared, what is waiting. |
| [plans/github-issue-breakdown.md](docs/reentrancy/plans/github-issue-breakdown.md) | How the findings were split into filable units. |

The distinction the lists turn on:

- **Must fix** - a concrete race, wrong-owner cache, constructor escape or broken lifecycle is
  *known*.
- **Must audit** - the code may be safe only because of an assumption that is not encoded anywhere,
  such as single-threaded compilation.
- **Should fix** - real, bounded, not load-bearing for correctness today.

### For master

| list | contents |
| --- | --- |
| [plans/master-issue-submissions.md](docs/reentrancy/plans/master-issue-submissions.md) | **Master bugs.** Rows 1-45. Each row: master evidence with file and line, failure mode, reachability argument, minimal portable fix, and whether it is proven red on master or only latent. |
| [plans/master-enhancement-submissions.md](docs/reentrancy/plans/master-enhancement-submissions.md) | **Master enhancements.** Rows E1-E37. Design-level improvements, sized and ordered, with dependencies named. |

**A row is only marked filed when it has a red-on-master reproduction.** Rows without one say
"latent, NOT filed" and give the reachability argument instead of pretending. Rows 41-45 are the
current examples: all verified present verbatim in master source, none with a master-side
reproduction yet.

### Deeper audits

`docs/reentrancy/` holds the per-area audits that feed the lists - constant pool state, ambient
context, clone usage, generics, `toString` purity, exception hygiene, JIT ownership, and others.
They are reference material; the lists above are what is acted on.

## How this branch works

- **Experimental, and PRs are not implied.** Anything applicable to master becomes a local branch,
  no PR, unless a PR is explicitly requested.
- **Measure before proposing a mechanism.** Several confident root-cause claims in this branch were
  wrong and were disproved by measurement - including one whose correction is preserved in T12,
  because the wrong version had already been acted on.
- **A green test is evidence only for what it varied.** 40 of 40 clean iterations turned out to mean
  "this one submission order works"; shuffling the order found three new failure classes
  immediately. Iterations, thread count and seed are all knobs for this reason.
- **Never swallow.** A silent `catch` has twice been the defect rather than a cover for one.
