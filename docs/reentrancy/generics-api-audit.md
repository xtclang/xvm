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

### Must Fix: Native Template Loading Uses Raw Class

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
