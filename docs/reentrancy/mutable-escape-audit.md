# Mutable-Reference Escape Audit

Status: COMPLETE (2026-08-26). MA5 on the must-audit board.

## Scope & Method

Audit of **mutable-reference escapes** in:

- `javatools/src/main/java/org/xvm/runtime/`
- `javatools/src/main/java/org/xvm/asm/` (incl. `asm/constants/`)

A mutable-reference escape is a place where a mutable value is aliased across an
ownership boundary and can corrupt an owner's invariants or break under reuse.
Three shapes:

- **(a) Leaky getter** — returns a DIRECT reference to a mutable field (raw
  `List`/`Map`/`Set`/`Collection` or a mutable object) with no defensive copy /
  unmodifiable wrap.
- **(b) Stored alias** — a constructor/setter stores a caller-supplied mutable
  reference DIRECTLY.
- **(c) Field from external mutable source** — a field holds an externally-owned
  mutable object (shared static collection handed out, exposed cache).

Method: grep-driven triage (collection/array getter signatures; collection
setters; array-param stores; non-final static collection fields), then read only
the return/store site of each hit. Mutability judged by field/param type; frozen
`TypeConstant` families, `record`s, `String`, and boxed primitives do not count.

### Exclusions (already audited — cross-reference, not re-audited here)

Array-ELEMENT exposure, array immutability, clone/copy, constant-adoption:
`array-element-exposure-audit.md`, `array-list-immutability-study.md`,
`clone-usage-audit.md`, `constant-adoption-clone-audit.md`, `generics-api-audit.md`.
This audit's value is the NON-array/non-clone mutable surface: raw collection
getters, mutable objects stored from params, exposed caches.

### Classification

- **severity**: MISUSE-REACHABLE-TODAY (must-fix) | SHOULD-FIX |
  SAFE-BY-CONVENTION/EFFECTIVELY-IMMUTABLE | ALREADY-COVERED(cross-ref)
- **timing**: ALWAYS (single-owner/sequential reuse can break) vs CONCURRENCY-ONLY

**Headline:** the runtime/asm mutable surface is **overwhelmingly fenced** — the
dominant conventions are `Collections.unmodifiable*`, `ROMap`/`List.of`, `emptyX`,
defensive `aOwned` copies (from the prior owner/constructor-escape work), and
copy-in setters. **No MISUSE-REACHABLE-TODAY escape was found** (no caller mutates
a returned collection to corrupt an owner). The open items are a small set of
**SHOULD-FIX** live-collection getters that return a field directly; all are
aliasing-latent or concurrency-only, and none is exercised as a corruption today.

---

## Shape (a) — Leaky collection/object getters

### Open — SHOULD-FIX (returns a live mutable field directly)

| Site | Returns | Timing | Note / closure |
| --- | --- | --- | --- |
| `runtime/ServiceContext.java:165` `getFibers()` | `f_setFibers` | CONCURRENCY-ONLY | live fiber set; iterated for scheduling/diagnostics. Wrap `unmodifiableSet` (or expose a copy). |
| `runtime/Container.java:219` `getServices()` | `f_setServices` | CONCURRENCY-ONLY | live service set of the container. Same closure. |
| `runtime/Fiber.java:141/148` `getTokens()`/`ensureTokens()` | `m_mapTokens` | ALWAYS (aliasing) | javadoc says "read-only access" but nothing enforces it; a caller can mutate the fiber's token map. Wrap unmodifiable on the read path. |
| `runtime/ClassComposition.java:444` (+ `DelegatingComposition.java:78`, `PropertyComposition.java:304`) `getFieldLayout()` | `fieldLayout().fields()` — the `FieldLayout` record's map component (`ClassComposition.java:911`) | ALWAYS (aliasing) | returns the layout map directly; SHOULD-FIX **iff** the layout map is a plain map (verify it is built unmodifiable at layout construction — if so this is SAFE). |
| `asm/Component.java:721` `getChildByNameMap()` | `m_childByName` | ALWAYS (aliasing) | live child-by-name map of a component; single-owner during structure assembly today. (`FileStructure` overrides to `throw UnsupportedOperationException` — already fenced; `CompositeComponent`/`MultiMethodStructure` delegate here.) |
| `asm/ModuleStructure.java:158` `getDependencies()` / `:128` `getDependencyTypes()` | lazy `m_mapDependencies` | ALWAYS (aliasing) | lazily-built dependency map returned directly; cross-ref `manual-lazy-cache-audit.md`. |
| `asm/ConstantPool.java:2491` `getJitPrimitiveTypes()` | lazy `m_setJitPrimitives` | CONCURRENCY-ONLY | JIT-codegen only; returns the lazily-built set directly. Rides the JIT hardening rows. |
| `asm/MethodStructure.java:461/577` `getReturns()`/`getParams()` | `Arrays.asList(m_aReturns)` / `Arrays.asList(m_aParams)` | ALWAYS (aliasing) | `Arrays.asList` is fixed-size but `list.set(i, x)` writes THROUGH to the owner's parameter array. Return `List.of(...)` (immutable) instead. Distinct from the frozen `SignatureConstant` work (Families A/B) which already fixed the constant-side arrays. |

### Safe-by-convention (cross-referenced)

| Site | Why acceptable |
| --- | --- |
| `asm/ErrorList.java:120` `getErrors()` → `f_list` | request-local diagnostics accumulator; returning the live list is the accepted contract. Cross-ref `logging-diagnostics-audit.md`. |

### Fenced (representative — NOT escapes)

- **All `TypeInfoReal.*` getters** (`getTypeParams`, `getProperties`, `getMethods`,
  `getClassChain`, `getVirtProperties`, `getChildInfosByName`, …): every one returns
  `Collections.unmodifiableMap/List(...)`. This is the audited immutable metadata.
- `asm/constants/MapConstant.java:176` → `new ROMap<>(...)`;
  `SignatureConstant.java:206` → `List.of(aParams())`;
  `MethodConstant.java:189/203` → delegate to the frozen signature (Families A/B).
- `asm/Component.java:470` `getContributionsAsList()` → `Collections.unmodifiableList`
  (explicit "must not evaporate under -da" wrapper); `Component.java:2929`
  `getInjections()` → `unmodifiableList`; `ChildInfo.java:107` → `unmodifiableSet`.
- Repositories (`DirRepository`, `FileRepository`, `LinkedRepository`) `getModuleNames()`
  → `unmodifiableSet` / `singleton` / `emptySet`.
- `asm/FileStructure.java:766` `getChildByNameMap()` → `throw UnsupportedOperationException`.

---

## Shape (b) — Stored aliases (param stored directly)

**Clean / already-covered.** The array stores in the constant families take a
defensively-**owned** copy, signalled by the `aOwned` parameter name and produced by
the prior owner/constructor-escape work: `asm/constants/MethodInfo.java:94`
(`m_aBody = aOwned`), `PropertyInfo.java:118` (`m_aBody = aOwned`) — cross-ref
`this-escape-removal-audit.md` / `constant-adoption-clone-audit.md`. The only
collection setter found, `asm/ModuleStructure.java:340`
`setFingerprintVersionPrefs(List)`, **copies in** (`clear()` + `addAll(listPrefer)`),
it does not retain the caller's list. `TypeConstant.java:2551` `aAnnoApply = aAnnoClass`
is a local reassignment, not a field store. No stored-alias escape found.

---

## Shape (c) — Field from external mutable source / shared static

**Clean.** No mutable non-final `static` collection FIELD is exposed anywhere in
`runtime/` or `asm/` — the only `static` collection hits are local helper methods
that build and return fresh collections (`OwnershipDiagnostics`,
`ConstantAdoptionValidator`, `JumpVal*`, etc.), and one commented-out field
(`MethodStructure.java:2276`). Consistent with the branch-wide static-field
hardening (no `INSTANCE`/static owner state).

---

## Verdicts & counts

- **MISUSE-REACHABLE-TODAY (must-fix): 0.** No caller was found that mutates a
  returned collection to corrupt an owner. (A full caller-by-caller sweep of the 8
  SHOULD-FIX getters would upgrade any that has a mutating consumer; none surfaced in
  the sites examined.)
- **SHOULD-FIX: 8 getter sites** (shape a): 3 runtime live-collection getters
  (`getFibers`, `getServices`, `getTokens`), the `getFieldLayout` record-map exposure
  (verify layout-map mutability), 2 asm structure getters (`Component.getChildByNameMap`,
  `ModuleStructure.getDependencies`/`getDependencyTypes`), the JIT-only
  `getJitPrimitiveTypes`, and `MethodStructure.getReturns`/`getParams` `Arrays.asList`
  set-through. All aliasing-latent or concurrency-only.
- **SAFE-BY-CONVENTION: 1** (`ErrorList.getErrors`).
- **SAFE / already-fenced / already-covered:** the large majority (all `TypeInfoReal`,
  the constant-family getters, wrapped Component/ChildInfo getters, repositories,
  `FileStructure` throw, `aOwned` stores, copy-in setter).
- **Shapes (b) and (c): clean.**

**Recommended closure (SHOULD-FIX batch, cheap, not urgent):** return unmodifiable
views from the 8 getters (or `List.of(...)` for `MethodStructure.getReturns/getParams`),
after a quick caller check that none rely on mutating the result. Priority is low —
nothing here is reachable as a corruption today, and the runtime live-collection ones
are only a concern once concurrent readers/mutators coincide, which the container
model keeps rare. Track under the should-fix "publication-visibility & guard-symmetry"
bucket on the board.
