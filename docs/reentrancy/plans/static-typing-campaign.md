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

### T1 — Covariant `IdentityConstant.getComponent()` — **IN PROGRESS**

The best ratio in the study: **8 declarative overrides address 222 cast sites.** The creation
side is already typed (`createClass → ClassStructure`, `createMethod → MethodStructure`, …), so
the asymmetry is exactly *you know what you made, and forget it the moment you look it up*.

Pure covariant narrowing: source- and binary-compatible, javac generates the bridge, free at
runtime. Land one constant type per commit, deleting casts as they become redundant.

- [ ] `ClassConstant.getComponent() → ClassStructure`
- [ ] `MethodConstant.getComponent() → MethodStructure`
- [ ] `PropertyConstant.getComponent() → PropertyStructure`
- [ ] `ModuleConstant`, `PackageConstant`, `TypedefConstant`, `MultiMethodConstant`
- [ ] Delete the 222 now-redundant casts
- [ ] Ratchet test pinning the narrowed return types

### T2 — `ValueConstant<V>` — **IN PROGRESS**

Only **6** call sites have a statically-`ValueConstant` receiver (all in `CaseManager.covers()`);
the other 893 `.getValue()` calls already have concrete receivers and already get the narrowed
type. Verified bytecode-identical by `javap` — the `Object getValue()` bridge already exists.

Two honest obstacles, both to be resolved rather than annotated around:

- [ ] `MatchAnyConstant` returns the string `"_"` and its own comment admits there is no correct
      answer. It is a **wildcard marker, not a value** — `CaseManager.covers()` already
      special-cases it *first*. Remove it from the `permits` list rather than typing the lie.
- [ ] `EnumValueConstant.getValue()` can return null. Make it total; do not annotate.
- [ ] Genericize the class and all 26 overriders
- [ ] `CaseManager.covers()` — the only site that needs a wildcard or a rethink

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
