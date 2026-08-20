# Must-Fix Runtime Race Inventory

This document lists the state patterns that are actually unsafe under parallel
startup or parallel container execution. These are not just style issues. They
can publish the wrong container-owned object, expose a construction struct, or
let another fiber observe a lifecycle state that never existed as a coherent
state.

Startup-race counts use `master` as the baseline and call out the current
branch remainder where this branch fixes part of a category. Broader raw-access
and lazy-publication counts are scan signals generated on branch
`lagergren/lazy-instance` on 2026-08-20.

## Summary

| Priority | Broken pattern | Signal | Failure mode | Required replacement |
| --- | --- | --- | --- | --- |
| Must fix | Mutable native template `INSTANCE` fields | `master`: 143 mutable template `INSTANCE` fields and 139 constructor assignments. This branch fixes 79 fields / 75 assignments and leaves 64/64. | Last writer wins across containers; constructor `this` escape | `NativeTemplates` central key table plus container/frame lookup |
| Must fix | Static runtime-owned metadata | `master`: 151 field-shaped runtime/template static metadata fields after excluding `INSTANCE`. This branch fixes 61 and leaves 90. | Type/composition/method/handle values from one owner reused in another owner | Owner-scoped final `Lazy`, grouped info records, or owner-owned `ConcurrentMap` |
| Must fix | Raw enum handles returned through public/native paths | 83 raw enum accessor references, including definitions/comments; several public helper groups still return raw handles | Natural enum construction struct escapes as if it were the finalized enum singleton | `ensureEnumByName`, `ensureEnumByOrdinal`, or `Utils.ensureInitializedEnum` on public paths |
| Must fix | Manual lazy publication in shared runtime/asm objects | 111 strong same-field lazy-init matches in runtime/asm | Plain field read/write with no happens-before edge; duplicate, stale, partial, or wrong-owner state | Final `Lazy`, `ConcurrentMap.computeIfAbsent`, or explicit atomic/locked state |
| Must fix | Split lifecycle state across several fields | `SingletonConstant` was the known concrete case and is fixed in this branch | Fibers see mixed handle/owner/waiter state; false recursion or missed wait | One immutable state snapshot in `AtomicReference<State>` or one lock |

## Mutable Template INSTANCE

Status: exact defect category.

Every mutable native-template `INSTANCE` is a process-global pointer to a
container-owned object. Most legacy templates assign it from the constructor,
which also publishes `this` before construction and `initNative()` have
completed.

Full list and audit commands:
[state-inventory.md#mutable-template-instance-inventory](state-inventory.md#mutable-template-instance-inventory).

Why this is broken:

- Container A can initialize a template, then container B can overwrite the
  same static field.
- Code running for A can later read B's template, B's `f_container`, B's pool,
  or metadata computed from B.
- A constructor assignment can expose an object before subclass fields and
  native metadata are initialized.

Required replacement:

- Add a private immutable key to `NativeTemplates` and expose a named accessor:

  ```java
  private static final NativeTemplateRef<X> X_KEY =
          NativeTemplateRef.of("template.Name", X.class);

  public X x() {
      return get(X_KEY);
  }
  ```

- Resolve through the active owner:

  ```java
  X template = NativeTemplates.get(frame).x();
  X template = NativeTemplates.get(container).x();
  ```

- Delete `INSTANCE = this`.
- Move derived metadata to final `Lazy` fields on the template or to a
  container-owned cache.

## Static Runtime-Owned Metadata

Status: exact defect category for fields whose values are derived from
`Container`, `ConstantPool`, `ClassStructure`, runtime handles, enum templates,
or native templates.

Broad current-branch audit command:

```bash
rg -n --pcre2 "^\s*(?:public|protected|private)?\s*static\s+(?!final\b)(?:Map<[^;=()]+>|TypeConstant|TypeComposition|ClassTemplate|ClassComposition|MethodStructure|MethodConstant|SignatureConstant|ArrayConstant|ArrayHandle|ObjectHandle|StringHandle|TupleHandle|EnumHandle|BooleanHandle|xEnum|x[A-Z][A-Za-z0-9_]*)\s+(?!INSTANCE\b)[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)" \
  javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/Utils.java | sort -u
```

Count with this broader command:

```text
master: 151
current branch: 90
fixed in this branch: 61
```

Representative current branch hits:

```text
javatools/src/main/java/org/xvm/runtime/Utils.java:1781:    private static ClassTemplate     ANNOTATION_TEMPLATE;
javatools/src/main/java/org/xvm/runtime/Utils.java:1782:    private static ClassTemplate     ANNOTATION_TEMPLATE_TEMPLATE;
javatools/src/main/java/org/xvm/runtime/Utils.java:1783:    private static ClassTemplate     ARGUMENT_TEMPLATE;
javatools/src/main/java/org/xvm/runtime/Utils.java:1784:    private static ClassTemplate     RT_PARAMETER_TEMPLATE;
javatools/src/main/java/org/xvm/runtime/Utils.java:1785:    private static MethodStructure   ANNOTATION_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/Utils.java:1786:    private static MethodStructure   ANNOTATION_TEMPLATE_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/Utils.java:1787:    private static MethodStructure   ARGUMENT_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/Utils.java:1788:    private static MethodStructure   RT_PARAMETER_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/Utils.java:1789:    private static MethodStructure   LIST_MAP_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/Utils.java:1790:    private static MethodStructure   STRING_VALUE_OF;
javatools/src/main/java/org/xvm/runtime/Utils.java:1791:    private static TypeConstant      ANNOTATION_ARRAY_TYPE;
javatools/src/main/java/org/xvm/runtime/Utils.java:1792:    private static TypeConstant      ARGUMENT_ARRAY_TYPE;
javatools/src/main/java/org/xvm/runtime/Utils.java:1793:    private static SignatureConstant SIG_FREEZE;
javatools/src/main/java/org/xvm/runtime/Utils.java:1794:    private static SignatureConstant SIG_GET_RESOURCE;
javatools/src/main/java/org/xvm/runtime/Utils.java:1795:    private static SignatureConstant SIG_INJECT;
javatools/src/main/java/org/xvm/runtime/template/_native/crypto/xRTKeyStore.java:581:    private static TypeConstant s_typeNamedPassword;
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xCPDirectory.java:56:    private static MethodStructure s_constructor;
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xCPFile.java:56:    private static MethodStructure s_constructor;
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xCPFileStore.java:159:    private static MethodStructure s_constructor;
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSDirectory.java:131:    private static MethodStructure s_constructor;
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSFile.java:509:    private static MethodStructure s_constructor;
javatools/src/main/java/org/xvm/runtime/template/_native/fs/xOSStorage.java:366:    private static MethodStructure s_methodOnEvent;
javatools/src/main/java/org/xvm/runtime/template/_native/lang/src/xRTCompiler.java:501:    private static MethodConstant GET_MODULE_ID;
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:312:    private static TypeConstant RETURN_TYPE;
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:313:    private static TypeConstant PARAM_TYPE;
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:314:    private static TypeConstant RTRETURN_TYPE;
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:315:    private static TypeConstant RTPARAM_TYPE;
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:317:    private static xConst RTRETURN_TEMPLATE;
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:318:    private static xConst RTPARAM_TEMPLATE;
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:320:    private static TypeComposition RETURN_ARRAY;
javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTSignature.java:321:    private static TypeComposition PARAM_ARRAY;
javatools/src/main/java/org/xvm/runtime/template/_native/temporal/xNanosTimer.java:522:    private static TypeComposition s_clzDuration;
javatools/src/main/java/org/xvm/runtime/template/annotations/xAtomic.java:253:    protected static Map<TypeConstant, xAtomic> NUMBER_TEMPLATES;
javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:43:    public static TypeConstant TYPE;
javatools/src/main/java/org/xvm/runtime/template/annotations/xFuture.java:44:    public static xEnum COMPLETION;
javatools/src/main/java/org/xvm/runtime/template/collections/xByteArray.java:230:    private static TypeComposition INT8_ARRAY_CLZ;
javatools/src/main/java/org/xvm/runtime/template/collections/xByteArray.java:231:    private static TypeComposition INT16_ARRAY_CLZ;
javatools/src/main/java/org/xvm/runtime/template/collections/xByteArray.java:232:    private static TypeComposition INT64_ARRAY_CLZ;
javatools/src/main/java/org/xvm/runtime/template/collections/xByteArray.java:233:    private static TypeComposition FLOAT64_ARRAY_CLZ;
javatools/src/main/java/org/xvm/runtime/template/collections/xTuple.java:47:    public static TupleHandle H_VOID;
javatools/src/main/java/org/xvm/runtime/template/maps/xListMap.java:138:    private static MethodStructure CONSTRUCTOR;
javatools/src/main/java/org/xvm/runtime/template/reflect/xClass.java:504:    private static TypeConstant CLASS_ARRAY_TYPE;
javatools/src/main/java/org/xvm/runtime/template/reflect/xRef.java:1187:    private static SignatureConstant s_sigGet;
javatools/src/main/java/org/xvm/runtime/template/reflect/xVar.java:259:    protected static SignatureConstant s_sigSet;
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:454:    public static StringHandle EMPTY_STRING;
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:455:    public static StringHandle EMPTY_ARRAY;
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:456:    public static StringHandle ZERO;
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:457:    public static StringHandle ONE;
javatools/src/main/java/org/xvm/runtime/template/text/xString.java:459:    private static MethodStructure METHOD_APPEND_TO;
javatools/src/main/java/org/xvm/runtime/template/xBoolean.java:19:    public static BooleanHandle TRUE;
javatools/src/main/java/org/xvm/runtime/template/xBoolean.java:20:    public static BooleanHandle FALSE;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:780:    private static MethodStructure FN_ESTIMATE_LENGTH;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:781:    private static MethodStructure FN_APPEND_TO;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:782:    private static MethodStructure FN_FREEZE;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:783:    private static MethodStructure RANGE_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:784:    private static MethodStructure NIBBLE_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:785:    private static MethodStructure TIME_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:786:    private static MethodStructure DATE_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:787:    private static MethodStructure TIMEOFDAY_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:788:    private static MethodStructure DURATION_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:789:    private static MethodStructure VERSION_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:790:    private static MethodStructure PATH_CONSTRUCT;
javatools/src/main/java/org/xvm/runtime/template/xConst.java:792:    private static SignatureConstant HASH_SIG;
javatools/src/main/java/org/xvm/runtime/template/xException.java:351:    private static ClassComposition s_clzDeadlock;
javatools/src/main/java/org/xvm/runtime/template/xException.java:352:    private static ClassComposition s_clzException;
javatools/src/main/java/org/xvm/runtime/template/xException.java:353:    private static ClassComposition s_clzIllegalArgument;
javatools/src/main/java/org/xvm/runtime/template/xException.java:354:    private static ClassComposition s_clzIllegalState;
javatools/src/main/java/org/xvm/runtime/template/xException.java:355:    private static ClassComposition s_clzInvalidType;
javatools/src/main/java/org/xvm/runtime/template/xException.java:356:    private static ClassComposition s_clzNotImplemented;
javatools/src/main/java/org/xvm/runtime/template/xException.java:357:    private static ClassComposition s_clzOutOfBounds;
javatools/src/main/java/org/xvm/runtime/template/xException.java:358:    private static ClassComposition s_clzOutOfMemory;
javatools/src/main/java/org/xvm/runtime/template/xException.java:359:    private static ClassComposition s_clzReadOnly;
javatools/src/main/java/org/xvm/runtime/template/xException.java:360:    private static ClassComposition s_clzSizeLimited;
javatools/src/main/java/org/xvm/runtime/template/xException.java:361:    private static ClassComposition s_clzStackOverflow;
javatools/src/main/java/org/xvm/runtime/template/xException.java:362:    private static ClassComposition s_clzTimedOut;
javatools/src/main/java/org/xvm/runtime/template/xException.java:363:    private static ClassComposition s_clzTypeMismatch;
javatools/src/main/java/org/xvm/runtime/template/xException.java:364:    private static ClassComposition s_clzUnsupported;
javatools/src/main/java/org/xvm/runtime/template/xException.java:365:    private static ClassComposition s_clzDivisionByZero;
javatools/src/main/java/org/xvm/runtime/template/xException.java:366:    private static ClassComposition s_clzPathException;
javatools/src/main/java/org/xvm/runtime/template/xException.java:367:    private static ClassComposition s_clzFileNotFoundException;
javatools/src/main/java/org/xvm/runtime/template/xException.java:368:    private static ClassComposition s_clzAccessDeniedException;
javatools/src/main/java/org/xvm/runtime/template/xException.java:369:    private static ClassComposition s_clzFileAlreadyExistsException;
javatools/src/main/java/org/xvm/runtime/template/xException.java:370:    private static ClassComposition s_clzIOException;
javatools/src/main/java/org/xvm/runtime/template/xException.java:371:    private static ClassComposition s_clzIOIllegalUTF;
javatools/src/main/java/org/xvm/runtime/template/xException.java:373:    private static MethodStructure METHOD_FORMAT_EXCEPTION;
javatools/src/main/java/org/xvm/runtime/template/xNullable.java:16:    public static EnumHandle NULL;
javatools/src/main/java/org/xvm/runtime/template/xObject.java:17:    public static ClassComposition CLASS;
javatools/src/main/java/org/xvm/runtime/template/xOrdered.java:18:    public static EnumHandle LESSER;
javatools/src/main/java/org/xvm/runtime/template/xOrdered.java:19:    public static EnumHandle EQUAL;
javatools/src/main/java/org/xvm/runtime/template/xOrdered.java:20:    public static EnumHandle GREATER;
```

Why this is broken:

- These values are not JVM constants. They carry pool, container, template, or
  handle identity.
- Two containers can race to initialize the same static field. The winner is
  arbitrary from the other container's point of view.
- Related fields can be populated from different owners, creating a metadata
  graph that is not valid in any runtime world.

Required replacement:

- Unkeyed metadata: final `Lazy<T>` on the owning template.
- Related metadata: final `Lazy<InfoRecord>` that computes all values from the
  same owner.
- Keyed metadata: owner-owned `ConcurrentMap<K, V>` or
  `ConcurrentMap<K, Lazy<V>>`.
- Runtime handles and enum values: prove they are true JVM-wide handles or move
  them behind container/frame initialized accessors.

## Raw Natural-Enum Handles

Status: exact defect category when a raw `EnumHandle` crosses a public/native
boundary without `Utils.ensureInitializedEnum(...)`.

Audit command:

```bash
rg -n "getEnumByName|getEnumByOrdinal" \
  javatools/src/main/java/org/xvm/runtime/template \
  javatools/src/main/java/org/xvm/runtime/MainContainer.java \
  javatools/src/main/java/org/xvm/runtime/Utils.java | sort
```

Current signal:

```text
83 raw enum accessor references, including definitions and comments
```

Branch-covered groups:

- `xRTComponentTemplate.makeFormatHandle(...)` still returns a raw helper
  handle internally, but public property assignment uses
  `Utils.assignInitializedEnum(...)`.
- `xRTType.makeAccessHandle(...)`, `xRTType.makeFormHandle(...)`,
  `xRTTypeTemplate.makeAccessHandle(...)`, and
  `xRTTypeTemplate.makeFormHandle(...)` still return raw helper handles
  internally, but their public/native return paths wrap the handle before
  publishing it.
- `xRTDelegate` and `xArray` mutability public properties use
  `Utils.assignInitializedEnum(...)`, and `xArray` constructor arguments use
  `ensureEnumByOrdinal(...)` plus deferred argument handling.
- `xRTServiceControl.SERVICE_STATUS` is fixed in this branch by moving the enum
  template to `f_templateServiceStatus`.

High-risk groups still requiring review:

- `xBoolean`, `xNullable`, and `xOrdered` assign static enum handles during
  `initNative()`.
- `xFuture.COMPLETION` is still a static enum template cache; its current public
  property path calls `Utils.assignInitializedEnum(...)`, but the static cache
  ownership is still wrong.
- Any remaining public/native raw `getEnumByName(...)` or
  `getEnumByOrdinal(...)` path not listed above must be reviewed before this
  category can be considered globally closed.

Why this is broken:

- `xEnum.makeEnumHandle(...)` creates a struct first. The finalized enum
  singleton may not exist yet.
- `getEnumByName(...)` and `getEnumByOrdinal(...)` can return that construction
  struct during startup.
- Returning or assigning the raw handle through a public/native path exposes an
  object with the wrong composition. PR #534's
  `ParameterTemplate.Category.TypeParameter:struct` failure is this class of
  bug.

Required replacement:

- Public/native return paths should use `ensureEnumByName(frame, name)`,
  `ensureEnumByOrdinal(frame, ordinal)`, or
  `Utils.ensureInitializedEnum(frame, hEnum)`.
- Helpers that return raw `EnumHandle` must be internal-only and documented as
  not crossing a public boundary.
- Static enum-template caches must move to owner-scoped final `Lazy<xEnum>` or
  container-owned lookup.

## Manual Lazy Publication

Status: exact defect category when the owner object is shared by runtime
threads or containers; must-review elsewhere.

Audit command:

```bash
rg -U --pcre2 -c "if\s*\(\s*((?:this\.)?(?:m_|s_)[A-Za-z][A-Za-z0-9_]*)\s*==\s*null\s*\)\s*\{[\s\S]{0,240}\1\s*=" \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm | awk -F: '{s+=$2} END {print s}'
```

Current count:

```text
111 strong same-field lazy-initialization matches in runtime/asm
```

Runtime-template subset after this branch:

```text
javatools/src/main/java/org/xvm/runtime/template/text/xRegEx.java:293:            if (m_pattern == null) {
javatools/src/main/java/org/xvm/runtime/template/text/xRegEx.java:294:                m_pattern = Pattern.compile(f_regex, (int) f_nFlags);
```

Why this is broken in shared owners:

- A plain read/write pair has no happens-before edge.
- A racing reader can observe stale null, duplicate computation, or partially
  related state.
- Duplicate computation is not harmless when the value is tied to a
  `ConstantPool`, `Container`, `ClassStructure`, or runtime owner.
- A null check cannot represent in-progress, completed, aborted, and waiting
  states.

Required replacement:

- Immutable unkeyed cache: final `Lazy`.
- Keyed cache: owner-owned `ConcurrentMap.computeIfAbsent`.
- Recursion/lifecycle: `AtomicReference<State>` or lock-protected transitions.
- Compiler-only cache: document confinement now and convert before enabling
  parallel or incremental compilation over the same objects.

## Split Lifecycle State

Status: exact defect category.

The known concrete case is `SingletonConstant`, which this branch replaces with
one atomic `InitState`. Similar designs should be rejected in review when one
logical lifecycle is represented by several mutable fields:

- current owner,
- current handle,
- in-progress marker,
- waiter future,
- abort/error flag.

Why this is broken:

- Another fiber can observe a handle without the matching owner or a waiter
  without the matching initialization attempt.
- Same-fiber recursion and other-fiber waiting become timing-dependent.
- Error cleanup can complete or clear only part of the state.

Required replacement:

- Store one immutable state snapshot in `AtomicReference<State>`.
- Use CAS for transitions and complete waiters after the successful transition.
- Use a lock only if it covers the entire state transition.
- Do not use `Lazy`; this is not a synchronous compute-once value.
