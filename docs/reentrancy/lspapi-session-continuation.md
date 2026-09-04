# Continuation prompt - working PR #545 findings one at a time

Paste the block below into a fresh session.

---

We are working through the review findings for **PR #545** (`cpurdy/LSPAPI`), **one issue at a
time**, placing each fix where it belongs rather than doing them in bulk.

## State

- Working repo: `/Users/marcus/src/xtclang0`, branch `lagergren/lazy-instance`, worktree clean.
- Their branch is checked out **read-only** at `../lspapi` (`cpurdy/LSPAPI` @ `2568d6be4`),
  currently **0 modified files**. Keep it that way unless I say otherwise.
- `origin/master` is `443770bcc` (as of 2026-09-04; the earlier `036e42ffd` was a stale local ref,
  and is an ancestor of it). Re-check with `git fetch origin master` rather than trusting this line.
- **PR #545 is Marcus's own draft PR whose head branch is `cpurdy/LSPAPI`** - opened as a review
  vehicle because Cameron and Gene had not opened one. Head sha `2568d6be4`, 8 commits (3 Cameron,
  5 Gene), `master...cpurdy/LSPAPI` is ahead 8 / behind 0. So commenting on #545 *is* commenting on
  their branch, and the six review threads live there. Do not close #545 and open a replacement:
  the threads do not travel, and H1 is meant to go as a reply into the one at `LspSupport.java:83`.
  Edit the title and body in place instead.

## Read these first

1. `docs/reentrancy/lspapi-review-playbook.md` - the running order, the disposition of each
   finding, the exact comment text and the file:line it anchors to, and a question router.
2. `docs/reentrancy/lspapi-integration-analysis.md` - the evidence behind every finding
   (**H1-H21**), Parts 1-6. Part 6 has a status ledger of what has already been migrated.
3. `PROJECT.md` - what this branch is for, if you need the wider frame.

Do not re-derive the findings. They are already evidenced with file and line references, and
several contain corrections of earlier wrong readings that should not be reintroduced.

## What has already been done

The LSPAPI runner model is **already migrated into this branch**: `MainContainer.invokeAsync`, the
`InterpreterConnector` accessors, `NativeContainer` dynamic resources, the per-handle repository on
`xCoreRepository`, `lib_runner` in the build (with its retention leak fixed), a redirectable console,
and the engine's run path going through `runTask`. `NestedContainer.createForHost`,
`registerInjections` and `RuntimePlane` are gone.

**Test state: 672 tests, 2 failing, deliberately.** `PerRunInjectionTest` (H19) and
`RepeatedRunSweepTest` (H21). Both report defects that apply to *their* branch as well, evidenced
with upstream file:line. Do not "fix" them by weakening the tests.

## How we work

- **One finding at a time.** Tell me which one you are taking, or ask me for the next in playbook
  order. Do not start several.
- **Respect the disposition** in the playbook: commit-directly vs sub-branch vs comment-only vs
  separate-master-issue. If you think a disposition is wrong, say so and why before acting.
- **Never push, open a PR, or comment on GitHub without me explicitly asking.** Local commits and
  local branches are fine.
- Anything applicable to master becomes a **local branch, no PR**, and gets a row in
  `docs/reentrancy/plans/master-issue-submissions.md` (bugs, currently up to row 46) or
  `master-enhancement-submissions.md` (enhancements, currently up to E38). A row is only marked
  filed when it has a red-on-master reproduction; otherwise it says "latent, NOT filed" with the
  reachability argument.
- **Verify, do not assume.** Cite file and line. Several confident claims in this work were wrong
  and were caught by measuring; keep doing that, and record corrections rather than quietly editing.
- Build notes: run `./gradlew` from the repo root only; `clean` runs alone; use
  `--rerun-tasks --no-build-cache` for tests and read results from
  `javatools/build/test-results/test/*.xml`, not the console. `-PtestMaxHeap=8g` for the
  concurrency tests.

## Start

Confirm the state above still holds, then tell me which finding you propose to take first and where
it goes. Default to playbook order unless I say otherwise: **H19** is the blocking one, **H21** is a
question to ask rather than a patch to write, and **H5** is the first thing that can simply be
committed.
