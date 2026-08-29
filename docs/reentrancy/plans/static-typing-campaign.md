# Static Typing Campaign: Retiring Runtime-Delegated Dispatch

**Goal.** Get to proper APIs, where the type system carries the information — instead of
delegating everything to runtime checks, where every second line is a cast or an `instanceof`.

**Baseline** (measured at `8b0b4360e`, `javatools/src/main/java/org/xvm`):

| Metric | Count |
| --- | --- |
| Downcasts `((Type) expr)` | 1056 |
| `instanceof` | 1594 |
| `(XHandle)` handle casts | 1212 |
| `Map<Object, …>` declarations | 80 |
| `case "…"` string-concat dispatch labels | 79 |
| `Constant.Format` constants vs `ValueConstant` classes | 107 vs 89 |

Design record and per-family analysis: [object-typing-static-safety-study.md](../object-typing-static-safety-study.md).
Board entry: **SF-Typing** in [must-audit-backlog.md](../must-audit-backlog.md).

## Sequencing rule

**`asm`-first, not cast-count-first.** `runtime/template/**` holds 46% of all casts and 83% of
handle casts, but `jit-implications.md:17-40` establishes the JIT uses none of
`Frame`/`ObjectHandle`/`ClassTemplate`. Ranking by cast population inverts the correct priority:
the biggest number sits on the least strategic path.

**Sealing is not the mechanism.** `Component` is already fully sealed and still carries 394
downcasts, because the pressure is on lookup *return types*, not on dispatch. And `Format` is a
*finer* partition than the class tree (107 vs 89), so format switches stay unwritable by hand
after sealing. E2 does not subsume this work.

## Tasks

### T1 — Covariant `IdentityConstant.getComponent()` — **DONE**

**The study's "222 cast sites" was an over-projection, and the reason matters.** That grep
matched `getComponent()` on *any* receiver, but there are at least four unrelated methods with
that name: `IdentityConstant.getComponent()`, `Component.getComponent()`,
`ChildInfo.getComponent()`, and the runtime handles' (`ComponentTemplateHandle`). Narrowing the
identity hierarchy can only reach its own.

Actual result: **8 overrides, 99 casts deleted, 8 restored, net 91 removed.** The 131 survivors
are the other methods — 40+ on runtime handles (`hComponent`, `hMethod`, `hTemplate`), 16 bare
calls on AST statements, and ~30 with a receiver statically typed `IdentityConstant`, where
subclass narrowing genuinely cannot apply. `Component.getComponent()` and
`ChildInfo.getComponent()` are separate narrowing opportunities, now folded into T3. The creation
side is already typed (`createClass → ClassStructure`, `createMethod → MethodStructure`, …), so
the asymmetry is exactly *you know what you made, and forget it the moment you look it up*.

Pure covariant narrowing: source- and binary-compatible, javac generates the bridge, free at
runtime. Land one constant type per commit, deleting casts as they become redundant.

- [x] `ClassConstant → ClassStructure`, `MethodConstant → MethodStructure`,
      `PropertyConstant → PropertyStructure`, `ModuleConstant`, `PackageConstant`,
      `TypedefConstant`, `MultiMethodConstant`, `DecoratedClassConstant`
- [x] Delete the now-redundant casts (91 net)
- [ ] Ratchet test pinning the narrowed return types

**Lesson for T3 and after:** a blanket regex over a method *name* is not safe when the name is
overloaded across unrelated hierarchies. The bulk deletion silently stripped casts from two
other `getComponent()` methods and my auto-repair then guessed the wrong types, stacking
duplicate casts. The compiler caught every one — but the cheap version of that lesson is to
scope the deletion by receiver type, or to delete per-package with a compile between.

### T2 — `ValueConstant<V>` — **DONE**

Only **6** call sites have a statically-`ValueConstant` receiver (all in `CaseManager.covers()`);
the other 893 `.getValue()` calls already have concrete receivers and already get the narrowed
type. Verified bytecode-identical by `javap` — the `Object getValue()` bridge already exists.

Two honest obstacles, both to be resolved rather than annotated around:

- [x] `MatchAnyConstant` left the family. Verified it has **zero** `getValue()` callers — every
      use in the tree is `instanceof MatchAnyConstant` — and that `CaseManager.covers()`
      special-cases it at lines 908 and 912, *before* any `getValue()`. Typing it
      `ValueConstant<String>` would have been a lie that type-checks.
- [x] `EnumValueConstant.getValue()` now throws instead of returning null. A negative ordinal
      means the enum value is not among its own parent's `ENUMVALUE` children — a structural
      inconsistency. Returning null pushed the failure to whoever dereferenced it; there are no
      callers, so the impossible state says so instead.
- [x] Genericized the class and all 20 direct subclasses
- [x] `CaseManager` — see below

**The clean compile was hiding something, and it is the lesson of this task.** After
genericizing, the tree compiled with **zero errors** — because `instanceof ValueConstant valLo`
binds `valLo` as a RAW `ValueConstant`, so `valLo.getValue()` returns `Object` and compiles
silently. Genericizing a type does not by itself fix its consumers; it can just relocate the
erasure into them. Four such bindings in `CaseManager` are now `ValueConstant<?>`. Worth
watching for in every later task: **a green build after a generics change is not evidence.**

### T3 — Covariant `Component` child lookup — **PARTIALLY DONE**

**Done: the AST-statement half.** `ComponentStatement.getComponent()` returns `Component`, and
each declaration statement knows exactly what it declares. Three overrides —
`MethodDeclarationStatement → MethodStructure`, `TypeCompositionStatement → ClassStructure`,
`PropertyDeclarationStatement → PropertyStructure` — removed **29 casts**.

**Measured, not assumed:** the 130 surviving `getComponent()` casts after T1 split evenly into
three groups of ~43 — AST statements (done), runtime `ComponentTemplateHandle`, and
`asm`-internal receivers typed `IdentityConstant`. Only the first was narrowable by the T1
pattern.

**The other two are NOT narrowable the same way, and that is worth recording:**

- `ComponentTemplateHandle` is ONE class holding any `Component` — the handle type is not
  per-component-kind, so there is no subclass to narrow. It would need
  `ComponentTemplateHandle<C extends Component>` plus a generic `makeComponentHandle`, which is
  a different and larger change.
- The `asm` receivers are statically `IdentityConstant` (`idClz`, `id`, `idLeft`), where T1's
  subclass narrowing cannot apply by construction. Narrowing accessors like
  `SingletonConstant.getClassConstant()` (which returns `IdentityConstant` and forces the cast at
  `EnumValueConstant:75`) would reach some of them — a separate, smaller task.

**I repeated the T1 mistake here**, having written the warning down after T1: a blanket regex
over the method name over-deleted, this time inside `compiler/` only. Recovery was worse than
last time — restoring an over-deleted line from `git show HEAD:` corrupted a multi-line statement
into a syntax error, because the line numbers had already shifted. **The rule that actually works
is: delete only where the receiver's static type is one of the narrowed classes, or delete one
file at a time with a compile between.** A regex over a method name is not scoped by anything.

- [x] `ComponentTemplateHandle<C extends Component>` — **done, and it removed ZERO casts.**
      Recording the negative result, because the projection was wrong and the reason generalises.

      The handle now carries what it holds and the seven per-kind factories declare what they
      produce (`xRTClassTemplate.makeHandle → ComponentTemplateHandle<ClassStructure>`, …).
      `makeComponentHandle` returns `<?>`, because it dispatches on a runtime `getFormat()` and
      genuinely cannot promise a type.

      But **every one of the 26 consumers obtains its handle by downcasting an `ObjectHandle`**
      from the runtime's argument array — measured: 26 downcast sites, **0** that call a typed
      factory and use the result. So there is no typed path for the parameter to flow along.
      Making the downcast parameterised — `(ComponentTemplateHandle<PropertyStructure>) hTarget`
      — would be an UNCHECKED cast: it trades a checked `ClassCastException` that fires at the
      right moment for one that succeeds and defers the failure. **Strictly worse.** The
      consumers therefore use `ComponentTemplateHandle<?>`, which removes the raw type and keeps
      the checked cast exactly where it was.

      **The generalisable lesson:** a type parameter only pays where there is a typed *path* from
      producer to consumer. Here the path runs through `ObjectHandle[]`, which erases everything
      at the boundary — so this cannot pay until `ClassTemplate<H extends ObjectHandle>` lands,
      and that is deliberately out of scope (561 casts, on the path the JIT does not use). The
      generic is kept because it makes the producers honest and costs nothing, not because it
      removed anything.
- [ ] Narrow the `IdentityConstant` accessors that force casts (`getClassConstant`, …)

### T3 (original scope) — Covariant `Component` child lookup

`getChild`, `getChildByPath`, `getChildByNameMap`, `children()` all return `Component`. The
existing `Class<T>` overloads are the transitional shape; typing the lookup by what the caller
holds is the design shape. Follows T1, which establishes the pattern.

### T4 — **THE PREMISE WAS WRONG: it is not one union, it is four (plus one that is not a nid at all)**

Investigated properly rather than taken from the study. The framing "the `Object nid` union, ~76
maps" is incorrect and led me — and the study — to plan a single carrier for a single family. What
is actually there:

| Map | Count | Keys actually held | All our own types? |
| --- | --- | --- | --- |
| `Map<Object, ParamInfo>` | 27 | `String` \| `NestedIdentity` | **No** — `String` |
| `Map<Object, MethodInfo>` | 14 | `SignatureConstant` \| `Integer` (lambda) \| `NestedIdentity` | **No** — `Integer` |
| `Map<Object, PropertyInfo>` | 13 | `String` \| `NestedIdentity` | **No** — `String` |
| `Map<Object, FieldInfo>` | 8 | `String` (`sField`) \| `NestedIdentity` (`enid`) | **No** — `String` |
| `Map<Object, Constant>` | 6 | **NOT a nid** — the pool's `getLocator()` union | separate problem |

**The decisive question turned out to be: are all the union's variants types we own?** Where they
are, a marker interface types the union for free — `SignatureConstant` and `NestedIdentity` are
both our classes, so `implements MethodNid` on each would carry the type with **zero** change to
`equals`, no wrapping, and no call-site conversion. That would have been the cheap win.

**Every one of these four families includes a JDK type** — `String` for three of them,
`Integer` for the lambda case in the fourth. You cannot retrofit an interface onto `String`. So
each family needs *wrapping* (`record ByName(String)`), and wrapping is what creates the whole
risk: today a property's nid **is** the string `"alpha"`, so any holder of that string indexes the
map directly, and `TypeInfoReal:642` does exactly that with a literal:

```java
mapTypeParams.put("Referent", new ParamInfo("Referent", typeReferent, pool.typeObject()));
```

Under a carrier that must become `Nid.of("Referent")`, and **every** such site must be converted or
the entry silently becomes unreachable.

**Scoped cost, measured:** 12 files declare genuinely nid-keyed maps. Of the accesses, 62 already
pass a nid expression and are fine; **45 need individual review** (22 bare variables like `sField`,
`sig`, `idNested`; 23 other expressions including string literals). That is the real size — much
smaller than the ~76-map figure this document started with, but every one of the 45 is a
silent-wrong-answer site if missed.

**Verdict: possible, and worth doing, but it is four separate refactors and none is free.** The
`Map<Object, Constant>` locator family should be split off first since it is not a nid at all and
was inflating every count. `NestedIdentityContractTest` is in place to hold the equality contract
through whichever one goes first.

### T4 (superseded framing) — Type the `Object nid` nested-identity union

**Step one is done and is the durable part.** `NestedIdentityContractTest` characterises the union
*before* anything touches it: the five variants exist, they are mutually unequal, equal identities
produce colliding keys, and distinct members do not collide. That last pair is what the ~76
`Map<Object, …>` caches actually depend on and what none of them states. Any sealed carrier must
keep that test green; if it cannot, the carrier design is wrong.

**Step two is measured and deliberately not started.** A sealed carrier of records would preserve
mutual inequality *by construction* — distinct record types are never equal — so the contract test
would pass. The risk is not there. It is here:

| | Count |
| --- | --- |
| Variables declared `Map<Object, …>` | 38 |
| Key accesses via a nid-shaped expression | 55 |
| **Key accesses built some OTHER way** | **60** |

Those 60 are the problem. Today a property's nid *is* the `String` `"alpha"`, so any code holding
that string can index the map directly. Under a carrier it becomes `new ByName("alpha")`, and
every one of those 60 sites must be converted **or it silently stops matching**. Not a crash — a
cache that never hits, or worse, one that hits the wrong entry.

That is the `unlinkSibling` failure mode (wrong answers, not exceptions) at sixty times the
surface, in the type-resolution path. It needs its own session with these 60 sites reviewed
individually, and the contract test is now in place to support that. Starting it at the tail of a
long session is precisely how a subtle miss ships green.

### T4 (original scope) — Type the `Object nid` nested-identity union

Variants are `String | Integer | SignatureConstant | NestedIdentity | null`, used as a key in
**80** `Map<Object, …>` declarations. `NestedIdentity` is a non-static inner class capturing its
owner. Needs a sealed carrier; wide but mechanical once the carrier exists.

### T5 — Close the `Format`/class-hierarchy drift

107 formats, 89 classes. Make the mapping total via enum-carried factories, so a new format
cannot be added without a home. Prerequisite for T6.

### T6 — Retire the `Format`-string-concatenation dispatch

The biggest prize and the biggest job: 5 methods, **1,279** `case "…"` labels switching on
`getFormat().name() + op.TEXT + that.getFormat().name()`. `IntConstant.apply` alone is **609
lines**, and 277 of its 550 labels share one 1-line body. Blocked on T5.

### T7 — Retire raw types and make them non-recompilable

Small, self-contained, and it closes the class permanently rather than just tidying it. The
population is already tiny — which is the argument for finishing it now, while it is cheap:

| Shape | Count |
| --- | --- |
| Raw `Class` locals/fields | 10 |
| Raw `Class` parameters | 1 |
| Raw `List`/`Map`/`Set`/`Collection`/`Iterator` declarations | 5 |
| `@SuppressWarnings("unchecked")` | 14 |
| `@SuppressWarnings("rawtypes")` | 0 |

The build already runs `-Xlint:this-escape` and `-Xlint:fallthrough` as first-class lints;
`rawtypes` is simply not among them.

- [ ] Parameterise the raw `Class` uses (`AstNode.fieldsForNames(Class clz, …)` is one)
- [ ] Parameterise the raw collection declarations
- [ ] Review each `@SuppressWarnings("unchecked")`: delete the ones generics can now express,
      and leave a recorded reason at the ones that are genuinely erasure-bound
- [ ] Enable `-Xlint:rawtypes` in `org.xtclang.build.java.gradle.kts` so a new raw type does
      not compile

Where generics *fold away* complexity rather than merely decorating it — a `Class<T>` that
removes a downcast at the call site, a typed carrier that removes an `instanceof` chain — take
it. Where a type parameter would only push the cast one level out, do not: that is ceremony,
not safety.

## STANDING RULE: a narrowing change is not verified until `xdk:installDist` passes

Learned the expensive way. **T1 narrowed `ClassConstant.getComponent()` to `ClassStructure` and
that was wrong** — a `ClassConstant` can name a `TypedefStructure`. The failure:

```
java.lang.ClassCastException: TypedefStructure cannot be cast to ClassStructure
    at ClassConstant.getComponent(ClassConstant.java:316)
    at ConstantPool.getImplicitlyImportedComponent(ConstantPool.java:1619)
    at NameResolver.resolve(NameResolver.java:249)
```

**760 unit tests did not catch it.** Only compiling `lib_ecstasy` did, because the failing path
runs when the compiler resolves an implicitly imported typedef. The regression sat in the tree
across four subsequent commits, all of them "green".

**The reasoning error, stated so it is not repeated:** a covariant return applies to EVERY caller,
not only the ones already casting. 113 sites cast a `ClassConstant`'s component to
`ClassStructure` and every one of them was in a path where it genuinely is one — but
`getImplicitlyImportedComponent` was a caller correctly using the supertype, and narrowing forced
the cast onto it too. **"Nearly every caller casts to X" is not grounds to narrow. The subclass
must ALWAYS produce X.** The cast-frequency survey cannot answer that question; only reading what
the method can return can.

`ClassConstant` and `DecoratedClassConstant` are reverted, and
`IdentityComponentNarrowingTest` pins both halves: the six sound narrowings, and the two that must
stay `Component` — so a future attempt fails immediately with the reason instead of surfacing as a
`ClassCastException` in the middle of building the core library.

## T4 — the proposed design, and how it is guaranteed

### The type

Wrap only what we do not own. `SignatureConstant` and `NestedIdentity` are our classes and can
carry the marker directly — no wrapper, no change to their `equals`, no conversion at any site that
already holds one. Only `String` and `Integer` need a wrapper, because you cannot retrofit an
interface onto a JDK type.

```java
public sealed interface Nid
        permits Nid.ByName, Nid.ByLambda, SignatureConstant, IdentityConstant.NestedIdentity {

    /** A member identified by name — properties, fields, type parameters. */
    record ByName(String name) implements Nid {}

    /** A lambda identified by its index within its parent. */
    record ByLambda(int index) implements Nid {}

    static Nid of(String name)            { return new ByName(name); }
    static Nid of(int index)              { return new ByLambda(index); }
    static Nid of(SignatureConstant sig)  { return sig; }
}
```

Records give the union property for free: `ByName("x").equals(new ByLambda(0))` is `false` by
construction, and no two record types are ever equal. That is the invariant
`NestedIdentityContractTest` exists to protect, and it is now structural rather than incidental.

### What javac guarantees, and the one thing it does NOT

Verified by compiling a minimal model, not assumed:

| Operation | Behaviour once the map is `Map<Nid, V>` |
| --- | --- |
| `map.put("Referent", v)` | **compile error** — `String cannot be converted to Nid` |
| `map.get("Referent")` | **compiles, returns null silently** |
| `ByName("x").equals(other record)` | `false`, always |

**`Map.get`, `containsKey` and `remove` take `Object`, not `K`.** So typing the map catches every
*write* with a wrong key and **no reads at all**. That is precisely the silent-wrong-answer hole,
and it is the whole reason this refactor is dangerous. Any plan that says "the compiler will find
them" is half right and the missing half is the dangerous half.

### How it is actually guaranteed

Five layers, in the order they catch things, and only the last two close the read hole:

1. **`NestedIdentityContractTest`** — already in the tree. Pins mutual inequality, key collision,
   and non-collision. Written before the refactor precisely so it cannot be rationalised afterwards.
2. **javac on every write** — all `put`/`putIfAbsent`/`computeIfAbsent` with a bare `String`,
   `Integer` or literal become compile errors the moment the map is typed. This converts the 22
   bare-variable sites and the literal sites from "must be found by review" into "cannot be missed".
3. **Bounded read review** — `get`/`containsKey`/`remove` are the hole. The population is knowable
   and small: **12 files**, and within them only the accesses on nid-keyed maps. That is a finite
   list to walk, not a codebase-wide hunt, and it must be walked file by file.
4. **`xdk:installDist`** — the empirical backstop, and the one that matters. These maps *are* type
   resolution; a read that silently misses does not stay silent when the compiler resolves
   `lib_ecstasy`. This is what caught the `ClassConstant` regression that 760 unit tests missed,
   and it is now a standing rule for this campaign.
5. **The full suite** — 760 tests, last.

### Optional hardening, if step 3 feels too thin

A `NidMap<V>` wrapper exposing `get(Nid)` rather than `get(Object)` would close the read hole at
compile time too, making the guarantee total rather than "total on writes, reviewed on reads". It
costs a small delegating class and one more type in the API. Worth it only if the step-3 walk turns
up more than a handful of read sites — decide after walking, not before.

### Order

`Map<Object, Constant>` (the pool's `getLocator()` union) is **not** a nid and should be split out
of these counts first — it has been inflating every estimate in this document. Then one family at a
time, each landing green through all five layers before the next starts. `FieldInfo` (8 maps) is the
smallest and is the right one to prove the pattern on.

## Classification rule: actual defect vs. hazard the ugliness hides

Every finding from this campaign gets one of two labels, and the distinction is not
cosmetic — it decides whether it goes on the **master bug list** or stays here.

**ACTUAL DEFECT** — a caller today produces a wrong result, a crash, or lost data. Goes on
the master bug list with a red proof. Found so far:

| Row | Finding | How the ugliness hid it |
| --- | --- | --- |
| 31 | `Op.toString()` throws on 16 opcodes | two parallel 200-case switches over one domain, kept in sync by hand |
| 32 | `TimeZone` literal compiles, then dies | the same fact spelled out in seven places |
| 33 | `AstNode` dead guard + null holes | `isInstance` where `isAssignableFrom` was meant; a guard that cannot fire reads as one that passes |
| 34 | `mergeChildren` derefs `info2` after checking `info1` | two adjacent lines differing by one character |

**LATENT HAZARD** — the shape permits a defect that no current caller commits. Fixed here,
NOT filed against master, because filing it would overclaim. Found so far:

- **`Component.unlinkSibling(Map kids, Object id, …)`** — the raw map plus `Object` key meant
  nothing checked that the key matched the map. A mismatch would `put` a `String` key into a
  `MethodConstant`-keyed map: no exception, no stack trace, just a child that is silently
  invisible to every later lookup — strictly worse than a `ClassCastException`, which at
  least names the moment. **Audited all three callers: every one passes a matching pair**, so
  there is no defect today. Fixed by tying key to map:
  `<K, C extends Component> void unlinkSibling(Map<K, C> kids, K id, Component child, C sibling)`.
  Verified by compiling a deliberate mismatch — javac now rejects it with *"inference variable
  K has incompatible bounds"*. One localised `@SuppressWarnings("unchecked")` remains on the
  value side, where a `Component` sibling chain meets a caller's narrower map; that is the one
  thing the pairing cannot express, and it is now one documented line instead of an erased map.

The lesson the four defects share: **none of them was a hard problem**. Each was a one-word
or one-line slip that the type system was in a position to catch and had been prevented from
catching — by a raw type, an `Object` parameter, a hand-maintained parallel switch, or a
guard written against the wrong overload. That is the argument for this campaign, and it is
stronger than any cast count.

## The `ObjectHandle` unlock: it is not a generics problem

The 1212 `(XHandle) hTarget` casts are the largest single population in the tree, and the study
deprioritised them as "the path the JIT does not use". That was the right call on priority but it
left the more useful question unasked: **what would actually fix them?**

**`ClassTemplate<H extends ObjectHandle>` will not.** The reason is visible in one line —
`CallChain.java:185`:

```java
hTarget.getTemplate().invokeNative1(frame, method, hTarget, hArg, iReturn)
```

The handle produces the template and then **passes itself back as a parameter**. `getTemplate()`
returns an erased `ClassTemplate`, so nothing can prove the returned template's `H` is
`hTarget`'s type. It is the same "no typed path from producer to consumer" failure that made
`ComponentTemplateHandle<C>` remove zero casts — and it would fail the same way, after a much
larger edit. Worth knowing *before* attempting it.

**The actual unlock is dispatch LOCATION, not type parameters.** That line is `this`-dispatch
written the long way: the receiver is being handed to a separate object as an argument. If native
dispatch lived on the handle, `this` would be the typed receiver:

```java
// today, in xString
char[] ach = ((StringHandle) hTarget).getValue();

// with dispatch on the handle - no cast, no generic, nothing to erase
final class StringHandle extends ObjectHandle {
    @Override int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hArg, int iReturn) {
        char[] ach = m_achValue;   // `this` IS a StringHandle
    }
}
```

Casts disappear **by construction** rather than by generics. Nothing needs a type parameter,
because nothing is being passed.

**Why it is still not cheap.** Templates carry per-container state — `f_container`, the owner-local
`Lazy.Bound` caches this branch introduced — that handles do not. Moving dispatch onto handles
means every moved method reaches back through `getTemplate()` for that state, so the refactor is
mechanical but enormous, and it touches the interpreter's hottest path. It is a project, not a
task, and it should not start until the `asm` half of this campaign is finished.

**What the codebase already does, and the asymmetry that suggests the next step.** There is
already a `Class<T>` reflective-cast accessor for the template side, used ~100 times:

```java
public <T extends ClassTemplate> T getTemplate(Class<T> clzTemplate) {
    return clzTemplate.cast(getComposition().getTemplate());
}
```

There is no symmetric helper for the handle side. Adding one would make the casts greppable and
give a better failure message, but it must be judged honestly: `clz.cast(x)` and `(X) x` throw the
same `ClassCastException` at the same moment, so it buys **inspectability, not safety**. That is
worth doing only as instrumentation to size the dispatch-relocation project — for example, to
count which handle types actually reach which templates — not as a fix.

## T4 — assessment before starting

The `Object nid` union is the study's rank 2 and it is **wider and more delicate than its
description suggests**. The five variants are confirmed:

| Variant | Produced by |
| --- | --- |
| `null` | a non-nested identity |
| `String` | `PropertyConstant` — the property name |
| `Integer` | `MethodConstant` when `isLambda()` — `Integer.valueOf(m_iLambda)` |
| `SignatureConstant` | `MethodConstant` otherwise |
| `NestedIdentity` | any identity, when recursively nested |

They are used as **map keys** in ~76 declarations (`Map<Object, ParamInfo>` ×27,
`Map<Object, MethodInfo>` ×14, `Map<Object, PropertyInfo>` ×13, `Map<Object, FieldInfo>` ×8, …).

**The delicate part, which the cast count does not show:** the union works today *because* the
variants are mutually unequal by Java's own `equals` — a `String` never equals a
`SignatureConstant`, an `Integer` never equals a `NestedIdentity`. Any sealed carrier must
preserve that exactly, or lookups silently start hitting or missing. This is a change where a
subtle `equals`/`hashCode` slip produces wrong answers rather than a crash — the same failure mode
as `unlinkSibling`, at 76× the surface. It needs its own dedicated pass with an equality-contract
test written **first**, not a pass tacked onto the end of a session.

## What is actually left, measured — and it is not what the raw counts say

Re-measured at `c75e855b9`, after T1/T2/T3/T7. The headline numbers in this document's baseline
are misleading, and correcting them changes the plan.

### `instanceof` is mostly NOT a problem

| Shape | Count | Target? |
| --- | --- | --- |
| Pattern form, binds a variable (`x instanceof Foo f`) | **1027** | **No** — this IS the modern idiom |
| Plain test, no binding | 558 | mostly fine (genuine predicates) |
| In an `else if` chain | **98** | **Yes** — dispatch pretending to be control flow |

So "1594 instanceof" is not a backlog of 1594 problems. It is **98**, concentrated in
`NameExpression` (8), `CaseManager` (7), `xRTType` (6), `ClassStructure` (5). Those want a sealed
hierarchy plus an exhaustive switch (E2), not casting work.

### Casts split into three shapes, each needing a DIFFERENT mechanism

**A. Covariant narrowing — the subclass knows its own type.** Proven: 120 casts removed (T1: 91,
T3: 29). Applies where a subclass can restate a supertype's return.

**B. Typed accessor pairs — a PREDICATE establishes the type.** This is the seam the study missed,
and it filed these under "cannot be typed" (§4.3, "metadata-predicated casts"). That verdict is
wrong: they can be typed, just not by narrowing a return. The pattern is

```java
// today - 32 sites in TerminalTypeConstant alone
if (!isSingleDefiningConstant()) {
    TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
```

The predicate `isSingleDefiningConstant()` already establishes what the constant is; the cast is
the caller re-asserting what the guard just proved. The fix is an accessor that does both:

```java
/** @return the TypedefConstant this type resolves to; only valid when !isSingleDefiningConstant() */
protected TypedefConstant ensureResolvedTypedef() {
    assert !isSingleDefiningConstant();
    return (TypedefConstant) ensureResolvedConstant();
}
```

One internal cast replacing 32 — the same trade as T1, reached by a different route. **This is the
best available ratio right now** and it is bounded to one file.

**C. Dispatch relocation — the receiver is passed as a parameter.** The `ObjectHandle` family. Not
fixable by generics (see the unlock section above); needs dispatch moved onto the handle. The
endgame, not the next step.

### Recommended order

1. **B on `TerminalTypeConstant`** — 32 casts, one file, bounded, mechanical. Do this next.
2. **Re-run the narrowing survey with a guard filter.** The survey that produced this section
   ranked candidates by "what fraction of casts target one type", and that metric is WRONG on its
   own: `ensureResolvedConstant` scored 100% purity yet must not be narrowed, because the casts
   are all inside one guarded branch while the method genuinely returns several types. The metric
   needs a second question — *is the cast guarded by a predicate?* — which separates shape A from
   shape B.
3. **The 98 `else if` chains** — sealed + exhaustive switch. Depends on E2's sealing, already
   partly landed.
4. **C** — after the `asm` half is finished.

## T8 — Retire the runtime-only handle model, incrementally

**The measurement that makes this tractable:** 482 `(XHandle) hTarget` casts across 83 template
files — but only **four** dispatch entry points (`invokeNative1`, `invokeNativeN`,
`invokeNativeNN`, `invokeNativeGet`), and every one funnels through the same shape at
`CallChain.java:185-246`:

```java
hTarget.getTemplate().invokeNative1(frame, method, hTarget, hArg, iReturn)
```

The handle produces the template and passes **itself** back as a parameter. Every one of the 482
casts exists to undo that.

### Why generics cannot fix it (settled, do not retry)

`ClassTemplate<H extends ObjectHandle>` fails because `getTemplate()` erases: nothing can prove the
returned template's `H` matches `hTarget`'s type. This is not speculation — it is the identical
"no typed path from producer to consumer" failure that made `ComponentTemplateHandle<C>` remove
**zero** casts (see the negative result above), and it would fail the same way after a far larger
edit.

### The incremental mechanism

Give `ObjectHandle` a **default that delegates**, so nothing changes until a handle opts in:

```java
// ObjectHandle — default behaviour is exactly today's behaviour
public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hArg, int iReturn) {
    return getTemplate().invokeNative1(frame, method, this, hArg, iReturn);
}
```

`CallChain` then calls `hTarget.invokeNative1(frame, method, hArg, iReturn)`. Any single handle
class can now override it and handle its own natives with `this` **already the right type** — no
cast, no generic, nothing to erase — while the other 82 keep delegating, unchanged and untouched.

```java
final class StringHandle extends ObjectHandle {
    @Override int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hArg, int iReturn) {
        char[] ach = m_achValue;   // `this` IS a StringHandle
    }
}
```

**This is the property that matters: it is one handle class per commit, each independently
revertible.** The earlier assessment called this "a project, not a task" — that was wrong about
the shape. It is a project only in total; each step is small.

### Corrected measurement — and it narrows the plan sharply

My first attribution was wrong: the parser never reset at method end, so casts leaked onto the
previously-matched method (it reported `setPropertyCapacity` at 51; the real figure is 8). Re-run
with brace tracking:

| Method receiving `hTarget` | Casts |
| --- | --- |
| `invokeNativeGet` | 44 |
| `invokeNativeN` | 42 |
| `invokeNative1` | 40 |
| `invokeNativeNN` | 25 |
| `buildHashCode` | 11 |
| `buildStringValue` | 9 |
| `invokeAdd` | 8 |
| `setPropertyCapacity` | 8 |
| `getFieldValue` | 7 |
| `invokePreInc` | 7 |

Total correctly attributed: **370**. And the number that decides the scope: **107 distinct methods
take `ObjectHandle hTarget`**.

**That kills the "move dispatch onto handles" plan in its general form.** Giving `ObjectHandle` a
delegating default for all 107 would drag the entire `ClassTemplate` API onto the handle class —
a cure worse than the disease, and it would make `ObjectHandle` the god object the runtime already
struggles with.

### The line that is actually principled

**Dispatch belongs on the receiver; operations belong on the template.**

The four `invokeNative*` methods are *dispatch* — "invoke this method on this object" — and the
receiver is exactly what is being cast. `buildHashCode`, `invokeAdd`, `getFieldValue`,
`setPropertyCapacity` are *operations the template performs on a handle*, and they belong on the
template; the handle should not know how to hash itself in Ecstasy terms.

Applying that line gives a genuinely good ratio:

> **Four delegating defaults on `ObjectHandle` recover 151 casts — 41% of the total.**

The remaining 219 stay, correctly, on the template side. They are not a backlog; they are the
operations half of a distinction that was never drawn.

### Ranked by yield

| Handle | Casts recovered |
| --- | --- |
| `IntNHandle` | 46 |
| `ViewHandle` | 30 |
| `ServiceHandle` | 28 |
| `LongArrayHandle` | 28 |
| `ArrayHandle` | 28 |
| `LongLongHandle` | 27 |
| `GenericHandle` | 24 |
| `FloatHandle` | 19 |

**Eight classes recover 230 of 482 — roughly half.** The tail is a long list of 1-3 cast classes
that can be left alone indefinitely without loss.

### The two real costs, stated up front

1. **Templates hold per-container state** (`f_container`, the owner-local `Lazy.Bound` caches this
   branch introduced) that handles do not. A moved method reaches it through `getTemplate()`, so
   the move is mechanical but each method grows one indirection.
2. **This is the interpreter's hot path.** A virtual call on `ObjectHandle` replaces a virtual call
   on `ClassTemplate` — same count, but the receiver changes and the call sites are megamorphic
   either way. Worth measuring on the first conversion rather than assuming.

**Verification is unchanged and non-negotiable: `xdk:installDist` per step**, since these paths are
what the interpreter runs.

## Explicitly NOT doing

- **`ClassTemplate<H extends ObjectHandle>`** — would collapse 561 `(X) hTarget` casts, the
  largest single number in the codebase, and is still deprioritised: it is on the path the JIT
  does not use. Revisit only if the interpreter becomes strategic again.
- **Sealing `Op`** — 824 opcode sites vs 14 `instanceof`, and a 210-entry `permits` clause. An
  unknown opcode already fails loudly in the factory, so sealing removes a hazard that does not
  exist. Decided, not deferred.
- **The `ArgId` record union** — rejected on interpreter hot-path cost. The free `enum` split of
  the overlapping integer protocols (`A_LABEL` and `CONSTANT_OFFSET` are both `-16`;
  `R_NEXT..R_RESET` reuse `A_STACK..A_STRUCT`) is the affordable part.
