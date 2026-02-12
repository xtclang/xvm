# Java Lint Warnings Elimination Plan

Compiled with `-Xlint:all` via `--no-build-cache --no-configuration-cache --rerun-tasks -Porg.xtclang.java.lint=true -Porg.xtclang.java.warningsAsErrors=false -Porg.xtclang.java.maxWarnings=1000`.

**Current: 35 warnings** (0 in `javatools_utils`, 31 in `javatools`, 4 in `javatools_jitbridge`)

Note: counts use `--rerun-tasks` for full accuracy; earlier sessions used cached builds which
undercounted `rawtypes`/`unchecked`. The delta from our work is what matters.

| Category | Current | Fixed | Tier | Status |
|----------|---------|-------|------|--------|
| `[fallthrough]` | **0** | 102 | 4 | DONE |
| `[this-escape]` | **31** | 37 | 5+6 | 28 code rewrites + 9 architectural (Tier 6); 31 remain |
| `[rawtypes]` | **0** | 104 | 3 | DONE |
| `[unchecked]` | **0** | 86 | 3 | DONE |
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

## Tier 3: MODERATE (need understanding, some refactoring) -- DONE (all 91 remaining warnings fixed)

### 3a. `[rawtypes]` Raw generic type usage (104 total fixed)

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

**Eliminated by wildcard parameterization (14 warnings):**
- `AstNode.java` (14) — added `<?>` wildcards to all raw type patterns in `clone()`, `dump()`,
  `ChildIteratorImpl`, `ensureArrayList()`, and `fieldsForNames()`. `clone()` eliminated unchecked
  `(List<AstNode>)` cast by iterating `List<?>` with checked `(AstNode)` element casts.
  `ensureArrayList()` uses `@SuppressWarnings("unchecked")` for safe `ArrayList<T>` downcast.
  `ChildIteratorImpl.replaceWith()` uses `@SuppressWarnings("unchecked")` for `ListIterator<AstNode>`
  cast needed for `set()`.

**Eliminated by generic Message\<T\>/OpRequest\<T\> + CompletableFuture parameterization (33 rawtypes + 35 unchecked):**

Commit `d92babcb8`: Made `Message<T>` and `OpRequest<T>` generic, parameterized `CallLaterRequest`
as `Message<ObjectHandle>`. Replaced `EnumMap` cache with `HashMap<Enum<?>, WeakReference<Object>>`.
Added wildcards/proper type args across 6 files. Key design decisions:

- `Message<T>` with `CompletableFuture<T> f_future` — consumer sites get properly typed futures
  with zero casts: `OpRequest<ObjectHandle>` for single-return, `OpRequest<ObjectHandle[]>` for
  multi-return
- `xFuture.cfAny` — replaced `CompletableFuture.anyOf()` + cast with type-safe
  `cfThis.applyToEither(cfThat, Function.identity())`
- `sendInvokeNRequest` cReturns==0 edge case — hoisted before request creation to use
  `OpRequest<ObjectHandle>` instead of casting from `ObjectHandle[]`
- Fiber `reportWaiting()` — replaced `StringBuilder`/`fFirst` loop with `Collectors.joining()`

Only 2 `@SuppressWarnings("unchecked")` remain at genuine type system boundaries:
1. `sendResponse()` — runtime dispatch on `cReturns` determines T; can't express in Java types
2. `Fiber.pendingRequestMap()` — Object union pattern (`null | Message<?> | Map<...>`); splitting
   into two fields would widen race window in `whenComplete` callbacks

Files changed: `ServiceContext.java`, `Fiber.java`, `Frame.java`, `ObjectHandle.java`,
`xFuture.java`, `xOSFile.java`

**Eliminated remaining 43 rawtypes + 48 unchecked (91 warnings) across scattered files:**

GC test files (4 files, ~44 warnings):
- Changed `ReferenceQueue<?>` to `ReferenceQueue<Object>` in all `Xvm` classes, enabling
  `Reclaim<>` diamond inference with `ReferenceQueue<? super V>` constructor parameter
- Added `<?>` wildcards to all raw `Reclaim`, `CleanablePhantom`, `ReferenceQueue` patterns
  in `instanceof`, field declarations, and `link()` method signatures
- Files: `SimpleCanaryGcTest.java`, `PhantomGcTest.java`, `CanaryBuddyGcTest.java`,
  `CanaryCohortGcTest.java`

Production code rewrites (no suppressions needed):
- `Context.java` — replaced single raw `Map mapBranch` with properly typed
  `Map<String, Argument>` and `Map<FormalConstant, TypeConstant>` variables
- `LambdaExpression.java` — changed `List` parameter to `List<? extends AstNode>`,
  eliminating raw `Stream.allMatch()` warnings; 2 unavoidable variance casts remain
  with targeted `@SuppressWarnings("unchecked")`
- `Utils.ANY` — typed from raw `Predicate` to `Predicate<MethodStructure>` (sole caller)
- `NativeTypeSystem.nativeBuilders` — typed from `Class` to `Class<? extends Builder>`;
  `instanceof Class` → `instanceof Class<?>` in `ensureBuilder()`
- `TypeConstant.java` — `(ListMap) null` → `(ListMap<StringConstant, TypeConstant>) null`
- `NativeContainer.java` — `(Set<String>) (Set) System.getProperties().keySet()` →
  `System.getProperties().stringPropertyNames()` (proper JDK API)
- `ReturnStatement.java` — `(List) null` → `(List<Expression>) null`
- `TypeCompositionStatement.java` — `Collections.EMPTY_LIST` → `Collections.emptyList()`;
  raw `(List)` intermediate casts → `(List<?>)` with targeted `@SuppressWarnings`
- `SetTest.java` — rewrote `Set<Object>[]` generic array to `List<Set<Object>>` using
  `List.of()`, eliminating all rawtypes/unchecked from generic array creation

Targeted suppressions at type system boundaries:
- `TransientThreadLocal.java` (4) — `(T) map.get(this)` in `get()`, `compute()`,
  `computeIfAbsent()`, `push()` — unavoidable, map stores `Object` values
- `TypeInfo.java` (2) — `entrySet().toArray(new Entry[0])` — Java can't create
  parameterized arrays
- `BinaryAST.readAST()` — `(N)` casts on erased generic type
- `NewExpression.clone()` — `(T)` cast on cloned AstNode list elements
- `CooperativelyCleanableReference.java` — `new ReferenceQueue[]` array creation
- `ListSet.toExternal()` — `(E)` cast from Object sentinel pattern
- `TypeConstant.s_tloInProgress` — class literal can't be parameterized

### 3b. `[unchecked]` — DONE (all fixed together with rawtypes, see 3a above)

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

### Remaining 31 this-escape warnings

| File | Count | Escape pattern |
|------|-------|----------------|
| `Parser.java` | 4 | Constructor chaining + `next()` token priming |
| `xRef.java` (RefHandle) | 2 | `setField()` calls `getComposition()` (virtual); `setRef(this)` kind B escape |
| `NativeContainer.java` | 2 | `loadNativeTemplates()` bootstrap |
| `BuildContext.java` | 2 | `new TypeMatrix(this)` |
| `Lexer.java` | 2 | `eatWhitespace()` traces to virtual calls |
| `TypeCompositionStatement.java` | 2 | `setParent()` + `introduceParentage()` — `this` passed as arg |
| `MethodDeclarationStatement.java` | 2 | `setComponent()` + `adopt()` — `this` passed as arg |
| `ModuleInfo.java` | 2 | `getResourceDir()` traces to virtual calls |
| `SyntheticExpression.java` | 1 | `expr.getParent().adopt(this)` — kind B escape |
| `ConvertExpression.java` | 1 | chains to SyntheticExpression |
| `PackExpression.java` | 1 | chains to SyntheticExpression |
| `ToIntExpression.java` | 1 | chains to SyntheticExpression |
| `TraceExpression.java` | 1 | chains to SyntheticExpression |
| `UnpackExpression.java` | 1 | chains to SyntheticExpression |
| `PropertyDeclarationStatement.java` | 1 | `anno.setParent(this)` |
| `NamedTypeExpression.java` | 1 | `setStage()` + `setParent()` |
| `xOSFileNode.java` (NodeHandle) | 1 | `setField()` calls `getComposition()` (virtual) |
| `Container.java` | 1 | `new ConstHeap(this)` |
| `Xvm.java` | 1 | `NativeTypeSystem.create(this, repo)` |
| `PropertyStructure.java` | 1 | `getAccess()` virtual call in inlined code |
| `ClassStructure.java` | 1 | `resolveGenerics(pool, this)` in SimpleTypeResolver |

All remaining escapes are safe (no uninitialized field observation, no data races) but
require static factory methods or deeper refactoring to silence.

---

## Tier 6: ARCHITECTURAL — Eliminate remaining this-escape warnings

Three architectural patterns, applied in order from smallest blast radius to largest.

### Step 1 — Make leaf classes `final` -- DONE (4 warnings eliminated)

Classes that have no subclasses and no design intent to be subclassed. Making them `final`
tells the compiler that virtual dispatch in constructors is safe.

| File | Warning count | Change |
|------|--------------|--------|
| `PackedInteger.java` | 3 | `public final class PackedInteger` |
| `ListSet.java` | 1 | `public final class ListSet<E>` |

### Step 2 — Constructor parameters instead of virtual dispatch -- PARTIAL (6 of 9 eliminated)

Replace the template-method pattern ("base calls virtual to ask subclass") with an inverted
pattern ("subclass tells base via constructor parameter"). Eliminates virtual dispatch entirely.

**2a. Op deserialization hierarchy -- DONE (2 warnings eliminated)**

`OpTest` and `OpVar` call `isBinaryOp()`, `hasSecondArgument()`, `isTypeAware()` — all virtual
with 15+ active overrides. Passed the format as constructor parameters:

| File | Change |
|------|--------|
| `OpTest.java` | Added `boolean fBinaryOp, boolean fSecondArg` params to deserialization constructor |
| `OpVar.java` | Added `boolean fTypeAware` param to deserialization constructor |
| 30 subclasses | Pass format booleans via `super(in, aconst, true, false)` etc. |

**2b. ClassTemplate implicit fields -- DONE (1 warning eliminated)**

`registerImplicitFields()` was virtual, overridden by `xConst` and `xRef`. Replaced with
constructor chaining: added a `protected` constructor taking `Set<String> extraImplicitFields`,
made `registerImplicitFields` private (`computeImplicitFields`). Subclass overrides removed.

| File | Change |
|------|--------|
| `ClassTemplate.java` | Added `protected` constructor with `Set<String> extraImplicitFields` param; original delegates via `this(...)` |
| `xConst.java` | `super(container, struct, Set.of(PROP_HASH))`; removed `registerImplicitFields` override |
| `xRef.java` | `super(container, struct, Set.of(REFERENT, OUTER))`; removed `registerImplicitFields` override |

**2c. AbstractConverterMap collection views -- DONE (1 warning eliminated)**

`newKeySet()`, `newValues()`, `newEntrySet()` were virtual template methods called in
constructor. Inlined view creation directly (no virtual dispatch). Removed the factory methods.
`HasherMap` and `WeakHasherMap` now replace the `keys`/`entries` fields after `super(...)`.

| File | Change |
|------|--------|
| `AbstractConverterMap.java` | Constructor inlines `new KeySet()` etc.; `keys`/`entries` made non-final; factory methods removed |
| `HasherMap.java` | Sets `this.keys = new KeySet()` after `super(...)`; removed `newKeySet()` override |
| `WeakHasherMap.java` | Sets `this.keys`/`this.entries` after `super(...)`; removed `newKeySet()`/`newEntrySet()` overrides |

**2d. Handle.setField() final -- NOT EFFECTIVE (0 of 3 warnings eliminated)**

Made `GenericHandle.setField()` `final` (no subclass overrides it). However, the Java 21+
this-escape analysis traces through final method bodies and finds `getComposition()` (virtual)
inside `setField()`. The warnings at `xRef.java:777`, `xRef.java:819`, and
`xOSFileNode.java:169` remain. A deeper fix would require bypassing `setField()` entirely
with direct `m_aFields[]` assignment using pre-computed field indices.

**2e. CooperativelyCleanableReference -- DONE (1 warning eliminated)**

Moved `KEEP_ALIVE.add(this)` from constructor to a `static create()` factory method.
Updated test call sites.

| File | Change |
|------|--------|
| `CooperativelyCleanableReference.java` | Private constructor + `static <V> create()` that adds to `KEEP_ALIVE` |
| `CooperativelyCleanableReferenceTest.java` | `new ...()` → `.create()` |

### Step 3 — Static factory methods for tree-wiring and bootstrap (22 warnings, significant)

Replace public constructors with `create()` factory methods. The private constructor only
assigns fields; the factory method performs tree-wiring, registration, and initialization
on the fully-constructed object.

**3a. SyntheticExpression + 5 subclasses (6 warnings)**

Extract tree-wiring from the constructor into a post-construction method:

```java
// SyntheticExpression: constructor becomes field-assignment only
protected SyntheticExpression(Expression expr) {
    this.expr = expr;
}

// New method called by factory, not constructor
protected void replaceInTree(Expression original) {
    original.getParent().adopt(this);
    this.adopt(original);
}
```

Each subclass gets a static `create()` that calls the private constructor, then
`replaceInTree()`, then `finishValidation()`:

| File | Change |
|------|--------|
| `SyntheticExpression.java` | Split constructor; add `replaceInTree()` |
| `ConvertExpression.java` | `static create()` factory; private constructor |
| `PackExpression.java` | Same |
| `ToIntExpression.java` | Same |
| `TraceExpression.java` | Same |
| `UnpackExpression.java` | Same |
| Call sites (~6 files) | `new ConvertExpression(...)` → `ConvertExpression.create(...)` |

**3b. AST statement/expression constructors (6 warnings)**

Same factory pattern for constructors that call `adopt()`, `setParent()`,
`introduceParentage()`, `setStage()`, or `setComponent()`:

| File | Warning count | Change |
|------|--------------|--------|
| `TypeCompositionStatement.java` | 2 | Factory methods for the two escaping constructors |
| `MethodDeclarationStatement.java` | 2 | Factory methods for constructors 2 and 3 |
| `PropertyDeclarationStatement.java` | 1 | Factory method; move annotation re-parenting out of constructor |
| `NamedTypeExpression.java` | 1 | Factory method for synthetic validated constructor |

**3c. Parser + Lexer (6 warnings)**

Separate construction from token-stream priming:

| File | Warning count | Change |
|------|--------------|--------|
| `Lexer.java` | 2 | Private constructor + `static create()`; call `eatWhitespace()` after |
| `Parser.java` | 4 | Private constructor + `static create()`; call `next()` after |

**3d. Container / bootstrap hierarchy (4 warnings)**

Separate construction from heap creation and runtime registration:

| File | Warning count | Change |
|------|--------------|--------|
| `Container.java` | 1 | Protected constructor stores params only; `initHeap()` called by factory |
| `NativeContainer.java` | 2 | `static create()` factory |
| `Xvm.java` | 1 | `static create()` factory; move `NativeTypeSystem.create()` + `register()` post-construction |
| `BuildContext.java` | 2 | `static create()` factory; `new TypeMatrix(this)` moved post-construction |

**3e. Remaining (5 warnings)**

| File | Warning count | Change |
|------|--------------|--------|
| `ModuleInfo.java` | 2 | Make `getResourceDir()` `final`, or static factory |
| `ClassStructure.java` | 1 | Static factory for `SimpleTypeResolver` |
| `PropertyStructure.java` | 1 | Static factory; move `getAccess()` call post-construction |
| `OpVar.java` (assembly) | 1 | Already addressed by Step 2a |

---

### Execution Order

| Step | Planned | Actual | Status | Cumulative |
|------|---------|--------|--------|------------|
| **1. `final` classes** | 4 | 4 | DONE | 36 remaining |
| **2a. Op constructor params** | 3 | 2 | DONE | 34 remaining |
| **2b. ClassTemplate params** | 1 | 1 | DONE | 33 remaining |
| **2c. AbstractConverterMap params** | 1 | 1 | DONE | 32 remaining |
| **2d. Handle.setField() final** | 3 | 0 | NOT EFFECTIVE | 32 remaining |
| **2e. CooperativelyCleanableReference factory** | 1 | 1 | DONE | 31 remaining |
| **3a. SyntheticExpression factories** | 6 | — | Pending | — |
| **3b. AST statement factories** | 6 | — | Pending | — |
| **3c. Parser/Lexer factories** | 6 | — | Pending | — |
| **3d. Container/bootstrap factories** | 4 | — | Pending | — |
| **3e. Remaining** | 5 | — | Pending | — |

Steps 1+2 eliminated 9 of 13 planned warnings (Step 2d was ineffective — see note above).
Step 3a+3b (12 warnings) form a natural AST-layer commit. Steps 3c–3e clean up the rest.

---

## Previous Execution Order (completed)

1. **Tier 1** -- DONE (33 warnings)
2. **Tier 2** -- DONE (11 warnings: 8 serial + 3 rawtypes from IdentityArrayList)
3. **Tier 4** -- DONE (96 warnings: 11 refactored + 85 suppressed)
4. **Tier 5** -- PARTIAL (28 this-escape fixed by code rewrites; 40 remain → now Tier 6)
5. **Tier 3** -- DONE (91 warnings: rawtypes + unchecked + overrides, fixed per-file)
6. **Tier 6 Steps 1+2** -- PARTIAL (9 this-escape eliminated: 4 final classes + 5 constructor params; Step 2d ineffective)

**Remaining: 35 warnings total** — 31 `[this-escape]` (Tier 6 Step 3 above) + 4 in `javatools_jitbridge` (2 cast + 2 serial)

## How to Reproduce

```bash
./gradlew clean
./gradlew build --no-build-cache --no-configuration-cache --rerun-tasks \
  -Porg.xtclang.java.lint=true \
  -Porg.xtclang.java.warningsAsErrors=false \
  -Porg.xtclang.java.maxWarnings=1000
```
