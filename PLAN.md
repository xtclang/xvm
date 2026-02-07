# Raw Types & Unchecked Warnings Elimination Plan

Remaining warnings after the ConstantPool/VersionTree generics cleanup.
Compiled with `-Xlint:all` (`-Porg.xtclang.java.lint=true`).

**Total: 347 warnings (189 raw/unchecked, 70 fallthrough, 50 this-escape, 38 other)**

This plan focuses on the 189 `[rawtypes]` + `[unchecked]` warnings. The fallthrough/this-escape/serial/etc.
warnings are real but lower priority and mostly stylistic.

---

## 1. CompletableFuture used raw (HARD)

**23 warnings across 5 files:**
- `Fiber.java` (10)
- `ServiceContext.java` (5+)
- `Frame.java` (4)
- `xFuture.java` (3)
- `xOSFile.java` (4)
- `ObjectHandle.java` (2)

**What's going wrong:** `CompletableFuture` is used without a type parameter throughout the async
runtime. Methods like `Frame.assignFutureResult()`, `Frame.createWaitFrame()`, and
`ServiceContext.Response` all traffic in raw `CompletableFuture` instead of
`CompletableFuture<ObjectHandle>` (or whatever the actual payload type is).

**Why it's hard:** This is the core async plumbing. The futures carry `ObjectHandle` values but also
get completed with exceptions, and some futures are "void" (completed with null). The raw usage is
deeply woven into the fiber scheduling, service invocation, and I/O callback mechanisms. Changing
`CompletableFuture` to `CompletableFuture<ObjectHandle>` would cascade through:
- `Fiber.waitForFuture()`, `Fiber.registerRequest()`, `Fiber.resume()`
- `Frame.assignFutureResult()`, `Frame.createWaitFrame()`, `Frame.createWaitIOFrame()`
- `ServiceContext.Response`, `ServiceContext.sendXxxRequest()`
- All native templates that create async handles (`xFuture`, `xOSFile`)

**Approach:** Parameterize `CompletableFuture<ObjectHandle>` everywhere. The `Response` inner class
in `ServiceContext` also needs a type parameter. Start from `Frame` (the leaf), work up through
`Fiber` and `ServiceContext`.

---

## 2. TypeInfo constructor takes raw ListMap parameters (MEDIUM)

**~15 warnings across 6 files:**
- `TypeConstant.java` (4)
- `ConstantPool.java` (4)
- `RelationalTypeConstant.java` (3)
- `PropertyClassTypeConstant.java` (3)
- `UnionTypeConstant.java` (1)
- `IntersectionTypeConstant.java` (2)
- `DifferenceTypeConstant.java` (1)

**What's going wrong:** The `TypeInfo` constructor has a massive parameter list including
`ListMap<...>` parameters. Callers pass `ListMap.EMPTY` (which is a raw `ListMap`) or construct
`ListMap` instances without full generic types. The constructor itself may accept some parameters
as raw or wildcard types.

**Why it's medium:** `TypeInfo` is a data carrier — its constructor signature is large but stable.
The fix is mechanical: properly type every `ListMap.EMPTY` usage (the `empty()` method was already
added to `ListMap` as a typed alternative), and ensure all constructor invocations use fully
parameterized types.

**Approach:** Audit the `TypeInfo` constructor. Replace all `ListMap.EMPTY` → `ListMap.empty()` at
call sites. Fix any remaining raw `ListMap` or `Map` parameter passing.

---

## 3. ServiceContext raw Enum/EnumMap/WeakReference for op metadata (MEDIUM)

**~12 warnings in `ServiceContext.java`:**
- `setOpInfo()` / `getOpInfo()` use raw `Enum`, `EnumMap`, `WeakReference`

**What's going wrong:** `ServiceContext.setOpInfo(Op, Enum, Object)` stores arbitrary op metadata
using a `Map<Op, EnumMap>` where the `EnumMap` is raw. The method accepts any `Enum` as key and
any `Object` as value, so it's inherently untyped.

**Why it's medium:** The interface is intentionally loose (different ops store different enum-keyed
data). Adding generics to `EnumMap<? extends Enum<?>, Object>` or introducing a small typed wrapper
is straightforward but needs care to preserve the flexibility.

**Approach:** Parameterize as `EnumMap<?, Object>` with appropriate wildcards, or introduce a
dedicated `OpMetadata` holder class. The `WeakReference<Op>` is trivially fixable.

---

## 4. LinkedIterator used raw (SIMPLE)

**9 warnings across 4 files:**
- `ClassStructure.java` (3)
- `MethodStructure.java` (3)
- `FileStructure.java` (3)
- `PropertyStructure.java` (3)

**What's going wrong:** `LinkedIterator` is already generic (`LinkedIterator<E>`) but is
instantiated raw at all call sites: `new LinkedIterator(iter1, iter2)`.

**Fix:** Change to `new LinkedIterator<>(iter1, iter2)` at each call site. Pure mechanical fix.

---

## 5. AstNode reflective child walking uses raw collections (MEDIUM-HARD)

**~18 warnings in `AstNode.java`:**

**What's going wrong:** `AstNode` has a generic child-walking framework that uses reflection to find
List/Map/Collection fields on subclasses. The methods `childForEach()`, `replaceChild()`, etc.
operate on `Object` fields, then test `instanceof List`, `instanceof Map`, etc. and iterate over
them raw.

**Why it's medium-hard:** The design is inherently reflective — the code walks arbitrary `List<?>`,
`Map<?,?>`, `Collection<?>` fields without knowing their element types at compile time. Adding
wildcards (`List<?>`) would eliminate the raw type warnings but might require unchecked casts at
boundary points where elements are passed to typed callbacks.

**Approach:** Use `List<?>`, `Map<?,?>`, `Collection<?>` wildcards in the instanceof checks and
local variables. Accept `@SuppressWarnings("unchecked")` at the few points where elements must be
cast to `AstNode`.

---

## 6. Component.unlinkSibling takes raw Map (SIMPLE)

**3 warnings in `Component.java`:**

**What's going wrong:** `unlinkSibling(Map kids, Object id, ...)` declares `Map` without type
parameters.

**Fix:** Change to `Map<?, ?>` or the actual types (likely `Map<Object, Component>`). The `put()`
calls inside need corresponding type alignment.

---

## 7. ClassStructure.equals uses raw Map (SIMPLE)

**2 warnings in `ClassStructure.java:3606-3607`:**

**What's going wrong:** `Map mapThisParams = this.m_mapParams` — the field is
`ListMap<StringConstant, TypeConstant>` but the local is declared as raw `Map`.

**Fix:** Declare the local with the proper type.

---

## 8. ClassStructure.getTypeParams returns ListMap.EMPTY (SIMPLE)

**1 warning in `ClassStructure.java:552`:**

**What's going wrong:** Returns `ListMap.EMPTY` (raw) instead of `ListMap.empty()` (typed).

**Fix:** Use `ListMap.empty()`.

---

## 9. Parser uses raw List for composition lists (SIMPLE)

**~10 warnings in `Parser.java`:**

**What's going wrong:** Several parser methods declare `List` without type parameters when building
lists of `CompositionNode` or similar AST elements.

**Fix:** Add the proper type parameter to each `List` declaration.

---

## 10. CaseManager / SwitchStatement raw Comparable (SIMPLE)

**6 warnings in `CaseManager.java`, 2 in `SwitchStatement.java`:**

**What's going wrong:** `CaseManager` is generic but `SwitchStatement` uses it raw. `Comparable`
comparisons inside `CaseManager` are done without type parameters.

**Fix:** Parameterize `CaseManager<?>` in `SwitchStatement` and use `Comparable<?>` with unchecked
suppress at the comparison point.

---

## 11. NativeContainer uses raw Class (SIMPLE)

**5 warnings in `NativeContainer.java`:**

**What's going wrong:** `Class` used without `<?>` wildcard in template registration.

**Fix:** `Class<?>` or `Class<? extends ClassTemplate>` as appropriate.

---

## 12. Various one-off raw types (SIMPLE)

Scattered across:
- `BinaryAST.java` — raw `HashSet` (2 warnings)
- `MethodStructure.java` — raw `HashSet` (2 warnings)
- `LambdaExpression.java` — raw `List` (1 warning)
- `NewExpression.java` — raw `List` (1 warning)
- `Context.java` — raw `Map` (1 warning)
- `TypeCompositionStatement.java` — raw `ArrayList` (1 warning)
- `Injector.java` — raw `Function` (3 warnings)
- `Ctx.java` — raw `Function` (2 warnings)
- `ModuleLoader.java` — raw `Class` (1 warning)
- `xOSStorage.java` — raw `WatchEvent`/`Kind` (2 warnings)
- `Utils.java` — raw `Predicate` (1 warning)
- `TypeConstant.java` — raw `TransientThreadLocal` (1 warning)
- `TypeInfo.java` — raw `Entry` (2 warnings)
- `ListMap.java` — raw `EMPTY` field, unchecked casts (4 warnings)
- `ListSet.java` — unchecked cast in `toExternal()` (1 warning)
- `TransientThreadLocal.java` — unavoidable unchecked casts from `Object` (4 warnings)
- `CooperativelyCleanableReference.java` — raw `ReferenceQueue` array (1 warning)

Most are trivial wildcard additions. A few (ListSet, TransientThreadLocal) require
`@SuppressWarnings("unchecked")` because Java generics simply can't express the invariant.

---

## Recommended Execution Order

| Priority | Item | Warnings | Difficulty |
|----------|------|----------|------------|
| 1 | LinkedIterator raw usage (#4) | 9 | Simple — add `<>` diamond |
| 2 | ClassStructure / ListMap.EMPTY (#7, #8) | 3 | Simple — use typed locals |
| 3 | Component.unlinkSibling (#6) | 3 | Simple — add type params |
| 4 | Parser raw List (#9) | 10 | Simple — add type params |
| 5 | NativeContainer raw Class (#11) | 5 | Simple — add `<?>` |
| 6 | One-off raw types (#12) | ~25 | Simple — mechanical |
| 7 | CaseManager/SwitchStatement (#10) | 8 | Simple |
| 8 | TypeInfo constructor (#2) | 15 | Medium — large constructor |
| 9 | ServiceContext op metadata (#3) | 12 | Medium — design decision |
| 10 | AstNode reflective walking (#5) | 18 | Medium-hard — inherently untyped |
| 11 | CompletableFuture async plumbing (#1) | 23 | Hard — core async runtime |

Items 1-7 are purely mechanical and can be done in one pass (~63 warnings).
Items 8-10 need some design thought (~45 warnings).
Item 11 is a significant refactor of the async runtime (~23 warnings).

The remaining ~58 non-raw warnings (redundant casts, this-escape, fallthrough, serial, try, overrides,
static) are separate cleanup tasks.
