# Embedded JIT work moved to a local archive

The experimental embedding backend has been removed from `lagergren/constant-pool-ownership`
and preserved on the local branch **`archive/embedded-jit-ownership`**. The eventual target is
**`JIT`**; that branch has not been modified. Nothing has been pushed.

On the archive branch, `doc/embedded-jit-handoff.md` describes the machinery, source commits,
prerequisites and validation limits. `doc/patches/embedded-jit.patch` contains the extracted
implementation and tests, and `doc/jit-embedding.md` retains the full implementation notes.
The archive has an explicit snapshot base followed by a JIT-only restoration commit. Its base
also preserves the unfinished interpreter/compiler ownership work; do not cherry-pick the
snapshot or the whole original mixed commit onto `JIT`.

## Current execution behavior

DIRECT and PERSISTENT support interpreter compilation, execution and xUnit. Embedded JIT requests
are rejected with an ATTACHED instruction. The explicit `runSmallFloatsJit` task uses ATTACHED;
no JIT task was added to build/check or CI dependencies.

The public `RunRequest.Backend.JIT`, `ensureConnector(Backend)` and explicit-template `create`
signatures remain as placeholders. Submitting a JIT request throws
`UnsupportedOperationException("Will be implemented separately in the JIT branch")` before
module lookup or runtime startup. Three library-independent unit tests verify that contract.

The removal covers backend implementation, JIT controls and loader lifecycle, request consoles and
injections, five JIT lifecycle tests, and the newer JIT-specific constant-pool/cache fixes.
Existing JIT implementation already present on master remains. The unused `JitControl` stub
implements the common control interface and supplies the unsupported-operation message.

## Follow-up work

Port the isolated restoration commit deliberately onto `JIT`, accounting for its newer compiler
and runtime code. The original feature commit `c884779d6` also includes interpreter fixes and
manual-test rollout; those remain here. The [submission plan](../plugin/doc/plans/embedded-runtime-pr-plan.md)
records PR 8 as deferred and no longer makes the manual-test rollout depend on it.

JIT callable-type destination ownership and generated-name/cache invalidation remain deferred
with this extraction. Interpreter ownership tests do not establish JIT ownership or resource
cleanup. Historical JIT test results are recorded in the archive; they are not fresh validation
against the target `JIT` branch.
