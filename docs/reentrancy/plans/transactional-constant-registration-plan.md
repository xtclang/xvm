# Transactional Constant Registration Plan

This plan covers the long-term replacement for the transitional
`ConstantPool.register(...)` completion guard in `lagergren/lazy-instance`.

## Problem Statement

`ConstantPool.register(...)` currently has two responsibilities mixed into one
public data structure:

- a private recursion worklist for registering cyclic constant graphs;
- the public pool storage used by normal readers, lookup maps, diagnostics, and
  serialization.

Master publishes a newly inserted constant by assigning its position, appending
it to `f_listConst`, and putting it into the constant lookup map before
`registerConstants(...)` and valid-pool checks finish. That was probably done so
same-thread recursive registration can resolve cycles. The design error is that
normal public readers can observe the same in-progress object.

The current branch adds a compatibility guard:

- the registration owner may still resolve its own in-progress constant;
- other threads wait for recursive registration to complete;
- failed recursive registration remains failed for later readers.

That guard is a hardening step, not the final architecture.

## Why The Old Design Is Wrong

The public meaning of "registered" should be "this constant is stable in this
pool." In master, "registered" sometimes means "temporarily discoverable so a
recursive registration call can find it."

That is bad even before arbitrary parallelism:

- phase is hidden in mutable object state rather than represented by an API;
- tests and diagnostics can accidentally observe a phase-local object;
- `registerConstants(...)` can still rewrite child references after map
  insertion;
- equality, hash, locator, and owner validity depend on an undocumented ordering
  rule.

For parallel/same-JVM containers, the failure becomes direct: another Java thread
can read the public pool index or lookup map while the registration owner is
still adopting child constants.

## Target Design

Registration should be transactional from the perspective of public readers.

1. Create a registration context owned by one registration call stack.

   The context owns:

   - a private identity map from source constants to in-progress target
     constants;
   - a private list of constants whose public position has not been committed;
   - a private locator map for locators discovered during the transaction;
   - failure state and diagnostics context.

2. Adopt or construct target constants privately.

   Recursive registration should look in the private identity map first. It
   should not need `f_listConst` or the public constant lookup maps to break
   cycles.

3. Run `registerConstants(...)` and valid-pool checks against the private
   context.

   During this phase, the context may resolve cycles, adopt child constants, and
   gather locators. Public readers should still see only the previously committed
   pool state.

4. Validate publication invariants.

   Before commit, assert:

   - logical hash and equality are stable;
   - locators do not collide with existing committed locators;
   - all child constants belong to valid pools or the destination pool;
   - no live runtime handle moves across owners unless a dedicated owner-local
     representation allows it.

5. Commit under the pool lock.

   Assign final positions and publish constants to `f_listConst`,
   `m_mapConstants`, and `m_mapLocators` only after the graph is complete.

## Open Compatibility Questions

The existing code may depend on public positions during recursive registration.
The transaction design must audit and either preserve or remove those uses.

Known compatibility points:

- `Constant.getPosition()` can be read from subclass `registerConstants(...)`.
- `indexOf(...)` may be used while assembling nested constants.
- locator adoption can itself register constants.
- recursive reference counting under `m_fRecurseReg` currently uses the public
  registration path and `Constant.addRef()`.
- unresolved or non-shareable type constants are returned without adoption.

The transaction can preserve the visible semantics by assigning temporary
transaction-local positions and remapping to committed positions at the end, but
that should be added only if an audit proves positions are required during the
private phase.

## Performance And Footprint

Steady-state runtime should not pay for transactional registration. The work is
registration/linking/warmup work.

Expected cost:

- one short-lived registration context per outer registration transaction;
- private identity/hash maps sized to the constants touched by that transaction;
- no per-constant completion object after commit;
- no public-reader volatile flag in the final architecture.

Compared with the current guard, the transaction should reduce runtime-reader
overhead and make the registration phase easier to reason about. The cost shifts
to registration time, where ownership correctness matters more than avoiding a
small private worklist.

## Incremental Implementation Plan

### Step 1: Preserve Current Guard As A Baseline

Keep the branch guard and tests:

- `registrationOwnerCanResolveInProgressConstant()`;
- `otherThreadsWaitForRecursiveRegistrationCompletion()`;
- `failedRecursiveRegistrationStaysFailedForReaders()`.

These tests define the compatibility behavior that the transaction must retain
or intentionally replace with a stronger invariant.

### Step 2: Introduce A Private Registration Context

Add an internal context type without changing behavior yet. Thread it through
`register(...)` and recursive `registerConstants(...)` calls where possible.

The first version can delegate to existing public publication, but it should
make the recursion owner explicit in code.

### Step 3: Move Cycle Resolution Out Of Public Maps

Use the context identity map for recursive same-thread lookup. Add source-shape
tests that fail if recursive registration needs to read its own in-progress
constant through `f_listConst`.

### Step 4: Commit Completed Constants Atomically

Change the public list/map mutation point so commit happens after recursive
registration and validation. Keep duplicate detection against already committed
constants at the commit boundary.

### Step 5: Remove The Completion Guard

After public readers can no longer observe in-progress constants, remove
`RegistrationCompletion`, `m_fCompletingRegistration`, and the reader wait path.
The tests should be rewritten to prove readers either see the old committed
state or the completed new state, never a blocked public in-progress value.

## Tests Required Before Claiming Done

- A reentrant/cyclic constant registration test that succeeds without public
  early publication.
- A cross-thread reader test proving readers cannot observe uncommitted
  constants. In the final design this should assert absence from public storage,
  not waiting on a completion marker.
- A failure test proving a transaction failure leaves no public list/map entry.
- Locator collision tests where a collision is discovered during private
  registration and no partial constants are committed.
- Adoption tests with warmed owner-local caches to prove private registration
  does not carry source-owner state.
- Same-JVM direct sequence and parallel stress with constant adoption,
  late-registration, and ownership diagnostics enabled.

## PR Split

This should not be bundled into the native-template or enum singleton PRs.
Recommended sequence:

1. Land the current completion guard as a defensive compatibility PR.
2. Land clone-free constant adoption family migrations.
3. Land the private registration context and transaction commit work.
4. Remove the compatibility guard after transactional publication is proven.

The final transaction PR should cite this plan, the bad-design reference, and the
focused ConstantPool tests.
