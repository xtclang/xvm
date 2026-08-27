# `ListMap` — load-bearing brittleness in type computation

**Read this before touching anything that returns, stores, or compares a `ListMap`.**

`org.xvm.util.ListMap` is not an ordinary `Map`. It is the **insertion-ordered** map used
pervasively for **type parameters** and related metadata throughout type computation and
`ConstantPool` resolution. Its **order and equality semantics are relied upon** across the type
system, which is precisely why the class (and its call sites) look the way they do. **You cannot
naively swap a `ListMap` for a `HashMap`, a `Map`, or "anything else" — doing so breaks type
resolution in subtle, wide-ranging ways.**

## Why order and identity matter

Type parameters are **ordered**: `Array<Int, String>` is a different type from
`Array<String, Int>`. A `HashMap` would lose that order; any comparison or serialization that
walks the parameter map in iteration order would then produce wrong results or non-deterministic
output. `ListMap` guarantees insertion order via a backing entry **list**, and the type system
depends on that guarantee when it iterates, compares, registers, and resolves parameterized types.

## What is SAFE vs NOT safe

Verified facts (as of 2026-08-27):

- **`ListMap extends AbstractMap` and defines NO custom `equals`/`hashCode`.** It therefore uses
  standard, order-INsensitive `AbstractMap.equals`, which accepts any `Map` argument. So value
  equality between a `ListMap` and any other `Map` with the same entries already holds — order is
  a *representation/iteration* guarantee, not an equality one.

**SAFE:**
- Returning `Collections.unmodifiableMap(theListMap)` from a *getter* as a read-only VIEW. The
  view **delegates iteration to the backing `ListMap`, so order is preserved**, and delegates
  `equals`/`hashCode` to it. The internal field stays a `ListMap`. This was applied to
  `ClassStructure.getTypeParams()` and validated by a full `xdk:installDist` (real type
  resolution), not just unit tests.

**NOT safe (will break type resolution):**
- Changing the internal FIELD type away from `ListMap` (e.g. to `HashMap`) — loses order.
- Replacing a `ListMap` with a non-order-preserving map anywhere it is iterated for type identity,
  parameter binding, or serialization.
- Assuming two type-parameter maps can be compared ignoring order where the surrounding code
  reconstructs types positionally.
- Casting a getter result back to `ListMap` after it has been narrowed to `Map` (the compiler
  catches this; do not "fix" it by re-widening the return type).

**Rule of thumb:** you may narrow a *getter's return* to an immutable order-preserving VIEW; you may
NOT change the stored representation or drop the ordering guarantee.

## The underlying problem

`ListMap` is **mutable and rewritten in place** (e.g. `ClassStructure.m_mapParams` is populated
during construction and reassigned/rewritten during registration, `MethodStructure.java:392`-style
element rewrites elsewhere). Exposing it safely therefore forces a choice today: hand out the live
mutable map (an aliasing leak), or wrap it read-only per call (a small per-call allocation — see
below). Neither is the right long-term shape.

## Proposed mechanism (lightweight — NOT a compiler rewrite)

We explicitly do **NOT** want to adopt full Roslyn-style copy-on-write reference semantics across
the compiler here — that is a large, separate project. What we *can* do, scoped to `ListMap`:

1. **A frozen / immutable `ListMap` variant.** Add `ListMap.freeze()` (or an `ImmutableListMap`
   that shares the backing entry list structurally). After a `ClassStructure`'s type parameters are
   finalized (post-registration), freeze the map. Getters then return the **frozen `ListMap`
   directly** — *unchanged type* (so every `ListMap`-typed call site and every order/equality
   assumption keeps working), inherently immutable (no per-call wrapper), and **zero-allocation**
   on read. This is strictly better than the `Collections.unmodifiableMap` view: it keeps the
   `ListMap` type AND removes the per-call allocation. It is the inherent-immutability fix that
   respects the type system's dependence on `ListMap`.

2. **(Optional) a lightweight copy-on-write `ListMap` for the build phase only.** While a structure
   is still being built/registered (the one phase that mutates the map), a COW `ListMap` lets a
   mutation produce a new version while any reader keeps a stable snapshot — a *scoped* version of
   the Roslyn idea applied ONLY to these metadata maps, not the whole compiler. This bounds the
   brittleness of in-place rewrite without a global redesign.

3. **Tie it to freeze-on-publish.** This is the same principle as
   `constant-pool-freeze-annex-design.md`: build mutably in a single-owner phase, then freeze an
   immutable value for shared reads. A frozen `ListMap` is the type-parameter-map instance of that
   pattern.

### Allocation note (why the frozen variant is preferable)

`Collections.unmodifiableMap(m)` allocates one small wrapper object **per getter call**;
`Collections.unmodifiableList(Arrays.asList(arr))` allocates two. These are short-lived
(young-gen, TLAB) so the GC cost is low, but on **hot** type-computation getters (e.g.
`getTypeParams()` inside relation/`isA` checks) the allocation RATE is non-trivial, and the XTC
runtime is allocation-sensitive. The frozen-`ListMap` mechanism (proposal 1) allocates the immutable
view **once at freeze time** and returns the same instance forever after → **zero per-call
allocation**. To measure current per-call cost precisely: wrap a tight loop of getter calls in
`com.sun.management.ThreadMXBean.getThreadAllocatedBytes()` deltas, or run the stress harness under
JFR (`jdk.ObjectAllocationSample`) / async-profiler `-e alloc` and read the `Collections$Unmodifiable*.<init>`
frames under these getters.

## Bottom line

- The `ListMap`-returning getters can be made read-only **as views** today, safely, provided the
  field stays a `ListMap` and order is preserved (validated on `getTypeParams`).
- The right long-term fix is a **frozen `ListMap`** (proposal 1): immutable, same type, zero-alloc —
  and it slots into the freeze-on-publish direction without a Roslyn-style compiler rewrite.
