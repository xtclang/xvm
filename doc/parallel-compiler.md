# Parallel compilation through the embedding API

This document answers one question: what is missing before the Ecstasy compiler in `javatools` can
run compilations safely in parallel through `EmbeddingSupport`
(`javatools/src/main/java/org/xvm/api/EmbeddingSupport.java`). It was written by reading the code
at `d2ecd0a254`; nothing was built or run. A claim marked *(inferred)* follows from reading the
code but was not traced to the end or observed. Every count names the grep that produced it.

Paths below are relative to the repository root. `EmbeddingSupport.java` stands for the path above,
and `asm/`, `compiler/`, `runtime/` and `tool/` for `javatools/src/main/java/org/xvm/asm/` and its
siblings.

## Verdict

The hypothesis was that compiles share the library modules they load from the repository, and that
compiling mutates them. **For the embedding API, that is wrong.** Each compile already works on
private copies of every library it links:

- `EmbeddingCompiler.configureLibraryRepo` builds a new
  `LinkedRepository(true, new BuildRepository(), [input,] core)` for every attempt
  (`EmbeddingSupport.java:1137-1143`). `ModuleCompiler` does the same
  (`javatools/src/main/java/org/xvm/api/ModuleCompiler.java:253-255`).
- With read-through on, `LinkedRepository.loadModule` copies every module it finds below the first
  repository into that first repository, the attempt's `BuildRepository`: "create a copy, allowing
  the compiler to mutate the repos[0] contents" (`asm/LinkedRepository.java:97-119`).
- The copy is a new `FileStructure` with a new `ConstantPool` (`asm/FileStructure.java:171-181`).
  Every component is cloned (`asm/Component.java:2046-2117`), and every constant is re-registered
  into the new pool, which clones each one (`asm/ConstantPool.java:199-202`,
  `asm/Constant.java:310-320`).
- Linking, name resolution, TypeInfo building, invalidation, the deferred TypeInfo lists and the
  NakedRef injection all happen on those copies (sections 2.4 and 3.1).

The registration maps, TypeInfo caches, relation caches, invalidation counters and deferred lists
that the hypothesis lists do exist, and compiling does write them. They sit on per-attempt copies,
though, so they are not what keeps compiles apart.

What two compiles that share a core repository really share is narrower:

1. **The source modules** in the configured repository. Every attempt reads them to make its
   copies, and the language server's library queries read them directly. The reads go through
   synchronized lazy deserialization plus a few racy caches that always store the same value
   (section 3.2). They are safe in practice *(inferred)*.
2. **Per-instance objects that `Object.clone()` carries from a source constant into every copy**.
   `Constant.adoptedBy` is a shallow clone (`asm/Constant.java:310-320`). One effect is that
   `TypeConstant.m_cRecursiveDepth`, a `final AtomicInteger` (`asm/constants/TypeConstant.java:8603`),
   is one object shared by every compile's copy of the same library type. That counter decides
   when TypeInfo building throws "Infinite loop" (`TypeConstant.java:1934-1944`). This is a real
   coupling between compiles (section 3.3).
3. **A versioned-import branch that skips the copy.** `LinkedRepository.loadModule(name, version,
   exact)` stores what it found without copying it (`asm/LinkedRepository.java:123-140`).
   `FileStructure.linkModules` then re-registers that module's constants into the compiling
   module's pool (`asm/FileStructure.java:860-863`). With the language server's `BuildRepository`
   as the core repository, the module re-registered is the shared one (section 2.5).
4. **Three JVM-wide statics that are mutable after class initialization.** The first is a
   `HashSet` in `TypeConstant` (`TypeConstant.java:8656-8661`, written at `:6189`). Then four
   `AtomicInteger` counters, one of which makes emitted modules depend on what else the JVM has
   compiled. Last, the `PackedInteger` cache (section 3.4). `d2ecd0a254` made the last two
   non-final statics final; these are `static final` fields that hold mutable objects.

**How hard is it?** Small to moderate for level (b), the language server's case. The fixes are
local:

- reset or re-create the clone-carried objects on adoption (seven files);
- make the counters per compile (four files);
- make the static `HashSet` concurrent and the `PackedInteger` cache safe;
- make the versioned branch copy (two files).

`XvmStructure.ensureReadOnly` already exists (`asm/XvmStructure.java:203-215`) and is unused in
production. It can freeze the shared sources, which turns any remaining structural write or pool
registration into an exception the test suites will find. Add a stress test that compares parallel
with serial compiles byte for byte, and the contract can be relaxed. After that, the language server
can give library compiles a lane of their own and delete its preemption. Stages 0 through 4 take
three to four weeks *(inferred estimate)*. The unknowns are writes to shared sources that
nobody has found yet, and the language server's own state that assumes one worker.

## 1. What "parallel" means here

| Level | What runs at once | Where it happens today | Verdict |
| --- | --- | --- | --- |
| a | Two compiles, each with its own core repository, in one JVM | Gradle direct mode, if tasks run in parallel *(inferred, section 4.a)*; `ModuleCompiler` hosts | Safe except for the JVM statics in section 3.4 |
| b | Two compiles sharing the configured core repository | Not today: the language server serializes them (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkAdapter.kt:78`, `:3211-3219`) | Blocked by sections 3.3, 3.4 and 2.5; section 3.2 is safe in practice |
| c | A compile while a run or debug session of the same `EmbeddingSupport` is going on | Not in the language server JVM: programs run in worker JVMs. Possible in other hosts and in Gradle direct mode | Same as (b), plus the module being run is re-serialized in place (section 4.c) |
| d | Parallelism inside one compile | Nowhere | Out of scope; section 4.d says what blocks it |

## 2. How a compile gets its libraries

### 2.1 Repositories

| Repository | Hands out | Thread safety | Cite |
| --- | --- | --- | --- |
| `DirRepository` | The same cached instance per module file, reloaded only if `isModified()` | The three public methods are `synchronized`. The comment calls out "two parallel compiles" | `asm/DirRepository.java:26-40`, `:83-88`, `:404-414` |
| `FileRepository` | A cached instance, including cached extracted versions | `synchronized` | `asm/FileRepository.java:61-145` |
| `BuildRepository` | Whatever was stored, the same instance each time | Unsynchronized `TreeMap`; safe only with no concurrent writer | `compiler/BuildRepository.java:28-68`, `:82` |
| `LinkedRepository(readThrough=true)` | A *copy* of anything found below index 0, stored at index 0; the stored copy afterward | Per attempt, so not shared | `asm/LinkedRepository.java:97-119` |
| `LinkedRepository(readThrough=false)` | The underlying instance | Delegates | `asm/LinkedRepository.java:97-119` |

`DirRepository` and the language server's bundle both load lazily. `ModuleInfo.tryLoad` uses
`new FileStructure(file)`, which is lazy (`asm/DirRepository.java:393-401`,
`asm/FileStructure.java:107-110`). The language server reads each bundled module with
`FileStructure(InputStream)`, also lazy, into one `BuildRepository`, and configures
`EmbeddingSupport` with it (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibraries.kt:34-53`,
`:62`, and `asm/FileStructure.java:135-138`). Nothing writes to that `BuildRepository` after
configuration. Read-through stores go to the attempt's own `BuildRepository`.

### 2.2 The library repository of one attempt

`runCompiler` makes a new `EmbeddingCompiler` per call and runs it on the caller's thread
(`EmbeddingSupport.java:957-977`). `process()` then does the following:

1. `ensureLibraryRepo()` → `configureLibraryRepo` →
   `new LinkedRepository(true, new BuildRepository(), [inRepo,] coreRepo)`
   (`EmbeddingSupport.java:1066-1068`, `:1137-1143`).
2. `prelinkSystemLibraries` loads and links `ecstasy.xtclang.org` and `mack.xtclang.org` through it
   (`tool/Launcher.java:757-777`).
3. It builds the compiling module's `FileStructure` and stores it in the same build repository
   (`EmbeddingSupport.java:1106-1118`).
4. It runs `resolveDeclarations` (link, resolve names, inject the turtle) and, for a full compile,
   validation and code generation (`tool/Compiler.java:312-343`).

### 2.3 What the copy is

`new FileStructure(module, false)` (`asm/FileStructure.java:171-181`) makes a new `ConstantPool`
and calls `merge` (`:228-301`):

- `module.cloneBody()` clones the module itself (`:229`).
- `cloneChildren(module.children())` clones the whole tree (`:242`). `children()` and
  `hasChildren()` force lazy deserialization (`asm/Component.java:1632-1644`), and `cloneChild`
  recurses into every child (`asm/Component.java:2089-2117`). **The copy is complete. Every
  attempt materializes every library it links, whatever it uses** *(inferred from the recursion;
  not measured)*.
- `registerConstants(pool)` and `registerChildrenConstants(pool)` re-point every constant reference
  into the new pool (`asm/FileStructure.java:281-282`). `ConstantPool.register` clones a foreign
  constant with `adoptedBy` (`asm/ConstantPool.java:199-202`). Two kinds of constant are returned
  *unregistered*: one that contains unresolved parts, and a `TypeConstant` that is not shared with
  the pool (`:190-197`).

Each structure class clears the caches it holds:

- `ClassStructure.registerConstants` clears `m_typeFormal` and `m_typeNormalized`
  (`asm/ClassStructure.java:3329-3339`).
- `ModuleStructure.cloneBody` clears its own (`asm/ModuleStructure.java:730-750`).
- `MethodStructure.cloneBody` clones `m_code`, the local constant array, parameters and source
  (`asm/MethodStructure.java:1703-1752`). Op bytes stay shared, but they are immutable and point
  into a per-method local constant array, which is re-registered (`:1931-1979`).

`XvmStructure.clone` clears the read-only flag (`asm/XvmStructure.java:623-627`), so clones of a
frozen structure can be mutated. That matters for section 5.2.

### 2.4 Linking

At compile time, `FileStructure.linkModules(repo, false)` (`asm/FileStructure.java:721-738`) loads
each dependency through the same read-through repository (`:782`, `:852`), so it gets the copies.
It points the compiling file's fingerprints at them with `setFingerprintOrigin` (`:876`) and
recurses into the copies' own dependencies (`:893-899`). Then:

- `injectNativeTurtle` writes the turtle copy's `NakedRef` type into the pool of every module in
  the build repository, which holds only copies and the compiling module
  (`tool/Compiler.java:370-384`).
- Cross-pool work during TypeInfo building registers constants into library pools on purpose
  (`asm/ConstantPool.java:3446-3450`; `TypeConstant.java:1924-1925`). Those pools are the copies.

Two attempts therefore never write the same `ConstantPool`, `FileStructure` or `TypeConstant`
through these paths.

### 2.5 The versioned exception

`TypeCompositionStatement` gives an import with a version a versioned `ModuleConstant`
(`compiler/ast/TypeCompositionStatement.java:1024-1035`). Such a module loads through
`loadModule(name, version, exact)` (`asm/FileStructure.java:782-783`, `:851-853`). In
`LinkedRepository`, that branch stores the module it found into the build repository **without
copying it** (`asm/LinkedRepository.java:123-140`). `linkModules` then runs
`moduleUnlinked.registerConstants(fileTop.m_pool)` and `registerChildrenConstants(fileTop.m_pool)`
on it (`asm/FileStructure.java:860-863`). That rewrites the module's constant references to point
into the *compiling module's* pool.

Whether that module is shared depends on the core repository:

| Core repository | What the versioned load returns | Cite |
| --- | --- | --- |
| `BuildRepository` (the language server) | Its stored instance, the shared source | `compiler/BuildRepository.java:38-54` |
| `FileRepository` | A cached instance, shared by later loads | `asm/FileRepository.java:104-140` |
| Default `ModuleRepository` method (`DirRepository`) | A fresh copy from `extractVersion`, after registering a versioned `ModuleConstant` into the *source* pool | `asm/ModuleRepository.java:106-120`, `asm/ModuleStructure.java:675-692` |

This is a defect even for serial compiles. The shared module keeps constants of an attempt that has
finished, so later copies adopt them, and that attempt's pool stays reachable *(inferred
consequence)*. Run concurrently, two attempts would re-register the same shared module into two
pools. Nothing in the tree takes this path today: `grep -rnE "import\s+[A-Za-z_][A-Za-z0-9_.]*\s+v:" --include="*.x" .`
outside `build/` and `.claude/` finds only `doc/archive/examples/MyApp.x:6`.

### 2.6 The runtime's path

The runtime copies too:

- `NativeContainer` loads ecstasy, the turtle and the native module from its repository, then
  copies and merges them into one `FileStructure` (`runtime/NativeContainer.java:110-139`).
- `ensureRuntimePool()` returns that copy's pool (`EmbeddingSupport.java:368-371`,
  `runtime/NativeContainer.java:705-707`). It is neither a compile pool nor a source pool.
- Each run serializes the module it is given, reads it back, and merges it with the runtime's
  system copies and fresh copies of crypto, net and web (`javatools/src/main/java/org/xvm/api/InterpreterControl.java:157-175`,
  `runtime/NativeContainer.java:760-773`).

## 3. Inventory of mutable state

"Private" means each attempt has its own instance. "Shared" means two attempts, or an attempt and a
run, can reach the same object.

### 3.1 Private to an attempt (the hypothesis's list)

| State | Cite | Written by, when | Type | Owner |
| --- | --- | --- | --- | --- |
| Constant list | `asm/ConstantPool.java:3756` | `register`, under `synchronized (this)` (`:205-230`), in every stage | `ArrayList` | Copy pool / compiling pool |
| Constant and locator lookups | `asm/ConstantPool.java:3763`, `:3770` | `ensureConstantLookupComplex` / `ensureLocatorLookupComplex`, synchronized copy-on-write (`:2831-2898`); the maps are `ConcurrentHashMap` | `EnumMap` of `ConcurrentHashMap` | Private |
| Valid-pool set | `asm/ConstantPool.java:3776` | `buildValidPoolSet`, following fingerprint origins, which are copies (`:262-286`) | identity `HashSet` | Private |
| Recursive-registration flag | `asm/ConstantPool.java:3781` | `preRegisterAll`/`postRegisterAll` when a file is written (`:2786-2806`); "single-thread scenario" (`:238-241`) | `boolean` | Private |
| Implicit-import cache | `asm/ConstantPool.java:3786` | Name resolution (`:1210-1245`) | `ConcurrentHashMap` | Private |
| Deferred TypeInfo list | `asm/ConstantPool.java:3791` | TypeInfo building (`:2908-2933`) | `TransientThreadLocal`, per pool and per thread | Private |
| Invalidation list and count | `asm/ConstantPool.java:3797`, `:3802` | `invalidateTypeInfos`, during validation (`:2944-2950`) | `Vector`, `volatile int` | Private |
| NakedRef TypeInfo cache | `asm/ConstantPool.java:3807` | `getNakedRefInfo`, lazily (`:3428-3430`) | `ConcurrentHashMap` | Private |
| NakedRef type | `asm/ConstantPool.java:3835` | `injectNativeTurtle` (`tool/Compiler.java:370-384`); `merge` copies the source pool's value (`asm/FileStructure.java:286-290`) | field | Private (see 3.2, last row) |
| TypeInfo, invalidation snapshot | `TypeConstant.java:8600`, `:8612` | `ensureTypeInfo`, lazily on read, by CAS and a `synchronized` builder (`:1810-1835`, `:1850`) | `volatile`, field updaters | Private (cleared on adoption, `:8296-8307`) |
| Relation cache | `TypeConstant.java:8619` | `calculateRelation`, lazily (`:6154-6191`) | `ConcurrentHashMap` | Private (cleared on adoption) |
| Recorded TypeInfo diagnostics | `TypeConstant.java:2142` | `ensureTypeInfo`, replayed to later callers (`:1813-1834`) | `volatile List` | Private (copied on adoption, overwritten on rebuild) |
| Structure caches | `asm/ClassStructure.java:3794-3799`, `asm/ModuleStructure.java:1056-1092` | Lazily on read | fields, maps | Private (cleared at `asm/ClassStructure.java:3337-3338`, `asm/ModuleStructure.java:743-748`) |
| AST, `Context`, `StageMgr`, binding collectors | `EmbeddingSupport.java:1011`, `:900` | All stages | various | Private |

Two of the language server's concerns from `doc/errs/errs.md` look different in this light:

- "library pools are shared across requests" (`doc/errs/errs.md:1687-1689`) is true of the *source*
  pools, which compiles only read. The pools a compile writes are its own copies.
- "TypeInfo is memoized … whoever builds it first produces its diagnostics" (`doc/errs/errs.md:1697-1698`)
  has since been addressed inside one compile by recording and replaying the diagnostics
  (`TypeConstant.java:1813-1834`). Across compiles it does not arise, because TypeInfos are per
  copy.

### 3.2 Shared: the source modules in the configured repository

| State | Cite | Written by, when | Type | What a race does |
| --- | --- | --- | --- | --- |
| Lazily deserialized children | `asm/Component.java:810-850`, `:3649` | The first `children()`/`hasChildren()`, in practice the first copy | `volatile byte[]` handoff under a monitor shared by siblings | Nothing: built for threads ("this blocks other threads until deserialization is complete", `:828-829`) |
| `DirRepository` scan cache and module instance | `asm/DirRepository.java:83-88`, `:404-414` | `loadModule` | `HashMap`/`TreeMap` under `synchronized` | Nothing; fixed in `669fcb1a3e` (#547) |
| `BuildRepository` map (the language server's bundle) | `compiler/BuildRepository.java:82` | Only during configuration (`XdkLibraries.kt:34-53`) | `TreeMap` | Nothing while no one writes. A host that stores into the configured `BuildRepository` while compiling would corrupt it |
| `IdentityConstant.m_component` | `asm/constants/IdentityConstant.java:597-603` | `getComponent()`, lazily on read. Library queries call it on source constants (`XdkLibrarySources.kt:367`); copying may, through `resolveTypedefs` *(inferred)* | plain field | Benign: the value is always the same component, already published by the deserialization handoff *(inferred, JMM argument)* |
| `Constant.m_iHash` | `asm/Constant.java:558-585`, `:975` | `hashCode()`, lazily | `int` | Benign, the same single-check idiom as `String.hash` |
| Source constant pool, list and maps | `asm/ConstantPool.java:3756-3770` | Normally nothing. `extractVersion` registers a versioned `ModuleConstant` (`asm/ModuleStructure.java:679`); `resolveTypedefs` could create constants in the source pool *(inferred; compiled pools already hold typedef-free constants)* | `ArrayList` + `ConcurrentHashMap` | `register` is synchronized, but its readers are not: `getConstant(int)` and `getConstants()` read the `ArrayList` unlocked (`:84-86`, `:131-134`). A new constant is visible in the map before `registerConstants` re-points its parts (`:205-256`) |
| Versioned import target | `asm/FileStructure.java:860-863` | Linking, section 2.5 | the whole module | Constants re-pointed into a finished attempt's pool; two attempts tearing one module |
| Source pool's NakedRef type | `asm/FileStructure.java:286-290` | Set only if the configured repository holds modules that went through `injectNativeTurtle`, such as a reused `BuildRepository` | field | Every copy would share one NakedRef `TypeConstant` and its TypeInfo *(inferred edge case; not the language server's bundle)* |

### 3.3 Shared: objects `Object.clone()` carries from a source constant into every copy

`adoptedBy` is `super.clone()` + `setContaining(pool)` + `resetRefs()`
(`asm/Constant.java:310-320`). A field that `setContaining` or `registerConstants` does not reset
keeps the source's value. When that value is a mutable object, every attempt's copy of the constant,
and the runtime's copy, share it.

| Field | Cite | Shared when | Type | What a race does |
| --- | --- | --- | --- | --- |
| `TypeConstant.m_cRecursiveDepth` | `TypeConstant.java:8603`; used `:1934-1944` | Always: `final`, created in the source constant's constructor | `AtomicInteger` | Concurrent deferred TypeInfo builds on copies of one type add up. Past 2, the build throws "Infinite loop while producing a TypeInfo", which the host sees as `EMB-5` (`EmbeddingSupport.java:972-975`). The decrement is not in a `finally`, so an exception leaves the shared count raised for every later compile in the JVM, serial ones too |
| `ParameterizedTypeConstant.m_lockPrev`, `SignatureConstant.m_lockPrev` | `asm/constants/ParameterizedTypeConstant.java:1134`, `asm/constants/SignatureConstant.java:944` | Always (`final`) | `StampedLock` | Lock contention between compiles; no wrong result |
| `TypeParameterConstant.f_tloReEntry` | `asm/constants/TypeParameterConstant.java:248` | Always (`final`) | `TransientThreadLocal` | None across threads, since values are per thread |
| `TypeConstant.m_mapConsumes`, `m_mapProduces` | `TypeConstant.java:8631`, `:8636`; "WARNING: thread-unsafe" at `:6747`, `:6793` | If the source constant's map exists when it is adopted. No compile path was found that calls these on source constants *(inferred)* | `HashMap` with an `IN_PROGRESS` sentinel | Corrupted map. A thread that sees another thread's `IN_PROGRESS` answers `NO` and stores it (`:6758-6761`): a wrong variance answer and so wrong diagnostics |
| `TypeConstant.m_tloInProgress` | `TypeConstant.java:8624` | If set on the source | `TransientThreadLocal` | None across threads |
| `PropertyConstant.m_info`, `PropertyClassTypeConstant.m_info` | `asm/constants/PropertyConstant.java:217-223`, `:466`; `asm/constants/PropertyClassTypeConstant.java:118-124`, `:495` | If set on the source; neither `invalidateCache` (`PropertyConstant.java:277-281`) nor `TypeConstant.setContaining` clears them | `PropertyInfo` | A copy answers with the source's `PropertyInfo`, whose constants are in the wrong pool |

### 3.4 JVM-wide statics

There are no non-final static fields in `org.xvm.compiler`, `org.xvm.asm`, `org.xvm.tool`,
`org.xvm.api` or `javatools_utils`. The command below printed nothing:

```bash
grep -rnE "^\s*(private|protected|public)?\s*static\s+[A-Za-z<>?, \[\].]+\s+[a-zA-Z_][a-zA-Z0-9_]*\s*(=|;)" \
  javatools/src/main/java/org/xvm/{compiler,asm,tool,api} javatools_utils/src/main/java | grep -v " final "
```

Final statics that point at mutable objects are a different matter:

| Static | Cite | Written | Type | What a race does |
| --- | --- | --- | --- | --- |
| `TypeConstant.s_setRecursions` | `TypeConstant.java:8656-8661`; written `:6189` | At run time, when an `isA` recursion is rejected | `HashSet` | Concurrent `add` can corrupt the table. It only deduplicates a log line, but corrupt `HashMap` bins can throw or loop *(inferred)* |
| `MethodDeclarationStatement.COUNTER` | `compiler/ast/MethodDeclarationStatement.java:302-304`, `:1257` | Every label and every `_` variable in a method | `AtomicInteger` | Atomic, so nothing breaks. But `_` becomes `"_:" + counter` (`compiler/ast/VariableDeclarationStatement.java:151-156`), and that name is emitted as a `StringConstant` in `Var_N`/`Var_DN` (`:244-272`). **The bytes of a module depend on how many labels and `_` variables the JVM has compiled before, so even serial compiles are not byte-identical** |
| `ElvisExpression.COUNTER`, `ElseExpression.COUNTER`, `ConditionalStatement.LABEL_COUNTER` | `compiler/ast/ElvisExpression.java:294-318`; `compiler/ast/ElseExpression.java:183`, `:220`; `compiler/ast/ConditionalStatement.java:64-77` | Label names | `AtomicInteger` | No effect on the bytes: a label's name is `transient` (`asm/op/Label.java:178`) |
| `PackedInteger.CACHE` | `javatools_utils/src/main/java/org/xvm/util/PackedInteger.java:94-108`, `:967` | Lazily, by any compile or run | array of objects with non-final fields (`:944-962`) | A racy publication. Another thread can, in theory, see a cached object before its fields; in practice this is unlikely and already shared with the runtime's threads *(inferred)* |
| `Token.Id` keyword maps | `compiler/Token.java:719-747` | Static initializer only | `HashMap` | None |
| `Expression.TypeFit.BY_FLAGS`, `Assignment.BY_FLAGS` | `compiler/ast/Expression.java:3045-3049`, `asm/Assignment.java:325-331` | Static initializer only | arrays | None |
| `InvocationBinding.Collector.NONE`, `CursorBinding.Collector.NONE` | `compiler/ast/bindings/InvocationBinding.java:178`; `compiler/ast/bindings/CursorBinding.java:580` | Disabled collectors write nothing. `finish` clears unconditionally (`InvocationBinding.java:325`), but only per-attempt collectors are finished (`EmbeddingSupport.java:900-908`, `:1011`) | `IdentityHashMap` | None today |
| `ConstantPool.IMPLICITS`, `ShallowSizeOf.SIZE_BY_CLASS` | `asm/ConstantPool.java:3708`; `javatools_utils/src/main/java/org/xvm/util/ShallowSizeOf.java:30` | Once; concurrent map | immutable / `ConcurrentHashMap` | None |

### 3.5 Per-thread state and the `withPool` discipline

These are per thread, so attempts on different threads cannot see each other's values:

- `ConstantPool.s_tloPool` (`asm/ConstantPool.java:3829-3830`);
- `MultiMethodStructure.s_tloIgnoreNative` (`asm/MultiMethodStructure.java:407`);
- `TypeParameterConstant.f_tloReEntry` (`:248`);
- the per-pool deferred list (`asm/ConstantPool.java:3791`);
- `TransientHasherReference.SPARE` (`javatools_utils/src/main/java/org/xvm/util/TransientHasherReference.java:15`);
- the map behind every `TransientThreadLocal`
  (`javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java:146`);
- `TypeConstant.s_context`, a `ScopedValue` (`TypeConstant.java:8667`).

`withPool` stores the pool in the current thread's slot and returns a closer that restores the
previous one (`asm/ConstantPool.java:3478-3483`). Its documentation requires opening and closing on
the same thread and says work moved to another thread must open its own scope (`:3468-3472`). Every
compiler phase opens one on its file's pool, as at `compiler/Compiler.java:172`, `:209`, `:253`
and `:297`. A grep finds 18 reads of `getCurrentPool()` and 11 `withPool(` scopes in compiler, asm,
tool and api:

```bash
grep -rn "getCurrentPool()" javatools/src/main/java/org/xvm/{compiler,asm} | wc -l
grep -rn "withPool(" javatools/src/main/java/org/xvm/{compiler,asm,tool,api} | wc -l
```

One exception: `NativeContainer` calls `setCurrentPool(pool)` and later `setCurrentPool(null)` on
the thread that creates the runtime (`runtime/NativeContainer.java:150`, `:221`) instead of using a
scope. If a host calls `ensureConnector()` from inside a `withPool` scope, `getCurrentPool()`
returns null for the rest of that scope *(inferred consequence)*.

### 3.6 Compiler work on other threads

None. Besides a doc comment in `EmbeddingSupport` and `BlockingQueueAdapter.parallelStream()`,
which only delegates, this grep finds only runtime code: `SingletonConstant`'s initialization
waiter and `InterpreterControl`:

```bash
grep -rn "parallelStream\|\.parallel()\|ForkJoin\|CompletableFuture\|ExecutorService\|Executors\.\|new Thread(\|Thread.ofPlatform\|Thread.ofVirtual\|StructuredTaskScope" \
  javatools/src/main/java/org/xvm/{compiler,asm,tool,api} javatools_utils/src/main/java
```

A compile runs on the thread that called it (`EmbeddingSupport.java:957-977`). Its phases loop over
compilers in order (`tool/Compiler.java:517-537`).

## 4. Each level

### 4.a Separate core repositories

`EmbeddingSupport` takes only one configuration (`EmbeddingSupport.java:320-334`), so this level is
reached through `ModuleCompiler` (`ModuleCompiler.java:45-50`, `:75-77`) or `Launcher`, which
builds new `DirRepository`s for each compile (`tool/Launcher.java:695-708`). Separate repositories
mean separate source instances, so sections 3.2, 3.3 and 2.5 cannot connect two compiles. What is
left is section 3.4: the `HashSet`, the output-changing counter, and the `PackedInteger` cache.

The Gradle plugin's opt-in direct mode is an example. It runs `Launcher.launch` in the build JVM
(`plugin/src/main/java/org/xtclang/plugin/runtime/impl/IsolatedDirectExecutor.java:25-30`), with
one class loader per build shared by all direct tasks
(`plugin/src/main/java/org/xtclang/plugin/runtime/DirectRuntimeBuildService.java:28-39`, `:77-90`).
The shared service is registered with no limit on parallel use
(`plugin/src/main/java/org/xtclang/plugin/tasks/XtcLauncherTask.java:155-157`). The default mode is
`ATTACHED`, a forked JVM (`plugin/src/main/java/org/xtclang/plugin/XtcPluginConstants.java:73`).
Whether Gradle actually overlaps two direct compiles depends on the build *(inferred)*. The
`DirRepository` race fixed in `669fcb1a3e` ("blocks parallel compilation", #547) shows that it has
happened. `ModuleCompiler`'s documentation ("Hosts must serialize calls that share repositories")
is stricter than the code needs once sections 3.3, 3.4 and 2.5 are fixed.

### 4.b A shared core repository: the language server's case

What two attempts share is sections 3.2, 3.3 and 3.4 and, for a versioned import, section 2.5:

| Item | How it shows | Probability |
| --- | --- | --- |
| `m_cRecursiveDepth` (3.3) | `EMB-5` "Compilation failed", caused by the *other* compile; or a count stuck high for the rest of the JVM | Low for each compile; grows with concurrency and with how many TypeInfos are deferred *(inferred)* |
| Versioned import (2.5) | Constants in the wrong pool; the language server's bundle changed for good | Only for sources with `import … v:`; none in the tree |
| `s_setRecursions` (3.4) | Corrupt set, so an exception or a hang | Rare path |
| `_:` counter (3.4) | Output differs between runs; not a correctness problem | Every compile with a `_` variable in a method |
| Lazy deserialization, racy caches (3.2) | None expected | — |

The language server also shares the bundle with its library queries.
`XdkLibraries.module(name)` returns the configured instance (`XdkLibraries.kt:134`). Hover,
definition and monikers in a library copy walk its pool and components
(`lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkLibrarySources.kt:169-232`,
`:361-367`), and so does semantic model building (`SemanticModelBuilder.kt:1550`). Today those
queries go through the same single worker ("Library queries share compiler serialization",
`XdkAdapter.kt:395-421`). They read like a copy does, so their safety class is section 3.2.

### 4.c A compile during a run or debug session

**In the language server: it does not happen.** "Programs never execute in the language server
process" (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorkerProcess.kt:31-32`). Runs and
debug sessions go to worker JVMs (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/run/RunWorker.kt:44`,
`:143`). The server process never calls `runMethod`, `ensureConnector` or `ensureRuntimePool`; the
only calls are in `RunWorker.kt`:

```bash
grep -rn "runMethod\|ensureConnector\|ensureRuntimePool" lang/lsp-server/src/main/kotlin
```

So the compile worker being busy while a session runs involves two JVMs.

**In another host, or in Gradle direct mode:**

- The runtime and the compiler use *different* pools and structures: the runtime's own copies
  (section 2.6) and each attempt's copies.
- They share the configured repository's source instances, which both read. `createFileStructure`
  reads crypto, net and web for every run (`runtime/NativeContainer.java:766-768`).
- They share the clone-carried objects of section 3.3. The runtime's copy of an ecstasy type shares
  `m_cRecursiveDepth` with every compile's copy, and the runtime builds TypeInfos on many threads.
  That makes the spurious "Infinite loop" more likely than with compiles alone *(inferred)*.
- They share the statics of section 3.4. `PackedInteger` is used heavily by both.
- `prepareModule` calls `module.getFileStructure().writeTo(bytes)` on the module it was given
  (`InterpreterControl.java:162`). `writeTo` runs `reregisterConstants(true)` (`asm/FileStructure.java:349-353`),
  which can drop and reorder that module's constants (`asm/ConstantPool.java:3489-3500`,
  `asm/FileStructure.java:1345-1352`). For `run(ModuleStructure, …)` that module is the host's
  `Compilation.module()` (`EmbeddingSupport.java:1282-1290`). For `runMethod` it is the input
  repository's instance (`EmbeddingSupport.java:1405-1408`, a `LinkedRepository` without
  read-through). If a concurrent compile reads the same instance as a dependency, it can copy a
  pool in the middle of being renumbered *(inferred; not reproduced)*. The `Compilation` contract
  ("worker-owned", `EmbeddingSupport.java:443`) covers the first case. Nothing covers the second.

Two kinds of compile already run inside a runtime:

- **Debugger evaluation.** `EvalCompiler.createLambda` adds a lambda to the live class of the
  stopped frame and validates it against the frame's pool (`compiler/EvalCompiler.java:65-79`;
  callers at `runtime/SessionDebugger.java:3466`, `:3475`). That is a compile writing live runtime
  structures while other fibers may be running *(inferred)*.
- **The Ecstasy compiler service.** `xRTCompiler` builds a read-through repository over the
  runtime's read-only repositories for each compile
  (`runtime/template/_native/lang/src/xRTCompiler.java:137-150`, `:473-475`). Nothing serializes
  two services that call it at once. That is level (b) inside the runtime, already relying on the
  copy mechanism of section 2 *(inferred: no test does it concurrently)*.

### 4.d Parallelism inside one compile

This is out of scope. What stands in the way:

- the AST and `StageMgr` are mutable and shared by the compilers of one compile, whose phases repeat
  until done (`tool/Compiler.java:517-537`);
- recursive registration assumes a single thread (`asm/ConstantPool.java:238-241`);
- deferred TypeInfo lists are per thread (`:2908-2933`), so a build that one thread defers cannot be
  finished by another;
- `ensureTypeInfo` is `synchronized` per `TypeConstant` (`TypeConstant.java:1850`), so two threads
  building TypeInfos that depend on each other can deadlock *(inferred)*;
- the consumes and produces maps are not thread-safe (`:6747`, `:6793`).

Much of the TypeInfo machinery is already built for several threads on one pool, because the
runtime works that way (`TypeConstant.java:1874-1886`; `asm/ConstantPool.java:174-177`, `:205`,
`:3759-3770`). That is a starting point, not a design for parallel compiles.

## 5. Fix options

### 5.1 Copy per compile

**This is the status quo for structures and pools** (section 2), so making it per compile costs
nothing new. Concurrency adds the peaks of the compiles that overlap:

- The largest single compile on record is ecstasy from source in the language server. Its live
  data peaks at about 450 MB, mostly TypeInfos, MethodInfos and their maps, and the analysis holds
  about 330 MB while a copy stays open (`doc/errs/remaining.md:82-86`).
- The heap gate for library compiles is 960 MiB, `(1L shl 30) - (1L shl 26)`, sized for one compile
  at a time (`XdkLibrarySources.kt:80-83`; `doc/errs/remaining.md:93-94`).
- A project compile's peak, including its full copy of ecstasy, has not been measured. The
  serialized bundle is 3,797,164 bytes, of which `ecstasy.xtc` is 1,687,427
  (`ls -la lang/lsp-server/build/resources/main/org/xvm/lsp/xdk/`). A materialized copy is larger
  in memory by a factor nobody has measured *(inferred)*.

A variant removes the shared source altogether: **give each attempt a private, lazily loaded
library set from immutable bytes**. Each `loadModule` would return `new FileStructure(bytes)` for
the attempt, at index 0 or behind a read-through that then has nothing left to protect. That
removes sections 3.2, 3.3 and 2.5 for compiles.

- Cost: deserialization CPU per attempt, against today's clone-and-register per attempt; neither is
  measured.
- Memory may *fall*: today's copy materializes every library completely (section 2.3), while a lazy
  private load materializes only what the compile touches. The shared, fully materialized source
  would also no longer be kept for the life of the JVM *(inferred; measure first)*.
- The language server already keeps immutable bytes for project dependencies and reopens them per
  attempt (`lang/lsp-server/src/main/kotlin/org/xvm/lsp/adapter/xdk/XdkDependency.kt:243-246`,
  `javatools/lsp-compiler.md`, "Preserve tolerant repositories…"). The bundle would follow the same
  pattern.

### 5.2 Freezing, with or without a per-compile overlay

**Freezing the sources (cheap; a detector, not a fix).** `XvmStructure.ensureReadOnly` marks a
whole `FileStructure`, its pool and its constants read-only (`asm/XvmStructure.java:203-226`). It
is tested (`javatools/src/test/java/org/xvm/asm/XvmStructureTest.java:43`) but has no caller in
production:

```bash
grep -rn "\.ensureReadOnly()" javatools/src/main/java   # VersionTree calls and super calls only
```

Marking walks `getContained()`, which is `children()` for a component
(`asm/Component.java:2194-2196`), so it materializes the whole tree. The first copy does that
anyway. Once frozen:

- structural mutators throw through `verifyMutable` (`asm/XvmStructure.java:177-182`;
  `asm/Component.java:260-534`);
- registration into the source pool throws (`asm/ConstantPool.java:199`);
- copies are still mutable (`asm/XvmStructure.java:623-627`).

That turns the versioned path of section 2.5 and any source-pool registration into immediate
failures. It does not cover caches on constants, which never call `verifyMutable`. This is the
cheapest way to find what nobody has found yet.

To freeze as instances come out of the repository, a small wrapper repository would suffice. A
`DirRepository` reloads a modified module (`asm/DirRepository.java:409`), and a frozen module cannot
be modified, so freezing keeps its instance stable.

**Sharing one frozen, linked copy with a per-compile overlay for caches (expensive; not
recommended).** This would save the per-compile copy, but it means taking every cache off the
constants and the pools:

- TypeInfo and invalidation snapshots (`TypeConstant.java:8600-8612`) and relation and usage maps
  (`:8619-8636`);
- the deferred and invalidation lists (`asm/ConstantPool.java:3791-3802`);
- `m_component` and the property caches;
- redirecting the deliberate cross-pool registrations (`asm/ConstantPool.java:3446-3450`) into the
  compile's pool.

The scale: 165 `ensureTypeInfo(` calls in 50 files, 388 `.isA(` calls, and 435 `getConstantPool()`
calls in `asm/constants` alone:

```bash
grep -rn "ensureTypeInfo(" javatools/src/main/java/org/xvm/{compiler,asm} | wc -l
grep -rln "ensureTypeInfo(" javatools/src/main/java/org/xvm/{compiler,asm} | wc -l
grep -rn "\.isA(" javatools/src/main/java/org/xvm/{compiler,asm} | wc -l
grep -rn "getConstantPool()" javatools/src/main/java/org/xvm/asm/constants | wc -l
```

That is a redesign of the type system's caching, out of proportion with the problem.

### 5.3 Making specific caches concurrent

For (b), only two caches need it:

- `s_setRecursions`, as `ConcurrentHashMap.newKeySet()`, or removed;
- the consumes and produces maps, as `ConcurrentHashMap` with a per-thread in-progress marker in
  place of the shared `IN_PROGRESS` value. They are shared only by accident, so 5.5's reset on
  adoption is the better fix.

Making everything else concurrent buys nothing for (b), because it is already private.

### 5.4 Locking

- **One lock for every compile** is today's single worker (`XdkAdapter.kt:3215-3219`).
- **A lock per source module while it is copied** would serialize the copy step and the library
  queries, and let everything after linking overlap. That closes section 3.2 without trusting the
  benign-race argument, at the cost of the copies' share of compile time (not measured). Every
  direct reader of the sources would have to take it too, including `XdkLibrarySources`.
- **Locks inside TypeInfo building** would add deadlock risk to code that is already `synchronized`
  per constant (`TypeConstant.java:1850`). No.

### 5.5 Targeted fixes

| Item | Fix | Files | Size |
| --- | --- | --- | --- |
| `m_cRecursiveDepth` (3.3) | Make it non-final and create a new one on adoption; move the decrement into `finally` (`TypeConstant.java:1934-1944`) | `TypeConstant.java` | ~15 lines |
| Other carried objects (3.3) | A `Constant` hook called by `adoptedBy` (`asm/Constant.java:310-320`) that lets a subclass replace per-instance objects. Use it for the two `StampedLock`s, the `TransientThreadLocal`s, `m_mapConsumes`/`m_mapProduces`, `m_diagnostics` and both `m_info` fields | `Constant.java`, `TypeConstant.java`, `ParameterizedTypeConstant.java`, `SignatureConstant.java`, `TypeParameterConstant.java`, `PropertyConstant.java`, `PropertyClassTypeConstant.java` | ~60 lines |
| Versioned import (2.5) | Copy in `LinkedRepository`'s versioned branch, as the unversioned one does (`asm/LinkedRepository.java:123-140`), so that `FileStructure.java:860-863` re-registers a private copy | `LinkedRepository.java`, maybe `FileStructure.java` | ~15 lines |
| `s_setRecursions` (3.4) | `ConcurrentHashMap.newKeySet()` | `TypeConstant.java` | 1 line |
| `_:`/label counters (3.4) | A counter per code container: `MethodDeclarationStatement` gets an instance counter as `PropertyDeclarationStatement` has (`compiler/ast/PropertyDeclarationStatement.java:205-210`); Elvis, Else and Conditional use `getCodeContainerCounter()` (`compiler/ast/AstNode.java:441-453`). Names need only be unique per method *(inferred from the method's contract)* | 4 AST files | ~25 lines |
| `PackedInteger.CACHE` (3.4) | Fill it in the static initializer, or use an `AtomicReferenceArray` | `PackedInteger.java` | ~10 lines |
| `setCurrentPool(null)` (3.5) | Use `withPool` in `NativeContainer` | `NativeContainer.java` | ~5 lines |
| Benign races (3.2) | Accept and document, or remove with 5.1's private loads | — | — |

## 6. Staged plan

### Stage 0: measure and detect (no behavior change)

- **Changes:**
  - An adoption-isolation test. For every concrete `Constant` subclass with a fixture, adopt it
    into a second pool and assert, by reflection, that no reference field of the clone is the same
    object as the source's. Exempt immutable types (`String`, enums, records, `List.of`, constants
    that registration re-points). It should fail today on `m_cRecursiveDepth` *(expected, not run)*.
  - A serial determinism test: compile the same source twice in one JVM, with a `_` variable in a
    method, and compare `writeTo` bytes. It should fail today *(expected, not run)*.
  - A measurement, not a test: wall time of the copy step against the whole compile, and the
    retained size of one attempt's copies, from a heap histogram as `doc/errs/remaining.md:84-85` did.
    Do not use heap after GC.
- **Size:** 2 test classes, ~300 lines; 2-3 days.
- **Risk:** none to production.
- **Acceptance:** both tests exist and fail for the documented reasons. The measurement is written
  down.

### Stage 1: isolate the attempts, make output deterministic

- **Changes:** the rows of section 5.5 except the last.
- **Size:** about 15 production files and ~130 lines, plus a test for versioned imports (compile
  twice against a `BuildRepository` core and check the source module's constants still belong to
  its own pool); 3-5 days.
- **Risk:** low to medium. Changing `_:` names changes emitted modules, but the names are
  "syntactically invalid" by design (`VariableDeclarationStatement.java:153`), and no test asserts
  them: `grep -rln '"_:' javatools/src/test lang/lsp-server/src/test` finds nothing. Resetting
  adopted state could expose code that relied on a stale cache *(inferred)*.
- **Acceptance:** the Stage 0 tests pass; the `javatools` and `lang` suites pass with `skipped=0`
  where the XDK outputs are required.

### Stage 2: freeze the shared sources

- **Changes:** a wrapper or option that calls `ensureReadOnly()` on every module the configured
  repository hands out, in `EmbeddingSupport.configure` and the `ModuleCompiler` constructor. Turn
  it on in the test JVMs first, then by default. Fix whatever throws: each exception is a write to
  shared state found.
- **Size:** 2-3 files plus the fixes found, unknown but expected to be small *(inferred)*; 2-4 days.
- **Risk:** medium. Writes nobody knows about surface as `IllegalStateException: … is read-only`.
  That is the purpose, but it must not reach users before the suites are clean.
- **Acceptance:** the full `javatools` and `lang` suites, the IntelliJ and VS Code playbooks in the
  container, and an XDK build all pass with freezing on.

### Stage 3: stress test, then relax the contract

The stress test is `javatools/src/test/java/org/xvm/api/ParallelCompileTest.java`:

- **Corpus:** the single-file modules in `manualTests/src/main/x` (58 entries); a few XDK libraries
  compiled from source through `ModuleInfo`, but not ecstasy; sources with deliberate errors, for
  diagnostics; and `analyzeIncomplete` and `analyzeDeclarations` calls, for the partial paths.
- **Core repositories:** run both repository types the hosts use, a `DirRepository` over the XDK
  outputs and a `BuildRepository` loaded the way `XdkLibraries` loads its bundle. Use the same
  instance for all threads (level b) and a variant with one repository per thread (level a).
- **Baseline:** compile each item serially twice and assert the two runs agree. Each result is the
  module bytes from `FileStructure.writeTo` plus the diagnostics normalized to code, severity,
  source name, range and parameters, then sorted. The baseline needs Stage 1.
- **Parallel run:** T ∈ {2, 4, 8} platform threads. Every item × R repetitions is submitted in a
  seeded shuffle and released by a `Phaser`. Compare each result with the baseline, byte for byte
  and diagnostic for diagnostic.
- **Invariants afterward:** each source pool's `size()` is unchanged; tests run with `-ea`, so
  `checkValidPools` (`asm/ConstantPool.java:254`) checks every registration; and with freezing on,
  no write reached a source.
- **Pass criteria:** results only, nothing about time. The single bound is a JUnit timeout of about
  an hour, to catch hangs. Note that the test can detect a race but cannot prove there is none. The
  deterministic guards are the freeze (Stage 2) and the adoption test (Stage 0).
- **Assumptions:** the test needs the XDK outputs, so it must report `skipped=0`.

Then update the documentation of `EmbeddingSupport` (`EmbeddingSupport.java:87-88`) and
`ModuleCompiler` (`ModuleCompiler.java:48-50`). Compilations that share a repository may run
concurrently. Each `Compilation`, `DeclarationAnalysis` and `PartialAnalysis` stays owned by the
thread that made it (`EmbeddingSupport.java:443`, `:610`, `:679-680`). A run must not be given a
module that a concurrent compile reads (section 4.c).

- **Size:** 1 test class (~400 lines) and the documentation; 2-3 days.
- **Risk:** low. A failure that shows up only sometimes means a race was found, not a flaky test,
  and must be treated that way.
- **Acceptance:** the test passes repeatedly with T = 8.

### Stage 4: the language server

Changes:

- A second lane in `XdkAdapter`: the `compiles` executor (`XdkAdapter.kt:3215-3219`) becomes a
  project lane and a library lane. Library-copy compiles and library queries (`:389-421`) go to the
  library lane.
- **Drop preemption:**
  - `yieldCopyCompiles` and its call (`XdkAdapter.kt:1209`, `:1211-1226`);
  - `requeued` (`:1232-1240`);
  - the `preempted` field and its checks (`:1300-1303`, `:1381`, `:1690`).

  A library compile no longer stops and starts again behind project work, so the 8.6 s compile is
  not repeated (`doc/errs/remaining.md:83`, `:89-92`).
- Check the adapter state that assumed one worker. `scheduled`, `overlays` and `scopes` are plain
  maps (`XdkAdapter.kt:3183`, `:3192-3193`); find out whether only the worker touches them or
  whether they are under `lifecycle`. The same goes for `runningCopy`.
- Raise the heap gate for the second lane (`XdkLibrarySources.kt:80-83`) to the library compile's
  peak plus a project compile's peak plus headroom, about 1.5 GB *(inferred until Stage 0 measures
  the project compile)*. Below the gate, keep one lane. With a heap under the gate the library
  compile is already off (`doc/errs/remaining.md:93-94`), so preemption is not needed there either.

The test uses `XdkAdapter`'s internal constructors, which take the compile functions
(`XdkAdapter.kt:89-140`). A library compile function blocks on a latch, and the test asserts that a
project compile, a cursor query and a rename all finish while it is blocked. That is deterministic,
with no timing. `XdkLibraryDocumentsTest` must still pass in both modes.

- **Size:** 1-2 files, net fewer lines; 3-5 days.
- **Risk:** medium, in the adapter's own state rather than the compiler's.
- **Acceptance:** the adapter suites pass, and the IntelliJ and VS Code playbooks pass in the
  container.

### Stage 5 (optional): private library loads

Section 5.1's variant. `XdkLibraries` keeps the bytes and hands each attempt its own lazy
`FileStructure`s. This removes the remaining benign races of section 3.2 for compiles.

- **Size:** 2-3 files; 3-5 days.
- **Risk:** low to medium (CPU for each attempt).
- **Acceptance:** the Stage 3 test passes; memory and time are no worse than the Stage 0
  measurement.

## 7. Open questions and risks

- **Writes to the sources nobody has found yet.** Section 3.2 lists the writes that reading turned
  up. The freeze in Stage 2 finds structural writes and pool registrations. It does not find
  caches on constants. A cache on a source constant that stores something other than an idempotent
  value, or that stores a mutable object, would carry state between compiles. A reflective audit
  of all `transient` fields on `Constant` subclasses is the systematic check:

  ```bash
  grep -rnE "^\s*(private|protected)\s+(final\s+)?(transient\s+)?(volatile\s+)?[A-Za-z<>?, .\[\]]+\s+[mf]_[A-Za-z]+\s*(=|;)" \
    javatools/src/main/java/org/xvm/asm/constants
  ```

  That listing is where section 3.3 came from.
- **The benign-race argument rests on the memory model**, and on the guess that compiled pools hold
  no typedefs left to resolve. Stage 5 removes the need for both.
- **Foreign types returned unregistered.** `register` returns a `TypeConstant` that is not shared
  with the pool unchanged (`asm/ConstantPool.java:195-197`). If a copy ever keeps such a reference
  to a *source* type, a compile would build TypeInfo on the shared source constant. That would be
  serialized per constant, but the TypeInfo and its diagnostics would be shared. `checkValidPools`
  under `-ea` should catch it *(inferred)*.
- **What the copy costs** in time and memory is unknown, so the gain from two lanes is too. Stage 0
  measures it.
- **The output may not be deterministic for reasons other than the counter**, such as iteration
  over identity-hashed collections during emission *(not checked)*. The stress test's baseline
  check, two serial runs agreeing, is where that would show. It has to pass before parallel results
  can be compared byte for byte.
- **Level (c) outside the language server:**
  - the renumbering in `prepareModule` (section 4.c);
  - debugger evaluation writing live classes (`compiler/EvalCompiler.java:65-79`);
  - concurrent calls to `xRTCompiler`.

  Each needs its own decision. They do not affect the language server, whose programs run in other
  JVMs.
- **Gradle direct mode** shares one class loader across tasks of a build
  (`DirectRuntimeBuildService.java:28-39`). Concurrent runs or tests there also share the
  interpreter's statics, which `EmbeddingSupport`'s documentation already says break with two
  interpreters (`EmbeddingSupport.java:90-96`). That is outside the compiler.
- **Peak heap with two lanes.** A project compile during a library compile adds the two peaks. The
  gate in Stage 4 must be set from measurements, not from this document's estimate.
