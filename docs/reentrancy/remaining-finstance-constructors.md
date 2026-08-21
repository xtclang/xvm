# Removed `fInstance` Constructors

This note records the removal of the legacy three-argument native-template
constructors on this branch and the replacements for the last five semantic
uses.

The old `boolean fInstance` shape mixed two different ideas:

- constructor-time publication of mutable static `INSTANCE` fields, which is a
  must-fix race and has been removed from the converted templates in this
  branch;
- a role distinction between the canonical native base template and a
  derived/fallback template, which is still real for a small number of classes
  but should be expressed explicitly instead of as a generic boolean.

In the migrated architecture, the runtime no longer needs a generic
`fInstance` constructor parameter. Canonical native templates are found through
`Container.nativeTemplates()`, and the one hierarchy that still needs a role
distinction uses an explicit `NativeRole` instead of an unlabeled boolean.

## Current Count

Current scan:

```bash
rg -n "public [A-Za-z0-9_]+\(Container container, ClassStructure structure, boolean fInstance\)" \
  javatools/src/main/java/org/xvm/runtime/template -g '*.java'
```

Result after the final cleanup in this branch:

- 0 native-template classes expose a `boolean fInstance` constructor.
- 0 native-template classes have an `f_fInstance` field.
- 73 ignored-flag constructors were removed and now expose only
  `(Container, ClassStructure)`.
- The reflective boolean-constructor fallback in
  `NativeContainer.instantiateNativeTemplate(...)` has been removed.

## Final Semantic Uses

These were the last constructors where the flag changed behavior. They were not
current mutable-static `INSTANCE` races, because the state selected by the flag
was owner-local. They were still worth removing because a generic boolean role
is vague, easy to misuse, and forced `NativeContainer` to keep a hidden fallback
constructor convention.

| File | What `fInstance` used to do | What would break if it were removed naively | Replacement in this branch |
| --- | --- | --- | --- |
| `reflect/xRef.java` | Marked the canonical native `Ref` template; only that template owns the native `Identity` child and its native rebased inception/get-signature metadata. Derived Ref-like templates delegate to the owner-local canonical `Ref`. | The canonical template might fail to register `Identity`, or derived templates could compute their own rebased metadata instead of delegating to the canonical owner. | `xRef.NativeRole.CANONICAL` for the public constructor and `NativeRole.DERIVED` for `@Inject`/`Var`-derived templates. The role is explicit and owner-local. |
| `reflect/xVar.java` | Marked the canonical native `Var` template; only that template owns the rebased inception/set-signature metadata. Derived Var-like templates delegate to owner-local canonical `Var`. | Derived templates such as `@Lazy`/`@Future` could get the wrong inception/signature metadata, or the canonical template could miss its native metadata. | `xVar` uses the inherited `NativeRole`: canonical Var owns Var metadata, while every Var is still a derived Ref and never registers Ref metadata. |
| `text/xChar.java` | Warmed the canonical `Char` small-value cache for code points `0..127`. | `makeHandle(...)` can return null for cached values if the canonical cache is not initialized. | The branch proved there is no derived native Java `xChar` role. The constructor is now ordinary two-argument owner construction, and `initNative()` eagerly fills the same private final owner-local array. |
| `numbers/xNibble.java` | Warmed the canonical `Nibble` value cache for `0..15`. | `makeHandle(...)` can return null for cached nibbles if the canonical cache is not initialized. | Same direct final-array cache as before, with no branch. `Nibble` has no derived native Java template, so every runtime-registered instance is the canonical owner cache. |
| `numbers/xUInt8.java` | Warmed the canonical `UInt8` value cache for `0..255`. | `makeHandle(...)` can return null for cached bytes if the canonical cache is not initialized. | Same direct final-array cache as before, with no branch. `UInt8` has no derived native Java template, so this preserves the hot one-array-index lookup. |

The small-value caches deliberately did not move to `Lazy<JavaLong[]>`.
`Lazy` would be safe, but it would add a volatile read to a hot path that did
not need it. Because those three templates have no derived Java-template role,
the simplest behavior-preserving replacement is to keep the eager private final
owner-local arrays and remove only the dead branch.

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

## Removed Reflective Fallback

`NativeContainer.instantiateNativeTemplate(...)` now requires:

```java
clz.getConstructor(Container.class, ClassStructure.class)
```

There is no fallback to `(Container, ClassStructure, boolean)`. This makes the
old `fInstance` convention impossible for runtime native-template startup: a
template that needs a derived/canonical distinction must model it in its own
type hierarchy, as `xRef`/`xVar` now do with `NativeRole`.

## Validation

Validation for this wave has three parts:

1. `NativeTemplatesTest.removedFInstanceUsersHaveExplicitOwnerState()` fails
   if any of the five classes reintroduces an `f_fInstance` field or the legacy
   three-argument constructor. It also verifies that the three small-value
   caches remain private final instance arrays, not globals.
2. `NativeTemplateOldPatternTest.derivedRefAndVarTemplatesUseOwnerBaseSignatures()`
   models the `Ref`/`Var` metadata hazard and proves why derived templates must
   delegate to the owner-local base template instead of computing metadata from
   their own structures.
3. The same-JVM direct-sequence stress task exercises actual native-template
   startup and ownership validation across repeated interpreter runs. That is
   the runtime-level guard that the reflective fallback removal did not break
   startup behavior.
