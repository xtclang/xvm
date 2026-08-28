# Resolving the ObjectHandle Clone Island

**Date:** 2026-08-28. **Status:** analysis; no production change proposed yet.

After the 2026-08-28 sweep routed all 63 array clones through `Handy.copyOf`,
**exactly one `Object.clone()` remains in main sources**:

```java
// ObjectHandle.java:98, inside cloneAs(TypeComposition)
ObjectHandle handle = (ObjectHandle) super.clone();
handle.m_clazz = clazz;
return handle;
```

`ObjectHandle` is one of the two documented `Cloneable` islands. This note answers
the question the island's existence raises: how does it get resolved?

## The surface

| Measure | Count |
| --- | --- |
| Classes extending `ObjectHandle`/`GenericHandle`/`RefHandle`/`ArrayHandle` | 49 |
| `cloneAs` overrides | 6 (`ObjectHandle`, `GenericHandle`, one nested, `xRef`, `Proxy`, `xRTDelegate`) |
| `supportsMutableViews()` opt-ins | 4 (`GenericHandle`, `xArray`, `xTuple`, `xRTFunction`) |

## What `cloneAs` actually is

It is **not** a copy. It creates another *access view* — a mask/reveal — of the
**same** runtime object under a different `TypeComposition`. `GenericHandle`'s
override says so directly: *"cloneAs() creates another access view of the same
object. The field array therefore remains shared."*

So a handle is really two things fused into one object:

- **shared state**, one per logical Ecstasy object: `GenericHandle.m_aFields`,
  the owner `Container`, `ArrayHandle`'s `ArrayState`, the freeze cell;
- **per-view identity**: `m_clazz`.

`super.clone()` exists to duplicate the second while carrying the first along,
without each of 49 subclasses hand-writing the copy.

## Resolution A — copy constructors per subclass. NOT RECOMMENDED.

This is the E6 pattern that retired `Cloneable` from `Component`,
`MethodStructure`, `Token`, `Source`, and `Constant`. Applied here it would mean
49 copy constructors plus an abstract `cloneAs`.

**It is the wrong fix here, and would make things worse.** For a 49-class
hierarchy whose copy must be *exhaustive*, `super.clone()` is genuinely safer
than hand-written constructors: it copies every field by construction, whereas a
copy constructor that forgets a field compiles cleanly and fails silently at
runtime. E6's targets were different — there the shallow copy carried *hidden
outer pointers* that the copy constructor needed to explicitly re-bind, so
writing them out was the point. Here there is nothing to re-bind; the copy is
supposed to be total.

Adopting A would remove the *idiom* while leaving the *bug class* untouched, and
add a new failure mode. Do not do it.

## The real defect

The hazard was never `clone()`. It is that **a shallow copy of a view splits
per-view state away from the storage every view shares.** That is the entire
freeze-split bug family: `makeImmutable()` through one view left sibling views
still claiming mutability, and therefore still willing to write into frozen
shared storage.

The branch already patches this, and the patch shows the shape of the answer:

```java
public boolean isMutable() {
    FreezeCell cell = m_cellFreeze;
    return cell == null ? m_fMutable : cell.mutable;   // cell wins once views exist
}
```

`cloneAs` calls `prepareMutableViewShare()` *before* the shallow copy, installing
a `FreezeCell` that both views then point at. A handle that never has a view
never allocates the cell. Alongside it, `supportsMutableViews()` **default-denies**
`cloneAs` on any mutable handle whose class has not explicitly earned the opt-in,
so an unreviewed future handle class fails loudly rather than desyncing silently.

That is a guard, not a proof. Which points at the two real resolutions.

## Resolution B — split state from view. The endpoint.

Make the fusion explicit: a handle becomes an immutable pair of a shared
`HandleState` (fields array, freeze/mutability, owner) and a `TypeComposition`.
`cloneAs` then degenerates to:

```java
return new GenericHandle(state, clazz);   // no field copying, no clone
```

Per-view desync becomes **unrepresentable** rather than guarded, and
`supportsMutableViews()` default-deny can be deleted rather than maintained.

**Cost is high.** All 49 subclasses restructure, and the interpreter and JIT
read handle fields directly throughout `runtime/**` and `javajit/**`. This is a
project, not a slice, and it should not be attempted before the escape-hatch and
ownership work it depends on has settled.

## Resolution C — finish migrating per-view state into shared cells. RECOMMENDED.

C is B's stepping stone and is **already the established pattern**, applied once
to the field that mattered (`m_fMutable` → `FreezeCell`) and once in
`ArrayHandle` (`ArrayState`).

The claim to establish is narrow and checkable:

> After `cloneAs`, every field the shallow copy duplicated is either (a) `m_clazz`,
> which `cloneAs` immediately overwrites — that IS the view identity; (b) a
> reference to state deliberately shared by all views; or (c) an immutable value.

If that holds for all 49 subclasses, the shallow copy is *provably* harmless, the
island is justified by proof rather than convention, and the `supportsMutableViews()`
default-deny becomes redundant — retirable with evidence instead of maintained as
a guard.

**The work is an enumeration**, and it is incremental and independently
reviewable per class:

1. Enumerate every field across the 49 subclasses.
2. Classify each as view-identity / shared-reference / immutable-value / **per-view
   mutable state**.
3. Category four is the bug list. Each entry moves into a shared cell, following
   the `FreezeCell` and `ArrayState` precedent.
4. When category four is empty, replace the `supportsMutableViews()` opt-in with a
   test that pins the classification, and record the proof in the island's javadoc.

Two known entries to start from, both needing verification rather than assumption:

- `GenericHandle.m_aFieldOverrides` — `cloneAs` copies the *reference*, so both
  views share one override array. Whether `overrideField` through one view is
  meant to be visible through the other has not been established here and must be
  checked before classifying it.
- `m_owner` (`Container`) — shared by reference across views; almost certainly
  correct, but unverified.

## Recommendation

Do **not** apply copy constructors. Pursue C incrementally — it is cheap per step,
each step is independently reviewable, and it converts the island from
*convention-defended* to *proof-defended*. Treat B as the endpoint that C makes
affordable, and do not start B until C's enumeration shows what actually has to move.

Until then, `ObjectHandle` legitimately remains the one `Object.clone()` in the
tree, and `CloneCensusTest` should keep it whitelisted.
