# Modern Java Syntax Audit

Date: 2026-08-22

Scope: documentation-only scan for old Java scaffolding that can be shortened with
modern Java syntax while preserving behavior, allocation shape, and the current
reentrancy work.

The build targets the current toolchain level:

- `version.properties:26` sets `org.xtclang.java.jdk=25`.
- `build-logic/common-plugins/src/main/kotlin/org.xtclang.build.java.gradle.kts:166`
  wires `JavaCompile.options.release` to that version.

That makes pattern matching for `instanceof`, lambdas, method references,
`Stream.toList()`, and `Collection.toArray(IntFunction<T[]>)` available. This
audit still treats those as readability tools, not as mechanical rewrites.

## Scope Commands

Representative scans used for this audit:

```bash
rg --files -g '*.java'
rg -n "instanceof [A-Za-z0-9_.$<>]+\)" -g '*.java' javatools javatools_utils plugin
rg -n "for \(Iterator<|for \(Iterator |\.iterator\(\)" -g '*.java' javatools javatools_utils plugin
rg -n "new (Predicate|Consumer|Function|Supplier|Comparator|Runnable|Callable)<" -g '*.java' javatools/src/main/java javatools_utils/src/main/java plugin/src/main/java
rg -n "toArray\(new .*\\[0\\]\)|Collectors\\.toList\\(\\)" -g '*.java' javatools/src/main/java javatools_utils/src/main/java plugin/src/main/java
```

## Priority Summary

| Priority | Category | Recommendation |
| --- | --- | --- |
| Must | Owner-safe array rewrite loops | Prefer `Arrays.setAll` for in-place array element replacement only when it does not run from an owner constructor or capture partially constructed `this`. |
| Should | Adjacent `instanceof` plus cast pairs | Convert to pattern variables in touched code when it removes repeated casts and does not obscure negated or compound conditions. |
| Should | Simple iterator pruning | Use `removeIf` or a small predicate helper for same-collection pruning; keep imperative iterators when there are side effects, early exits, or cross-map mutations. |
| Should | Typed `toArray` and stream terminal cleanup | Use `toArray(T[]::new)` and possibly `Stream.toList()` in cold/readability code after checking mutability semantics. |
| Backlog | Recursive anonymous functional classes | Do not convert blindly; many use anonymous-class `this` as the recursive visitor or transformer. Extract a named helper only if the call site is already being refactored. |
| Backlog | Repeated chain/builder scaffolding | Add small local helpers where repeated `new MethodBody(...)` or array append scaffolding hides intent. |
| Backlog | Manual loops as streams | Convert only cold, declarative queries; keep hot chain scans and mutation loops imperative unless profiling and readability both support the change. |
| Backlog | Needless temporaries | Inline single-use locals only when it improves local clarity and does not remove useful debugging names. |

## Must: Owner-Safe Array Rewrite Loops

The clearest modernized pattern in the active reentrancy area is an in-place
array rewrite after constructing the owner. Current examples:

- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:73`
- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:81`
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:86`
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:100`

Manual rewrite loop:

```java
MethodBody[] aOwned = info.m_aBody;
for (int i = 0, cBodies = aOwned.length; i < cBodies; ++i) {
    aOwned[i] = aOwned[i].forMethod(info);
}
```

Recommended shape:

```java
Arrays.setAll(info.m_aBody, i -> info.m_aBody[i].forMethod(info));
```

Use this pattern when all of these are true:

- the array has already been cloned or allocated for the target owner;
- the lambda captures the fully constructed owner variable, not constructor
  `this`;
- the body expression is a pure element replacement with no control-flow side
  effects;
- the array is not concurrently visible while it is being rewritten.

The current `MethodInfo.create(...)` and `PropertyInfo.create(...)` factory
shape satisfies that: the owner constructor finishes first, then the factory
attaches per-owner body copies before publishing the result.

Constructor caveat: do not modernize to lambdas inside constructors when that
captures `this` and triggers or creates constructor escape. The real
counterexample is `javatools_utils/src/main/java/org/xvm/util/ListSet.java:44`,
where `ListSet(Collection)` calls `addAllInternal(that)` at line 46. That helper
uses a plain enhanced `for` loop at line 167. Replacing it with
`that.forEach(this::addElement)` inside the constructor would capture the
partially constructed `ListSet` and undo the constructor-escape hardening that
the helper currently preserves.

## Should: Pattern Matching For `instanceof`

The codebase already uses pattern variables in newer code, for example
`javatools/src/main/java/org/xvm/asm/Annotation.java:247` and many runtime
ownership diagnostics sites. Remaining adjacent `instanceof` plus cast pairs are
good candidates when touched:

- `javatools/src/main/java/org/xvm/asm/constants/VersionedCondition.java:128`
  and `:130`
- `javatools/src/main/java/org/xvm/asm/constants/ThisClassConstant.java:111`
  and `:114`
- `javatools/src/main/java/org/xvm/asm/constants/NativeRebaseConstant.java:72`
  and `:75`
- `javatools/src/main/java/org/xvm/asm/constants/AnyCondition.java:83` and
  `:87`
- `javatools_utils/src/main/java/org/xvm/util/ListSet.java:310` and `:542`

Example transformation:

```java
if (that instanceof VersionedCondition) {
    Version verThat = ((VersionedCondition) that).m_constVer.getVersion();
    ...
}
```

becomes:

```java
if (that instanceof VersionedCondition condition) {
    Version verThat = condition.m_constVer.getVersion();
    ...
}
```

For negated comparisons:

```java
if (!(that instanceof ThisClassConstant)) {
    return -1;
}
return m_constClass.compareTo(((ThisClassConstant) that).m_constClass);
```

becomes:

```java
if (!(that instanceof ThisClassConstant constant)) {
    return -1;
}
return m_constClass.compareTo(constant.m_constClass);
```

Risk: pattern variables can make dense compound boolean expressions harder to
read. Avoid changes where the current code is only testing a marker type and
does not need the cast, or where introducing a pattern variable forces awkward
scope restructuring.

## Should: Simple Iterator Pruning

The repo already uses `removeIf` in places:

- `javatools/src/main/java/org/xvm/tool/LauncherOptions.java:1438`
- `javatools/src/main/java/org/xvm/compiler/ast/Context.java:472`

That makes it a reasonable style for simple pruning. Candidate:

- `javatools/src/main/java/org/xvm/compiler/ast/AstNode.java:1687`

Current shape prunes empty dump categories through an iterator and repeated
`iter.remove()` calls. A clearer modern shape would be:

```java
cats.entrySet().removeIf(entry -> isEmptyDumpCategory(entry.getValue()));
```

with a small private helper using the existing pattern switch:

```java
private static boolean isEmptyDumpCategory(Object value) {
    return switch (value) {
    case null -> true;
    case Map<?, ?> map -> map.isEmpty();
    case Collection<?> coll -> coll.isEmpty();
    case Object[] array -> array.length == 0;
    default -> false;
    };
}
```

Do not apply this mechanically. Keep the imperative form for examples such as:

- `javatools/src/main/java/org/xvm/compiler/ast/Context.java:1769`, which removes
  from the local name map and also updates definite assignments and a branch
  context.
- `javatools/src/main/java/org/xvm/compiler/ast/WhileStatement.java:361`, which
  removes discarded continues but also merges surviving assignment/narrowing
  state.
- `javatools/src/main/java/org/xvm/runtime/DebugConsole.java:143`, which has
  early exit and one-time breakpoint behavior.

## Should: Typed Array And Stream Terminals

Typed `Collection.toArray` is available and removes the magic zero-length array
argument:

- `javatools_utils/src/main/java/org/xvm/util/ConstOrdinalList.java:388`
- `javatools_utils/src/main/java/org/xvm/util/ConstBitSet.java:431`

Candidate transformation:

```java
Node[] aNode = list.toArray(new Node[0]);
```

to:

```java
Node[] aNode = list.toArray(Node[]::new);
```

This is a readability-level change, not a correctness fix. It is best applied
only when the surrounding code is already being touched.

`Stream.toList()` is also available, but it returns an unmodifiable list. The
candidate at `javatools/src/main/java/org/xvm/asm/MultiMethodStructure.java:186`
currently uses `collect(Collectors.toList())` while returning
`Collection<? extends Component>`. It can become:

```java
methods().stream()
        .filter(method -> !method.isTransient())
        .toList()
```

only after confirming callers do not mutate the returned collection. If a
mutable `ArrayList` is part of the contract, keep `Collectors.toList()` or use
an explicit `Collectors.toCollection(ArrayList::new)`.

## Backlog: Recursive Anonymous Functional Classes

Anonymous classes are not automatically obsolete here. Several current
`Function` and `Consumer` implementations rely on anonymous-class `this` to
recurse through `replaceUnderlying(...)` or `forEachUnderlying(...)`:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:295` and
  `:303`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:365` and
  `:372`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:663` and
  `:673`
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:1240` and
  `:1243`
- `javatools/src/main/java/org/xvm/runtime/ClassTemplate.java:267` and `:271`
- `javatools/src/main/java/org/xvm/asm/ClassStructure.java:363` and `:376`
- `javatools/src/main/java/org/xvm/asm/constants/TypedefConstant.java:68` and
  `:84`

In a lambda, `this` would mean the enclosing object, not the visitor or
transformer. A direct anonymous-class-to-lambda conversion would therefore be
wrong. Reasonable modernizations are:

- extract a named private helper when the traversal is reused or too long;
- introduce a small local holder only if it is clearly simpler than the
  anonymous class;
- leave the anonymous class alone when it is the most explicit way to express
  recursive self-use.

## Backlog: Repeated Chain And Builder Scaffolding

`PropertyInfo.augmentPropertyChain(...)` has repeated method-body construction
and array append scaffolding:

- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:1366`
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:1371`
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:1378`
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:1390`
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:1407`
- `javatools/src/main/java/org/xvm/asm/constants/PropertyInfo.java:1408`

This is a candidate for small local helpers, for example `fieldBody(idMethod,
identity)`, `singletonChain(body)`, or `appendBody(chain, body)`. The goal would
be to make the property-chain policy visible and hide only the repetitive
array mechanics.

Risk: this is branch-sensitive logic with subtle ordering semantics. Do not
extract a generic builder unless tests cover native, field, injected, default,
and custom-logic paths. For a narrow cleanup, `Arrays.copyOf(chain, cBodies + 1)`
can replace the explicit `new MethodBody[...]` plus `System.arraycopy(...)`
without changing chain ordering.

## Backlog: Manual Loops As Streams

Small declarative queries can be shorter as streams, but hot path loops should
not be modernized just to use streams.

Candidate family:

- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:639`
- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:651`
- `javatools/src/main/java/org/xvm/asm/constants/MethodInfo.java:665`

For cold code, these could become:

```java
return Arrays.stream(m_aBody).anyMatch(body -> id.equals(body.getIdentity()));
return Arrays.stream(m_aBody).anyMatch(that::equals);
return Arrays.stream(m_aBody).anyMatch(match);
```

For `MethodInfo`, the existing imperative loops are probably the right default:
method-chain scans are core compiler/runtime structure code, the loops allocate
nothing, and the current form has straightforward control flow.

## Backlog: Needless Temporaries

Some locals are pure return scaffolding. Example:

- `javatools/src/main/java/org/xvm/asm/OpInPlaceAssign.java:228`
- `javatools/src/main/java/org/xvm/asm/OpInPlaceAssign.java:229`

Candidate transformation:

```java
MethodInfo method = bctx.getTypeInfo(typeTarget).findOpMethod(sName, sOp, typeArg);
return method;
```

to:

```java
return bctx.getTypeInfo(typeTarget).findOpMethod(sName, sOp, typeArg);
```

This belongs in opportunistic cleanup only. Keep named locals when they aid
debugging, make aligned declarations easier to scan, or anchor assertions/logging
nearby.

## When Not To Modernize

Do not modernize when the new form changes any of these properties:

- constructor escape behavior, especially lambdas or method references that
  capture `this` inside constructors;
- owner or pool attachment ordering;
- mutability of returned collections, especially `Collectors.toList()` versus
  `Stream.toList()`;
- allocation profile in hot runtime/compiler loops;
- recursive anonymous-class `this` semantics;
- iterator behavior with early exits, `iter.remove()`, or side effects on other
  maps/sets;
- generated bridge/stub code whose structure is produced by tooling rather than
  maintained by hand.

The best cleanup policy for this codebase is opportunistic: apply these
modernizations in touched code where the new shape makes the ownership and
control flow easier to read, and avoid broad syntax churn in runtime hot paths.
