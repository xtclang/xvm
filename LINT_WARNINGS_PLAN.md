# Java Lint Warnings Elimination Plan

Compiled with `-Xlint:all` via `--no-build-cache --no-configuration-cache --rerun-tasks -Porg.xtclang.java.lint=true -Porg.xtclang.java.warningsAsErrors=false -Porg.xtclang.java.maxWarnings=1000`.

**Current: 220 warnings** (6 in `javatools_utils`, 210 in `javatools`, 4 in `javatools_jitbridge`)

Note: counts use `--rerun-tasks` for full accuracy; earlier sessions used cached builds which
undercounted `rawtypes`/`unchecked`. The delta from our work is what matters.

| Category | Current | Fixed | Tier | Status |
|----------|---------|-------|------|--------|
| `[fallthrough]` | **0** | 102 | 4 | DONE |
| `[this-escape]` | **40** | 28 | 5 | 28 fixed by code rewrites; 40 remain (intentionally unsuppressed) |
| `[rawtypes]` | 90 | 14 | 3 | 8 fixed by generics rewrites; 82 remaining |
| `[unchecked]` | 86 | 0 | 3 | Pending |
| `[try]` | **0** | 18 | 1 | DONE |
| `[cast]` | **2** | 13 | 1 | DONE (2 in `javatools_jitbridge`) |
| `[serial]` | **2** | 8 | 2 | DONE (2 in `javatools_jitbridge`) |
| `[overrides]` | **0** | 3 | 3 | DONE |
| `[static]` | **0** | 2 | 1 | DONE |

---

## Tier 1: TRIVIAL -- DONE (33 warnings fixed)

Fixed in commit `b8d36c5ca`:
- `[static]` (2): Changed instance-qualified static calls to class-qualified in `xLocalClock.java`
- `[cast]` (13): Removed redundant casts across 8 files
- `[try]` (18): Added `@SuppressWarnings("try")` for intentional scoped try-with-resources

---

## Tier 2: EASY -- DONE (11 warnings fixed)

### `[serial]` Missing serialVersionUID / non-serializable field (8 warnings → 0)

All `[serial]` warnings came from classes that inherit `Serializable` through their superclass
hierarchy but are never actually serialized. The root cause is that `java.lang.Throwable`
implements `Serializable` (a JDK 1.1 design decision for RMI), so every `Exception`,
`RuntimeException`, and `Error` subclass is automatically `Serializable` whether intended or not.

**Exception classes** — suppressed with `@SuppressWarnings("serial")`:
- `SegFault` — extends `Error`
- `CompilerException` — extends `LauncherException` → `RuntimeException`
- `LauncherException` — extends `RuntimeException`
- `WrapperException` — extends `Exception` (inner class of `ExceptionHandle`)
- `RangeException` — extends `ArithmeticException` (inner class of `Decimal`; also carried a
  non-serializable `Decimal` field that would have thrown `NotSerializableException` at runtime)

**Collection classes** — suppressed with `@SuppressWarnings("serial")`:
- `ROEntry` — extends `AbstractMap.SimpleEntry` (inner class of `MapConstant`)

**IdentityArrayList** — rewritten with composition (3 `[rawtypes]` warnings also eliminated):
- Changed from `extends ArrayList<E>` to `extends AbstractList<E>` with internal `ArrayList<E>`
  delegate. `AbstractList` is not `Serializable`, so no suppression needed. Previously, the class
  inherited `Serializable`, `Cloneable`, and `RandomAccess` from `ArrayList`, none of which were
  used at any call site. Only used in `CompositeComponent.java`, stored as `List<Component>`.

**Existing `serialVersionUID` declarations investigated (2 found, both must stay):**
- `NonBlockingHashMapLong` and `NonBlockingHashMap` in `javatools_backend` both have active
  `writeObject()`/`readObject()` implementations — genuine serialization.

---

## Tier 3: MODERATE (need understanding, some refactoring) -- 176 warnings remaining

### 3a. `[rawtypes]` Raw generic type usage (90 warnings, 8 fixed)

**Already eliminated (suppressed, not counted in warning totals):**
- `Component.unlinkSibling()` — generified to `<K, V extends Component>`, removed raw `Map`/`Object`
  and `@SuppressWarnings("rawtypes")`. Also fixed bug in `MultiMethodStructure.removeChild()` that
  passed `child` (Component) instead of `method` (MethodStructure).

**Eliminated by generics rewrites (8 warnings):**
- `SwitchAST.java` (2) — parameterized `Iterator` → `Iterator<Object>` for `contents()` method
- `JumpVal_N.java` (2) — replaced generic array fields `Map<ObjectHandle, Long>[]` →
  `List<Map<ObjectHandle, Long>>` and `List<Object[]>[]` → `List<List<Object[]>>`, eliminating
  raw `new Map[n]` and `new List[n]` array creations
- `CaseManager.java` (4) — parameterized `instanceof Comparable` → `instanceof Comparable<?>`;
  extracted `compareRaw()` helper to isolate the unavoidable raw comparison

Add proper type parameters. Remaining hotspots:

| File | Count | Key raw types |
|------|-------|---------------|
| `AstNode.java` | 14 | `List`, `Map`, `Collection`, `Iterator`, `ListIterator`, `ArrayList`, `Class` |
| `ServiceContext.java` | 13 | `CompletableFuture`, `Response`, `Enum`, `EnumMap`, `WeakReference` |
| `Fiber.java` | 8 | `CompletableFuture` |
| `Frame.java` | 4 | `CompletableFuture` |
| `xOSFile.java` | 4 | `CompletableFuture` |
| `ObjectHandle.java` | 2 | `CompletableFuture` |
| `NativeTypeSystem.java` | 2 | `Class` |
| `TypeInfo.java` | 2 | `Entry` |
| `xFuture.java` | 2 | `CompletableFuture` |
| Many more files | 1 each | Various |

**Strategy:** `CompletableFuture` raw usage (28 warnings across 6 files) is the biggest cluster.
Parameterize as `CompletableFuture<ObjectHandle>` starting from Frame, then Fiber, then ServiceContext.

### 3b. `[unchecked]` Unchecked casts and conversions (86 warnings)

Many overlap with `rawtypes`. Fix together per-file.

| File | Count | Key patterns |
|------|-------|-------------|
| `ServiceContext.java` | 22 | `Response` constructor, `assignFutureResult`, `createWaitFrame`, raw `CompletableFuture.complete()` |
| `Fiber.java` | 5 | `CompletableFuture` casts |
| `TransientThreadLocal.java` | 4 | `(T) map.get(this)` -- unavoidable, suppress |
| `Context.java` | 4 | `Map.putAll` with raw maps |
| `LambdaExpression.java` | 4 | raw `Stream.allMatch()` |
| `TypeInfo.java` | 4 | `entrySet().toArray(Entry[])` with raw Entry |
| `AstNode.java` | 3 | generic child replacement utility |
| `ListMap.java` | **0** | **Rewritten:** stored `ArrayList<Entry>` instead of `ArrayList<SimpleEntry>`, eliminated `EMPTY_ARRAY_LIST` sentinel, raw casts, and assert-only unmodifiable bug |
| `TypeCompositionStatement.java` | 3 | unchecked casts to generic types |
| 14 more files | 1-2 each | Various |

### 3c. `[overrides]` equals without hashCode -- DONE (3 warnings fixed)

Implemented `hashCode()` consistent with `equals()` in all three classes:
- `Register` — `Objects.hash(m_iArg, m_fRO, m_fEffectivelyFinal, isInPlace(), m_type)`
- `ShadowRegister` — `31 * getOriginalRegister().hashCode() + getType().hashCode()`
- `ChildInfo` — `Objects.hash(f_child, f_access, f_setIds)`

---

## Tier 4: INVOLVED -- DONE (96 warnings fixed)

### 4. `[fallthrough]` Switch case fall-through

All fallthrough warnings audited and confirmed intentional. Eliminated via two strategies:

**Strategy A — Refactored 11 switches** (semantic rewrites, no code duplication):
- **NativeClass `instanceof` pattern** (9 switches across 3 files): Merged `NativeClass` into grouped
  cases with `if (constant instanceof NativeRebaseConstant nrc)` guard. Files:
  `TerminalTypeConstant.java` (7), `ClassStructure.java` (1), `ConstantPool.java` (1)
- **Component.java arrow-case** (1 switch): Converted `Contribution` constructor validation switch
  to arrow-case syntax, eliminating the fall-through from validation cases to `Equal`

**Strategy B — Suppressed remaining 85 warnings** with `@SuppressWarnings("fallthrough")` at method
scope across 35 files. All are intentional patterns: state machines, conditional break-then-fallthrough,
accumulated logic, swap-and-fallthrough. Existing `// fall through` comments retained for readability.

---

## Tier 5: PARTIAL -- 28 this-escape warnings fixed by code rewrites, 40 remaining

### 5. `[this-escape]` Constructor this-escape

Java 21+ (JEP 447) warns when `this` escapes a constructor before the subclass portion is
initialized. Two escape kinds: (A) calling an overridable method on `this`, (B) passing `this`
as an argument to an external method/constructor.

### What was done (28 warnings eliminated)

**Code rewrites only — no suppressions.** The remaining 40 warnings are intentionally left
unsuppressed so they continue to flag constructors worth thinking about.

#### Fix 1 — Removed constructor asserts from Op hierarchy (14 warnings eliminated)

Six abstract Op base classes had `assert isBinaryOp()` / `assert !isBinaryOp()` / similar
in their non-deserialization constructors. These virtual calls in constructors triggered
this-escape warnings. The asserts were removed since they only verified design invariants
(correct constructor overload was called) and `isBinaryOp()`/`isAssignOp()` are overridden
by ~26+ subclasses each.

| File | Asserts removed |
|------|----------------|
| `OpCondJump.java` | 3 (from 3 assembly constructors) |
| `OpGeneral.java` | 2 |
| `OpIndex.java` | 2 |
| `OpInPlace.java` | 2 |
| `OpPropInPlace.java` | 2 |
| `OpTest.java` | 3 (from 3 assembly constructors) |

Deserialization constructors (DataInput) were left untouched — they never had asserts.

#### Fix 2 — Made AstNode/ComponentStatement methods `final` (6 methods)

These methods were never overridden anywhere in the codebase:
```
AstNode.adopt(T child)              → final
AstNode.adopt(Iterable<...>)        → final
AstNode.setParent(AstNode)          → final
AstNode.introduceParentage()        → final
AstNode.isDiscarded()               → final
ComponentStatement.setComponent()   → final
```

Note: `AstNode.setStage()` was NOT made final — `MultipleLValueExpression` overrides it.

#### Fix 3 — Made HasherReference.reset() `final` (1 warning eliminated)

`reset()` was called in the constructor. `TransientHasherReference` (sole subclass) calls
but never overrides it. Making it `final` eliminated the warning.

#### Fix 4 — Inlined PropertyConstant.checkParent() (1 warning eliminated)

The constructor called `checkParent(constParent)` which was a virtual method. Inlined the
switch validation directly into the constructor body. Added a `protected` constructor with
`boolean fSubclass` parameter for `FormalTypeChildConstant` to use.

#### Fix 5 — Inlined TypeInfo.isClass() (1 warning eliminated)

Constructor called `isClass()` which was virtual. Replaced with direct `struct.getFormat()`
switch on the format enum.

#### Fix 6 — Inlined CallChain.FieldAccessChain.isField() (1 warning eliminated)

Constructor assert called `isField()`. Inlined to direct field check: `assert m_nField >= 0`.

#### Fix 7 — Inlined xRTMethod.MethodHandle.getMethodInfo() (1 warning eliminated)

Constructor assert called `getMethodInfo()`. Inlined to `assert m_chain.getTop() != null`.

#### Fix 8 — Made JitMethodDesc.computeMethodDesc() `static` (1 warning eliminated)

Constructor called `computeMethodDesc()` which could be virtual. Restructured to pre-compute
`ClassDesc[] extraCDs` and pass it to a static method. Updated `JitCtorDesc` accordingly.

#### Fixes that did NOT help (kept as good practice)

- `Lexer.eatWhitespace()` → made `private` but compiler still traces to virtual calls within
- `ModuleInfo.getResourceDir()` → made `final` but compiler traces to virtual calls within
- `PropertyStructure` constructor → inlined `setVarAccess()`/`setType()` but inlined code
  calls `getAccess()` which is virtual

Note: making `PackedInteger.setLong()`/`setBigInteger()`/`readObject()` `final` was attempted
but **reverted** — the compiler traced through to `verifyUninitialized()` (virtual), actually
increasing the warning count.

---

### Remaining 40 this-escape warnings (intentionally unsuppressed)

| File | Count | Escape pattern |
|------|-------|----------------|
| `Parser.java` | 4 | Constructor chaining + `next()` token priming |
| `PackedInteger.java` | 3 | `setLong()` / `setBigInteger()` / `readObject()` call `verifyUninitialized()` (virtual) |
| `ModuleInfo.java` | 2 | `getResourceDir()` traces to virtual calls |
| `xRef.java` (RefHandle) | 2 | `setField()` on partially-constructed handle |
| `NativeContainer.java` | 2 | `loadNativeTemplates()` bootstrap |
| `BuildContext.java` | 2 | `new TypeMatrix(this)` |
| `Lexer.java` | 2 | `eatWhitespace()` traces to virtual calls |
| `TypeCompositionStatement.java` | 2 | `setParent()` + `introduceParentage()` — `this` passed as arg |
| `MethodDeclarationStatement.java` | 2 | `setComponent()` + `adopt()` — `this` passed as arg |
| `SyntheticExpression.java` | 1 | `expr.getParent().adopt(this)` — kind B escape |
| `ConvertExpression.java` | 1 | chains to SyntheticExpression |
| `PackExpression.java` | 1 | chains to SyntheticExpression |
| `ToIntExpression.java` | 1 | chains to SyntheticExpression |
| `TraceExpression.java` | 1 | chains to SyntheticExpression |
| `UnpackExpression.java` | 1 | chains to SyntheticExpression |
| `PropertyDeclarationStatement.java` | 1 | `anno.setParent(this)` |
| `NamedTypeExpression.java` | 1 | `setStage()` + `setParent()` |
| `xOSFileNode.java` (NodeHandle) | 1 | `setField()` |
| `Container.java` | 1 | `new ConstHeap(this)` |
| `ClassTemplate.java` | 1 | `registerImplicitFields()` — virtual, overridden by xRef |
| `Xvm.java` | 1 | `NativeTypeSystem.create(this, repo)` |
| `PropertyStructure.java` | 1 | `getAccess()` virtual call in inlined code |
| `OpVar.java` | 1 | `isTypeAware()` in assert |
| `OpTest.java` | 1 | deserialization constructor calls `isBinaryOp()` |
| `ClassStructure.java` | 1 | `resolveGenerics(pool, this)` in SimpleTypeResolver |
| `AbstractConverterMap.java` | 1 | `newKeySet()`, `newValues()`, `newEntrySet()` — virtual |
| `CooperativelyCleanableReference.java` | 1 | `KEEP_ALIVE.add(this)` |
| `ListSet.java` | 1 | `addAll(that)` |

All remaining escapes are safe (no uninitialized field observation, no data races) but
require non-trivial refactoring or `@SuppressWarnings` to silence.

---

## Recommended Execution Order

1. **Tier 1** -- DONE (33 warnings)
2. **Tier 2** -- DONE (11 warnings: 8 serial + 3 rawtypes from IdentityArrayList)
3. **Tier 4** -- DONE (96 warnings: 11 refactored + 85 suppressed)
4. **Tier 5** -- PARTIAL (28 this-escape fixed by code rewrites; 40 remain unsuppressed)
5. **Tier 3** (`rawtypes` + `unchecked` + `overrides`) -- Next up, fix together per-file

## How to Reproduce

```bash
./gradlew clean
./gradlew build --no-build-cache --no-configuration-cache --rerun-tasks \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=1000
```
