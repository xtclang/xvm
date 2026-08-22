# Array/List Immutability Study

This is a study and backlog document, not a proposal to replace every Java
array in the XVM Java implementation.

The purpose is narrower: identify where pervasive raw Java array usage makes
ownership, immutability, aliasing, and reentrant execution harder to reason
about, and where `List`, `List.of(...)`, `List.copyOf(...)`, immutable
collection snapshots, or ordinary `ArrayList` builder phases would reduce
complexity without hurting performance.

The main conclusion is that arrays are both essential and overused. They are
the correct representation for hot register storage, primitive payloads,
binary/JIT bridge data, and Java varargs or classloader boundaries. They are a
poor public or long-lived representation for immutable metadata, descriptor
sets, annotation/signature lists, AST child lists, and owner-sensitive tables
where callers need the API to communicate "this cannot be mutated through this
reference".

## Context

Local scan context:

- Branch: `lagergren/lazy-instance`.
- Date: 2026-08-22.
- Source roots scanned with `rg` shell commands only:
  - `javatools/src/main/java`
  - `javatools_utils/src/main/java`
  - `javatools_jitbridge/src/main/java`
  - `plugin/src/main/java`

Related reentrancy documents read before writing this guide:

- [state-inventory.md](state-inventory.md)
- [manual-lazy-cache-audit.md](manual-lazy-cache-audit.md)
- [clone-usage-audit.md](clone-usage-audit.md)
- [modern-java-syntax-audit.md](modern-java-syntax-audit.md)
- [bad-design-decisions-reference.md](bad-design-decisions-reference.md)
- [must-audit-backlog.md](must-audit-backlog.md)
- [fixed-in-this-branch.md](fixed-in-this-branch.md)

Those documents already establish several rules that this study inherits:

- mutable process-global state and owner-bearing static caches are must-fix
  when they can cross `Container`, `ConstantPool`, runtime, fiber, or compiler
  request boundaries;
- `final` protects a field reference, not mutable state behind that reference;
- shallow `clone()` and shallow array copies must not be mistaken for owner
  transfer;
- owner-bearing values need explicit owner parameters, owner-local caches, or
  proof of confinement;
- hot frame/register state can remain mutable only with a clear owner-thread or
  scheduler confinement story;
- syntax modernization is useful only when it preserves owner ordering,
  mutability semantics, and hot-path allocation shape.

## Local Scan Summary

Counts below are broad `rg` line-hit counts. They are useful for scale and
prioritization, not precise AST-level declarations.

| Scan | Count |
| --- | ---: |
| `[]` line hits in the four requested Java roots | 4,503 |
| `[]` line hits in `javatools/src/main/java` | 4,171 |
| `[]` line hits in `javatools_utils/src/main/java` | 193 |
| `[]` line hits in `javatools_jitbridge/src/main/java` | 114 |
| `[]` line hits in `plugin/src/main/java` | 25 |
| `[]` line hits in `javatools/.../runtime` | 1,433 |
| `[]` line hits in `javatools/.../asm` | 1,553 |
| `[]` line hits in `javatools/.../compiler` | 898 |
| `[]` line hits in `javatools/.../javajit` | 225 |
| `[]` line hits in `javatools/.../tool` | 51 |
| public/protected static array declarations in the four roots | 48 |
| public/protected static array declarations in `javatools` | 42 |
| public/protected array fields in `runtime`/`asm`/`compiler` | 75 |
| public/protected array fields in the requested roots, including static | 81 |
| public/protected non-static array fields in the requested roots | 37 |
| likely array field declarations in the requested roots | 2,223 |
| static final array declarations in the requested roots | 149 |
| non-final static array declarations in the requested roots | 0 |
| primitive-array line hits in the requested roots | 1,094 |
| primitive-array line hits in `javatools_jitbridge` plus `javajit` | 123 |
| `ObjectHandle[]` line hits in runtime/ASM | 674 |
| `int[]` line hits in runtime/ASM/compiler | 409 |
| selected metadata array line hits in ASM/compiler (`TypeConstant[]`, `Constant[]`, `Annotation[]`, `Parameter[]`, `MethodBody[]`, `PropertyBody[]`) | 1,166 |
| `.clone()` call sites in the requested roots | 99 |
| `Arrays.copyOf`, `System.arraycopy`, `copyOfRange`, or `toArray(...)` line hits | 335 |
| `new ArrayList`, `List.of`, `List.copyOf`, unmodifiable-list, or stream-list idiom hits | 394 |
| `new ArrayList` hits | 298 |
| `List.of` hits | 74 |
| `List.copyOf` hits | 19 |
| `Collections.emptyList`/`unmodifiable*`/`singletonList` selected hits | 132 |
| `Collectors.toList` hits | 1 |
| `.toList()`/`Stream.toList` hits | 26 |

The counts show why a mechanical rewrite is not realistic. The Java sources use
arrays for several different jobs: ABI surfaces, bytecode/runtime storage,
compiler temporary work arrays, immutable-looking constants, shallow copy-on-
write metadata chains, primitive numeric payloads, generated bridge storage,
and Gradle/JVM interop. Only some of those jobs are improved by collection
abstractions.

## Why Raw Arrays Are A Reentrancy Smell

Raw Java arrays are not inherently unsafe. The problem is that they encode only
element type and length. They do not encode ownership, mutability, publication,
or depth of copy. In this codebase, those missing properties are exactly where
same-JVM and multi-container bugs tend to hide.

### Public And Protected Array Constants

`public static final T[]` looks like a constant, but only the array reference is
final. Any code that can see the reference can write an element:

```java
SomeType.NO_VALUES[0] = foreignOwnerValue;
```

For a zero-length array, there is no element to overwrite. That lowers the
immediate risk, but the API still teaches callers that an array is an
acceptable public constant and that identity sharing is part of the contract.
If the constant later gains one element, or if a caller assumes it can retain
and mutate a returned array, the API has already lost its immutability signal.

The existing `state-inventory.md` scan lists this as "Should Fix Soon: Exposed
Mutable Arrays". The current local scan reproduces the same risk family:

- `javatools/src/main/java/org/xvm/runtime/Utils.java:1895` exposes
  `OBJECTS_NONE`.
- `javatools/src/main/java/org/xvm/runtime/Utils.java:1896` exposes
  `STRINGS_NONE`.
- `javatools/src/main/java/org/xvm/runtime/Utils.java:1897` exposes `NO_NAMES`.
- `javatools/src/main/java/org/xvm/asm/Constant.java:977` exposes `NO_CONSTS`.
- `javatools/src/main/java/org/xvm/asm/Annotation.java:366` exposes
  `NO_ANNOTATIONS`.
- `javatools/src/main/java/org/xvm/asm/Parameter.java:498` exposes
  `NO_PARAMS`.
- `javatools/src/main/java/org/xvm/asm/ast/BinaryAST.java:260` through `:265`
  expose several `NO_*` arrays.
- `javatools_utils/src/main/java/org/xvm/util/Handy.java:2310` through `:2320`
  expose empty byte, char, and string arrays.

This category should not be mixed into the current runtime must-fix branch
unless a specific site is proven to carry owner-bearing runtime state across
containers. Most empty-array constants are design debt, not an urgent
correctness failure.

### Ownership Is Invisible

An array type says `TypeConstant[]`, not "all elements belong to this
`ConstantPool`" or "all elements are logical constants already adopted by the
destination pool". That matters because many XVM Java objects are not pure
values:

- `TypeConstant` and `Constant` usually imply a `ConstantPool`;
- `ObjectHandle` implies a runtime `Container`, `ServiceContext`, or native
  template owner;
- `MethodBody` and `PropertyBody` can point back to owning metadata;
- `Parameter` can contain method-owned dereference state;
- AST nodes are usually compiler-request-local and mutable through staged
  compilation.

An array can carry all of those objects, but it cannot say whether the array is
an owner-local table, a shared immutable snapshot, a shallow copy-on-write
container, or a mutable builder.

This is the same design problem documented for shallow `clone()` in
`clone-usage-audit.md`: copying an array container does not copy or adopt the
owner-bearing elements. If the elements cross owners, each element needs an
explicit proof or explicit adoption/copy path.

### Aliasing And Defensive Copies Are Easy To Miss

When a constructor stores a caller-provided array directly, the caller can
mutate the owner after construction. When a getter returns an internal array,
the caller can mutate the owner without using owner-preserving methods. When a
method returns a `clone()`, the array container is isolated but the elements are
still shared.

That creates three different meanings that look nearly identical in code:

- "this constructor takes ownership of the caller's mutable buffer";
- "this constructor snapshots the caller's elements";
- "this constructor retains a shared immutable metadata sequence".

`List.copyOf(...)` is not a deep copy either, but it makes one important fact
visible: the returned collection cannot be structurally modified. For metadata
sequences, that is often the right default. For owner-bearing elements, it still
must be paired with element ownership checks or element adoption.

### Partial Fill And Null Sentinels Hide State

Arrays invite staged construction:

```java
TypeConstant[] types = new TypeConstant[count];
for (...) {
    if (...) {
        types[i] = type;
    }
}
```

That is appropriate for tight generation loops and fixed-size results. It is a
problem when a partially filled array can escape, or when `null` means several
different things: not computed, failed conversion, no value, default case, or
owner unavailable.

The backlog item in `must-audit-backlog.md` for multi-value constant folding is
an example of this family: a legacy partial `Constant[]` with null slots needs
semantic cleanup, not just syntax cleanup.

### Protected Arrays Extend The Mutation Surface

Protected arrays are less visible than public arrays, but they still widen the
mutation surface to every subclass. That is important in runtime templates and
compiler/ASM metadata because subclasses often participate in owner setup,
native-template initialization, or staged AST processing.

`ClassTemplate` now documents why common descriptor arrays are `protected
static final` rather than public mutable arrays. That is an improvement over
public exposure, but it is still a shared mutable array. A subclass can mutate
`STRING[0]` and change descriptor behavior globally. That makes it a reasonable
separate cleanup candidate, not a current runtime must-fix item.

### Reentrant Execution Magnifies Ordinary Aliasing

In single-threaded code, array mutation bugs often require a caller to mutate
at the wrong time. Reentrant runtime/compiler execution creates more wrong
times:

- callbacks and diagnostics can observe metadata while an owner is still being
  assembled;
- parallel same-JVM executions can share decoded op graphs, constants, or
  template metadata;
- runtime handles can be passed between services or containers;
- compiler AST nodes can be revisited while staged state is still mutable;
- JIT bridge state can be reused under a classloader/type-system owner.

The problem is not that an array read is non-atomic. Reference reads and writes
are atomic. The problem is that array elements are separate mutable variables
with no ownership or compound-invariant boundary. A final array field safely
publishes the reference after construction, but it does not make later element
writes safe or make the element graph immutable.

## Where Arrays Are Still Appropriate

The strongest recommendation in this document is to keep arrays where they are
part of the runtime representation, binary protocol, primitive payload, or JVM
interop boundary.

### Hot Frame, Register, And Opcode Storage

`Frame` is the clearest example:

- `javatools/src/main/java/org/xvm/runtime/Frame.java:69` stores `Op[] f_aOp`.
- `javatools/src/main/java/org/xvm/runtime/Frame.java:73` stores
  `ObjectHandle[] f_ahVar`.
- `javatools/src/main/java/org/xvm/runtime/Frame.java:74` stores
  `VarInfo[] f_aInfo`.
- `javatools/src/main/java/org/xvm/runtime/Frame.java:77` stores
  `int[] f_aiReturn`.
- `javatools/src/main/java/org/xvm/runtime/Frame.java:80` stores
  `int[] f_anNextVar`.

These are real execution registers and instruction arrays. Replacing them with
`List<ObjectHandle>` or `List<Integer>` would add indirection, boxing for
primitive indexes, interface dispatch risk, and worse locality. More
importantly, it would not solve the owner model. The correct proof here is
frame/fiber/scheduler confinement and controlled runtime mutation.

The follow-up should be documentation and encapsulation where possible, not
conversion to collections.

### Runtime Wait-Op Arrays

Examples:

- `javatools/src/main/java/org/xvm/runtime/Frame.java:2706` defines
  `WAIT_FOR_FUTURE`.
- `javatools/src/main/java/org/xvm/runtime/Frame.java:2749` defines
  `WAIT_FOR_IO`.
- `javatools/src/main/java/org/xvm/runtime/Utils.java:1905` defines
  `WAIT_FOR_RELIEF`.

These are static arrays of stateless `Op` implementations. The main concern is
not per-element owner state. The concern is public/protected mutation of the
array contents. If these arrays remain protected and static, they should be
documented as process-wide immutable implementation tables or made private with
factory/accessor methods. They do not need `List` for runtime speed.

### Primitive Arrays And Dense Numeric Delegates

Primitive arrays avoid boxing and are central to performance and storage
layout:

- `javatools/src/main/java/org/xvm/runtime/template/_native/collections/arrays/ByteBasedDelegate.java`
  stores `byte[]`.
- `javatools/src/main/java/org/xvm/runtime/template/_native/collections/arrays/LongBasedDelegate.java`
  stores `long[]`.
- `javatools/src/main/java/org/xvm/runtime/template/_native/collections/arrays/xRTFloat64Delegate.java`
  stores `double[]`.
- `javatools_utils/src/main/java/org/xvm/util/LongList.java:51` uses
  `long[] NO_LONGS` and mutable `long[] m_aVals`.
- `javatools_utils/src/main/java/org/xvm/util/ConstBitSet.java:110` returns
  `m_ab.clone()` for compressed bitset bytes.
- `javatools_utils/src/main/java/org/xvm/util/ConstOrdinalList.java:135`
  returns `m_ab.clone()` for compressed ordinal bytes.

Replacing these with `List<Long>`, `List<Byte>`, or `List<Double>` would be a
performance regression and a memory regression because primitive values would
box. The better cleanup is:

- keep primitive arrays private;
- return `clone()`/`Arrays.copyOf(...)` when exposing payloads;
- document whether a returned empty array is shared;
- use immutable collections only for metadata around the payload, not the
  payload itself.

### Binary Serialization And Payload Boundaries

Byte arrays are the natural representation for class bytes, file contents,
hash digests, compressed bitsets, and XTC/native bridge payloads. Examples:

- `plugin/src/main/java/org/xtclang/plugin/XtcPluginUtils.java:276` reads into
  a byte buffer.
- `plugin/src/main/java/org/xtclang/plugin/runtime/DirectRuntimeFingerprint.java:54`
  hashes classpath bytes.
- `javatools/src/main/java/org/xvm/javajit/NativeTypeSystem.java:157` returns
  generated class bytes.
- `javatools/src/main/java/org/xvm/javajit/ModuleLoader.java:123` tracks
  `Map<String, byte[]>` loaded class bytes.

For this category, the collection question should be "is the byte payload
defensively copied at the owner boundary?" not "should this become
`List<Byte>`?"

### Varargs, Reflection, Classloader, And CLI Interop

Several APIs are array-shaped because the Java/JVM boundary is array-shaped:

- `String...` varargs in plugin helpers.
- `String[]` arguments passed to command-line builders.
- `URL[]` required by `URLClassLoader`.
- `Class<?>[]` required by reflective method lookup.
- `Container[]` used for ownership diagnostics in plugin direct execution.

Representative plugin sites:

- `plugin/src/main/java/org/xtclang/plugin/XtcPluginUtils.java:46` accepts
  `String...` and returns an immutable stream `toList()`.
- `plugin/src/main/java/org/xtclang/plugin/launchers/ForkedCommandLineBuilder.java:21`
  builds a mutable `ArrayList<String>` and returns `args.toArray(String[]::new)`.
- `plugin/src/main/java/org/xtclang/plugin/runtime/DirectRuntimeBuildService.java:115`
  creates `URL[]` for the isolated classloader.
- `plugin/src/main/java/org/xtclang/plugin/runtime/impl/IsolatedDirectExecutor.java:108`
  converts a `Set<Container>` to `Container[]` for diagnostics.

These are good examples of a healthy boundary pattern: use lists for local
construction or immutable request state, then convert to arrays at the API that
requires arrays.

### JIT And JIT Bridge Storage

JIT code uses arrays for slot layout, descriptors, generated bridge state, and
runtime fast paths:

- `javatools/src/main/java/org/xvm/javajit/Ctx.java:61` and `:62` store
  overflow primitive/object registers in `long[] iN` and `Object[] oN`.
- `javatools/src/main/java/org/xvm/javajit/JitMethodDesc.java:77` through
  `:80` store parameter/return descriptor arrays.
- `javatools/src/main/java/org/xvm/javajit/JitTypeDesc.java:120` returns
  `ClassDesc[]` for primitive layout.
- `javatools/src/main/java/org/xvm/javajit/TypeMatrix.java:25` stores
  `OpView[]`.
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/nLongBasedArray.java:70`
  stores `long[] $storage`.
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/collections/Array.Object.java:31`
  stores object-array bridge state.
- `javatools_jitbridge/src/main/java/org/xtclang/ecstasy/text/String.java:212`
  stores string contents in `long[] data`.

These are not good list-conversion targets. They need owner/classloader
documentation, direct field encapsulation where possible, and careful treatment
of generated-code conventions. Lists would hide the storage model rather than
clarify it.

### Fixed-Size Algorithm Temporaries

Arrays are also appropriate for local, fixed-size temporaries when the array
does not escape:

- compiler code generation result arrays;
- `int[]` return-register lists;
- short arrays of `ClassDesc` or `TypeConstant` built to call existing APIs;
- local `boolean[]` flags in hot runtime code;
- local sorting or merge buffers.

For these, clarity can still improve by naming the local variable well. A
local `int[] aiReturn` is often better than a list because the register-index
semantics are obvious and no boxing occurs.

## Where Lists Or Immutable Snapshots Are Likely Better

The best list candidates are not the hot storage arrays. They are arrays whose
real meaning is "sequence of metadata", "zero or more descriptors", "children",
"annotations", or "contributions".

### Metadata Descriptor Lists

Small descriptor arrays are common:

- `javatools/src/main/java/org/xvm/runtime/ClassTemplate.java:2460` through
  `:2466` define common native signature descriptor arrays such as `THIS`,
  `OBJECT`, `STRING`, and `BYTES`.
- `javatools/src/main/java/org/xvm/runtime/template/collections/xArray.java:1018`
  defines `ELEMENT_TYPE`.
- `javatools/src/main/java/org/xvm/runtime/template/_native/collections/arrays/xRTDelegate.java:959`
  defines another `ELEMENT_TYPE`.

These arrays are small, shared, and logically immutable. A future cleanup could
introduce a small descriptor abstraction or immutable list form:

```java
protected static final List<String> STRING = List.of("text.String");
```

or:

```java
private static final List<String> STRING = List.of("text.String");

protected static String[] stringSignature() {
    return STRING.toArray(String[]::new);
}
```

The exact pattern depends on `markNativeMethod` and related APIs. If those APIs
require arrays for performance or compatibility, a private immutable list plus
boundary conversion may not be worth it. The better API-level improvement would
be typed descriptor helpers so subclasses do not pass raw arrays at all.

### Empty Public Metadata Constants

Examples:

- `Annotation.NO_ANNOTATIONS`
- `Parameter.NO_PARAMS`
- `MethodBody.NO_BODIES`
- `Constant.NO_CONSTS`
- `TypeConstant.NO_TYPES`
- `BinaryAST.NO_ASTS`, `NO_EXPRS`, `NO_TYPES`, `NO_REGS`, `NO_ALLOCS`

These are low immediate risk when empty, but they are poor public API signals.
The best future direction depends on compatibility:

- for internal APIs, prefer `List.of()` or private arrays returned through
  typed copy methods;
- for stable array-returning APIs, keep the array but make it private or
  package-private and return `clone()` or `Arrays.copyOf(...)`;
- where callers only iterate, change return types to `List<T>` or
  `Collection<T>`;
- where callers rely on identity or zero-allocation empty arrays, document that
  explicitly and leave the site alone until a larger API cleanup.

### Annotation, Signature, And Parameter Sequences

`Annotation[]`, `Parameter[]`, `TypeConstant[]`, and `Constant[]` appear
throughout ASM and compiler code. The local scan found 1,166 selected metadata
array line hits in ASM/compiler.

Good candidates for immutable snapshots:

- annotation arrays returned from metadata getters;
- method parameter and return lists after method construction completes;
- signature parameter lists after constant-pool ownership is resolved;
- repeated descriptor tuples used only for lookup or declaration;
- compiler conversion candidate lists once inference has produced the final
  answer.

Important caveat: `List.copyOf(...)` would only freeze the container. It would
not adopt `Parameter`, `TypeConstant`, `Constant`, or `MethodBody` elements
into a different owner. Any migration here must retain or improve the explicit
owner-copy behavior introduced by this branch.

### MethodInfo And PropertyInfo Chains

`MethodInfo` and `PropertyInfo` are already part of the active reentrancy
story. This branch fixed constructor-time body owner attachment by building
owner-linked body arrays before assigning final fields. For example,
`MethodInfo` uses a local `MethodBody[] aOwned` and `Arrays.setAll(...)` before
assigning `m_aBody`.

Remaining chain operations still use shallow array copies in places:

- `MethodInfo.nestNarrowingIdentity(...)` clones `getChain()`.
- `MethodInfo.rebaseInto(...)` clones `m_aBody`.
- `PropertyInfo.augmentPropertyChain(...)` creates one-element arrays, clones a
  chain, or appends with `System.arraycopy(...)`.

The correct follow-up is not "replace `MethodBody[]` with `List<MethodBody>`"
as a blind rule. The correct question is whether a method/property chain is:

- a fixed immutable logical sequence after construction;
- an owner-linked sequence whose elements must all belong to one `TypeInfo`;
- a temporary merge buffer during type-info construction;
- or a hot chain scanned frequently enough that arrays remain preferable.

If it is a fixed immutable logical sequence, `List<MethodBody>` stored as
`List.copyOf(...)` may communicate the state model better than a final array.
If the code relies on fast indexed scans in central type-info paths, arrays may
remain appropriate with private final storage and copy-on-write helper names.

### Compiler AST Child Lists

The compiler AST already uses `List` heavily:

- `ForStatement` has `List<Statement> init` and `update`.
- `ConditionalStatement` has `List<AstNode> conds`.
- `MethodDeclarationStatement` has `List<Token>`, `List<AnnotationExpression>`,
  `List<Parameter>`, and other child lists.
- `TypeCompositionStatement` has child lists for modifiers, annotations,
  parameters, type arguments, expressions, and compositions.
- `StatementBlock` stores `List<Statement> stmts`.

This is a good design direction for AST shape: child lists express variable
arity better than arrays. The reentrancy question is whether those lists are
request-confined mutable builder state or immutable AST snapshots. Current
compiler docs treat much AST mutation as request-local, but incremental and
parallel compilation will need stronger proof.

Future cleanup candidates:

- constructor-time `List.copyOf(...)` for AST child lists that are never
  structurally modified after parse;
- `ArrayList` only for parse/build phases, followed by immutable ownership;
- explicit mutable fields for compiler stages that rewrite children;
- no conversion for transient generated `ExprAST[]`, `BinaryAST[]`,
  `Register[]`, or `TypeConstant[]` local arrays used during code generation.

### Non-Hot Ownership Tables

Some arrays represent ownership tables but are not hot per-op storage. These
need audit before migration:

- JIT `ModuleLoader[]` and `ModuleStructure[]` owner inputs;
- `JitParamDesc[]` parameter descriptors;
- plugin `Container[]` ownership diagnostics;
- ASM method/property metadata chains;
- compiler contribution and import preference lists.

For non-hot tables that are logically immutable after construction,
`List.copyOf(...)` on input and `List<T>` fields often improve both API clarity
and defensive-copy behavior. For JIT tables, array storage may still be the
better representation if descriptors are passed to generated code or indexed in
hot paths.

### Plugin Request State

The Gradle plugin is already a useful model:

- `DirectRunRequest` copies `modulePath` and `moduleArgs` with
  `List.copyOf(...)`.
- `DirectCompileRequest` and `DirectTestRequest` do the same for request lists.
- `DefaultXtcRuntimeExtension` initializes `ListProperty` values with
  `List.of()`.
- command-line builders use `ArrayList<String>` as a local mutable builder and
  return `String[]` only at the process-launch boundary.
- `PluginRuntimeClassLoader` stores child-first prefixes in `List.of(...)` and
  accepts `URL[]` because `URLClassLoader` requires it.

This is close to the desired pattern: immutable request/config snapshots at
owner boundaries, mutable `ArrayList` while building, arrays only for APIs that
require arrays.

## Performance Implications

The performance question is not "array is fast, list is slow". It is more
specific.

### Indexing

Array indexing is O(1), compact, and easy for HotSpot to optimize. `ArrayList`
indexing is also O(1), but it is a method call through a list abstraction unless
the JIT inlines it. In most cold metadata code, that difference does not matter.
In runtime register loops and JIT bridge storage, it does.

Guideline:

- keep arrays for `Frame` registers, opcode arrays, return indexes, primitive
  delegates, and generated/JIT slot layout;
- allow lists for metadata sequences scanned during setup, declaration,
  diagnostics, or compilation phases where clarity dominates.

### Memory Overhead

A Java array is one object containing the elements. An `ArrayList` is at least
two objects when it owns its backing array: the `ArrayList` object and the
`Object[]` backing store. Immutable JDK lists have specialized small forms for
some sizes and compact forms for larger sizes, but they are still object
wrappers around references.

That overhead is irrelevant for a handful of method descriptor lists and
important for millions of register/index arrays or tiny per-handle caches.

This is why `fixed-in-this-branch.md` explicitly keeps some per-handle string
memoization out of the current PR: adding `Lazy` or wrapper objects to each hot
value can be worse than the old plain field when the issue is only a
should-fix cleanup.

### Primitive Boxing

Standard `List<Integer>`, `List<Long>`, `List<Byte>`, and similar collections
box primitives. That is usually disqualifying for:

- runtime register indexes;
- dense bit/byte/nibble/integer delegates;
- compressed bitset payloads;
- JIT primitive slot arrays;
- binary serialization and hashing buffers.

Do not replace primitive arrays with boxed lists unless the collection is tiny,
cold, and the readability benefit is proven to outweigh the allocation cost.

### Escape Analysis

HotSpot can eliminate some short-lived array or collection allocations when
they do not escape. That helps local temporaries, but it cannot be assumed for:

- arrays returned from methods;
- arrays stored in fields;
- arrays captured by lambdas;
- arrays passed to reflective/native/classloader APIs;
- arrays retained in constants, handles, or JIT bridge objects.

For field storage and public API shape, choose the representation with the
right ownership contract first. Do not rely on escape analysis to paper over an
extra wrapper in long-lived runtime objects.

### Allocation Churn

`List.copyOf(...)` and `toArray(...)` allocate. `ArrayList` growth can allocate
several backing arrays if capacity is not known. Conversely, hand-built arrays
often allocate exact size once. That matters in hot loops and code generation
inner loops.

Good migration patterns avoid churn:

- build with `new ArrayList<>(expectedSize)` when count is not fixed;
- freeze once with `List.copyOf(...)`;
- return immutable empty/singleton lists with `List.of(...)`;
- convert to array only at the boundary that needs an array;
- do not convert array to list and back on every runtime execution.

### Immutable Lists Versus Unmodifiable Views

`List.copyOf(...)` creates an unmodifiable snapshot, rejecting null elements.
`List.of(...)` creates an unmodifiable list, also rejecting null elements.
`Collections.unmodifiableList(list)` creates an unmodifiable view over the
same mutable list.

For ownership cleanup, snapshots are usually better than views. Views preserve
aliasing and can still observe later mutation through the original list.
Views are acceptable when the owner deliberately wants live read-only exposure
of a private mutable collection and the mutation protocol is documented.

### Streams, Lambdas, And Hot Loops

Streams and lambdas can make cold metadata queries clearer:

```java
return Arrays.stream(bodies).anyMatch(body -> id.equals(body.getIdentity()));
```

But the existing `modern-java-syntax-audit.md` guidance should remain the rule:

- use `Arrays.setAll(...)` for pure in-place element replacement only after the
  owner object is fully constructed and not visible;
- do not capture constructor `this` in lambdas or method references;
- keep imperative loops when they are hot, allocate nothing, have early exits,
  mutate several structures, or need checked-exception control flow;
- be careful with `Stream.toList()`, because it returns an unmodifiable list
  and may change caller mutability expectations;
- do not convert recursive anonymous classes blindly, because anonymous-class
  `this` and lambda `this` have different meanings.

## Migration Categories

### Must Fix: Public Mutable Arrays With Non-Empty Owner-Bearing Contents

This is the highest-risk category, but it needs proof per site. A public or
protected static array becomes must-fix when:

- it has mutable, owner-bearing, or runtime-bearing elements;
- callers can write different owner elements into the array;
- the array is process-global and reused across containers, pools, services, or
  same-JVM executions;
- mutation can invalidate cached hash/equality or metadata invariants.

Likely closure:

- make the array private;
- expose `List<T>` as `List.of(...)` or `List.copyOf(...)`;
- or return defensive array copies for compatibility;
- add tests that mutation through the old public path is impossible;
- add owner assertions if the elements are owner-bearing.

Current scan examples to inspect first:

- non-empty public/protected static descriptor arrays in `ClassTemplate`,
  `xArray`, and `xRTDelegate`;
- `LongLong.ZEROx2` and `OVERFLOWx2`, which are non-empty public arrays even if
  the elements themselves are value-like;
- JIT `ClassDesc[]` public constants in `Builder`, if they are reachable as
  public mutable state.

### Should Fix: Public Empty Array Constants

Empty arrays are low immediate risk, but they are a bad API pattern. They can
be migrated gradually when touched:

- `NO_*` metadata constants can become `List.of()` if callers only iterate;
- stable array-returning APIs can keep a private empty array and clone only if
  the array may be mutated by callers;
- hot zero-allocation internal paths can keep package-private/private empty
  arrays with a comment.

This should not be part of the current runtime must-fix branch unless a site is
already being touched for a real owner bug.

### Should Fix: Immutable Metadata Arrays

Arrays that represent fixed metadata sequences are good list candidates:

- annotation parameter lists after resolution;
- method return/parameter metadata once attached to the owner;
- native signature descriptors;
- AST child lists after parse if the compiler phase does not rewrite them;
- contribution/import preference lists after assembly.

Use `List.copyOf(...)` for snapshots and `List.of(...)` for literals. Keep
`ArrayList` as a mutable local builder, not as a published mutable field.

### Should Not Change: Hot Primitive And Runtime Storage Arrays

Do not convert these without a benchmark and an owner model reason:

- `Frame.f_ahVar`, `f_aInfo`, `f_aiReturn`, `f_anNextVar`;
- opcode arrays used in execution;
- primitive array delegates and compressed payloads;
- JIT `Ctx` register arrays;
- bridge string/number/array storage;
- local code-generation result arrays in hot paths;
- byte arrays for class bytes, hashes, and serialized payloads.

The right improvement is encapsulation, confinement proof, or defensive copies
at API boundaries.

### Audit Needed: Owner-Bearing Object Arrays

This is the largest and most subtle category:

- `ObjectHandle[]` runtime arguments and tuple values;
- `TypeConstant[]`, `Constant[]`, and `Annotation[]` compiler/ASM metadata;
- `Parameter[]` method returns and parameters;
- `MethodBody[]` and `PropertyBody[]` chains;
- JIT descriptor arrays that combine type-system and classloader ownership.

The audit question is not "array or list?" The audit question is:

1. Who owns each element?
2. Is the container mutable after publication?
3. Can the container cross a `Container`, `ConstantPool`, `TypeInfo`, service,
   runtime, classloader, or compiler request boundary?
4. Is a shallow container copy intended and documented?
5. Are element adoption/copy/freeze/proxy semantics explicit?

Only after those questions are answered should the site be migrated.

### Good Existing Pattern: Build Mutable, Publish Immutable

The plugin shows a good pattern:

```java
public DirectRunRequest {
    modulePath = List.copyOf(modulePath);
    moduleArgs = List.copyOf(moduleArgs);
}
```

and:

```java
final List<String> args = new ArrayList<>();
...
return args.toArray(String[]::new);
```

This is often the right direction for non-hot metadata and request state:
mutable builder inside one method or owner, immutable snapshot at the boundary,
array only for the external API that requires it.

## Representative Source Sites

### `javatools`

Runtime and ASM are the dominant array users:

- `runtime`: 1,433 `[]` line hits.
- `asm`: 1,553 `[]` line hits.
- `compiler`: 898 `[]` line hits.
- `javajit`: 225 `[]` line hits.
- `tool`: 51 `[]` line hits.

Representative keep-array sites:

- `Frame.f_ahVar`, `Frame.f_aInfo`, `Frame.f_aiReturn`, and `Frame.f_anNextVar`
  are hot execution storage.
- `xRTDelegate` and concrete array delegates store `ObjectHandle[]`, `byte[]`,
  `long[]`, `char[]`, and `double[]` as runtime array backing storage.
- `CallChain` builds `ObjectHandle[]` argument/register arrays for invocation.
- `BuildContext` and JIT classes use `int[]`, `ClassDesc[]`, and `Op[]` for
  generated-code layout and method processing.

Representative list-candidate sites:

- public empty arrays such as `Constant.NO_CONSTS`, `Annotation.NO_ANNOTATIONS`,
  `Parameter.NO_PARAMS`, `MethodBody.NO_BODIES`, and `BinaryAST.NO_*`;
- `ClassTemplate` descriptor arrays `THIS`, `OBJECT`, `INT`, `STRING`,
  `BOOLEAN`, and `BYTES`;
- `xArray.ELEMENT_TYPE` and `xRTDelegate.ELEMENT_TYPE`;
- method/property chain arrays where the owner-copy semantics are now explicit
  but the published container remains a final array;
- compiler AST child lists that are already `List<T>` but may need
  `List.copyOf(...)` snapshots once parse/build mutation ends.

Representative audit-needed sites:

- `ObjectHandle[]` runtime pass-throughs, especially service/container
  boundaries;
- `MethodStructure.cloneBody()` local constant arrays and source clones;
- `MethodInfo` and `PropertyInfo` chain clone/copy-on-write operations;
- `Parameter[]` delegated method arrays, already tracked in
  `clone-usage-audit.md`;
- `Constant[]` and `TypeConstant[]` compiler inference arrays when they outlive
  the request.

### `javatools_utils`

The utility source root has 193 `[]` line hits. Many are low-risk primitive or
defensive-copy sites:

- `Handy.EMPTY_BYTE_ARRAY`, `EMPTY_CHAR_ARRAY`, and `NO_ARGS` are public empty
  arrays and should be considered API-shape cleanup, not must-fix runtime
  owner bugs.
- `PackedInteger.xB_FACTORS` and `xI_FACTORS` are non-empty public arrays.
  They are logical value tables and good candidates for private arrays,
  immutable lists, or accessor methods if touched.
- `LongList` uses `long[]` storage and returns `NO_LONGS` for empty output.
  The primitive storage should stay array-backed.
- `ConstBitSet.getBytes()` and `ConstOrdinalList.getBytes()` return cloned
  compressed `byte[]` payloads. That is an appropriate defensive-copy pattern.
- `ConstOrdinalList` still has old-style `list.toArray(new Node[0])`, already
  identified in `modern-java-syntax-audit.md` as a readability candidate for
  `Node[]::new`, not a reentrancy fix.

### `javatools_jitbridge`

The JIT bridge has 114 `[]` line hits. Most are storage or generated/native
bridge shape:

- `nLongBasedArray.$storage` is `long[]`.
- bridge array classes expose storage shaped like XTC array implementations.
- `String` stores contents in `long[] data`.
- enum bridge classes expose `$names` and `$values` arrays.
- numeric bridge classes use caches such as `UInt8[] CACHE` and `Bit[] CACHE`.
- `Char` uses a lazily populated `Char[][]` cache.

This source root should not be a first target for list conversion. It needs a
separate JIT/classloader owner audit. Converting storage arrays to lists would
likely hurt performance and obscure generated-code expectations.

The enum `$names`/`$values` arrays are a possible metadata cleanup category, but
only after confirming generated bridge APIs and XTC reflection contracts.

### `plugin`

The Gradle plugin has only 25 `[]` line hits and already uses immutable-list
patterns heavily:

- `DirectRunRequest`, `DirectCompileRequest`, and `DirectTestRequest` use
  `List.copyOf(...)` in compact constructors.
- `DefaultXtcRuntimeExtension` and task code use `List.of(...)` defaults.
- command-line builders use `ArrayList<String>` locally and return
  `String[]` at CLI boundaries.
- `PluginRuntimeClassLoader` accepts `URL[]` because `URLClassLoader` requires
  it.
- `DirectRuntimeBuildService` uses `Class<?>[]` for reflection.

Recommendation: use plugin code as a style model for request/config snapshots.
Do not force arrays out of Java interop boundaries.

## Migration Patterns

### Public Constant To Immutable List

Use when callers do not require an array:

```java
public static final List<Annotation> NO_ANNOTATIONS = List.of();
```

Proof needed:

- no caller writes to the old array;
- no caller relies on array identity;
- no caller requires `T[]` for overload selection or varargs;
- null elements are not legal, because `List.of(...)` rejects nulls.

### Private Array Plus Defensive Copy

Use when the public API must remain array-shaped:

```java
private static final Annotation[] NO_ANNOTATIONS_ARRAY = new Annotation[0];

public static Annotation[] noAnnotations() {
    return NO_ANNOTATIONS_ARRAY.clone();
}
```

For empty arrays, cloning may be unnecessary if the returned array is private
and never has elements. For non-empty arrays, clone or `Arrays.copyOf(...)` is
the usual minimum defensive boundary.

### Mutable Builder To Immutable Snapshot

Use when construction needs mutation but the published value should not change:

```java
var bodies = new ArrayList<MethodBody>(expectedSize);
...
this.bodies = List.copyOf(bodies);
```

Proof needed:

- the snapshot is taken after all builder mutation;
- builder references do not escape;
- the elements themselves are already owner-correct;
- callers do not require structural mutability.

### Owner-Aware Element Copy Then Snapshot

Use when the elements are owner-bearing:

```java
var owned = new ArrayList<MethodBody>(source.size());
for (var body : source) {
    owned.add(body.forMethod(owner));
}
this.bodies = List.copyOf(owned);
```

or keep an array when array storage is still better:

```java
MethodBody[] owned = new MethodBody[source.length];
Arrays.setAll(owned, i -> source[i].forMethod(owner));
this.bodies = owned;
```

The important part is owner-aware element processing. `List.copyOf(...)` alone
does not fix wrong-owner elements.

### Shallow Copy-On-Write Helper

When shallow array copying is intentional, name it honestly:

```java
MethodBody[] shallowChainWithTail(MethodBody[] chain, MethodBody tail) {
    MethodBody[] copy = Arrays.copyOf(chain, chain.length + 1);
    copy[chain.length] = tail;
    return copy;
}
```

That is clearer than sprinkling `clone()` and `System.arraycopy(...)` through
policy code. It also gives tests one helper to target.

### Varargs Boundary

Keep varargs as varargs, but freeze when they become owner/request state:

```java
public Request(String... args) {
    this.args = Arrays.stream(args)
            .map(arg -> requireNonNull(arg, "arg"))
            .toList();
}
```

Do not retain the varargs array directly unless the method documents ownership
transfer and never exposes it.

### Primitive Payload Boundary

Keep primitive arrays, but copy at boundaries:

```java
public byte[] bytes() {
    return bytes.clone();
}
```

or:

```java
this.bytes = Arrays.copyOf(bytes, bytes.length);
```

Do not migrate to boxed lists for payloads.

## Tests And Proofs Required

Array-to-list migrations should be treated like ownership changes, not style
changes, when the data is shared or owner-bearing.

Required proof categories:

- Semantic equivalence: same element order, same duplicate handling, same null
  policy, same mutability contract, same exception timing where callers depend
  on it.
- Owner equivalence: elements in the new container belong to the same
  `Container`, `ConstantPool`, `TypeInfo`, method, service, classloader, or
  compiler request as before.
- Non-sharing: caller mutation of the original array/list cannot mutate the
  owner after construction.
- Defensive copy removal proof: if a migration removes `clone()` or
  `Arrays.copyOf(...)`, a test or code proof must show the new snapshot is
  unmodifiable and not aliased to caller-owned mutable state.
- Unmodifiable view proof: if using `Collections.unmodifiableList(...)`, prove
  that observing later owner mutation through the view is intended.
- Performance equivalence: hot paths need micro/loop tests, allocation counters,
  or targeted stress/benchmark output before arrays are replaced.
- Reentrant stress: owner-bearing runtime changes should run same-JVM direct
  stress with ownership validation, especially for runtime arrays holding
  handles, constants, or templates.

Focused test ideas:

- public constant mutation test: old `NO_*[0]` style mutation should be
  impossible because there is no public array;
- constructor alias test: mutate the caller's input list/array after
  construction and assert the owner does not change;
- getter alias test: mutate a returned array/list and assert the owner does not
  change or the returned list throws `UnsupportedOperationException`;
- owner-copy test: build two `TypeInfo`/`Container` owners from shared source
  metadata and assert all child bodies/parameters point to the correct owner;
- performance guard: compare register/op processing or JIT descriptor hot loops
  before replacing any array in those paths.

## Relationship To Modern Java Style

The array/list question overlaps with modern Java syntax, but it should not be
reduced to syntax.

Useful modernizations:

- `toArray(T[]::new)` instead of `toArray(new T[0])` in readability-only code;
- `List.of(...)` for fixed immutable metadata literals;
- `List.copyOf(...)` for owner/request snapshots;
- `Arrays.setAll(...)` for pure array element replacement after the owner is
  fully constructed;
- pattern variables and local `var` where they reduce noise without hiding
  owner types.

Modernizations to avoid:

- `Stream.toList()` when callers expect a mutable list;
- streams in hot runtime/compiler loops where an imperative loop is allocation-
  free and clear;
- lambdas or method references inside constructors when they capture partially
  constructed `this`;
- anonymous-class-to-lambda rewrites where anonymous-class `this` is used for
  recursion;
- converting array temporaries to streams when checked exceptions or early
  exits make the stream form harder to reason about;
- using `ArrayList` as a published field and calling that "immutable enough".

In short: modern Java helps when it makes ownership and mutability explicit. It
hurts when it changes those semantics or hides hot-path cost.

## Incremental PR Plan

Do not mix this work into the current runtime must-fix branch. The current
branch is about concrete owner/reentrancy bugs: native template ownership,
constructor publication, owner-bearing lazy caches, shallow clone/adoption
fixes, and same-JVM validation. Array/list cleanup should be separate unless a
specific array is part of an already proven owner bug.

Recommended sequence:

1. Documentation-only study PR.
   - Add this guide.
   - No code changes.
   - Use it as reviewer context for later small PRs.

2. Scan/test harness PR.
   - Add targeted source scans for public/protected arrays if the project wants
     a guardrail.
   - Keep the guard advisory at first.
   - Do not fail builds until intentional exceptions are classified.

3. Low-risk public empty-array cleanup PR.
   - Pick one package, probably ASM metadata constants or utility empty arrays.
   - Convert only internal callers that do not require arrays.
   - Keep array-returning compatibility methods where needed.
   - Add mutation/alias tests for any public API change.

4. Descriptor metadata PR.
   - Target `ClassTemplate`/`xArray`/`xRTDelegate` signature descriptor arrays
     or introduce typed descriptor helpers.
   - Keep `markNativeMethod` performance and subclass ergonomics in view.
   - Avoid touching native-template owner caches in the same PR.

5. Method/property chain study PR.
   - Decide whether `MethodInfo`/`PropertyInfo` body chains should remain
     private final arrays or become immutable lists.
   - If changed, include owner-copy tests and type-info parallel construction
     tests.
   - If kept as arrays, extract shallow copy-on-write helpers and document the
     element ownership rules.

6. Compiler AST child snapshot PRs.
   - Work one AST family at a time.
   - Convert parse-time child lists to `List.copyOf(...)` only after proving
     the compiler stage does not structurally mutate them later.
   - Keep transient code-generation arrays as arrays.

7. Owner-bearing runtime array audit PR.
   - Target `ObjectHandle[]` pass-throughs at service/container boundaries.
   - Add assertions or freeze/proxy/share semantics rather than deep-copying
     handles blindly.
   - Run same-JVM direct stress with ownership validation.

8. JIT/JIT bridge audit PR.
   - Treat this as a classloader/type-system owner audit, not a list cleanup.
   - Keep primitive and generated storage arrays.
   - Consider immutable metadata wrappers only around descriptor arrays that
     are not generated ABI.

Each PR should be small enough that reviewers can answer two questions:

- What mutability/ownership contract changed?
- What test or benchmark proves the new contract?

## Specific Recommendations

- Keep arrays for `Frame`, opcode execution, primitive delegates, binary
  payloads, varargs/reflection/classloader boundaries, and JIT bridge storage.
- Prefer `List.copyOf(...)` for request/config/metadata snapshots where callers
  should not mutate the container.
- Prefer `List.of(...)` for small fixed descriptor lists and empty metadata
  lists when API compatibility allows it.
- Keep `ArrayList` as a local mutable builder, then publish an immutable
  snapshot.
- Replace public/protected non-empty mutable array constants first when they
  expose owner-bearing or mutable elements.
- Treat public empty array constants as style/API cleanup, not urgent runtime
  bugs.
- For owner-bearing elements, copy/adopt/freeze/proxy the elements explicitly
  before freezing the container.
- Name shallow copy-on-write helpers so future readers do not mistake container
  copies for deep owner copies.
- Add aliasing and owner-boundary tests before removing defensive copies.
- Benchmark before changing arrays in runtime register loops, primitive
  delegates, or JIT descriptor paths.

## Specific Non-Recommendations

- Do not replace all arrays with lists.
- Do not replace primitive arrays with boxed primitive lists.
- Do not convert hot `Frame` register arrays or opcode arrays without a failing
  owner proof and performance data.
- Do not treat `ArrayList` fields as immutable merely because the field is
  `final`.
- Do not use `Collections.unmodifiableList(...)` when a snapshot is required.
- Do not assume `List.copyOf(...)` fixes wrong-owner elements.
- Do not mix broad empty-array cleanup with runtime must-fix ownership work.
- Do not modernize loops to streams in hot paths just to reduce line count.
- Do not convert generated or bridge-shaped JIT arrays without understanding
  the generated ABI.

## Commands Used

Representative local scan commands:

```bash
rg -n '\[\]' \
  javatools/src/main/java \
  javatools_utils/src/main/java \
  javatools_jitbridge/src/main/java \
  plugin/src/main/java | wc -l

rg -n --pcre2 '\b(public|protected)\s+static\s+(final\s+)?[^;\n]*\[\]\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)' \
  javatools/src/main/java \
  javatools_utils/src/main/java \
  javatools_jitbridge/src/main/java \
  plugin/src/main/java | wc -l

rg -n --pcre2 '^\s*(?:public|protected)\s+(?:static\s+)?(?:final\s+)?[^;\n]*\[\]\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:=|;)' \
  javatools/src/main/java/org/xvm/runtime \
  javatools/src/main/java/org/xvm/asm \
  javatools/src/main/java/org/xvm/compiler | wc -l

rg -n '\.clone\(\)' \
  javatools/src/main/java \
  javatools_utils/src/main/java \
  javatools_jitbridge/src/main/java \
  plugin/src/main/java | wc -l

rg -n 'Arrays\.copyOf|System\.arraycopy|copyOfRange|toArray\(' \
  javatools/src/main/java \
  javatools_utils/src/main/java \
  javatools_jitbridge/src/main/java \
  plugin/src/main/java | wc -l

rg -n 'new ArrayList|List\.of|List\.copyOf|Collections\.unmodifiableList|Stream\.toList|Collectors\.toList' \
  javatools/src/main/java \
  javatools_utils/src/main/java \
  javatools_jitbridge/src/main/java \
  plugin/src/main/java | wc -l
```

These commands intentionally stay broad. Any future code PR should narrow them
to one package and classify each hit by owner, mutability, publication, and
performance role before making changes.
