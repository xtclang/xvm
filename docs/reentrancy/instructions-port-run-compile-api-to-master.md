# Instructions: port the compile/run embedding API (`XtcEngine`) to a branch off master

**Audience:** an autonomous agent starting fresh, with no prior context on this work.
**Goal:** reproduce the first-class Java compile+run embedding API (`org.xvm.api.XtcEngine`) that
was built on the `lagergren/lazy-instance` branch, but on a **new branch cut directly from `master`**.

This is deliberately a **contrast piece**. On `lazy-instance` the API is the payoff for a large
reentrancy / container-model hardening effort. On `master` those fixes do not exist. The port is
therefore expected to **compile and run correctly for one-shot, sequential compile+run**, and to be
**unsafe for the warm / parallel workload the API is actually designed for** (see
[§7 Honest limitations](#7-honest-limitations-bake-these-into-the-port)). Do **not** try to fix that by
porting the reentrancy work — that would defeat the purpose and effectively rebuild `lazy-instance`.

---

## 1. Mission

Deliver, on a master-based branch:

1. `javatools/src/main/java/org/xvm/api/XtcEngine.java` — a resident engine that boots the runtime +
   native container once and offers:
   - `compile(String name, String source)` / `compile(Map<String,String>)` — **in-memory** compile
     (LSP-buffer style), returning structured diagnostics and assembled (runnable) modules.
   - `CompileResult.writeTo(File dir)` — sync the in-memory compilation out to `.xtc` binaries.
   - `run(CompileResult, String moduleName)` — run a compiled module as a nested container under the
     shared native plane, returning a `CompletableFuture<ObjectHandle>` that completes when the run
     finishes (exceptionally if it threw).
2. The three small runtime hooks the run path needs (see §5).
3. Tests mirroring the `lazy-instance` ones, all green, plus a short note documenting where the
   master port diverges and why.

**Do not** port: the pool-publication fence (`markRuntimePublished`), single-root enforcement, the
`INSTANCE`-cache / pool-race / clone / array hardening, or any other reentrancy commit. The port is
the *minimum* needed to compile+run.

---

## 2. Ground rules (read `AGENTS.md` first)

- **Git:** never push, create remote branches, delete branches, or open PRs without explicit user
  approval. Local commits/branches are fine. Do **not** touch `master` itself — work on the new branch.
- **Gradle:** never run `./gradlew clean` combined with any other task — `clean` runs alone, then the
  rest. All Gradle changes must stay configuration-cache compatible.
- **Style:** newline at EOF; no star imports; no fully-qualified Java type names where an import works;
  prefer `var`; modern Java (records, streams, `List.of`, fluent/functional) — no Java-1.0-style
  arrays/objects. No `Co-Authored-By` lines.

---

## 3. Branch setup

```bash
git checkout master
git checkout -b <yourname>/embedding-api-on-master   # do NOT push without approval
```

Confirm you are NOT carrying lazy-instance commits:

```bash
git rev-list --count master..HEAD   # should be 0 right after branching
```

---

## 4. Reference implementation (read these on `lagergren/lazy-instance`)

The finished, working source lives on `lagergren/lazy-instance`. Read it with `git show` — do **not**
merge it. Key references:

| What | Where on `lazy-instance` |
|---|---|
| Engine (compile + run + writeTo) | `javatools/src/main/java/org/xvm/api/XtcEngine.java` |
| Runtime hook 1 (completion future) | commit `03555b779` — `MainContainer.java` (~25 lines) |
| Runtime hook 2+3 (linchpin: `runModule` + `createForHost` + injection fallback) | commit `f5d3e45eb` — `Container.java`, `NestedContainer.java` (~104 lines) |
| Test support (build-output locator, CLI compile) | `javatools/src/test/java/org/xvm/api/EmbeddingTestSupport.java` |
| Tests | `javatools/src/test/java/org/xvm/api/XtcEngineTest.java`, `.../runtime/RuntimeCompletionFutureTest.java`, `.../runtime/HostNestedContainerTest.java` |

```bash
git show f5d3e45eb -- javatools/src/main/java/org/xvm/runtime/Container.java
git show f5d3e45eb -- javatools/src/main/java/org/xvm/runtime/NestedContainer.java
git show 03555b779 -- javatools/src/main/java/org/xvm/runtime/MainContainer.java
git show lagergren/lazy-instance:javatools/src/main/java/org/xvm/api/XtcEngine.java
```

**Substrate that already exists on master (verified) — you build on these, don't add them:**
- `ServiceContext.callLater(FunctionHandle, ObjectHandle[])` already returns
  `CompletableFuture<ObjectHandle>` (completes normally with the result, exceptionally with a
  `WrapperException` carrying the XTC `ExceptionHandle`).
- `NestedContainer`'s constructor already accepts an `hProvider` and stores `f_hProvider`
  (public final). Passing `null` is valid.
- `findModuleMethod`, `resolveClass`, `ensureSingletonConstConstant`,
  `xRTFunction.NativeFunctionHandle`, `TypeComposition.getMethodCallChain` all exist on master.

---

## 5. The port, step by step

### Step A — Runtime hook 1: expose the run-completion future (`MainContainer`)

Master's `MainContainer.invoke0` calls `m_contextMain.callLater(...)` and **throws the returned future
away**. Capture it.

- Add field `private CompletableFuture<ObjectHandle> m_futureResult;` and
  `import java.util.concurrent.CompletableFuture;`.
- Assign it where `invoke0` calls `callLater`:
  `m_futureResult = m_contextMain.callLater(hInstantiateModuleAndRun, Utils.OBJECTS_NONE);`
- Add accessor `public CompletableFuture<ObjectHandle> futureResult() { return m_futureResult; }`.
- **DIVERGENCE:** the `lazy-instance` version of `invoke0` also calls
  `frame.poolContext().markRuntimePublished(...)`. **Omit that line** — master has no pool-publication
  fence (`ConstantPool.markRuntimePublished` does not exist on master). Everything else ports verbatim.

### Step B — Runtime hook 2: `Container.runModule(...)`

Add this method to `Container` (base class). Port the body from `f5d3e45eb` **verbatim except drop the
`markRuntimePublished` call**. The reference body:

```java
public CompletableFuture<ObjectHandle> runModule(String sMethodName, ObjectHandle... ahArg) {
    ServiceContext ctx      = ensureServiceContext();
    ModuleConstant idModule = getModule();
    MethodConstant idMethod = findModuleMethod(sMethodName, ahArg);
    if (idMethod == null) {
        return CompletableFuture.failedFuture(new IllegalStateException(
                "Missing \"" + sMethodName + "\" method for " + idModule.getValueString()));
    }
    TypeComposition   clzModule = resolveClass(idModule.getType());
    SignatureConstant sigMethod = idMethod.getSignature();
    CallChain         chain     = clzModule.getMethodCallChain(sigMethod);
    boolean           fReturn   = sigMethod.getReturnCount() > 0;

    FunctionHandle hInstantiateAndRun = new xRTFunction.NativeFunctionHandle(this, (frame, ah, iRet) -> {
        SingletonConstant idSingleton = frame.poolContext().ensureSingletonConstConstant(idModule);
        ObjectHandle      hModule     = frame.getConstHandle(idSingleton);
        int               iReturn     = fReturn ? Op.A_STACK : Op.A_IGNORE;
        Frame.Continuation invoke = frameCaller -> {
            ObjectHandle target = frameCaller.popStack();
            // NOTE (master port): the lazy-instance version calls
            // frameCaller.poolContext().markRuntimePublished(...) here. OMITTED on master.
            return chain.invoke(frameCaller, target, ahArg, iReturn);
        };
        if (Op.isDeferred(hModule)) {
            return hModule.proceed(frame, invoke);
        }
        frame.pushStack(hModule);
        return invoke.proceed(frame);
    });
    return ctx.callLater(hInstantiateAndRun, Utils.OBJECTS_NONE);
}
```

Add imports as needed: `SignatureConstant`, `xRTFunction`, `FunctionHandle` (+ whatever the file
doesn't already import). Verify each referenced symbol resolves on master before moving on.

### Step C — Runtime hook 3: `NestedContainer.createForHost(...)` + host injection fallback

Two additions to `NestedContainer`:

1. **Factory** (host container = no guest provider):

```java
public static NestedContainer createForHost(Container containerParent, ModuleConstant idModule,
                                            List<ModuleConstant> listShared) {
    return containerParent.f_runtime.registerContainer(
            new NestedContainer(containerParent, idModule, null, listShared));
}
```

Master's constructor is already public and takes `hProvider` — call it with `null`; no constructor
change needed. (Optionally add `registerHostResource(InjectionKey, InjectionSupplier)` if you want
hosts to override resources — not required for the basic tests.)

2. **Injection fallback (REQUIRED).** Master's `getInjectable` is strict: it returns `null` when a
   resource isn't in `f_mapResources`, so a host container gets **no `Console`** and every run dies
   with "Invalid resource". Add a parent fallback **only** for host containers (`f_hProvider == null`):

```java
public ObjectHandle getInjectable(Frame frame, String sName, TypeConstant type, ObjectHandle hOpts) {
    InjectionSupplier supplier = f_mapResources.get(new InjectionKey(sName, type));
    if (supplier != null) {
        return supplier.supply(frame, hOpts);
    }
    if (f_hProvider == null) {
        // trusted host container: fall back to the parent/native plane for standard resources.
        // Guest containers (f_hProvider != null) keep the strict sandbox.
        ObjectHandle hResource = f_parent.getInjectable(frame, sName, type, hOpts);
        if (hResource != null) {
            return hResource;
        }
    }
    return type.isNullable() ? xNullable.NULL : null;   // keep master's existing nullable form
}
```

> Note: `lazy-instance` writes the nullable branch as `xNullable.makeHandle(frame)` (part of separate
> per-container work). On master, keep the existing `xNullable.NULL` form — only the
> `f_hProvider == null` fallback is what you need to add.

### Step D — `XtcEngine.java` (fully portable — copy the compile pipeline)

This half has **zero** branch dependencies. Port `XtcEngine.java` from `lazy-instance` essentially
verbatim. Its important pieces, all using public master APIs:

- **Constructor:** `new Runtime()` → `start()` → `NativeContainer.create(runtime, repoLibrary)`. The
  native-container boot is what makes turtle (`mack.xtclang.org`) + native bridge
  (`_native.xtclang.org`) available from one module path — the caller never passes per-request
  `-L turtle.xtc -L native.xtc` flags.
- **`compile(Map)`:** read-through `LinkedRepository(true, repoBuild, f_repoLibrary)` with a
  `BuildRepository` at front; `prelinkSystemLibraries` (load+link `ECSTASY_MODULE` and `TURTLE_MODULE`
  so read-through caches them into the build repo); then per compiler:
  `generateInitialFileStructure` → `repoBuild.storeModule` → `linkModules(repoCompile)` →
  `resolveNames` → **inject NakedRef across every module in `repoBuild`** → `validateExpressions` →
  `generateCode`. **Phase order is load-bearing** (matches `org.xvm.tool.Compiler`): inject turtle
  AFTER link+resolveNames, BEFORE validate.
- **NakedRef injection:** `((ClassStructure) repoBuild.loadModule(TURTLE_MODULE).getChild("NakedRef"))
  .getFormalType()`, set on the pool of every module in `repoBuild` — mirrors
  `org.xvm.tool.Compiler.injectNativeTurtle`.
- **`assemble(FileStructure)`:** round-trip through serialization —
  `struct.writeTo(ByteArrayOutputStream)` then `new FileStructure(new ByteArrayInputStream(bytes))
  .getModule()`. **Required:** a freshly-compiled in-memory module has unresolved op arguments; assembly
  rewrites them into constant-pool indices. Without it the first op fails with an `AssertionError` on
  the constant id. Store assembled modules into the result repo.
- **`run(CompileResult, name)`:** `f_containerNative.createFileStructure(moduleApp)` (merges the
  native container's turtle whose pool already carries NakedRef — no hand-patching needed) →
  `struct.linkModules(repoRun, true)` → `NestedContainer.createForHost(f_containerNative, moduleId,
  List.of())` → `runModule("run")`.
- **`CompileResult.writeTo(File dir)`:** store each assembled module into a writable
  `DirRepository(dir, false)` (writes `<unqualifiedName>.xtc`).
- Keep the `Builder` (`modulePath(File...)`), the `Diagnostic` record, and the TODO comments
  (source-directory-tree compile via `ModuleInfo`, method-level incremental recompilation, unified
  pipeline `DiagnosticSink`).

### Step E — tests + test support

Port `EmbeddingTestSupport.java` and the three test classes from `lazy-instance`. They:
- locate gradle build outputs (`xdk/build/install/xdk/{lib,javatools}`,
  `lib_ecstasy/build/xtc/main/lib`, `javatools_bridge/build/xtc/main/lib`);
- guard with `assumeTrue(systemModulesAvailable())`;
- cover: clean run completes the future normally; throwing run completes it exceptionally; host runs
  two nested modules over one native plane; engine compiles+runs in-memory; multi-module compile;
  in-memory→disk sync round-trip; structured diagnostics on bad source.

---

## 6. The three divergences from `lazy-instance` (summary)

1. **Drop `markRuntimePublished`** in both `MainContainer.invoke0` and `Container.runModule` — master
   has no `ConstantPool.markRuntimePublished`.
2. **Add the `f_hProvider == null` fallback** in `NestedContainer.getInjectable` — master's is strict
   and would starve host containers of `Console`. Keep master's `xNullable.NULL` nullable form.
3. **`createForHost` needs no constructor change** — master's `NestedContainer` constructor already
   accepts `hProvider`; pass `null`.

Everything else (the entire compile pipeline, assemble round-trip, writeTo, `futureResult` capture,
`runModule` body) ports directly.

---

## 7. Honest limitations (bake these into the port)

The port works for **one-shot, sequential** compile+run. It is **NOT** safe for the warm/parallel
workload the API is designed for, because master lacks this branch's reentrancy fixes. Document these
plainly in the master `XtcEngine` Javadoc (do not claim parallel or repeated-reuse safety):

- Shared thread-unsafe `INSTANCE` caches and native-injection singletons.
- Published-pool mutation races (no publication fence — you dropped it).
- No single-root enforcement; sibling-main / repeated-bootstrap paths hit static corruption
  (cf. issue 543).
- TypeInfo / lazy-cache races under concurrent compiles or runs.

Frame the deliverable as: "the same API on master — compiles, runs once, and here is exactly where it
falls over under the warm/parallel load it was built for." That contrast is the point.

---

## 8. Verification gates (must be green before any commit)

Ensure XDK build outputs exist first (otherwise the tests silently `assumeTrue`-skip):

```bash
./gradlew xdk:installDist                      # run ALONE if you just cleaned
```

Then:

```bash
./gradlew :javatools:test :javatools_utils:test --rerun-tasks --no-build-cache
```

- Confirm the embedding tests actually **ran** (`skipped=0`) by reading the JUnit XMLs under
  `javatools/build/test-results/test/*.xml` — console output truncates.
- **Red-verify** each new test: confirm it fails before the corresponding hook/fix is in place, then
  passes after (the two decisive ones on this project were "Mack module (javatools_turtle) is missing"
  before correct turtle handling, and a `Frame.getConstant` `AssertionError` before the assemble
  round-trip).
- Re-run `xdk:installDist --rerun-tasks --no-build-cache` and confirm fresh output timestamps.

Commit as small, rollbackable steps (hook A, hook B+C, engine+tests) with green gates at each.
Do not push or open a PR without explicit approval.

---

## 9. Deliverables

1. The master-based branch with `XtcEngine` + the three runtime hooks + tests, all green.
2. A short comparison note (in the commit messages or a brief `.md` if the user asks) stating what
   ported directly, the three divergences, and the concurrency limitations from §7.
3. Report back the branch name and the gate results.
