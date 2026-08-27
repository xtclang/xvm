# Type-resolution testability — what we can stage, what is too encapsulated

Any change to the `ConstantPool` / type system (the frozen-base+annex work, ListMap changes,
adoption, interning) risks breaking **type resolution** in subtle, wide-ranging ways. The
`getTypeParams` change proved the point: the unit suites passed, but the change was only trusted once
`xdk:installDist` (real type resolution over the whole XDK) went green. So we need **many
type-resolution tests**, and we need to be honest about **what can be tested in stages vs. what the
runtime's over-encapsulation makes integration-only** — the endemic disease here.

## What CAN be tested in stages today

- **`ListMap` itself** (`javatools_utils`): order, `AbstractMap` equality, mutation — fully isolated.
- **`ConstantPool` observable counters**: `size()` (mirror/list count) is a real, assertable seam -
  used by `SharedPoolGrowthCharacterizationTest` to pin pool growth. `register`/dedup can be
  exercised on a pool with a handful of constants.
- **Adoption / owner isolation**: `ConstantAdoptionTest`, `MethodInfoTest` (TypeInfo construction,
  fresh-body owner rules), the reflection/ownership tests - these already stage specific mechanisms.
- **End-to-end resolution via compile+run**: `XtcEngine`/`EmbeddingTestSupport` compile real modules
  and run them; `SharedPoolGrowthCharacterizationTest`/`HostNestedContainerTest` measure runtime
  behavior. These are the real gate, but they are **integration** tests (need the built XDK).

## What is TOO ENCAPSULATED to test in stages (the endemic disease)

1. **You cannot build a realistic `TypeConstant`/`TypeInfo` without booting the whole system.**
   `ensureTypeInfo` for even a trivial type recursively pulls in `Object`, `Ref`, the whole native
   type universe, so every "type-resolution unit test" is actually a full integration test needing a
   `NativeContainer` + the compiled ecstasy/turtle/native modules. There is **no minimal type-system
   fixture** - the type universe is all-or-nothing.
2. **The pool's runtime state is private and entangled with the runtime.** The read mirror, the
   publication marker, the synthesis windows, `invalidateTypeInfos`, the (future) annex - almost none
   is assertable from a test. You can see `size()` and end-to-end behavior; you cannot directly assert
   "this constant is in the base vs the annex" or "this TypeInfo was invalidated." The mechanism is
   invisible to tests.
3. **The compiler↔runtime pool handoff needs both sides.** A compile builds a pool; the runtime
   consumes a frozen one. Testing the boundary (the exact thing freeze-on-publish changes) requires
   driving both, i.e. the whole pipeline.
4. **Private lazy caches** (`m_typeinfo`, `f_mapCompositions`, relation caches) are only observable
   through behavior, so a regression shows up as a wrong *result* far from the cause, not a failed
   assertion on the cache.

**Verdict:** type resolution today is only testable **through the full system** (NativeContainer +
XDK). That is slow and coarse, and it is why pool changes are scary. The fix is not one big harness;
it is two complementary tracks below.

## What to build (the gate for constant-pool work)

1. **A type-resolution equivalence corpus (integration, high value now).** A curated set of Ecstasy
   modules that exercise generics, parameterized-type identity (`Array<Int,String>` vs
   `Array<String,Int>`), relation/`isA` checks, `TypeInfo` member sets, and resolved parameter maps -
   compiled and resolved, with the results asserted. Run it before/after any pool change; a diff is a
   regression. This is the honest gate (it needs the XDK), and it is what would have caught a bad
   `getTypeParams`/`ListMap`/annex change without waiting to notice a broken `installDist`.
   **`xdk:installDist` itself is the crudest version of this and must be a required gate for any
   pool/type change** (unit-green is NOT sufficient - the `getTypeParams` change is the proof).
2. **Assertable test seams on the pool/type system (de-encapsulation, enabling).** Add test-visible
   accessors so the *mechanism* can be pinned in stages, not just inferred: published/frozen status,
   base-vs-annex membership and counts, `TypeInfo` completeness/invalidation counts, composition
   cache identity per container. `ConstantPool.size()` is the model - each new seam turns an
   invisible mechanism into a ratchet test. The frozen-base+annex work (Phase A-C,
   `constant-pool-freeze-annex-design.md`) should ship these seams *with* each phase, so the phase is
   testable in isolation instead of only end-to-end.
3. **Characterization ratchets (cheap, immediate).** Pin current observable behavior - pool size
   growth (already done), TypeInfo counts, resolved-parameter order/equality for a fixed corpus - so
   any change that moves them is caught. These are the down-payment while the corpus (1) and seams
   (2) are built.

## The honest limitation

A truly *isolated* type-resolution unit test (build types A, B; assert `A.isA(B)` with no XDK) is
**not achievable without a minimal type-universe fixture**, which the current design does not permit -
the type system cannot be partially instantiated. Until (or unless) that is built, type-resolution
testing stays integration-level, and the discipline is: **`installDist` + the equivalence corpus are
the mandatory gate for anything that touches the pool or type resolution; unit-green is not enough.**
The seams in track 2 shrink the gap incrementally; a minimal fixture is its own large project (in the
same spirit as, but separate from, the Roslyn-style rewrite noted in `listmap-issues.md`).
