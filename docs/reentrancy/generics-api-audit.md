# Generics And Typed API Audit

This document records a separate design smell found while cleaning up runtime
ownership: many older APIs use raw types, broad `Object` returns, and caller
casts where a typed boundary would have made ownership and payload type visible.

This is not the same severity as mutable `INSTANCE` fields or wrong
`ConstantPool` ownership. A cast is not automatically a race. The problem is
that erased APIs make incorrect owner/type combinations compile and fail later,
often far away from the call that selected the wrong owner.

## Current Scan Signals

These are broad source-shape signals, not exact bug counts:

```bash
rg -n --pcre2 "\([A-Z][A-Za-z0-9_$.]*(?:<[^\n()]*>)?\)\s*[A-Za-z_$][A-Za-z0-9_$.]*(?:\.|\[|\)|;)" \
  javatools/src/main/java/org/xvm | wc -l
```

Result on branch `lagergren/lazy-instance`: about 2,659 explicit casts in
`javatools/src/main/java/org/xvm`.

```bash
rg -n --pcre2 "^\s*(?:public|protected|private|static|final|transient|volatile|abstract|synchronized|\s)*\b(?:List|Map|Set|Iterator|Enumeration|Comparable|Class)\s+[A-Za-z_]" \
  javatools/src/main/java/org/xvm | wc -l
```

Result on branch `lagergren/lazy-instance`: 12 obvious raw declaration sites.

The explicit-cast count includes valid parser, AST, constant, and pattern-match
downcasts. It is still a useful signal: the code base routinely asks callers to
remember the concrete type returned by an owner-sensitive operation instead of
letting the Java type system carry that fact.

## Why This Matters For Reentrancy

Owner-safe code has two questions:

- Which owner produced this value?
- Which concrete type is this value expected to have?

Raw or weakly typed APIs hide both. For example:

```java
ClassTemplate template = container.getTemplate(type);
xEnum enumTemplate = (xEnum) template;
```

The reviewer has to verify by hand that `type` resolves to the expected
template and that `container` is the right owner. The typed form is better:

```java
xEnum enumTemplate = container.getTemplate(type, xEnum.class);
```

It still performs a runtime check, but the check is centralized at the owner
boundary and the call site declares the expected result type.

The same applies to constant handles. This branch already has a typed helper:

```java
ArrayHandle handle = container.getConstHeap()
        .getConstHandle(container, constant, ArrayHandle.class);
```

That is preferable to:

```java
ArrayHandle handle = (ArrayHandle) container.getConstHeap()
        .getConstHandle(container, constant);
```

The typed helper does not magically make the value correct, but it makes the
owner boundary explicit and moves the cast into the API that can attach better
diagnostics later.

## Bad Single-Threaded Design, Not Just Parallel Risk

The lack of generics is not merely inconvenient modern-Java style debt. It
creates hidden preconditions even in single-threaded code:

- A caller can fetch a value from the wrong owner and only discover it when a
  later cast or field access fails.
- A broad `Object` return can represent several lifecycle states, forcing every
  caller to know which state is legal.
- Raw collections allow unrelated payload types to be inserted into owner-local
  caches. The failure then appears during iteration, not insertion.
- Suppressed unchecked casts make it harder to tell whether the unsafe edge is
  a deliberate serialization bridge, a JIT boundary, or an accidental shortcut.
- APIs returning base types encourage repeated scattered casts instead of one
  checked, documented owner boundary.

These problems were cheap to avoid when the APIs were first written. A
generic accessor, typed key, or small result record is often less code than the
caller-side cast soup it replaces.

## Existing Good Patterns

The tree already has examples of the right direction:

```java
public <T extends ClassTemplate> T getTemplate(TypeConstant type, Class<T> clzTemplate) {
    return clzTemplate.cast(getTemplate(type));
}
```

```java
public <H extends ObjectHandle> H getConstHandle(
        Container container, Constant constValue, Class<H> clzHandle) {
    ObjectHandle hValue = getConstHandle(container, constValue);
    return hValue == null ? null : clzHandle.cast(hValue);
}
```

```java
private <T extends ClassTemplate> T get(NativeTemplateRef<T> ref) {
    return ref.cast((ClassTemplate) lazy.get(this));
}
```

The last example still contains a cast at the internal lazy boundary, but the
public API is typed by `NativeTemplateRef<T>`. That is the right compromise:
one checked cast in the owner table, not repeated casts at every runtime call
site.

## Concrete Audit Findings

The argument for generics here is not "faster bytecode". Java erasure means many
of these replacements produce similar bytecode. That is irrelevant to the
problem. The missing value is at source level: compile-time contracts, shorter
call sites, one checked owner boundary, and impossible state combinations that
cannot be written.

The examples below are representative, not exhaustive. They were selected
because the API shape itself forces casts, hides the owner/type contract, or
encodes a closed set of states as `Object`.

Use this section as a reviewer-facing source-level argument. A response such as
"the bytecode is the same after erasure" misses the point. These APIs are bad
because the source permits impossible calls, forces humans to remember hidden
contracts, and moves owner/type validation away from the boundary that has the
context to diagnose it. The proposed replacements are valuable even if the JVM
instructions are similar: they make wrong code fail at compile time, make
callers shorter, and make later refactors local instead of search-and-cast
exercises.

Each finding also includes split guidance:

- `Safe separate PR` means the change is mostly API cleanup with focused tests
  and should not be bundled into the current constant-adoption work.
- `Separate PR, high priority` means the topic is runtime/owner-sensitive and
  should be fixed independently, but still not mixed with unrelated source/test
  changes.
- `Needs design PR` means the right fix changes a state model or public-ish
  internal contract and should start with a narrow design/adapter commit.

### Must Fix: Service Responses Erase Future Payload Shape

Examples:

- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:1322` stores an
  `OpRequest` future as `CompletableFuture<ObjectHandle[]>`.
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:1328` then casts
  that future to raw `CompletableFuture` for an ignored-return path.
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:1620` accepts a
  raw `CompletableFuture future` in `Message.sendResponse(...)`.
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:1679` stores
  `Message.f_future` as raw `CompletableFuture`.
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:2113` stores
  responses in raw `Queue<Response>`.

Why it is bad:

The code knows there are distinct response payloads: zero/ignored return, one
`ObjectHandle`, tuple-as-one-handle, and N `ObjectHandle[]` returns. The current
API erases that distinction, then rebuilds it with `cReturns`, switches, raw
futures, and casts. A future carrying `ObjectHandle[]` can be passed through a
raw path that is later treated as a single `ObjectHandle`. If that mistake is
made, the failure is a late `ClassCastException` or a confusing future
completion failure in another service/fiber.

Before:

```java
CompletableFuture<ObjectHandle[]> future = request.f_future;
return frame.assignFutureResult(Op.A_IGNORE, (CompletableFuture) future);

protected void sendResponse(Fiber fiberCaller, Frame frame,
        CompletableFuture future, int cReturns) {
    // switch on cReturns and complete future with different payload shapes
}
```

After sketch:

```java
sealed interface ServiceReturn permits IgnoredReturn, SingleReturn,
        TupleReturn, MultiReturn {}

record IgnoredReturn(ObjectHandle emptyTuple) implements ServiceReturn {}
record SingleReturn(ObjectHandle value) implements ServiceReturn {}
record TupleReturn(TupleHandle tuple) implements ServiceReturn {}
record MultiReturn(ObjectHandle[] values) implements ServiceReturn {}

abstract static class Message<R extends ServiceReturn> {
    final CompletableFuture<R> future = new CompletableFuture<>();
    abstract ReturnShape<R> returnShape();
}
```

Or, if that is too large for a first pass, split the current API:

```java
final CompletableFuture<ObjectHandle> oneFuture;
final CompletableFuture<ObjectHandle[]> manyFuture;

sendOneResponse(..., CompletableFuture<ObjectHandle> future);
sendManyResponse(..., CompletableFuture<ObjectHandle[]> future);
```

Classification: must-fix runtime/reentrancy risk. This crosses service and
fiber boundaries. A typed design would make a single-handle response impossible
to complete with `ObjectHandle[]` without an explicit conversion method.

PR split: separate PR, high priority. This should not be mixed with constant
adoption changes; it needs service/future tests that exercise zero, one, tuple,
and multi-return paths.

### Must Fix: Op-Info Cache Uses Object Values And Raw Enum Keys

Examples:

- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:226` returns
  `Object` from `getOpInfo(Op, Enum)`.
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:227` uses raw
  `EnumMap`.
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:231` casts the
  map value to raw `WeakReference`.
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:242` accepts
  `Object info` in `setOpInfo(...)`.
- `javatools/src/main/java/org/xvm/runtime/ServiceContext.java:2179` stores the
  cache as `Map<Op, EnumMap>`.

Representative callers:

- `javatools/src/main/java/org/xvm/asm/OpCallable.java:198` casts category
  `Constructor` to `MethodStructure`.
- `javatools/src/main/java/org/xvm/asm/OpCallable.java:200` casts category
  `TargetClass` to `IdentityConstant`.
- `javatools/src/main/java/org/xvm/asm/OpCallable.java:430` casts category
  `TargetType` to `TypeConstant`.
- `javatools/src/main/java/org/xvm/asm/OpInvocable.java:131` casts category
  `Chain` to `CallChain`.
- `javatools/src/main/java/org/xvm/asm/OpVar.java:155` casts category
  `Composition` to `TypeComposition`.

Why it is bad:

The `Category` enum names the intended payload, but Java cannot verify the
association. The API allows `setOpInfo(op, Category.TargetClass, constructor)`
and `getOpInfo(op, Category.TargetClass)` as an `IdentityConstant` to compile.
That is exactly the kind of owner/type bug that does not appear until a hot
runtime path reuses cached metadata.

Before:

```java
MethodStructure constructor =
        (MethodStructure) context.getOpInfo(this, Category.Constructor);
IdentityConstant idParent =
        (IdentityConstant) context.getOpInfo(this, Category.TargetClass);
```

After sketch:

```java
record OpInfoKey<T>(Enum<?> category, Class<T> type) {}

static final OpInfoKey<MethodStructure> CONSTRUCTOR =
        new OpInfoKey<>(Category.Constructor, MethodStructure.class);
static final OpInfoKey<IdentityConstant> TARGET_CLASS =
        new OpInfoKey<>(Category.TargetClass, IdentityConstant.class);

MethodStructure constructor = context.getOpInfo(this, CONSTRUCTOR);
IdentityConstant idParent  = context.getOpInfo(this, TARGET_CLASS);
```

The setter then becomes:

```java
public <T> void setOpInfo(Op op, OpInfoKey<T> key, T info)
```

Classification: must-fix runtime/reentrancy risk. The cache is service-local,
but it stores owner-bearing runtime metadata for ops. A typed key would prevent
wrong category/value pairings at compile time and centralize any unavoidable
runtime check.

PR split: separate PR, high priority. This is a good typed-cache migration on
its own because the first compatibility layer can keep the existing storage
while changing call sites to `OpInfoKey<T>`.

### Must Fix: Fiber Pending Requests Encode A Union As Object

Examples:

- `javatools/src/main/java/org/xvm/runtime/Fiber.java:358` reads
  `m_oPendingRequests` as `Object`.
- `javatools/src/main/java/org/xvm/runtime/Fiber.java:367` casts it to
  `Map<CompletableFuture, Message>`.
- `javatools/src/main/java/org/xvm/runtime/Fiber.java:381` casts it again while
  removing a request.
- `javatools/src/main/java/org/xvm/runtime/Fiber.java:520` casts it a third
  time while reporting waits.
- `javatools/src/main/java/org/xvm/runtime/Fiber.java:698` documents the field
  as `Message | Map<CompletableFuture, Message>`.

Why it is bad:

The field is a small state machine, but the compiler only sees `Object`.
Every reader must manually preserve the hidden invariant: `null`, one
`Message`, or a `Map<CompletableFuture, Message>`. The representation is also
coupled to a micro-optimization. That may be valid, but it should not leak into
every caller as casts.

Before:

```java
Object oPending = m_oPendingRequests;
if (oPending instanceof Message requestPrev) {
    Map<CompletableFuture, Message> mapPending = new HashMap<>();
    ...
} else {
    Map<CompletableFuture, Message> mapPending =
            (Map<CompletableFuture, Message>) oPending;
}
```

After sketch:

```java
sealed interface PendingRequests permits NoPending, OnePending, ManyPending {}
record NoPending() implements PendingRequests {}
record OnePending(Message request) implements PendingRequests {}
record ManyPending(Map<CompletableFuture<?>, Message> requests)
        implements PendingRequests {}
```

Or keep the compact representation behind one helper:

```java
final class PendingRequests {
    void add(Message request);
    void remove(Message request);
    Iterable<Message> values();
}
```

Classification: must-fix runtime/reentrancy risk if service scheduling remains
multi-fiber and cross-service. The current code can compile with any object in
the field; a sealed or wrapper API would make the legal states explicit and
remove repeated unchecked map casts.

PR split: separate PR, high priority if touching service scheduling; otherwise
safe separate PR as a wrapper-only refactor with no scheduling behavior change.

### Must Fix: ConstantPool Locator Tables Erase Format/Key/Value Contracts

Examples:

- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:216` uses
  `Map<Constant, Constant>` for constants by `Format`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:217` casts lookup
  results back to the caller's `T`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:242` casts
  `constant.adoptedBy(this)` back to `T`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:543`,
  `:581`, `:697`, `:758`, `:785`, `:1631`, and `:2070` cast values out of
  `ensureLocatorLookup(format).get(...)`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:1331` exposes the
  decorated-class locator as `Map<Object, Constant>`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:3325` returns
  `Map<Object, Constant>` from `ensureLocatorLookup(Format)`.
- `javatools/src/main/java/org/xvm/asm/ConstantPool.java:4259` and `:4266`
  store the tables as `EnumMap<Format, Map<Constant, Constant>>` and
  `EnumMap<Format, Map<Object, Constant>>`.

Why it is bad:

`Format.Char` is keyed by `Character` and returns `CharConstant`.
`Format.String` is keyed by `String` and returns `StringConstant`.
`Format.TerminalType` is keyed by an identity constant and returns
`TypeConstant`. The code knows those facts, but the API collapses them all into
`Object -> Constant`. As a result, every `ensureXxxConstant` method manually
casts, and a wrong format/key pairing compiles.

Before:

```java
StringConstant constant =
        (StringConstant) ensureLocatorLookup(Format.String).get(s);
TypeConstant constType =
        (TypeConstant) ensureLocatorLookup(Format.TerminalType).get(constId);
```

After sketch:

```java
final class ConstantLookup<K, C extends Constant> {
    C get(K key);
    C put(K key, C value);
}

private final ConstantLookup<String, StringConstant> strings;
private final ConstantLookup<IdentityConstant, TypeConstant> terminalTypes;

StringConstant constant = strings.get(s);
TypeConstant constType = terminalTypes.get(constId);
```

If preserving `Format` lookup is required:

```java
record FormatKey<K, C extends Constant>(Format format, Class<K> keyType,
        Class<C> constantType) {}

static final FormatKey<String, StringConstant> STRING =
        new FormatKey<>(Format.String, String.class, StringConstant.class);

StringConstant constant = locator(STRING).get(s);
```

Classification: must-fix runtime/reentrancy risk for owner-sensitive constants.
The current generic `register(T)` helps at the outer API, but the internal
tables immediately erase the type. A typed locator table would make wrong
format/value pairings fail at compile time or at one central checked boundary.

PR split: needs design PR. This touches central constant-pool storage and should
be split from both constant adoption and runtime-template work. A first PR can
add typed locator wrappers for a few high-traffic formats while preserving the
underlying maps.

### Must Fix: Native Template Loading Uses Raw Class

**RESOLVED 2026-08-31.** `NativeContainer` now declares
`final Map<String, Class<? extends ClassTemplate>> mapTemplateClasses`, and the scan resolves each
class with `Class.forName(sClass).asSubclass(ClassTemplate.class)`. `@NativeTemplate` lets a
template declare the Ecstasy class it implements instead of having it derived from its file name,
which is what made the `xRTDelegate` split expressible.

Examples:

- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:183` creates
  `Map<String, Class>`.
- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:199` iterates
  `Map.Entry<String, Class>`.
- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:214` assigns
  `entry.getValue()` to `Class<ClassTemplate>`.
- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:244` and
  `:288` accept `Map<String, Class>`.
- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:277` returns
  raw `Class` from `classForName(...)`.
- `javatools/src/main/java/org/xvm/runtime/NativeContainer.java:795` to `:796`
  receives `Class<ClassTemplate>` for reflective construction.

Why it is bad:

This is container startup code that turns JVM classes into owner-local native
templates. The map should only contain subclasses of `ClassTemplate`, but raw
`Class` lets unrelated classes enter the map. The current failure mode would be
late reflective constructor failure, not a clear "loaded class is not a native
template" error at discovery time.

Before:

```java
Map<String, Class> mapTemplateClasses = new HashMap<>();
Class<ClassTemplate> clz = entry.getValue();
storeNativeTemplate(instantiateNativeTemplate(clz, structClass));
```

After sketch:

```java
Map<String, Class<? extends ClassTemplate>> mapTemplateClasses =
        new HashMap<>();

private static Class<? extends ClassTemplate> classForName(String sFile) {
    return Class.forName(sClz).asSubclass(ClassTemplate.class);
}

private ClassTemplate instantiateNativeTemplate(
        Class<? extends ClassTemplate> clz, ClassStructure structClass) {
    return clz.getConstructor(Container.class, ClassStructure.class)
            .newInstance(this, structClass);
}
```

Classification: must-fix runtime/reentrancy risk. The code constructs
owner-bearing templates. Using `asSubclass(ClassTemplate.class)` would reject
wrong classes at the owner boundary and remove the unchecked raw-class path.

PR split: safe separate PR. This is a narrow startup-boundary cleanup:
parameterize the maps, use `Class<? extends ClassTemplate>`, and add a native
template discovery test for a non-template class rejection if practical.

### Must Fix: TypeConstant Updaters And Recursive State Lose Generic Detail

Examples:

- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8259`
  correctly types `s_typeinfo` as `AtomicReferenceFieldUpdater<TypeConstant,
  TypeInfo>`.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8282`
  stores `m_tloInProgress` as `TransientThreadLocal<Set<TypeConstant>>`.
- `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java:8283`
  then declares the updater as
  `AtomicReferenceFieldUpdater<TypeConstant, TransientThreadLocal>`.

Why it is bad:

The in-progress set protects recursive `isA`/type-info work. That is exactly
where wrong owner/type values are painful to diagnose. The field has a generic
contract, but the updater erases it, so future edits can assign a
`TransientThreadLocal` with a different payload type and still compile.

Before:

```java
private transient volatile TransientThreadLocal<Set<TypeConstant>> m_tloInProgress;
private static final AtomicReferenceFieldUpdater<TypeConstant, TransientThreadLocal>
        s_tloInProgress = AtomicReferenceFieldUpdater.newUpdater(...);
```

After sketch:

```java
private static final class InProgressTypes
        extends TransientThreadLocal<Set<TypeConstant>> {}

private transient volatile InProgressTypes m_tloInProgress;
private static final AtomicReferenceFieldUpdater<TypeConstant, InProgressTypes>
        s_tloInProgress = AtomicReferenceFieldUpdater.newUpdater(
                TypeConstant.class, InProgressTypes.class, "m_tloInProgress");
```

Classification: must-audit, likely should-fix soon. Java's updater API often
forces awkward shapes, but the unsafe edge should be boxed into a named type or
small helper so the recursive type-state payload is not raw.

PR split: safe separate PR if limited to a named holder/helper and existing
type-recursion tests. Avoid bundling with broader `TypeConstant` relation-cache
or invalidation behavior changes.

### Should Fix Soon: JIT Reflection Erases Generated Runtime Types

Examples:

- `javatools/src/main/java/org/xvm/javajit/JitConnector.java:61` to `:63` uses
  `asSubclass(Injector.class)`. This is the correct pattern.
- `javatools/src/main/java/org/xvm/javajit/JitConnector.java:93` loads the
  generated module into raw `Class mainClass`.
- `javatools/src/main/java/org/xvm/javajit/JitConnector.java:95` stores the
  constructed module as `Object`.
- `javatools/src/main/java/org/xvm/javajit/JitConnector.java:108`, `:111`,
  `:112`, and `:121` load generated array/string/object classes into raw
  `Class` locals.
- `javatools/src/main/java/org/xvm/javajit/Ctx.java:47` to `:62` stores
  additional return values in `Object` slots and `Object[]`.
- `javatools/src/main/java/org/xvm/javajit/Ctx.java:162` returns `Object` from
  `inject(...)`.

Why it is bad:

Generated code and classloader boundaries do require reflection, but raw
`Class` and `Object` make it unclear which generated base type owns each
method. The injector load already proves the cleaner pattern: check the class
once with `asSubclass(...)`, then reflect against the typed class.

Before:

```java
Class  mainClass = loader.loadClass(typeName);
Object module    = mainClass.getDeclaredConstructor(Ctx.class).newInstance(ctx);
Class  arrayClass = loader.loadClass(Builder.N_ArrayObj);
```

After sketch:

```java
Class<? extends XvmModule> mainClass =
        loader.loadClass(typeName).asSubclass(XvmModule.class);
XvmModule module = mainClass.getDeclaredConstructor(Ctx.class).newInstance(ctx);

Class<? extends XvmArray> arrayClass =
        loader.loadClass(Builder.N_ArrayObj).asSubclass(XvmArray.class);
```

If generated classes cannot share real Java interfaces yet, introduce small
bridge interfaces for the shapes the connector reflects on: runnable module,
array factory, string constructor target, and immutable array.

Classification: should-fix soon. This is JIT/classloader work, not the
interpreter native-template PR, but it is the same source-level design problem:
the owner/type fact exists and should be checked once at the boundary.

PR split: separate JIT PR. The interpreter reentrancy work should only cite it;
the implementation belongs with generated-class bridge tests and classloader
coverage.

### Should Fix Soon: MethodBody Stores Target Variants As Object

Examples:

- `javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:552` casts
  `m_target` to `PropertyConstant` when implementation is `Delegating` or
  `Field`.
- `javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:561` returns
  narrowing nested identity as raw `Object`.
- `javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:740` casts
  `m_target` to `MethodInfo` for `FromInto` and `Implicit`.
- `javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:742` casts
  `m_target` to `MethodInfo[]` for `Union`.
- `javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:973` stores
  all variants in `private final Object m_target`.

Why it is bad:

`Implementation` and `m_target` form a closed pair, but the compiler cannot
enforce the pair. A body can be constructed with `Implementation.Union` and a
single `MethodInfo`, or `Implementation.Field` and a `MethodInfo[]`, and the
class still compiles. The crash arrives when comparison, property lookup, or
narrowing walks that body.

Before:

```java
private final Object m_target;

return switch (m_impl) {
case FromInto, Implicit ->
    methodTargetEquals((MethodInfo) this.m_target, (MethodInfo) that.m_target);
case Union ->
    unionTargetEquals((MethodInfo[]) this.m_target, (MethodInfo[]) that.m_target);
...
};
```

After sketch:

```java
sealed interface MethodTarget permits NoTarget, PropertyTarget,
        MethodInfoTarget, UnionTarget, NestedIdentityTarget {}

record NoTarget() implements MethodTarget {}
record PropertyTarget(PropertyConstant property) implements MethodTarget {}
record MethodInfoTarget(MethodInfo method) implements MethodTarget {}
record UnionTarget(MethodInfo left, MethodInfo right) implements MethodTarget {}
record NestedIdentityTarget(NestedIdentity identity) implements MethodTarget {}
```

Classification: should-fix soon. This is a closed hierarchy hiding behind
`Object` and an enum. It may not be a current reentrancy race, but it can turn
metadata construction mistakes into runtime `ClassCastException` instead of
compile-time errors.

PR split: needs design PR. Start by introducing sealed target records beside the
existing constructor paths, then migrate the `Implementation` cases one at a
time with metadata equality tests.

### Should Fix Soon: ClassComposition Field Identity Is A Commented Union

Example:

- `javatools/src/main/java/org/xvm/runtime/ClassComposition.java:888` stores
  `private final Object f_enid; // String | PropertyConstant | NestedIdentity`.

Why it is bad:

The comment is doing the work that the type system should do. Field identity is
runtime metadata tied to a composition. A typo, wrong constant, or unresolved
nested identity can enter the field and all downstream users must know the
commented union by memory.

Before:

```java
private final Object f_enid; // String | PropertyConstant | NestedIdentity
```

After sketch:

```java
sealed interface FieldIdentity permits NamedField, PropertyField,
        NestedIdentityField {}

record NamedField(String name) implements FieldIdentity {}
record PropertyField(PropertyConstant property) implements FieldIdentity {}
record NestedIdentityField(NestedIdentity identity) implements FieldIdentity {}
```

Classification: should-fix soon. This is owner-bearing runtime composition
metadata. A sealed identity type would document the legal variants and remove
scattered `instanceof`/cast logic.

PR split: safe separate PR if it only wraps `String`, `PropertyConstant`, and
`NestedIdentity` in a sealed `FieldIdentity` and keeps serialized/runtime
behavior unchanged.

### Should Fix: Token Values And Source Resources Use Object

Examples:

- `javatools/src/main/java/org/xvm/compiler/Token.java:119` returns `Object`
  from `getValue()`.
- `javatools/src/main/java/org/xvm/compiler/Token.java:796` stores token value
  as `Object`.
- `javatools/src/main/java/org/xvm/compiler/Parser.java:3638`, `:4107`, and
  `:5359` cast token values to `String`.
- `javatools/src/main/java/org/xvm/compiler/Source.java:139` returns `Object`
  from `resolvePath(...)`.
- `javatools/src/main/java/org/xvm/tool/ResourceDir.java:181` returns `Object`
  from `getByName(...)`.
- `javatools/src/main/java/org/xvm/tool/ModuleInfo.java:1317` returns `Object`
  from `resolveResource(...)`.

Why it is bad:

The parser often knows from grammar position that the token is a string/path
token. The source resolver knows it returns a file, a directory aggregate, or
missing. Instead of making those contracts visible, the API makes every caller
recover them by casts or `instanceof`.

Before:

```java
Token tokFile = parsePath();
String sFile  = (String) tokFile.getValue();
Object resource = m_source.resolvePath(sFile);
```

After sketch:

```java
sealed interface TokenValue permits TextValue, IntegerValue, DecimalValue,
        VersionValue, NoValue {}

String sFile = parsePath().requireValue(TextValue.class).text();

sealed interface SourceResource permits SourceFile, SourceDirectory {}
Optional<SourceResource> resource = m_source.resolvePath(sFile);
```

Classification: should-fix readability/API safety. This is not a known
parallel runtime bug, but it is a clear example of `Object` making code longer
and less readable. A bad grammar assumption would fail at the token boundary
with a useful message instead of a generic cast failure later.

PR split: safe separate compiler PR. Keep it away from runtime reentrancy fixes;
it can be validated with parser/token tests and no runtime behavior changes.

### Should Fix: ValueConstant Base Type Loses The Value Type

Examples:

- `javatools/src/main/java/org/xvm/asm/constants/ValueConstant.java:38`
  declares `public abstract Object getValue()`.
- Subclasses often restore the type covariantly, such as
  `DecimalConstant.getValue()` returning `Decimal`, `IntConstant.getValue()`
  returning `PackedInteger`, `ArrayConstant.getValue()` returning `Constant[]`,
  and `MapConstant.getValue()` returning `Map<Constant, Constant>`.
- `javatools/src/main/java/org/xvm/javajit/Builder.java:270`, `:276`, and
  `:282` still cast a `DecimalConstant` value to `Decimal32`, `Decimal64`, and
  `Decimal128` based on `Format`.
- `javatools/src/main/java/org/xvm/compiler/ast/CaseManager.java:938` and
  `:962` read values through `ValueConstant`, then recover comparability with
  raw `Comparable` checks.

Why it is bad:

The base type says "some object", while the subclasses and `Format` enum carry
more precise information. That mismatch forces switch-plus-cast code. It also
paints APIs into a corner: callers that only have `ValueConstant` cannot state
which value type they expect.

Before:

```java
case DecimalConstant decConstant:
    return switch (decConstant.getFormat()) {
    case Dec32 -> {
        Decimal32 dec = (Decimal32) decConstant.getValue();
        ...
    }
    ...
    };
```

After sketch:

```java
abstract class ValueConstant<V> extends Constant {
    abstract V getValue();
}

sealed interface DecimalValueConstant permits Dec32Constant, Dec64Constant,
        Dec128Constant {}

record Dec32Payload(Decimal32 value) {}
```

Or use a typed visitor where the `Format` split is still required:

```java
decConstant.accept(new DecimalConstant.Visitor<>() {
    Slot dec32(Decimal32 value) { ... }
    Slot dec64(Decimal64 value) { ... }
    Slot dec128(Decimal128 value) { ... }
});
```

Classification: should-fix readability/API safety. The JIT builder and case
manager are not automatically wrong, but the current API forces them to prove
value shape manually even when the source object already knows it.

PR split: needs design PR if changing `ValueConstant<V>` broadly. A safe first
PR can add typed helper/visitor methods for decimal values and update only the
JIT builder casts.

### Should Fix: Native Argument APIs Force Same-Line Casts

Examples:

- `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:244`
  to `:246` casts adjacent native arguments to `StringHandle` and `JavaLong`.
- `javatools/src/main/java/org/xvm/runtime/template/_native/web/xRTServer.java:336`
  to `:340` repeats the same pattern for route arguments, including
  `ServiceHandle` and `KeyStoreHandle`.
- `javatools/src/main/java/org/xvm/runtime/template/_native/collections/arrays/ByteBasedDelegate.java:51`
  casts each array element to `JavaLong` while building byte storage.
- `javatools/src/main/java/org/xvm/runtime/template/_native/collections/arrays/ByteBasedDelegate.java:135`
  and `:144` repeat same-line `JavaLong` casts for assignment and insertion.
- `javatools/src/main/java/org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java:216`
  to `:224` casts arguments and the delegate template before wrapping a byte
  buffer.

Why it is bad:

The native method registration already knows the expected XTC signature. The
Java body then repeats that signature as casts on `ObjectHandle[]`, often on
the same line as value extraction. This is verbose and opaque: the reader has
to map `ahArg[2]` to a method signature by hand, and a wrong index/type pairing
compiles. The failure becomes a runtime `ClassCastException` inside a native
method instead of a compile-time error in the Java binding.

Before:

```java
String sBindAddr  = ((StringHandle)   ahArg[1]).getStringValue();
int    nHttpPort  = (int) ((JavaLong) ahArg[2]).getValue();
int    nHttpsPort = (int) ((JavaLong) ahArg[3]).getValue();
```

After sketch:

```java
record ServerBindArgs(ObjectHandle binding, String bindAddress,
        int httpPort, int httpsPort) {}

ServerBindArgs args = frame.args(ahArg, ServerBindArgs.class);
```

Or a smaller helper that does not require generated binders:

```java
String sBindAddr  = args.string(1);
int    nHttpPort  = args.int64(2).toIntExact();
int    nHttpsPort = args.int64(3).toIntExact();
```

For delegates:

```java
JavaLong hElement = args.handle(0, JavaLong.class);
ByteBasedDelegate template = hDelegate.getTemplate(ByteBasedDelegate.class);
```

Classification: should-fix soon for native APIs that sit on service, file,
network, crypto, or collection mutation boundaries. This is not automatically a
reentrancy race, but it is a runtime boundary where a typed Java binding would
turn wrong handle shapes into compile-time failures or one checked argument
decode with native-method context.

PR split: safe separate native-binding PR. Start with helper accessors and a
few high-cast templates; generated or record-based binders can be a later
design PR.

### Should Fix: Compiler AST Raw Collections Hide Element Types

Examples:

- `javatools/src/main/java/org/xvm/compiler/ast/LambdaExpression.java:87`
  accepts raw `List params`, then asserts whether it contains `Expression` or
  `Parameter`.
- `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1232`
  declares `<T extends AstNode> List<T> clone(List<? extends AstNode> list)`.
- `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1234`
  casts `(List<T>) list`.
- `javatools/src/main/java/org/xvm/compiler/ast/NewExpression.java:1237`
  creates raw `List listCopy`.
- `javatools/src/main/java/org/xvm/compiler/ast/Context.java:1895` uses raw
  `Map mapBranch`.

Why it is bad:

These are examples of verbosity and opacity, not known owner bugs. The code
uses assertions and unchecked casts to compensate for APIs that could carry
their element type. In `LambdaExpression`, the two legal parameter shapes are a
closed union. In `NewExpression.clone(...)`, the type parameter promises
`List<T>` but the input only says `List<? extends AstNode>`, so the promise is
not actually proved.

Before:

```java
public LambdaExpression(List params, Token operator, StatementBlock body,
        long lStartPos) {
    if (!params.isEmpty() && params.get(0) instanceof Expression) {
        assert params.stream().allMatch(Expression.class::isInstance);
        this.paramNames = params;
    } else {
        assert params.stream().allMatch(Parameter.class::isInstance);
        ...
    }
}
```

After sketch:

```java
sealed interface LambdaParams permits ExpressionParams, DeclaredParams {}
record ExpressionParams(List<Expression> expressions) implements LambdaParams {}
record DeclaredParams(List<Parameter> parameters) implements LambdaParams {}

public LambdaExpression(LambdaParams params, Token operator,
        StatementBlock body, long lStartPos) { ... }
```

Classification: should-fix. This is mostly compile-time readability debt, but
it is the same pattern: the code knows the legal variants and asks comments,
assertions, and casts to enforce them after the fact.

PR split: safe separate compiler AST PR. It should not be coupled to runtime
owner work; parser/AST clone tests are enough for a first pass.

### Should Fix: Nested Identity APIs Return Object

Examples:

- `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:258`
  returns `Object` from `getNestedIdentity()`.
- `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:273`
  returns `Object` from `resolveNestedIdentity(...)`.
- `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java:511`
  resolves nested identity elements through `Object`.
- `javatools/src/main/java/org/xvm/asm/constants/MethodBody.java:561`
  exposes narrowing nested identity as raw `Object`.

Why it is bad:

Nested identity can be absent, canonical, or resolver-backed. This branch
already had to harden resolver-backed identities with an explicit output pool.
Returning `Object` hides that owner fact from the type signature.

Before:

```java
public Object resolveNestedIdentity(ConstantPool pool,
        GenericTypeResolver resolver) {
    return isNested()
            ? resolver == null
                ? getCanonicalNestedIdentity()
                : new NestedIdentity(pool, resolver)
            : null;
}
```

After sketch:

```java
sealed interface NestedIdentityRef permits NoNestedIdentity,
        CanonicalNestedIdentity, ResolvedNestedIdentity {}

record ResolvedNestedIdentity(ConstantPool owner,
        GenericTypeResolver resolver,
        IdentityConstant identity) implements NestedIdentityRef {}
```

Classification: should-fix soon where nested identities cross type-info or
constant-pool owner boundaries. The existing explicit-pool fix is good; the
next step is making the returned identity shape carry that owner contract.

PR split: separate PR, medium priority. This is adjacent to owner fixes but
should be staged after the explicit-pool APIs are stable, with nested identity
owner tests proving no ambient pool is used.

## Recommended Replacements

| Pattern | Problem | Replacement |
| --- | --- | --- |
| Repeated `(xEnum) container.getTemplate(...)` | Caller has to prove both owner and type. | Use `container.getTemplate(..., xEnum.class)` or a named `NativeTemplates` accessor. |
| Repeated `(ArrayHandle) heap.getConstHandle(...)` | Handle owner/type failure appears as a scattered cast. | Use `getConstHandle(container, constant, ArrayHandle.class)` and later enrich that helper with owner diagnostics. |
| Raw `Class` in JIT/loader code | Generated class identity and payload type are erased. | Use `Class<?>` for unknown classes and `Class<? extends X>` where a base type is known. |
| Raw `List`, `Map`, `Set`, `Iterator`, or `Comparable` declarations | Wrong payload can be inserted or compared until a later runtime failure. | Parameterize collections, use records for mixed payloads, or isolate raw deserialization/JIT bridges with local suppression. |
| Broad `Object` result with state-dependent casts | Every caller re-implements the state machine. | Use sealed result types, small records, or typed helper methods that validate state at the boundary. |
| `@SuppressWarnings("unchecked")` around large methods | Hides both deliberate and accidental unsafe casts. | Move the cast into the smallest helper that can check owner/type and document why erasure is unavoidable. |

## Must Fix Vs Should Fix

Must fix:

- typed owner boundaries on runtime paths that manufacture or cache
  owner-bearing templates, handles, constants, or type metadata;
- unchecked/raw APIs that allow a value from one `Container` or `ConstantPool`
  to be treated as if it belonged to another;
- broad `Object` state machines where the wrong state can escape across
  fibers, callbacks, or same-JVM repeated executions.

Status of the six Must Fix rows, each re-checked against the source 2026-09-02 rather than taken
from this file's own history:

| Row | Status |
| --- | --- |
| Service responses erase future payload shape | **open** - `ServiceContext.java:1698` still declares a raw `CompletableFuture f_future`; 11 raw sites in that file |
| Op-info cache uses Object values and raw enum keys | **open** - `Map<Op, EnumMap>` field, raw `new EnumMap(...)` at the write site |
| Fiber pending requests encode a union as Object | **appears done** - now `Map<CompletableFuture<ObjectHandle>, ObjectHandle>` |
| ConstantPool locator tables erase Format/key/value | **open** - still `Map<Object, Constant>` |
| Native template loading uses raw `Class` | **done in this branch** - the container-owned table replaced it |
| TypeConstant updaters lose generic detail | **open** - raw `AtomicReferenceFieldUpdater<TypeConstant, TransientThreadLocal>` |

Should fix soon:

- repeated casts where a typed accessor already exists;
- raw `Class` in JIT/loader code that should at least be `Class<?>`;
- raw collection declarations in compiler/AST code before incremental
  compilation relies on parallel or repeated in-process requests.

Should fix:

- local casts after `instanceof` checks where Java pattern matching can shorten
  the code but the current shape is not an ownership bug;
- compare/equality casts inside old class hierarchies where the receiver has
  already checked the concrete type.

## Status As Of 2026-08-31

Measured, not estimated. Numbers from `javac -Xlint:all` with the warning cap lifted - the build's
`maxWarnings=0` does NOT lift javac's own default of 100, so any count taken without an explicit
`-Porg.xtclang.java.maxWarnings=100000` is truncated.

| | then | now |
| --- | --- | --- |
| casts in `javatools` | 2,879 | 2,724 |
| casts targeting a handle type | 1,439 | 1,277 |
| `@SuppressWarnings("unchecked")` | 14 | 8 |
| `unchecked` warnings | - | 93 |
| `rawtypes` warnings | - | 61 |
| `serial` warnings | 9 | 0 |
| javac lint categories made fatal | 2 | 22 |

### Done, and how

- **Operator protocol swept**: 115 overrides to 22 via typed per-template bindings. The 22 left are
  deliberate (`xRTType`'s operands are polymorphic; the `xChecked*` family is unreachable).
- **Native binding built and proven** on `xOSStorage`, `xOSFileStore`, `xRTRandom` - four protocols,
  the framework consulted only in each base's fall-through so unmigrated natives are untouched.
- **`xRTDelegate` split, now complete**: all twelve object-array implementations moved to
  `xRTGenericDelegate`, and the base declares the whole storage protocol abstract. The first four
  turned master bug 36 into a compile error; the remaining eight (done 2026-08-31) exposed master
  bug 37 - `compareIdentity` was the object-array implementation sitting in the shared base, so
  `&slice1 == &slice2` and `&view1 == &view2` both died with a `ClassCastException`. `xRTView` and
  `xRTSlicingDelegate` now implement the five they were missing.
- **`@NativeTemplate`**: a template can now declare which Ecstasy class it implements instead of
  having it derived from its file name, which is what made the split expressible at all.
- **Suppressions 14 to 6**: the self-typed builder in `LauncherOptions` gained a `self()` hook
  (4 gone); `JumpVal_N`'s arrays of generic types became lists (4 gone); one 80-line suppression
  narrowed to a one-line factory. The remaining 6 are documented in place.
- **`xRTDelegate` split completed (2026-08-31)**: the remaining eight storage methods moved, and
  the base now declares all twelve abstract. All eight dereferenced `m_ahValue`, so none was
  shared behaviour. The earlier note here claimed five were "derived" and that `createDelegate`
  was overloaded; both were wrong, inferred from method names without reading the bodies. The
  real blocker was visibility - four are `public`, and a scripted pass had declared all eight
  `protected abstract`, which Java forbids on an override. The split named the five overrides
  `xRTView` and `xRTSlicingDelegate` were missing, one of which was crashing: master bug 37,
  fixed for master in PR #564.
- **Lint ratchet**: fifteen already-clean categories made fatal, verified by inserting a deprecated
  call and watching the build fail.

### Must audit

- **The remaining `@SuppressWarnings("unchecked")` - 9, re-counted 2026-09-02** (this row said 6,
  and the status table above said 8; both were stale). Two in `MarkAndSweepGcSpace` are the
  `ArrayList` idiom and correct; `ConstantPool.register` keeps method scope deliberately for four
  casts of one invariant; one is the `CacheKey` value cast in `NativeTemplates.get`, where the key
  carries the type and the map cannot. The rest are one-line declarations. None is known-wrong;
  all should be re-checked if the surrounding code changes.

  Counted with `grep -rhoE '@SuppressWarnings\([^)]*\)' --include='*.java'` over `javatools`, which
  also reports the categories this row has never tracked: 54 `fallthrough`, 31 `unused`, 1
  `rawtypes`, and 5 assorted IDE-specific ones. Only `unchecked` and `rawtypes` bear on this audit.

### Should fix

- **E25 generification of the delegate hierarchy - CORE DONE 2026-08-31, 128 handle casts left**
  (was 252; see E30 for why the rest are not simply relocatable).**
  `xRTDelegate<H extends DelegateHandle>` landed; the protected `*Impl` layer takes `H`, narrowed
  once in `narrow()`. Removed **110** of 252 handle casts in the package. 671 tests, 0 failures;
  xdk builds; `array.x` and `numbers.x` clean. The one added
  `@SuppressWarnings("unchecked")` costs no runtime safety - each override gets a synthetic bridge
  that checkcasts to the concrete handle, verified in the bytecode, so threading a `Class<H>`
  through 27 constructors to call `Class::cast` would add a redundant second check and nothing else.
  **E25 is closed as done-as-far-as-it-pays (2026-09-03).** The remaining buckets were attempted
  and measured, not estimated, and the row below is kept only to record what was tried. The
  successor is **E30** - move the storage operations onto the handle - which supersedes the type
  parameter rather than extending it.

  What is left, in decreasing tractability:
  - **`BitView` / `ByteView` - ATTEMPTED AND REVERTED, do not retry as generification.** The row
    used to claim ~35 casts and "the same mechanical change and the natural next increment". Both
    claims are wrong, measured by doing it: `ByteView<H>` plus `BitView<H> extends ByteView<H>`,
    pushed through the hierarchy until it compiled clean, removed **6** casts (134 -> 128) and
    turned **4 call sites into raw types**.

    The reason is structural. A caller discovers the view by `instanceof` on the *template* while
    the handle arrives separately:

    ```java
    ClassTemplate tDelegate = hDelegate.getTemplate();
    if (tDelegate instanceof BitView tView) {
        return tView.getBits(hDelegate, ofStart, cSize, fReverse);
    }
    ```

    The handle-to-template pairing is established at run time and no type parameter can express
    it, so generifying only moves a *checked* cast out of the implementation and leaves a raw call
    site behind. This is exactly what the retrospective below predicts: "added ahead of that
    decision it relocates casts into wildcards and buys nothing."

    Two traps worth recording, because both produce a green build that proves nothing: leaving
    `implements ByteView` un-parameterized makes it raw and silently erases every signature; and
    the resulting raw call sites raise no `rawtypes` warning in this build's lint configuration.
  - **`callEquals` / `compareIdentity` (~28 casts).** These take two handles, not a target, and
    either may be foreign, so `narrow()` does not apply - they want `instanceof` guards, the shape
    already used in `xRTSlicingDelegate.compareIdentity` for master bug 37.
  - **`invokePreInc`/`PostInc`/`PreDec`/`PostDec`/`invokeIndexOf` (~20 casts).** These are
    `ClassTemplate` API over `ObjectHandle`; typing them means generifying `ClassTemplate`, which
    is a much larger change than E25 and should not be folded into it.
  - **`createBitViewDelegate` (6 casts).** View construction; follows the `BitView` step.
- **The `CompletableFuture` request hierarchy - NEXT, and smaller than this row used to claim.**
  Re-measured 2026-08-31: **51** raw sites, not eleven, but the hierarchy behind them is tiny.
  `ServiceContext.Message` declares `public final CompletableFuture f_future` and has exactly **two**
  subclasses, `OpRequest` and `CallLaterRequest`; `Response<T>` is *already* generic, which is why
  `new Response<ObjectHandle[]>(.., future)` reports "unchecked conversion" - a typed constructor
  being handed a raw future. The payload types that actually flow are `ObjectHandle` (60 uses),
  `ObjectHandle[]` (2) and `Void` (2).

  ~~This is the single highest-value item left, because three separate rows converge on it. Of the
  154 `unchecked`+`rawtypes` warnings in the tree, **39 are in `ServiceContext` and `Fiber`
  alone**...~~ **DONE 2026-09-03**, and the measurement that justified the whole Should Fix tier no
  longer holds.

  Re-measured with lint actually enabled (`-Porg.xtclang.java.lint=true`; it is off by default
  because the build also sets `-Werror`): the tree has **9** `unchecked` warnings, all of them in
  `javatools_utils` (`TransientThreadLocal`, `ListMap`, `ListSet`). `ServiceContext` and `Fiber`
  have **zero**. The 39-warning concentration those three rows pointed at is gone, cleared by
  `Message<T>` (Must Fix 1), `OpInfoKey<T>` (Must Fix 2) and the Fiber pending-request split
  (Must Fix 3).

  `rawtypes` is still not measurable from the build - it is deliberately not enabled
  (`org.xtclang.build.java.gradle.kts:59-60`, "~40 sites remain and a half-enabled fatal lint just
  blocks the build"). Enabling it is the honest next measurement, and should happen before any
  further row in this tier is funded on the strength of a warning count.

  Note `f_future` is a public field. Pre-existing, but worth making private behind an accessor while
  the class is being changed anyway.
- **`ServiceContext.setOpInfo`'s `EnumMap`.** `EnumMap<K extends Enum<K>, V>` needs a concrete enum
  class and the category type varies per op, so no type argument exists to write. Either a plain
  `Map<Enum<?>, ..>` or leave it raw - a design choice, not a cleanup.
- **The native sweep.** Re-measured: **719** case labels across **75** templates (this row previously said 736/~60). The mechanism is proven; the
  work is per-template and independent.

### Note on nullability

**Corrected 2026-08-31.** An earlier version of this note said `javatools` does not depend on
`org.jetbrains.annotations` at all. That is wrong: `javatools/build.gradle.kts` declares
`compileOnly(libs.jetbrains.annotations)` (and `testCompileOnly`), and `@NotNull` is already used at
7 sites in `javatools`, 1 in `javatools_utils`. So the annotation IS available and can be applied.

What remains true is that it enforces nothing on its own - `compileOnly` means no runtime
dependency, and javac has no nullability lint (`javac --help-lint` lists none; that needs ErrorProne
or NullAway). So `@NotNull` documents intent for the IDE, and `Objects.requireNonNull` is what
actually enforces it. For a field or constructor parameter that is never legitimately null, use
both: the annotation to state it and the check to prove it. See `JumpVal_N.findSmall`.

## Retrospective: which generification actually paid (2026-08-31)

Worth recording before the next row is attempted, because the answer is not the intuitive one.

### The scoreboard

| | |
| --- | --- |
| handle casts in the arrays package | 252 -> 128 |
| `rawtypes` tree-wide | 59 -> 61 (+2, both unwritable - see below) |
| `@SuppressWarnings("unchecked")` | 6 -> 8 (`narrow()`, `SameAs`) |
| master bugs surfaced | 36, 37, 38 |

### All three bugs came from the split, none from the type parameter

- **36** - making `deleteRangeImpl` abstract made javac name `xRTStringDelegate`'s missing override.
- **37, 38** - making the protocol abstract forced `xRTView` and `xRTSlicingDelegate` to implement
  `compareIdentity`, which exposed that the body they had been inheriting was an unconditional
  `ClassCastException`.

`xRTDelegate<H>` found nothing. It removed casts, which is worth something, but it also introduced
two `rawtypes` that **cannot be fixed at all**: `NativeTemplateRef` keys on `Class<T>`, and
`Class<xRTDelegate<?>>` is unwritable in Java because a class literal is always raw in its own type
argument. E30 then measured that the parameter should not exist - the storage operations belong on
the handle, and the parameter is the symptom of their being on the template.

### The pattern: substitution failed, design change worked

Three attempts at generification-as-substitution failed, each differently:

- scripted retyping of the overrides corrupted brace structure and had to be reverted (twice);
- `DelegateHandle<SELF>` hit a structural wall - `ByteArrayHandle` is simultaneously a concrete leaf
  and `BitArrayHandle`'s superclass, which no self type can express;
- the `<?>` spread was invisible until after the substitution had landed.

Three design changes worked, and each removed a class of defect rather than an instance:

- **declare the protocol** (the split) - turned "a subclass forgot this" into a compile error;
- **move the operation to the value that knows its own type** (`SameAs`) - made
  `compareIdentity` total, so bugs 37 and 38 became unwritable rather than fixed;
- **recognize that behaviour is data** (`LongCodec`) - the packing was seven derived fields, not
  polymorphism, which is what makes E30 affordable at all.

**The rule this suggests:** a type parameter is worth adding when it *follows from* a decision about
where behaviour belongs. Added ahead of that decision it relocates casts into wildcards and buys
nothing. `ObjectHandle<T>` was analysed on those grounds and rejected - 4,120 sites across 394 files
to make roughly twenty methods cast-free, with the win concentrated in code that a design change
(E28/E29/E30) fixes properly instead.

## Follow-Up Plan

1. Add a source-shape lint task that reports raw declarations and unchecked
   suppressions without failing the build.
2. Convert changed runtime call sites to existing typed helpers before adding
   new casts.
3. Add typed accessors at high-traffic owner boundaries instead of repeating
   casts in templates, `Utils`, and `OwnershipDiagnostics`.
4. Audit JIT and loader raw `Class` usage separately; that code has different
   classloader and generated-bytecode constraints.
5. Move unavoidable unchecked casts into small helpers with owner/type checks
   and comments.

## Must-Audit Row 132 Classification (2026-08-24)

This section closes must-audit backlog row 132 ("Raw/unchecked
async/service/native-template/JIT metadata paths") by classifying every
owner-sensitive unchecked/raw site. The closure standard is: prove
confinement/ownership, or identify the sites that need typed checked
boundaries with owner assertions.

Enumeration method:

```bash
./gradlew javatools:compileJava -Porg.xtclang.java.lint=true \
    -Porg.xtclang.java.warningsAsErrors=false \
    -Porg.xtclang.java.maxWarnings=100000 --rerun-tasks
```

The `maxWarnings` override matters: without it, javac's default `-Xmaxwarns
100` truncates the list and hides roughly two thirds of the warnings. The
run covers `javatools` and its `javatools_utils` dependency. Suppressed sites
were enumerated separately with `rg '@SuppressWarnings\("unchecked"\)'`.

### Summary Counts

| Measure | Count |
| --- | --- |
| `[unchecked]` warnings | 126 |
| `[rawtypes]` warnings | 162 |
| Total unchecked/rawtypes warnings | 288 |
| `@SuppressWarnings("unchecked")` sites (not in the counts above) | 12 in 6 files |
| Owner-sensitive site groups examined | 18 (152 of 288 warnings) |
| Proven confined | 9 site groups |
| Needs typed checked boundary | 9 site groups |
| Low-risk local generic plumbing | 136 warnings (compiler AST, `VersionTree`, utils) |

### Confinement Proofs Used Below

Two facts carry most of the "proven confined" verdicts; they were verified by
reading the producers, not assumed:

1. **Service response completions are scheduling-lock confined.** The only
   completion sites for `Message.f_future` are
   `ServiceContext.Response.run()` (`ServiceContext.java:2008`/`:2010`) and
   the no-caller `CallLaterRequest` continuation
   (`ServiceContext.java:1964`/`:1968`). `Response.run()` executes only from
   `processResponses()` inside `nextFiber()`/`drainWork()`
   (`ServiceContext.java:444`, `:457`, `:312`), and `drainWork()` runs only
   under the owning context's scheduling lock with the pool asserted
   (`ServiceContext.java:303-338`, `tryAcquireSchedulingLock` at `:347`). Therefore every `whenComplete` callback that mutates
   fiber-confined state (`Fiber.m_cPending`, `m_oPendingRequests`,
   `m_mapPendingUncaptured`) executes on the thread currently holding that
   context's scheduling lock. The unchecked casts in `Fiber` are shape
   hazards, not races.
2. **The switch-op cache already has the typed owner boundary.** `JumpVal`
   and `JumpVal_N` publish decode caches through
   `Container.getRuntimeOpCache(op, category, Class<T>)` /
   `putRuntimeOpCacheIfAbsent`, which is container-scoped and performs
   `type.cast(...)` at the boundary (`Container.java:143-148`). Owner-bearing
   `ObjectHandle` keys never land on the shared `Op` instance. This is the
   model that the remaining sites should converge on.

### Owner-Sensitive Site Classification

Verdicts: `confined` = same-owner proven by reading the producer;
`confined, needs typed boundary` = no cross-owner path exists today but the
invariant is maintained by hand and must become a typed checked API;
`needs typed boundary` = the erased edge is where a wrong owner/payload would
enter undetected.

| # | Site | What is erased/cast | Producer / owner analysis | Verdict |
| --- | --- | --- | --- | --- |
| 1 | `ServiceContext.java:1601`,`:1692` (`Message.f_future` raw); conversions at `:1106`,`:1131`,`:1157`,`:1273`,`:1335`,`:1527`; raw cast `:1341`; `sendResponse` raw param `:1633`; raw `Response` at `:436`,`:445`,`:2126`; `:1964` | One raw future field is re-typed per call site as `CompletableFuture<ObjectHandle>` or `CompletableFuture<ObjectHandle[]>`; payload shape is chosen at response time by `cReturns` (`sendResponse` switch `:1636-1685`) | Completions confined (proof 1). Shape invariant is manual: `:1335` binds the future as `ObjectHandle[]` while the `cReturns == 0` completion delivers a single empty-tuple handle, and the raw cast at `:1341` exists to launder exactly that mismatch. Same-owner today because every conversion site is adjacent to the request it created. | confined, needs typed boundary (`ServiceReturn`/split futures; see must-fix section above) |
| 2 | `ServiceContext.java:226-244`,`:2192` op-info cache; consumers `OpCallable.java:198`,`:200`,`:253`,`:255`,`:324-325`,`:392`,`:430`,`:473-474`, `OpInvocable.java:131-132`, `OpVar.java:155-156`, `OpIndex.java:173`,`:175` | `Object` values, raw `EnumMap`/`WeakReference`; consumers blind-cast per `Category` | `f_mapOpInfo` is a per-`ServiceContext` instance field; a context has exactly one final container, all stored values are derived from that context's own frames, and access happens only during op execution under the scheduling lock. Cross-container reuse is impossible by construction. Category-to-payload pairing is compile-time unchecked. | confined, needs typed boundary (`OpInfoKey<T>`; see must-fix section above) |
| 3 | `Fiber.java:358-384` (`m_oPendingRequests` union, unchecked map casts `:367`,`:381`,`:520`), raw `whenComplete` `:344`,`:408`, raw field decls `:698`,`:704` | `Object` holding `Message \| Map<CompletableFuture, Message>`; raw `CompletableFuture` keys | All mutations run under the owning context's scheduling lock (proof 1). The union invariant is re-derived by hand in four readers. | confined, needs typed boundary (sealed/wrapper; see must-fix section above) |
| 4 | `Frame.java:314-322` (raw `List<CompletableFuture>` in `createWaitFrame`), `:531-532` (`waitForIO` raw param), `:2754` (`WAIT_FOR_IO` poll); `ObjectHandle.java:1215-1226` (`NativeFutureHandle` raw `f_future`); `xOSFile.java:219`,`:302`,`:330`,`:363` | Raw IO futures with per-call payload types (`Void`, `byte[]`) | Producer is `frame.f_context.f_container.scheduleIO(task)` — the frame's own container. Consumer is the same fiber: `WAIT_FOR_IO` only polls `isDone()`; the payload is read by a continuation in the same method that created the task, so payload type is same-method visible. | confined; parameterize as `CompletableFuture<?>`/generic `NativeFutureHandle<T>` as cheap hardening |
| 5 | `xFuture.java:530-532` (`anyOf` raw + unchecked `makeHandle`), `:816` raw local | `CompletableFuture.anyOf` erases to `Object` payload | Both inputs are `CompletableFuture<ObjectHandle>` belonging to the current service, so the `anyOf` payload is an `ObjectHandle` by construction. | confined; a typed `anyOf` helper would remove the unchecked edge |
| 6 | `JumpVal.java:121`,`:329`; `JumpVal_N.java:135`,`:465-468`; raw/suppressed generic arrays `JumpVal_N.java:390-512` | Switch decode caches; generic array creation in the builder | Published only through the container-scoped typed cache (proof 2). The raw arrays never escape the builder except inside the immutable `SwitchCache` record handed to that typed API. | confined (typed boundary already exists — model pattern) |
| 7 | `NativeContainer.java:183`,`:199`,`:214`,`:244`,`:277`,`:288` raw `Class` template maps; reflective construction `:795-796` | Raw `Class` for what must be `Class<? extends ClassTemplate>` | Single-threaded container bootstrap over the fixed native-template package scan; wrong classes are only rejected by late reflective-constructor failure instead of at discovery. | needs typed boundary (`asSubclass(ClassTemplate.class)` in `classForName`; see must-fix section above) |
| 8 | `NativeContainer.java:543` `(Set<String>) (Set) System.getProperties().keySet()` | Double-cast over JVM-global properties | Read-only scan; produces fresh handles created with `this` container. A non-`String` key would CCE locally at startup. | confined (low) |
| 9 | `xOSStorage.java:323`,`:365` raw `WatchEvent`/`Kind`; `:397` unchecked `findMethodDeep` | JDK watch API rawness; unchecked from raw `Utils.ANY` | The watcher daemon derives the container from the watched handle and asserts the pool before building handles (`Container container = context.hStorage.f_context.f_container` + `ConstantPool.assertCurrentPool`), then posts via `callLater` into the owning context. Owner-correct cross-thread path, already hardened. | confined; `:397` disappears when `Utils.ANY` is typed (row 10) |
| 10 | `Utils.java:1900` `public static final Predicate ANY` | Raw `Predicate` constant | Behavior-safe (`t -> true`) but infects every caller with unchecked warnings, masking real ones. | needs typed boundary (trivial: `Predicate<Object>` + `static <T> Predicate<T> any()`) |
| 11 | `JitConnector.java:93-126` raw `Class`/`Object` over generated classes | Generated module/array/string classes reflected raw | All classes come from `ts.loader` — the connector's own `TypeSystem` loader; a misload fails reflectively rather than delivering a foreign object. The injector load at `:61-63` already shows the correct `asSubclass` pattern. | needs typed boundary (bridge interfaces/`asSubclass`; see JIT section above) |
| 12 | `Ctx.java:163-165` `inject(...)` via raw `Function`; `Injector.java:39`,`:50`,`:64` `supplierOf` raw `Function` | Injected resource typed only as `Object` | The injector belongs to the parent container and supplies resources into the child container's generated code. `supplier.apply(opts)` performs no type or owner check before the value enters JIT-generated frames. This is the most cross-owner-capable edge in the JIT set. | needs typed boundary + owner assertion (typed supplier checked against the requested `TypeConstant`) |
| 13 | `ModuleLoader.java:88` raw `Class` local; `Refiner.java:36-48` raw `VersionTree`; `CommonBuilder.java:696` `Map.Entry[]` snapshot | Loader-local raws | `findLoadedClass` result asserted against `this` loader; version selection and constant-emission snapshot are method-local. | confined (low) |
| 14 | `ConstantPool.java:199-242` `register()` `(T)` casts (suppressed); `Constant.java:743` `registerConstants` (suppressed) | `T`-typed register over erased maps | This is the cross-pool adoption boundary and it is already instrumented: foreign types rejected by `isShared` (`:235-237`), adoption validated by `ConstantAdoptionValidator.assertValidIfEnabled`, runtime publication guarded by `assertRegisterBeforeRuntimePublished`. Equal-key lookups return same-class constants; `adoptedBy` clones preserve the concrete class. `registerConstants` clones the array before mutation because callers may share it across pools. | confined (documented owner boundary with runtime validation) |
| 15 | `TypeConstant.java:8283` raw `AtomicReferenceFieldUpdater<TypeConstant, TransientThreadLocal>` | Recursion-state payload type erased by the updater | Payload is thread-confined by `TransientThreadLocal` contract; the erased updater lets a future edit swap payload types silently. | needs typed boundary (named holder subclass; see must-audit section above) |
| 16 | `TypeInfoReal` construction: `TypeConstant.java:2635-2639`,`:5756`; `ConstantPool.java:2669-2676`; `RelationalTypeConstant.java:506-520`; `PropertyClassTypeConstant.java:413-416`; `TypeInfoReal.java:660-679`; `UnionTypeConstant.java:749`; `IntersectionTypeConstant.java:585-586`; `DifferenceTypeConstant.java:386`; `Entry[]`/`toArray` at `TypeInfoReal.java:1070`,`:1362`,`:2017`,`:2399` | Raw `ListMap.EMPTY`, `(ListMap) null`, generic-array creation | All inputs are assembled in-method from the same pool's constants under the TypeInfo build path; the warnings are erased-empty-collection idioms, not owner joints. | confined; a typed `ListMap.empty()` factory would remove ~20 warnings |
| 17 | `BinaryAST.java:296-298` `readAST` `(N)` cast (also raw `HashSet` `:271`) | Caller-declared `N` vs node type chosen by a stream byte | Deserialization bridge: the expected type is a caller promise the stream can violate; failure is a distant CCE. Compiler-side, per-compilation confined; not a runtime container boundary. | needs typed boundary (`Class<N>` checked parameter), compiler priority |
| 18 | `Component.java:955-973` raw child map; `ModuleStructure.java:441-443` raw `VersionTree` cache; `ClassStructure.java:550` `ListMap.EMPTY`; `IdentityConstant.java:496` raw `Comparable`; `SwitchAST.java:157-158` raw `Iterator`; `xRTModuleTemplate.java:66` raw `VersionTree` | Structure-local raws | Child-map mutation is structure-lock confined; version trees are module-local caches; the `Comparable` cast deliberately falls back to string comparison on heterogeneous path elements. | confined (low) |

### Low-Risk Bulk (Classified, Not Itemized)

The remaining 136 warnings are single-owner local generic plumbing with no
service/container/pool crossing: `VersionTree` internal raw `Node` links
(63), compiler AST and parser code (`AstNode` 17, `Parser` 10, `Lexer` 4,
`CaseManager`/`Context`/`LambdaExpression`/`NewExpression`/
`TypeCompositionStatement`/`SwitchStatement`/`ReturnStatement` 29), and
`javatools_utils` collection internals (13: `ListMap`, `ListSet`,
`IdentityArrayList`, `TransientThreadLocal` — the last is thread-confined by
contract, `CooperativelyCleanableReference`). The suppressed sites not
covered above are the same shape: `MarkAndSweepGcSpace` generic backing
arrays confined to the space instance, `LauncherOptions` builder `(T) this`
self-casts, and `JumpVal_N` builder arrays (row 6).

### Row 132 Verdict

Row 132 can be closed as **classified**. No reachable cross-owner delivery
was found: every owner-sensitive erased path either has a confinement proof
(scheduling-lock response processing, container-scoped typed op cache,
validated constant adoption, same-container IO futures) or is same-owner by
construction at every current call site. Nine site groups need typed checked
boundaries because their correctness is a hand-maintained invariant that
erasure hides: rows 1, 2, 3, 7, 11, 15 already carry must-fix/should-fix
entries earlier in this document; rows 10, 12, and 17 are new findings from
this pass — `Utils.ANY` (trivial), the JIT injection supplier (typed +
owner-asserted, highest cross-owner capability), and `BinaryAST.readAST`
(checked deserialization type). Until those boundaries land, the flagship
shape hazard to protect in review is `ServiceContext.java:1335`/`:1341`,
where the erased future's compile-time payload type is already wrong for the
zero-return path and only the raw cast keeps it compiling.

## Worked Examples: What Proper Generics Would Have Refused To Compile (2026-08-24)

The classification table above proves confinement; this section proves cost.
For each of the strongest sites it shows the current code, the concrete
runtime failure the erased API permits, the typed replacement, and — the
payoff — the exact wrong call that the typed API makes a javac error instead
of a latent runtime defect. Line numbers were re-verified against the working
tree on this date; where they drift from the older sections above, these are
the current ones.

### Example 1: The Future That Lies About Its Payload (Row 1)

BEFORE. `Message.f_future` is created raw and declared raw
(`ServiceContext.java:1593`, `:1684`):

```java
f_future = new CompletableFuture();
...
public final CompletableFuture f_future;
```

Every sender then re-types that one field to whatever shape it wants.
`sendOp1Request` binds it as `CompletableFuture<ObjectHandle>`
(`ServiceContext.java:1123` into `Frame.assignFutureResult(int,
CompletableFuture<ObjectHandle>)`, `Frame.java:1053`), while
`sendInvokeNRequest` binds the same field as `CompletableFuture<ObjectHandle[]>`
(`ServiceContext.java:1327`) and then launders it straight back through a raw
cast for the zero-return path (`ServiceContext.java:1330-1334`):

```java
CompletableFuture<ObjectHandle[]> future       = request.f_future;
boolean                           fOverwhelmed = addRequest(request);

if (cReturns == 0) {
    frame.f_fiber.registerUncapturedRequest(request);
    return fOverwhelmed || future.isDone()
            ? frame.assignFutureResult(Op.A_IGNORE, (CompletableFuture) future)
            : Op.R_NEXT;
}
```

The raw cast exists because the completion side breaks the promise the local
variable just made. `sendResponse` takes the future raw
(`ServiceContext.java:1625`) and for `cReturns == 0` completes it with a
*single* empty-tuple handle, not an array (`ServiceContext.java:1629-1632`,
completed at `Response.run()`, `:2000`):

```java
protected void sendResponse(Fiber fiberCaller, Frame frame,
                            CompletableFuture future, int cReturns) {
    ...
    case 0:
        ctxDst.respond(new Response<ObjectHandle>(
                fiberCaller, xTuple.ensureEmptyTuple(ctxDst.f_container),
                frame.m_hException, future));
        break;
```

So the compile-time type of `future` at `:1327` is already false: for a
zero-return invoke, the `ObjectHandle[]`-typed future is completed with a
`TupleHandle`. Nothing fails today only because the raw cast at `:1333`
routes it into the single-handle consumer, which happens to be what the
payload really is. The permitted failure: any maintainer who trusts the
declared type — for example by routing the `cReturns == 0` path through
`frame.call(frame.createWaitFrame(future, aiReturn))` (`Frame.java:275`) or by
attaching `future.thenApply(ah -> ah[0])` — compiles cleanly and gets

```
java.lang.ClassCastException: class xTuple$TupleHandle cannot be cast
    to class [Lorg.xvm.runtime.ObjectHandle;
```

inside a `whenComplete`/`thenApply` continuation on the *caller's* service
thread, an arbitrary number of frames away from `sendResponse`, with no line
in the stack pointing at the completion that lied.

AFTER. Parameterize the message by its response payload and split the
response send by shape:

```java
public abstract static class Message<R> {
    public final CompletableFuture<R> f_future = new CompletableFuture<>();
    ...
}

public static class OpRequest  extends Message<ObjectHandle>   { ... }
public static class OpNRequest extends Message<ObjectHandle[]> { ... }

protected void sendSingleResponse(Fiber fiberCaller, Frame frame,
        CompletableFuture<ObjectHandle> future, int cReturns) {
    // cReturns is 0 (empty tuple), 1, or -1 (tuple); all complete with one handle
}

protected void sendMultiResponse(Fiber fiberCaller, Frame frame,
        CompletableFuture<ObjectHandle[]> future) {
    // cReturns > 1 only
}
```

The rewritten call sites lose their conversions and their raw cast:

```java
// sendOp1Request — was an unchecked conversion, now exact:
return frame.assignFutureResult(iReturn, request.f_future);

// sendInvokeNRequest, cReturns == 0 — a zero-return call *is* a
// single-handle protocol, so it creates an OpRequest, and the launder
// at :1333 becomes a plain typed pass-through:
return frame.assignFutureResult(Op.A_IGNORE, request.f_future);

// sendInvokeNRequest, cReturns > 1 — OpNRequest, exact match for
// createWaitFrame(CompletableFuture<ObjectHandle[]>, int[]):
return frame.call(frame.createWaitFrame(request.f_future, aiReturn));
```

`Response<T>` (`ServiceContext.java:1978`) is already generic; with a typed
`f_future` its construction sites at `:1630`, `:1639`, `:1649`, and `:1672`
stop erasing `T` on the way in, and `Queue<Response>` at `:2118` becomes
`Queue<Response<?>>`.

WOULD NOT HAVE COMPILED. The current zero-return completion, written against
a future that keeps its `ObjectHandle[]` promise:

```java
CompletableFuture<ObjectHandle[]> future = request.f_future;
...
ctxDst.respond(new Response<>(fiberCaller,
        xTuple.ensureEmptyTuple(ctxDst.f_container),   // TupleHandle
        frame.m_hException, future));
```

```
ServiceContext.java: error: incompatible types: cannot infer type
    arguments for Response<>
  reason: inference variable T has incompatible bounds
    equality constraints: ObjectHandle[]
    lower bounds: TupleHandle
```

and the direct form is just as dead:

```java
future.complete(xTuple.ensureEmptyTuple(ctxDst.f_container));
```

```
ServiceContext.java: error: incompatible types: TupleHandle cannot be
    converted to ObjectHandle[]
```

The launder itself also dies — `assignFutureResult(Op.A_IGNORE, future)` with
a genuinely `ObjectHandle[]`-typed future is
`error: incompatible types: CompletableFuture<ObjectHandle[]> cannot be
converted to CompletableFuture<ObjectHandle>`, and there is no raw
`(CompletableFuture)` escape hatch left to silence it. The shape lie that the
Row 132 verdict flags as the flagship review hazard is not reviewable away —
it is unwritable.

### Example 2: The Op-Info Cache That Trusts The Enum's Name (Row 2)

BEFORE. The cache API is `Object` in, `Object` out, keyed by a bare `Enum`
(`ServiceContext.java:227-246`, storage `:2184`):

```java
public Object getOpInfo(Op op, Enum category) {
    EnumMap mapByCategory = f_mapOpInfo.get(op);
    if (mapByCategory == null) {
        return null;
    }
    WeakReference ref = (WeakReference) mapByCategory.get(category);
    return ref == null ? null : ref.get();
}

public void setOpInfo(Op op, Enum category, Object info) {
    f_mapOpInfo.computeIfAbsent(op, (op_) -> new EnumMap(category.getClass()))
               .put(category, new WeakReference(info));
}

private final Map<Op, EnumMap> f_mapOpInfo = new WeakHashMap<>();
```

Four op families consume it by blind cast, fifteen casts in all:
`OpCallable.java:198`, `:200`, `:253`, `:255`, `:324-325`, `:392`, `:430`,
`:474`; `OpInvocable.java:131-132`; `OpVar.java:155-156`; `OpIndex.java:173`,
`:175`. Representative (`OpCallable.java:198-200`, write side `:237-238`):

```java
MethodStructure  constructor = (MethodStructure) context.getOpInfo(this, Category.Constructor);
if (constructor != null) {
    IdentityConstant idParent = (IdentityConstant) context.getOpInfo(this, Category.TargetClass);
    ...
}
...
context.setOpInfo(this, Category.TargetClass, idParentR);
context.setOpInfo(this, Category.Constructor, constructor);
```

The write at `:237-238` is two adjacent calls whose second and third
arguments can be swapped, crossed with the pair at `:281-282`, or crossed
between `Category.Function` and `Category.Constructor`, and javac says
nothing. The permitted failure: `setOpInfo(this, Category.TargetClass,
constructor)` poisons the cache silently; the *next* execution of the same op
on this service reads it back at `OpCallable.java:200` and dies with

```
java.lang.ClassCastException: class org.xvm.asm.MethodStructure cannot be
    cast to class org.xvm.asm.constants.IdentityConstant
```

on the cache-hit path — that is, only on the second and subsequent runs, the
classic bug that passes the smoke test and fails in the hot loop. These are
checked downcasts from `Object`, so they produce no `-Xlint` warning at all:
the 288-warning inventory cannot even see this family.

AFTER. The typed key pairs the category with its payload class once, and the
boundary does the single checked cast (the `Container.getRuntimeOpCache`
model, `Container.java:143-171`):

```java
public record OpInfoKey<T>(Enum<?> category, Class<T> type) {}

public <T> T getOpInfo(Op op, OpInfoKey<T> key) {
    EnumMap<?, WeakReference<?>> mapByCategory = f_mapOpInfo.get(op);
    if (mapByCategory == null) {
        return null;
    }
    WeakReference<?> ref = mapByCategory.get(key.category());
    return ref == null ? null : key.type().cast(ref.get());
}

public <T> void setOpInfo(Op op, OpInfoKey<T> key, T info) { ... }
```

The call sites in `OpCallable` become cast-free:

```java
private static final OpInfoKey<MethodStructure> CONSTRUCTOR =
        new OpInfoKey<>(Category.Constructor, MethodStructure.class);
private static final OpInfoKey<IdentityConstant> TARGET_CLASS =
        new OpInfoKey<>(Category.TargetClass, IdentityConstant.class);

MethodStructure constructor = context.getOpInfo(this, CONSTRUCTOR);
if (constructor != null) {
    IdentityConstant idParent = context.getOpInfo(this, TARGET_CLASS);
    ...
}
...
context.setOpInfo(this, TARGET_CLASS, idParentR);
context.setOpInfo(this, CONSTRUCTOR, constructor);
```

WOULD NOT HAVE COMPILED. The swapped write:

```java
context.setOpInfo(this, CONSTRUCTOR, idParentR);
```

```
OpCallable.java: error: method setOpInfo in class ServiceContext cannot be
    applied to given types;
  required: Op,OpInfoKey<T>,T
  found:    OpCallable,OpInfoKey<MethodStructure>,IdentityConstant
  reason: inference variable T has incompatible bounds
    equality constraints: MethodStructure
    lower bounds: IdentityConstant
```

and the mis-shaped read:

```java
IdentityConstant idParent = context.getOpInfo(this, CONSTRUCTOR);
```

```
OpCallable.java: error: incompatible types: MethodStructure cannot be
    converted to IdentityConstant
```

Fifteen blind casts across four op families collapse into one `type.cast` at
the owning boundary, and the wrong pairing stops being a second-execution
runtime surprise.

### Example 3: The Fiber Field That Is Two Types In A Trench Coat (Row 3)

BEFORE. The pending-request set is an undeclared union
(`Fiber.java:696-698`), and every reader re-proves the invariant by hand with
an unchecked cast (`Fiber.java:358-369`, `:377-382`, `:507-520`):

```java
/**
 * Pending requests: Message | Map<CompletableFuture, Message>.
 */
private Object m_oPendingRequests;

// addDependee, Fiber.java:358-369
Object oPending = m_oPendingRequests;
if (oPending == null) {
    m_oPendingRequests = request;
} else if (oPending instanceof Message requestPrev) {
    Map<CompletableFuture, Message> mapPending = new HashMap<>();
    mapPending.put(requestPrev.f_future, requestPrev);
    mapPending.put(request.f_future, request);
    m_oPendingRequests = mapPending;
} else {
    Map<CompletableFuture, Message> mapPending = (Map<CompletableFuture, Message>) oPending;
    mapPending.put(request.f_future, request);
}

// removeDependee, Fiber.java:381
((Map<CompletableFuture, Message>) oPending).remove(request.f_future);

// reportWaiting, Fiber.java:520
for (Message request : ((Map<CompletableFuture, Message>) oPending).values()) {
```

The scheduling-lock proof (proof 1 above) says these are not races, but the
field type accepts *anything*. The permitted failure: any edit that adds a
third representation — say an ordered `ArrayList<Message>` for deadlock
reporting — compiles at the write site, and the crash arrives at
`removeDependee` (`Fiber.java:381`) as

```
java.lang.ClassCastException: class java.util.ArrayList cannot be cast to
    interface java.util.Map
```

*inside the `whenComplete` callback registered at `Fiber.java:344`*, i.e. on
the response-processing path under `processResponses()` — a completely
different stack from the code that wrote the field, and only once a response
actually arrives.

AFTER. Keep the compact one-or-many representation, but behind a type; the
union stops leaking:

```java
private static final class PendingRequests {
    private Message                            one;
    private Map<CompletableFuture<?>, Message> many;

    void add(Message request)    { ... } // owns the one->many promotion
    void remove(Message request) { ... }
    boolean isEmpty()            { ... }
    Iterable<Message> values()   { ... }
}

private final PendingRequests f_pendingRequests = new PendingRequests();
```

The three readers shrink to intent:

```java
// addDependee
f_pendingRequests.add(request);

// removeDependee
f_pendingRequests.remove(request);

// reportWaiting
for (Message request : f_pendingRequests.values()) {
```

WOULD NOT HAVE COMPILED. The third-representation edit, written against the
typed field:

```java
f_pendingRequests = new ArrayList<>(listPending);
```

```
Fiber.java: error: incompatible types: ArrayList<Message> cannot be
    converted to Fiber.PendingRequests
```

(and `f_pendingRequests` is final, so the representation cannot be swapped at
all outside the class that owns the promotion logic). The equally silent raw
hazard today — `((Map) oPending).put("oops", request)` type-checks against
the raw-keyed map — has no typed spelling: `many` is private and keyed
`CompletableFuture<?>`, so a `String` key is
`error: incompatible types: String cannot be converted to
CompletableFuture<?>`.

### Example 4: The Injection Chain That Delivers Whatever It Has (Row 12)

BEFORE. JIT-generated code asks for an injected resource through
`Ctx.inject` (`Ctx.java:162-166`, generated call descriptor `MD_inject` at
`:186-187` returning `CD_JavaObject`), which drains a raw `Function` from the
parent container's injector (`Injector.java:39-41`, `:50-52`, `:63-65`):

```java
// Ctx.java:162-166
public Object inject(TypeConstant resourceType, String resourceName, Object opts) {
    Function supplier = container.injector.supplierOf(resourceType, resourceName);

    return supplier == null ? null : supplier.apply(opts);
}

// Injector.java:39-41
public Function supplierOf(Resource res) {
    return null;
}
```

`supplier.apply(opts)` is an unchecked call on a raw type; nothing between
the supplier registration in the parent container and the generated frame in
the child container ever compares the produced object against the requested
`resourceType`. The Row 132 table calls this the most cross-owner-capable
edge in the JIT set, and it is also the *only* family here where the wrong
value flows with **no cast anywhere**: a supplier registered for
`("Console", console-type)` that returns a `java.lang.String`, or a handle
carried by the wrong generated classloader, is handed straight into generated
bytecode. The failure is a `ClassCastException` inside a generated class
body (unmapped back to any `.x` source line), or — if the generated code
stores it into one of the `Object` return slots `o0..o7`/`oN`
(`Ctx.java:47-62`) — silent flow until some later consumer casts.

AFTER. The resource identity carries its Java carrier type, the supplier is
typed against it, and the boundary does one checked, owner-asserting cast
(the injector-load site at `JitConnector.java:61-63` already proves the
`asSubclass` half of this pattern):

```java
public record Resource<R>(TypeConstant type, String name, Class<R> carrier) {}

public <R> Function<Object, ? extends R> supplierOf(Resource<R> res) {
    return null;
}

public <R> void register(Resource<R> res, Function<Object, ? extends R> supplier) { ... }

// Ctx
public <R> R inject(Resource<R> resource, Object opts) {
    Function<Object, ? extends R> supplier = container.injector.supplierOf(resource);
    if (supplier == null) {
        return null;
    }
    // one checked boundary: carrier class proves the Java shape, and the
    // injector can additionally assert the produced value against
    // resource.type() before it enters child-container frames
    return resource.carrier().cast(supplier.apply(opts));
}
```

WOULD NOT HAVE COMPILED. Registering a supplier that produces the wrong
resource:

```java
static final Resource<nConsole> CONSOLE =
        new Resource<>(typeConsole, "console", nConsole.class);

injector.register(CONSOLE, opts -> new nClock(ctx));
```

```
error: incompatible types: bad return type in lambda expression
    nClock cannot be converted to nConsole
```

Today that registration compiles without so much as a warning, and the first
symptom is a `ClassCastException` deep inside generated code in the *child*
container — the textbook wrong-owner delivery this audit exists to prevent.

### Example 5: The Template Table That Accepts Any Class Named xSomething (Row 7)

BEFORE. Native-template discovery keys classes by name pattern only
(`NativeContainer.java:183`, `:199`, `:214`, scan methods `:244`/`:288`,
loader `:277-285`, reflective construction `:795-801`):

```java
Map<String, Class> mapTemplateClasses = new HashMap<>();
...
for (Map.Entry<String, Class> entry : mapTemplateClasses.entrySet()) {
    ...
    Class<ClassTemplate> clz = entry.getValue();      // unchecked, proves nothing
    ...
}

private static Class classForName(String sFile) {
    ...
    return Class.forName(sClz);                        // raw: any class at all
}

private ClassTemplate instantiateNativeTemplate(
        Class<ClassTemplate> clz, ClassStructure structClass) throws Exception {
    return clz.getConstructor(Container.class, ClassStructure.class)
            .newInstance(this, structClass);
}
```

The filter (`isNativeClass`, `:254-259`) checks only "in the template
package, ends with `.class`, no `$`, simple name starts with `x`". Any
helper class matching that pattern enters the map; the unchecked
`Class<ClassTemplate>` assignment at `:214` launders it. The permitted
failure: a non-template `xSomething` with no
`(Container, ClassStructure)` constructor dies at `:800` as a
`NoSuchMethodException` wrapped into `LauncherException("Constructor failed
for ...")`; one that *does* have such a constructor is instantiated, and the
`ClassCastException` fires on the erased-return checkcast at `:800` — in both
cases a late reflective failure instead of "this class is not a native
template" at discovery time.

AFTER. Prove template-ness once, where the class is loaded:

```java
Map<String, Class<? extends ClassTemplate>> mapTemplateClasses = new HashMap<>();

private static Class<? extends ClassTemplate> classForName(String sFile) {
    assert sFile.endsWith(".class");
    String sClz = sFile.substring(0, sFile.length() - ".class".length()).replace('/', '.');
    try {
        return Class.forName(sClz).asSubclass(ClassTemplate.class);
    } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
    }
}

private ClassTemplate instantiateNativeTemplate(
        Class<? extends ClassTemplate> clz, ClassStructure structClass) throws Exception {
    return clz.getConstructor(Container.class, ClassStructure.class)
            .newInstance(this, structClass);
}
```

The loop body loses its laundering local: `entry.getValue()` is already
`Class<? extends ClassTemplate>`, and a rogue class in the package is
rejected by `asSubclass` at scan time with a message naming the class,
instead of a constructor-failure autopsy later.

WOULD NOT HAVE COMPILED. Putting a non-template into the table:

```java
// xNativeHelper is a utility class, not a ClassTemplate subclass
mapTemplateClasses.put("mgmt.NativeHelper", xNativeHelper.class);
```

```
NativeContainer.java: error: incompatible types: Class<xNativeHelper>
    cannot be converted to Class<? extends ClassTemplate>
```

and inside `classForName`, forgetting the proof no longer compiles either:
`return Class.forName(sClz);` is
`error: incompatible types: Class<CAP#1> cannot be converted to
Class<? extends ClassTemplate>`. The owner boundary that manufactures every
native template in the container can no longer be entered by name-pattern
coincidence.

### Example 6: The AST Reader That Believes Whatever The Caller Hoped (Row 17)

BEFORE. `BinaryAST.readAST` lets the caller pick `N` and casts whatever node
the stream byte selects (`BinaryAST.java:294-303`):

```java
public static <N extends BinaryAST> N readAST(DataInput in, ConstantResolver res)
        throws IOException {
    N node = (N) (NodeType.valueOf(in.readUnsignedByte())).instantiate();
    if (node == null) {
        node = (N) new StmtBlockAST(NO_ASTS, false);
    } else {
        node.readBody(in, res);
    }
    return node;
}
```

Both `(N)` casts are erased no-ops; the real checkcast is inserted by javac
at whichever call site fixed `N`. `readExprAST`'s escape path
(`BinaryAST.java:379`) infers `N = ExprAST` with zero evidence:

```java
if (nodeType == NodeType.Escape) {
    return readAST(in, res); // "escape" for expressions
}
```

The permitted failure: a corrupt or version-skewed `.xtc` stream whose
escape byte is followed by a statement node compiles into

```
java.lang.ClassCastException: class org.xvm.asm.ast.IfStmtAST cannot be
    cast to class org.xvm.asm.ast.ExprAST
```

at whatever call site fixed `N` — and for readers reached through
`MethodStructure.java:702` (`m_ast = BinaryAST.readAST(in, res)`), the node
can also survive the read entirely (when `N` is inferred as plain
`BinaryAST`) and fail later, when a consumer walks a child field that the
wrong node type filled differently. Either way the diagnostic says nothing
about stream offset, expected node type, or the fact that the input was
corrupt rather than the code wrong.

AFTER. The expectation becomes an argument, checked where the stream is
still in hand:

```java
public static <N extends BinaryAST> N readAST(DataInput in, ConstantResolver res,
                                              Class<N> clzNode)
        throws IOException {
    NodeType  nodeType = NodeType.valueOf(in.readUnsignedByte());
    BinaryAST node     = nodeType.instantiate();
    if (node == null) {
        node = new StmtBlockAST(NO_ASTS, false);
    } else {
        node.readBody(in, res);
    }
    if (!clzNode.isInstance(node)) {
        throw new IOException("corrupt AST stream: expected "
                + clzNode.getSimpleName() + ", read " + nodeType);
    }
    return clzNode.cast(node);
}
```

Call sites state what they mean, and the two erased casts disappear:

```java
// WhileStmtAST.java:55 and friends
body = readAST(in, res, BinaryAST.class);

// BinaryAST.java:379 — the escape path stops guessing
return readAST(in, res, ExprAST.class);
```

WOULD NOT HAVE COMPILED. A caller whose declared type disagrees with its
expectation token:

```java
ExprAST cond = readAST(in, res, StmtBlockAST.class);
```

```
error: incompatible types: StmtBlockAST cannot be converted to ExprAST
```

The two failure modes separate cleanly: caller-side type confusion is now a
javac error, and stream-side corruption is an `IOException` naming the
expected and actual node types at the exact read — instead of both collapsing
into one distant `ClassCastException`.

### Example 7 (Bonus): The Raw Predicate That Disables Checking For Everyone (Row 10)

BEFORE. `Utils.java:1901`:

```java
public static final Predicate          ANY  = t -> true;
```

Consumed at `xOSStorage.java:396-397` against
`ClassStructure.findMethodDeep(String, Predicate<MethodStructure>)`
(`ClassStructure.java:1895`):

```java
private final Lazy.Owner<xOSStorage, MethodStructure> f_methodOnEvent =
        Lazy.ofOwner(owner -> owner.getStructure().findMethodDeep("onEvent", Utils.ANY));
```

`ANY` itself is behavior-safe, but raw. Two costs: the unchecked-conversion
warning it emits at every consumer buries real warnings (the Row 9 verdict
notes `xOSStorage.java:397` clears when this is typed); and the pattern it
licenses is not safe — a raw predicate constant that *does* look inside its
argument, e.g. `Predicate CONSTRUCTOR_ONLY = m -> ((MethodStructure)
m).isConstructor()`, is accepted by javac for a `Predicate<PropertyStructure>`
parameter just as readily, and detonates as a `ClassCastException` inside the
predicate during someone else's tree walk.

AFTER:

```java
public static <T> Predicate<T> any() {
    return t -> true;
}

// consumer
Lazy.ofOwner(owner -> owner.getStructure().findMethodDeep("onEvent", Utils.any()));
```

WOULD NOT HAVE COMPILED. The unsafe cousin, once predicates are typed:

```java
public static final Predicate<MethodStructure> CONSTRUCTOR_ONLY =
        MethodStructure::isConstructor;

structure.findChildDeep("x", CONSTRUCTOR_ONLY); // wants Predicate<Component>
```

```
error: incompatible types: Predicate<MethodStructure> cannot be converted
    to Predicate<Component>
```

One three-line change deletes a whole class of warning noise and closes the
raw-`Predicate` loophole with it.

### Example 8 (Bonus): The Version Tree That Would Store A Version As Its Own Value (Bulk Row)

BEFORE. `VersionTree<V>` has a properly generic node class
(`VersionTree.java:419`, `private static class Node<V>`), but the internal
links are raw at 63 sites — the single largest warning block in the
inventory (`VersionTree.java:426` raw parent link, raw locals at `:59`,
`:73`, `:83`, `:190`, `:211`, `:239`, `:325`, and throughout `Node`). The
write path (`VersionTree.java:239-243`):

```java
Node node = ensureNode(ver);       // raw local, though ensureNode returns Node<V>
if (!node.isPresent()) {
    ++count;
}
node.value = value;                // unchecked write through the raw link
```

Through a raw `Node`, the `value` field is just `Object`, so any edit that
stores the wrong thing compiles — `node.value = ver;` (the version instead of
the value) is accepted today. `get()` reads through a typed `Node<V>`
(`VersionTree.java:141-143`), so the corruption is invisible at the API:
erasure defers the checkcast to the *caller's* use site, e.g. a
`VersionTree<ModuleStructure>` consumer doing `tree.get(ver).getName()` gets

```
java.lang.ClassCastException: class org.xvm.asm.Version cannot be cast to
    class org.xvm.asm.ModuleStructure
```

in module-resolution code, with the tree itself never having thrown.

AFTER. Parameterize the links; the one unavoidable erased spot (the
`Node[] kids` array) gets a single localized suppression inside `Node`:

```java
private static class Node<V> {
    Node(Node<V> parent, int part) { ... }

    Node<V>    parent;
    Node<V>[]  kids;
    V          value;
    ...
}

public void put(Version ver, V value) {
    ...
    Node<V> node = ensureNode(ver);
    if (!node.isPresent()) {
        ++count;
    }
    node.value = value;
}
```

WOULD NOT HAVE COMPILED:

```java
node.value = ver;
```

```
VersionTree.java: error: incompatible types: Version cannot be converted to V
```

Sixty-three warnings disappear, and the value channel of every version tree —
module version caches included (`ModuleStructure.java:441-443`,
`xRTModuleTemplate.java:66`, `Refiner.java:36-48`) — becomes writable only
with a `V`.

### Shorter Code

Counting only what the AFTER shapes above delete, against the numbers already
established in this document:

- The eight worked families account for roughly **100 raw/unchecked sites**:
  15 blind op-cache downcasts (`OpCallable`/`OpInvocable`/`OpVar`/`OpIndex`)
  plus 3 raw cache internals; ~10 raw-future declarations, conversions, and
  the `:1333` launder in `ServiceContext`/`Fiber`; 3 unchecked union casts
  plus 2 raw field declarations in `Fiber`; 4 raw-`Function` sites in
  `Ctx`/`Injector`; 6 raw-`Class` sites in `NativeContainer`; 2 erased casts
  in `BinaryAST.readAST`; 2 for `Utils.ANY`; 63 raw `Node` links in
  `VersionTree`.
- Across the classified inventory, the typed boundaries proposed here plus
  the cheap hardening already recommended in rows 4-6 and 16 eliminate about
  **215 of the 288** `unchecked`/`rawtypes` warnings (the 152 warnings in the
  18 owner-sensitive groups plus the 63-warning `VersionTree` block) — roughly
  75%. Most of the 12 `@SuppressWarnings("unchecked")` sites are deliberate
  erasure bridges (GC arrays, builder self-casts, the row 6 model pattern, the
  row 14 adoption boundary) and correctly stay.
- Laundering locals disappear outright: the retyped `future` locals in
  `sendInvokeNRequest`, the three per-reader union re-derivations in `Fiber`
  (`addDependee`'s three-branch state machine becomes one call), the
  `Class<ClassTemplate> clz` line in `NativeContainer`.
- Honest net line count: modest. The typed shapes add roughly 150 lines of
  records, keys, and wrapper classes and delete roughly 250-300 lines of
  casts, re-derived state machines, and laundering locals — call it **~100-150
  net lines saved** across `javatools`. The real reduction is not lines but
  obligations: each deleted cast was a contract a reviewer had to re-verify by
  hand, and the 15 op-cache downcasts never appeared in any warning count at
  all — checked downcasts from `Object` are invisible to `-Xlint`.

### Compile-Time Instead Of Runtime

Six failure classes move left of the run button:

1. **Response shape lies** (Example 1): completing a multi-return future with
   a single handle, or consuming a zero-return future as an array. Today only
   the scheduling-lock confinement proof and the adjacency of every conversion
   site to its request keep this correct.
2. **Wrong-payload cache hits** (Example 2): a swapped or crossed
   category/value pair that fails only on the second execution of the op on
   that service.
3. **Pending-union corruption** (Example 3): a third representation entering
   `m_oPendingRequests` and failing inside a response callback.
4. **Foreign-resource injection** (Example 4): a supplier delivering the
   wrong type — or the wrong container's object — into JIT-generated frames,
   currently with no cast anywhere on the path.
5. **Foreign-template loading** (Example 5): a name-pattern coincidence
   entering the native template table and failing reflectively at
   construction.
6. **Wrong-node AST reads** (Example 6): stream/expectation disagreement
   surfacing as a distant `ClassCastException` instead of an `IOException` at
   the read.

Classes 1-3 deserve emphasis: their confinement proofs (proof 1 above) rest
on the scheduling lock, so when the invariant does break, the symptom
appears on the response-processing path of another fiber's turn — exactly the
kind of failure that today is catchable only by the stress and ownership
diagnostics this branch added, and by nothing in the normal test suite.
Class 4 is the one edge the Row 132 pass rated most cross-owner-capable;
typed suppliers make the wrong registration unwritable rather than
undetectable. Classes 5 and 6 are deterministic once triggered, but the
typed versions convert an autopsy (constructor failure, distant CCE) into a
named rejection at the boundary that still has the context — the class name
being scanned, the stream offset being read.

### Lock-In And Change Resistance

Erased `Object`-union fields and raw containers do not just permit bugs;
they freeze the protocols they carry. Every reader of `Message.f_future`
re-derives the `cReturns`-to-payload mapping from folklore; every reader of
`m_oPendingRequests` re-implements the one-or-many state machine; every op
family re-states the category-to-payload table as casts. The implicit
contract lives in N call sites instead of one signature, so changing any
payload shape — say, moving the zero-return protocol off the empty-tuple
handle, or adding a payload variant to the op cache — means finding and
auditing every blind cast by hand. Grep does not even find them all: the
op-cache consumers are warning-free checked downcasts, and the injection
chain has no cast at all. That audit cost is precisely how the code in this
document got hard to change, and it compounds: each new caller copies the
casts, adding one more site the next change must find.

Typed boundaries invert this. When `f_future` is `CompletableFuture<R>`, a
payload-shape change is a type change, and javac enumerates every affected
call site as a build error — the compiler performs the audit that today is a
manual, easy-to-miss search. The owner-safety angle is the same inversion:
the `Container.getRuntimeOpCache(op, category, Class<T>)` pattern
(`Container.java:143-171`), already cited above as the model, keys the cache
by the owning container and pays one `type.cast` at the boundary, so "wrong
container's object" and "wrong payload type" both fail immediately, at the
boundary, with the owner in hand for diagnostics — instead of leaking into
another container's fiber as a latent value that only the ownership
instrumentation can trace back. Nine of the eighteen owner-sensitive site
groups classified above currently maintain such invariants entirely by hand;
each one converted to a typed, owner-asserting boundary is one less protocol
that must be re-proved from scratch every time someone touches it.
