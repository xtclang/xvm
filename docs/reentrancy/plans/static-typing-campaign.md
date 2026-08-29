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

### T3 — Covariant `Component` child lookup

`getChild`, `getChildByPath`, `getChildByNameMap`, `children()` all return `Component`. The
existing `Class<T>` overloads are the transitional shape; typing the lookup by what the caller
holds is the design shape. Follows T1, which establishes the pattern.

### T4 — Type the `Object nid` nested-identity union

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
