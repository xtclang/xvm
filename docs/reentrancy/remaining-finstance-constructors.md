# Remaining `fInstance` Constructors

This note records the remaining legacy three-argument native-template
constructors on this branch, the trivial constructors already removed, and why
the reflective fallback in `NativeContainer.instantiateNativeTemplate(...)`
cannot be removed yet.

The old `boolean fInstance` shape mixed two different ideas:

- constructor-time publication of mutable static `INSTANCE` fields, which is a
  must-fix race and has been removed from the converted templates in this
  branch;
- a role distinction between the canonical native base template and a
  derived/fallback template, which is still real for a small number of classes
  but should be expressed explicitly instead of as a generic boolean.

In the fully migrated architecture, the runtime should not need a generic
`fInstance` constructor parameter. Canonical native templates should be found
through `Container.nativeTemplates()`, and any class that truly needs a role
distinction should use an explicit role enum, a named factory, or separate
constructors whose names and visibility explain the ownership.

## Current Count

Current scan:

```bash
rg -n "public [A-Za-z0-9_]+\(Container container, ClassStructure structure, boolean fInstance\)" \
  javatools/src/main/java/org/xvm/runtime/template -g '*.java'
```

Result after the trivial cleanup in this branch:

- 5 native-template classes still expose the three-argument constructor.
- Those 5 still use the flag for real canonical-template behavior.
- 73 ignored-flag constructors were removed and now expose only
  `(Container, ClassStructure)`.
- No remaining constructor forwards `fInstance` to a superclass with
  `super(container, structure, fInstance)`.

## Direct Semantic Uses

These are the only remaining constructors where the flag changes behavior.
They are not current mutable-static `INSTANCE` races, because the state selected
by the flag is owner-local. They are still should-fix-soon architecture debt
because a boolean constructor role is vague and easy to misuse.

| File | What `fInstance` does today | What breaks if it is removed naively | Proper replacement |
| --- | --- | --- | --- |
| `reflect/xRef.java` | Marks the canonical native `Ref` template; only that template owns the native `Identity` child and its native rebased inception/get-signature metadata. Derived Ref-like templates delegate to the owner-local canonical `Ref`. | The canonical template might fail to register `Identity`, or derived templates could compute their own rebased metadata instead of delegating to the canonical owner. | Replace the boolean with an explicit role, for example `TemplateRole.CANONICAL` vs `TemplateRole.DERIVED`, or named factories such as `xRef.canonical(...)` and `xRef.derived(...)`. |
| `reflect/xVar.java` | Marks the canonical native `Var` template; only that template owns the rebased inception/set-signature metadata. Derived Var-like templates delegate to owner-local canonical `Var`. | Derived templates such as `@Lazy`/`@Future` could get the wrong inception/signature metadata, or the canonical template could miss its native metadata. | Same explicit role/factory replacement as `xRef`; do not infer this from process-global state. |
| `text/xChar.java` | Warms the canonical `Char` small-value cache for code points `0..127`. | `makeHandle(...)` can return null for cached values if the canonical cache is not initialized. Always warming every derived template would waste memory and may create handles of the wrong template role. | Move the small-value cache into an owner-local `Lazy<JavaLong[]>` on the canonical `xChar` template, resolved through `NativeTemplates.charTemplate()`. |
| `numbers/xNibble.java` | Warms the canonical `Nibble` value cache for `0..15`. | `makeHandle(...)` can return null for cached nibbles if the canonical cache is not initialized. | Move the small-value cache into an owner-local final lazy cache on the canonical template. |
| `numbers/xUInt8.java` | Warms the canonical `UInt8` value cache for `0..255`. | `makeHandle(...)` can return null for cached bytes if the canonical cache is not initialized. | Move the small-value cache into an owner-local final lazy cache on the canonical template. |

These five are not enough reason to keep a generic reflective boolean
convention forever. They are a reason to do the constructor cleanup deliberately
instead of deleting the fallback in `NativeContainer` first.

## Removed Dead Compatibility Signatures

The following constructors accepted `boolean fInstance` but did not read it.
They were should-fix cleanup, not must-fix race work. This branch removes the
dead boolean parameter from them. The owning `Container` and `ClassStructure`
remain explicit, and no static `INSTANCE` field is assigned from these
constructors.

Native array delegate/view leaves:

- `_native/collections/arrays/xRTBitDelegate.java`
- `_native/collections/arrays/xRTBooleanDelegate.java`
- `_native/collections/arrays/xRTFloat64Delegate.java`
- `_native/collections/arrays/xRTInt16Delegate.java`
- `_native/collections/arrays/xRTInt64Delegate.java`
- `_native/collections/arrays/xRTInt8Delegate.java`
- `_native/collections/arrays/xRTNibbleDelegate.java`
- `_native/collections/arrays/xRTSlicingDelegate.java`
- `_native/collections/arrays/xRTUInt8Delegate.java`
- `_native/collections/arrays/xRTViewFromBitToBoolean.java`
- `_native/collections/arrays/xRTViewFromBitToByte.java`
- `_native/collections/arrays/xRTViewFromBitToNibble.java`
- `_native/collections/arrays/xRTViewFromByteToFloat64.java`
- `_native/collections/arrays/xRTViewFromByteToInt16.java`
- `_native/collections/arrays/xRTViewFromByteToInt64.java`
- `_native/collections/arrays/xRTViewFromByteToInt8.java`
- `_native/collections/arrays/xRTViewToBitFromNibble.java`

Filesystem leaves:

- `_native/fs/xOSFileNode.java`
- `_native/fs/xOSFileStore.java`
- `_native/fs/xOSStorage.java`

Reflection/native metadata leaves:

- `_native/reflect/xRTFileTemplate.java`
- `_native/reflect/xRTMethodTemplate.java`
- `_native/reflect/xRTPackageTemplate.java`
- `_native/reflect/xRTProperty.java`
- `_native/reflect/xRTPropertyTemplate.java`
- `_native/reflect/xRTSignature.java`

Annotation templates:

- `annotations/xAtomic.java`
- `annotations/xInject.java`
- `annotations/xLazy.java`

Collection templates:

- `collections/xBitArray.java`
- `collections/xByteArray.java`
- `collections/xNibbleArray.java`

Number templates with ignored flags:

- `numbers/xBit.java`
- `numbers/xCheckedInt16.java`
- `numbers/xCheckedInt32.java`
- `numbers/xCheckedInt64.java`
- `numbers/xCheckedInt8.java`
- `numbers/xCheckedUInt16.java`
- `numbers/xCheckedUInt32.java`
- `numbers/xCheckedUInt64.java`
- `numbers/xCheckedUInt8.java`
- `numbers/xDec128.java`
- `numbers/xDec32.java`
- `numbers/xDec64.java`
- `numbers/xFPLiteral.java`
- `numbers/xFloat16.java`
- `numbers/xFloat32.java`
- `numbers/xFloat64.java`
- `numbers/xInt128.java`
- `numbers/xInt16.java`
- `numbers/xInt32.java`
- `numbers/xInt64.java`
- `numbers/xInt8.java`
- `numbers/xIntLiteral.java`
- `numbers/xIntN.java`
- `numbers/xIntNumber.java`
- `numbers/xNumber.java`
- `numbers/xUInt128.java`
- `numbers/xUInt16.java`
- `numbers/xUInt32.java`
- `numbers/xUInt64.java`
- `numbers/xUIntN.java`

Reflect/text/root templates with ignored flags:

- `reflect/xClass.java`
- `reflect/xClassTemplate.java`
- `reflect/xEnumValue.java`
- `reflect/xEnumeration.java`
- `text/xRegEx.java`
- `text/xString.java`
- `xBoolean.java`
- `xConst.java`
- `xException.java`
- `xNullable.java`
- `xOrdered.java`

## Why The Reflective Fallback Still Exists

`NativeContainer.instantiateNativeTemplate(...)` currently tries:

```java
clz.getConstructor(Container.class, ClassStructure.class)
```

and falls back to:

```java
clz.getConstructor(Container.class, ClassStructure.class, Boolean.TYPE)
```

Removing that fallback today would fail startup for the five direct semantic
users, because they still expose only the three-argument constructor. Their fix
needs an explicit replacement for "canonical native template" before the old
boolean can go away.

## Proper Cleanup Plan

1. Replace the five semantic flag users with explicit owner-local state:
   `xRef`/`xVar` get a role/factory API; `xChar`/`xNibble`/`xUInt8` get final
   owner-local lazy small-value caches.
2. Treat the small-value cache migration as low-to-moderate risk and narrow in
   scope. It touches only `xChar`, `xNibble`, and `xUInt8`; the replacement
   must keep the same precomputed handle identities per container, avoid
   per-call allocation, and prove that two containers get distinct cached
   handles.
3. Treat the `xRef`/`xVar` role migration as moderate risk because it controls
   native child registration and rebased metadata. The replacement should be an
   explicit role enum or named canonical/derived factories, with tests for
   canonical `Ref.Identity` registration and derived-template delegation.
4. Change `NativeContainer.instantiateNativeTemplate(...)` to require only the
   two-argument constructor.
5. Delete the reflective three-argument fallback and the boolean import/path.
6. Add a test or source scan assertion that no
   `(Container, ClassStructure, boolean fInstance)` constructor remains.

This is worthwhile, but it is not the same priority as the original mutable
static `INSTANCE` bug. The must-fix race is owner-bearing data escaping through
process-global mutable fields. The removed constructors were dead API
compatibility; the remaining five are local role flags that should be made
explicit for clarity and future maintenance.
