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
| Must fix | Mutable native template `INSTANCE` fields | `master`: 143 mutable template `INSTANCE` fields and 139 constructor assignments. This branch fixes all 143 fields and all 139 constructor assignments. | Last writer wins across containers; constructor `this` escape | `NativeTemplates` central key table, existing container template cache, plus container/frame lookup |
| Must fix | Static runtime-owned metadata | `master`: 151 field-shaped runtime/template static metadata fields after excluding `INSTANCE`. This branch fixes 145 and leaves 6. | Type/composition/method/handle values from one owner reused in another owner | Owner-scoped final `Lazy`, grouped info records, or owner-owned `ConcurrentMap` |
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
current branch: 6
fixed in this branch: 145
```

Representative current branch hits:

```text
javatools/src/main/java/org/xvm/runtime/template/xBoolean.java:19:    public static BooleanHandle TRUE;
javatools/src/main/java/org/xvm/runtime/template/xBoolean.java:20:    public static BooleanHandle FALSE;
javatools/src/main/java/org/xvm/runtime/template/xNullable.java:16:    public static EnumHandle NULL;
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
- `xConst` and `xException` no longer use static metadata caches. Their helper
  methods, hash signature, exception classes, and format method are grouped in
  owner-scoped `Lazy` info records.

High-risk groups still requiring review:

- `xBoolean`, `xNullable`, and `xOrdered` assign static enum handles during
  `initNative()`.
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

## Stress-Discovered Runtime Issues

Status: separate runtime failures found while validating this branch.

During stress validation on 2026-08-20,
`manualTests:runParallelStress -PstressIterations=2 -PstressModules=TestServices`
failed in the runner console path:

```text
ecstasy:TypeMismatch: Expected "immutable Array<Char>", actual "Array<Char>"
    at collections.Array.add(Array.Element) (Array.x:418)
    at text.StringBuffer.commitBuf() (StringBuffer.x:630)
    at ConsoleBack.print(Object, Boolean) (runner.x:90)
```

This was not a Java static owner-cache race. It was a deterministic
`StringBuffer` chunk mutability invariant bug: large immutable string chunks
could make the committed chunk list reject a later mutable append buffer. The
fix and proof are documented in
[stress-discovered-runtime-issues.md](stress-discovered-runtime-issues.md).

The broader lesson remains relevant to this inventory: the parallel runner is
valuable because it turns hidden representation and ownership assumptions into
observable crashes. Future waves should continue to record any such finding as a
separate issue with a focused reproducer and a post-fix stress command.

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
