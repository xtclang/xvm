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
