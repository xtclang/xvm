  
  Continue the hardening-and-extraction work on branch `lagergren/lazy-instance` in
  /Users/marcus/src/xtclang0 (XVM/Ecstasy compiler + runtime). Your mission has two
  parts, in order: (1) close out the remaining audit umbrellas, graduating and fixing
  anything they prove; (2) bring the branch to PR-launch readiness - everything staged
  so the human can start filing PRs with single approvals. You do NOT file, push, or
  create anything remote yourself, ever.

  == NON-NEGOTIABLE RULES ==
  - Read AGENTS.md first and obey it exactly. Never push, never create PRs or remote
    branches, never delete branches, never run any `gh` command that mutates remote
    state. The branch has ~46 local commits ahead of origin (origin was last pushed to
    aecc76821 by the user); it stays that way until the user says otherwise.
  - Never run `./gradlew clean` combined with any other task. Configuration cache must
    stay green. Both fatal lint gates (-Xlint:this-escape, -Xlint:fallthrough under
    -Werror) must stay green.
  - Every code fix documents: (1) what was broken in master, (2) why the original
    design is unsafe/bad, (3) why the replacement is semantically equivalent or
    intentionally different, (4) what test proves it. Every new test is red-verified
    against the old shape (git stash of ALL interdependent changed files together - a
    compile failure is NOT a valid red; widen the stash until the red is behavioral)
    and carries JavaDoc explaining the original failure.
  - Classify every fix honestly in docs/reentrancy/plans/github-issue-breakdown.md:
    Category A (master bug, single-run reachable), H (portable hardening), B
    (reentrancy enablement), C (branch-internal repair). The user asks "is this a real
    single-JVM master bug?" constantly and wants "no" when the answer is no.
  - Verify every quantitative claim ("all X retired", "zero remain") with a BROAD grep
    pattern before recording it - a prior session recorded "ALL 23 switches retired"
    while 7 survived because the verification grep used a too-literal receiver name.
  - Gate every commit: `./gradlew :javatools:test :javatools_utils:test` and
    `./gradlew xdk:installDist`. For verification claims, force real execution with
    `--rerun --no-build-cache` and confirm via test-result XML timestamps - cache hits
    finish in ~5s and prove nothing. Gate only committed/clean states, never a dirty
    tree another writer is editing.
  - One focused commit per issue. No Co-Authored-By lines. End files with newline, no
    star imports, no FQNs where an import belongs, `var` when the RHS makes the type
    obvious.
  - The authoritative task board is docs/reentrancy/must-audit-backlog.md
    ("Consolidated Must-Fix Task List"). Trust it over this prompt if they diverge,
    and keep it updated as you close rows.

  == CURRENT STATE (verify with git log before starting) ==
  Tip ~cd432c873 "Record the sealing campaign's designed end state". All previously
  graduated must-fixes are closed with tests except: two JIT owner-statics rows
  (parked by design until a JIT rebase - do NOT start them) and issue #541 (open
  design-level master bug - analysis in docs/reentrancy/541.md; its FIX is out of
  your scope). The sealing campaign is complete (48 sealed declarations, every
  surviving format switch categorized). The priority-one Category A master-bug table
  in github-issue-breakdown.md is current, ~18 rows, each with a named passing test.
  Key docs: must-audit-backlog.md, plans/github-issue-breakdown.md (Category A/H/B/C,
  PR slices 1-19, the 2026-08-24 commit-to-PR map), runtime-ownership-hardening-
  ledger.md, fixed-in-this-branch.md, test-failure-evidence.md, clone-usage-audit.md,
  array-element-exposure-audit.md, sealed-hierarchy-audit.md, 541.md,
  ownership-diagnostics.md.

  == PART 1: REMAINING AUDITS (work them in this order) ==
  For each: read the named source docs first, audit by reading sites (never classify
  from grep alone), record evidence per row, and graduate anything proven to a
  must-fix row. Graduated items that are runtime/asm-scoped: fix them under the full
  discipline above. Graduated items that are compiler-scoped or JIT-scoped: record
  them with evidence but do NOT fix (compiler fixes go to the separate compiler
  branch; JIT fixes wait for the rebase).

  1. Complete ConstantPool ownership/mutation/late-registration model (backlog rows
     107/172). The biggest unknown on the board. Map: who writes a pool after
     publication, every late-registration path, ambient-pool entry points that
     survived the earlier hardening, the deferred-constant machinery, and whether a
     published runtime pool can be mutated by a second compile/run in the same JVM.
     Deliverable: extend clone-usage-audit.md or a new constantpool-ownership-audit.md
     section with a site-by-site table; graduate proven mutations.
  2. Runtime-published pools remain mutable unless diagnostics are enabled (row 109,
     already flagged "likely must fix"). Determine exactly what freeze/validation is
     diagnostics-gated, what invariant production runs lose, and whether an
     always-on cheap freeze is possible. If proven: graduate and fix.
  3. Owner-bearing type/class metadata caches (rows 132/174). Sweep the runtime
     metadata caches (TypeInfo layering, class/composition caches) for entries that
     embed an owner and outlive it or serve a different owner.
  4. xRTSocket.finishConnect mask escape (row 151). The circulation trace proved it
     returns the revealed inception view, defeating maskAs(net.Socket). Decide the
     fix (re-mask before returning?), state what application code observably holds
     before and after, fix with a two-view test if runtime-scoped and safe.
  5. clone()/Cloneable residue (row 179). The Token/Source and structure-family waves
     consumed most of it; enumerate what still implements Cloneable or calls clone()
     in javatools main, classify each survivor, close the row honestly.
  6. JIT ambient-helper API umbrella (rows 139/140) - AUDIT ONLY: classify and record;
     fixes stay parked with the JIT rows.
  7. Compiler AST/context mutation (row 175) - AUDIT ONLY: evidence and row updates;
     fixes belong to the compiler-reentrancy branch.

  == PART 2: PR-LAUNCH READINESS ==
  Goal: the user can start filing with nothing left to prepare. Everything stops at
  "ready-to-file"; the human does every push.

  1. Fetch master read-only (git fetch origin master - fetching is allowed, pushing
     is not) and, in a scratch worktree (git worktree add under the session scratchpad
     - never disturb the main checkout), dry-run cherry-pick each Category A fix onto
     origin/master. Record per row: applies clean / needs which trivial conflict
     resolution / blocked by what dependency. The jsondb rollback row claims "applies
     verbatim" - verify that claim now.
  2. Re-verify every Category A row end-to-end: named test exists, passes at tip, the
     red-on-master claim is recorded in test-failure-evidence.md, and the row names
     the exact commits. Fix any gap you find (the FullyBoundHandle test gap was found
     this way - assume there is another).
  3. Audit the commit-to-PR map (github-issue-breakdown.md) against `git log
     origin/lagergren/lazy-instance..HEAD` - every commit since the map was written
     (wave C, composition sealing, UOE naming, 541 docs, ledger repairs) must be
     assigned to exactly one PR slice or explicitly marked branch-bookkeeping.
  4. Produce the PR launch plan as a new section in github-issue-breakdown.md:
     the ordered queue (defect-first: jsondb rollback, reflection tuple corruption,
     short-hand Parameter sharing as openers; then the remaining Category A rows;
     then Category H gates/guards; then PR 16-19 modernization, PR 19 still marked
     DO-NOT-FILE until explicit approval), per-PR: title, exact commit hashes, the
     dry-run cherry-pick result from step 1, the ready-to-paste description per the
     doc's template, dependency edges, and the one-line "what the human must do"
     (create branch X from master, cherry-pick hashes Y, push, open PR with body Z).
  5. Final report: the launch plan location, any newly graduated/fixed rows from
     Part 1, updated open-board summary (musts/audits/shoulds), and anything that
     blocks filing which only the user can decide.

  == OUT OF SCOPE (do not start) ==
  The #541 fix and X-ray slices 2-5 (separate workstream), the two JIT must-fix rows,
  compiler-branch fixes, logging (parked last by user decision), Op/Argument/
  TypeComposition-root sealing (costed optionals awaiting a user decision).

  Two notes on feeding it: the audits in Part 1 are read-heavy and independent, so the agent can legitimately parallelize items 1–3 with sub-agents if it
  has that capability; and Part 2 step 1 (the master dry-run) is the single most valuable early signal — if master has drifted under the Category A
  hunks, the user will want to know before anything else.
