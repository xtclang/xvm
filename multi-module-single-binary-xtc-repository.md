# Plan: Multi-Module Single-Binary XTC Repository (`xtc bundle`)

> **Status (2026-08-13): phases 0-3 implemented and validated on this branch.**
> The spike answered the runtime-consumption question (both launch forms work; the merge
> fingerprint-synthesis fix doubles as the detach helper). The platform's 11 modules bundle into
> one 2.48 MB `platform.xtc` (vs ~4.9 MB of separate files — the shared constant pool halves the
> size) and the platform boots from the single file with full parity, including dynamic by-name
> module resolution and runtime jsondb module generation. Two consumers needed detached-copy
> serving of non-main bundle members: `xCoreRepository.getModule` (the
> `getResolvedModule(...).mainModule` assumption) and `FileRepository.loadModule` (the runtime
> compiler's fingerprint hoisting). Unit tests and docs are in. Phase 4 (single-file XDK) is the
> remaining work.

## Goal

Add a `bundle` verb to the XTC launcher (`xtc bundle`, alongside `init`/`build`/`run`/`test`/`disass`)
that takes a module path full of per-module `.xtc` files and merges them, as a post-pass, into a
single self-contained `.xtc` file that acts as a multi-module repository. A consumer should then be
able to run an application with `xec -L bundle.xtc <main-module>` instead of pointing `-L` at a
directory of separate `.xtc` files.

Validation target ("guinea pig"): the platform project at `../platform`, which builds 11 XTC modules
and runs via `xec -L build/install/platform/lib build/install/platform/lib/kernel.xtc <password>`.
Success criterion: the platform boots and serves identically when launched from a single bundled
blob instead of the 11-file lib directory.

The XDK itself is *out of scope for implementation* in the first iteration, but the question
"could the XDK ship as a single `.xtc`?" has been investigated and answered below
(see [Can the XDK itself ship as a single `.xtc`?](#can-the-xdk-itself-ship-as-a-single-xtc)):
conceptually yes — the runtime already fuses the core modules into one `FileStructure` in memory on
every startup — with a short list of concrete blockers that the platform work knocks out first.

## Background: what the codebase already supports

### The binary format is already a multi-module container

- `FileStructure` (`javatools/src/main/java/org/xvm/asm/FileStructure.java`) javadoc:
  "contains one or more Ecstasy (XVM) modules ... the module container". One module is the
  *primary* (main) module (`getModuleId()`); siblings are stored as child `ModuleStructure`s and
  enumerable via `moduleIds()`, retrievable via `getModule(id)` / `findModule(...)`.
- `FileStructure.merge(ModuleStructure, fSynthesize, fTakeFile)` (FileStructure.java:172-208)
  clones a module (body + children) into the container, adds fingerprints for its unresolved
  dependencies, and re-registers constants into the container's pool. `fTakeFile=true` makes the
  merged module the primary.
- Precedent — this exact mechanism is used in production today:
  - `runtime/NativeContainer.java:137-139, 746-755` merges ecstasy + turtle + crypto + net + web +
    native + app modules into one `FileStructure` for the native type system.
  - `javajit/NativeTypeSystem.java:98-100`, `asm/ModuleStructure.java:606-608`.
- Fingerprints vs. embedded modules: a fingerprint is an unresolved by-name reference satisfied at
  link time from a repository; an embedded module is the real thing. A bundle replaces "resolve
  from a lib dir at runtime" with "already embedded in the container".

### The compiler will not do this today (hence: post-pass)

- `xcc` output is strictly 1 module → 1 file. `Compiler.validateModuleOutput`
  (Compiler.java:678-716) errors with *"The single file {} is specified, but multiple modules are
  expected"*; `Compiler.process:185-190` rejects `-o file.xtc` with >1 target.
- Therefore `bundle` is a separate post-pass verb over already-compiled binaries — which also makes
  it trivially usable from Gradle builds (run it over an install/lib dir) without touching the
  compilation model.

### Repositories: the read-path gap that must be closed

- `Launcher.configureLibraryRepo(List<File>)` (Launcher.java:686-700) builds the `-L` chain:
  `BuildRepository` head + per-entry `DirRepository` (directory) or `FileRepository` (file), wrapped
  in a read-through `LinkedRepository`.
- **Gap:** `FileRepository` (asm/FileRepository.java) self-restricts to "single file with a single
  module": `getModuleNames()` returns a singleton of the primary name (:61-64), `tryLoad` returns
  only `struct.getModule()` (:201-211). `DirRepository` likewise exposes only each file's primary
  module (DirRepository.java:365-373).
- This matters doubly for the platform: the kernel resolves `host.xqiz.it`, `platformUI.xqiz.it`,
  and `proxy_manager.xqiz.it` **dynamically by name** from the injected repository at runtime
  (`kernel.x:198,221,234`), not via static dependencies. If the bundle's siblings are not
  name-resolvable through the repository interface, the platform cannot boot from a bundle.

## Design decision: generalize `FileRepository` (no new repository class)

Agreed direction: make `FileRepository` multi-module-aware on the read path, rather than adding a
`MultiFileRepository` subclass or sibling class.

Rationale:
- A subclass would override every method and could reuse none of the private single-module-shaped
  state (cached name/version/timestamp scalars) — inheritance with zero reuse.
- `configureLibraryRepo` maps *file → `FileRepository`* unconditionally, so a distinct class would
  additionally require content-sniffing dispatch there.
- The "single module" restriction lives only in `FileRepository`'s javadoc/implementation; the
  on-disk format is already a container. Generalizing makes the facade honest.
- For existing single-module `.xtc` files the generalized behavior degenerates to exactly today's,
  so it is backward compatible, and `xec -L bundle.xtc` then works with **zero changes** to
  `Runner`, the linker, or consumer projects.

Changes in `FileRepository`:
- `getModuleNames()` → all module names in the container (today: `Collections.singleton(name)`,
  FileRepository.java:61-64). Enumerate via `FileStructure.children()`/`moduleIds()` — **not**
  `getChildByNameMap()`, which throws `UnsupportedOperationException` for multi-module files
  (FileStructure.java:753).
- `loadModule(name)` → any embedded module by name (today: primary only, `tryLoad()` :201-211).
- **Must also override `loadModule(String, Version, boolean fExact)`**: the interface's default
  implementation (ModuleRepository.java:90-124) ends in `ModuleStructure.extractVersion`, which
  `assert`s `isMainModule()` (ModuleStructure.java:601) — it fails for every non-main module served
  out of a shared container. Same for `getAvailableVersions(String)` (route to the right module's
  `getVersions()`; note the existing NPE-on-null quirk mirrored in
  `LinkedRepository.getAvailableVersions:84`).
- Internal cache: name → module map + one container timestamp, replacing the scalar
  name/version/timestamp fields.
- Write path (`storeModule`) stays single-module; the bundler writes the merged container directly
  via `FileStructure.writeTo(File)`. (`storeModule` on a multi-module-read file should error rather
  than silently truncate the container to one module.)

Related read-path touchpoints to check in the same change:
- `DirRepository`'s persistent side cache stores exactly one module name per file
  (`readCache`/`writeCache`, `CACHE_VERSION = 1`, DirRepository.java:196/244/411) — if a bundle may
  ever sit *inside* a lib directory, the cache format needs a bump; for the first iteration it's
  enough that `-L <bundle-file>` goes through `FileRepository`.
- `ModuleInfo`/`--deduce` heuristics read only `getModuleId().getName()` from a binary
  (tool/ModuleInfo.java:558,1450; Runner.java:192-193) and will identify a bundle by its main
  module only — acceptable, but document it.

A separate repository class remains the fallback if bundles ever become a visibly distinct concept
(own extension, manifest, compression); nothing in this plan precludes that later.

## The `bundle` verb

### CLI

```
xtc bundle -L <module-path> [-o <output.xtc>] [--main <qualified-module-name>] [<module-name-or-file> ...]
```

- `-L <path>`: standard module path (repeatable, `File.pathSeparator`-splittable) — the modules to
  bundle are drawn from here; also used to resolve/verify dependencies.
- Trailing args: optional explicit selection (module names or `.xtc` paths). Default with no
  trailing args: bundle *all* modules found on the module path (the "glom the install dir" case).
- `-o <file>`: output file. Default: `<main-module-simple-name>.bundle.xtc` (bikeshed in review) in
  the current directory.
- `--main <name>`: which module becomes the container's primary module (`fTakeFile=true` merge).
  Default: if exactly one selected module is not depended upon by any other selected module, use it;
  otherwise require the flag (error message lists candidates).
- Standard common options apply (`-v`, `-h`, `--version`).
- XDK/system modules (`ecstasy`, `javatools_bridge`/`_native`, turtle, web, etc.) are **excluded by
  default** — they stay fingerprints resolved from the XDK at runtime, exactly as with a lib dir.
  (Optional later: `--include-system` to attempt a truly self-contained blob; out of scope now.)

### Implementation sketch (`org.xvm.tool.Bundler`)

The merge machinery has two sharp edges that shape the recipe (details in Risks):
`FileStructure.merge` **ignores** `addChild()`'s `false` return (FileStructure.java:176), so merging
a module whose id already exists as a fingerprint *silently drops the real module and keeps the
stub* — the `// TODO CP/GG: for now the order is critical` at NativeContainer.java:748 is this
exact trap. And fingerprint-vs-real is a key collision because `f_moduleById` is keyed by
`ModuleConstant` (versionless fingerprint id == versionless real id).

1. `configureLibraryRepo(options().getModulePath())` → linked read repo; enumerate/select input
   modules by **declared module name** (never file name — cf. `proxy.xtc` declaring
   `proxy_manager.xqiz.it`); explicit `.xtc` args via `new FileStructure(file, /*fLazy*/ true)`.
2. Determine the main module (rule above).
3. `var bundle = new FileStructure(mainModule, /*fSynthesize*/ false)` — main module + cloned
   fingerprints of its dependencies (copy-ctor = `merge(module, false, /*fTakeFile*/ true)`).
4. For each remaining selected module `m` (any order, thanks to the explicit removal step):
   - if `bundle.getModule(m.getIdentityConstant())` is a **fingerprint**, `removeChild` it first
     (this is what makes the merge deterministic instead of order-critical);
   - if it is already a real module, skip (duplicate input);
   - `bundle.merge(m, /*fSynthesize*/ false, /*fTakeFile*/ false)` — this also clones in
     fingerprints for `m`'s own deps unless a real module with that id is already present
     (FileStructure.java:183-190).
5. Mark every non-main merged module as `ModuleType.Embedded` — **requires a new setter** on
   `ModuleStructure` (`m_moduletype` is private with no Embedded-setter today; see Risks). Without
   it, embedded modules persist as `Primary` and `isEmbeddedModule()`'s assert fires under `-ea`
   (which the shipped launcher scripts enable).
6. Sanity pass: every remaining fingerprint in the container must be a known system/XDK module (or
   deliberately-external via a future flag) — error on dangling references so a half-bundled blob
   fails at bundle time, not boot time. Then `assert bundle.validateConstants()`
   (FileStructure.java:979) — the constant-pool merge has *no other* guard; `ConstantPool.register`
   silently refuses non-shared constants rather than throwing.
7. `bundle.writeTo(outputFile)` (reregisters + optimizes the shared pool; all-or-nothing, bundles
   are not incrementally appendable) and report the embedded module list + sizes.

Deliberately **not** calling `linkModules()` at bundle time: `linkModules(repo, ...)` aborts on the
first module missing from the repo, and the XDK modules are intentionally absent. The
fingerprint-removal merge above achieves the same "inputs fully embedded, externals remain
fingerprints" result deterministically.

### Runtime consumption — the one open design risk (spike first)

> **RESOLVED** — see [Implementation findings](#implementation-findings-phases-0-3). Both launch
> forms work; outcome 2(b) happened, but the merge fingerprint-synthesis fix *is* the detach
> helper, so no separate mechanism was needed. Two additional consumers of the single-module-file
> assumption were found and fixed at the repository boundaries.

How the Runner gets the main module out of the bundle matters more than the bundle itself:

- The `-L` chain is a read-through `LinkedRepository` (Launcher.java:699): `loadModule()` clones a
  hit into the head `BuildRepository` via `new FileStructure(module, false)`
  (LinkedRepository.java:103-112). That copy-ctor clones the module plus **only the fingerprint
  children** of its source file (FileStructure.java:183-184). In a bundle, sibling deps are *real*
  modules, not fingerprints — so a detached clone of `kernel` would lose its links to
  `auth`/`common`/…, and `PackageStructure.getImportedModule()` (resolves via
  `getFileStructure().getChild(id)`, PackageStructure.java:56-61) returns null → NPE in
  `ClassStructure.collectDependencies:824-825`.
- Countervailing mechanism: at runtime linking, `FileStructure.linkModules(repo, /*fRuntime*/true)`
  line 496 does `listModulesTodo.addAll(fileUnlinked.moduleIds())` — loading *any* module that
  lives in a multi-module file absorbs **every** module of that file into the app's structure. For
  a platform bundle this absorb-everything behavior is arguably the point.

Spike (step 0 of implementation, before polishing the CLI): hand-build a two-tiny-module bundle and
trace what `xec -L bundle.xtc bundle.xtc` actually does — whether the Runner's explicit-file path
(`Runner.java:192-193` reads the `FileStructure` directly) bypasses the read-through clone, and
whether link-time absorption restores the sibling links. Expected outcomes, in order of preference:
1. Explicit-file launch uses the container directly → works as-is; document `xec <bundle>` as the
   canonical launch form.
2. Read-through clone breaks sibling links → either (a) make the generalized `FileRepository`
   return container-attached modules and teach `LinkedRepository` not to detach multi-module hits,
   or (b) write the missing "detach helper" that re-synthesizes fingerprints from the import
   contributions when copying a module out of a shared container. (a) is less code and matches the
   absorb-everything runtime semantics; (b) is the general fix and is also what a future
   "bundle-subset" feature would need.

### Wiring recipe (from the Launcher architecture analysis)

1. `Launcher.java:86-90`: add `public static final String CMD_BUNDLE = "bundle";` — note the
   comment there: verb constants intentionally live in `Launcher` (subclass statics in the
   superclass static init deadlock class loading). Do not "clean this up".
2. `Launcher.java:100-106` `COMMANDS` map: add
   `CMD_BUNDLE, (args, console, err) -> launch(BundlerOptions.parse(args), console, err)`
   (`Map.of` caps at 10 pairs; currently 5 — fine).
3. `Launcher.java:291-324` options-type pattern switch: add
   `case final BundlerOptions opts -> new Bundler(opts, console, errListener);`
   `BundlerOptions` extends `LauncherOptions` directly, so case ordering vs. the
   `TestRunnerOptions`/`RunnerOptions` pair is not an issue — but keep it above any future
   superclass case.
4. `Launcher.java:250-270` `showHelp` text block: add the `bundle` line by hand (hand-maintained;
   drifts silently otherwise).
5. `LauncherOptions.java`: new `BUNDLER_OPTIONS = copyOptions(COMMON_OPTIONS)` + `-o`, `--main`
   options (near :108-128); nested `BundlerOptions` class with ctor
   `super(cl, BUNDLER_OPTIONS, "bundle")`, `parse(String[])`, `builder()`, `buildUsageLine`, typed
   getters, `toCommandLine()`, `toJson()`/`fromJson`, `Builder extends AbstractBuilder<Builder>` —
   follow the `DisassemblerOptions` template.
6. New `javatools/src/main/java/org/xvm/tool/Bundler.java extends Launcher<BundlerOptions>`:
   `static void main(String[] a) { Launcher.main(insertCommand(CMD_BUNDLE, a)); }`,
   `validateOptions()` (incl. `validateModulePath()`), `process()`, `desc()`.
7. Shipped launcher scripts: **no build changes needed** — the generated `xtc` script forwards
   `"$@"` verbatim (`XdkDistribution.kt:194-259`). A dedicated `xbundle` exe is possible later
   (`XdkDistribution.kt:236`, `xdk/build.gradle.kts:195,204,341-347`, `docker/Dockerfile:56`,
   `docker/scripts/extract-distribution.sh:90-92`) but not part of this plan.
8. Docs that enumerate verbs (update in the same PR): `doc/xtc-cli.md` (table :5-13 + a section
   modeled on the `disass` one at :291-313), `README.md:236`, `.devcontainer/README.md:21-22`,
   `xdk/src/main/resources/xdk/README.md:38-55`.

## Validation: the platform as guinea pig

Platform facts (from the build/run analysis):

- 11 modules, all via the XTC Gradle plugin (XDK `0.4.4-SNAPSHOT`), installed by `./gradlew
  installDist` to `build/install/platform/lib/*.xtc` (~4.8 MB total; no XDK modules in there — xec
  supplies the XDK itself). `cfg.json` sits next to `lib/` with an mtime-preservation hack.
- Canonical run: `xec -L build/install/platform/lib build/install/platform/lib/kernel.xtc <pw>`.
- File name ≠ module name in one case: `proxy.xtc` declares `proxy_manager.xqiz.it` — a reason the
  bundler must select by *declared* module name, never by file name.
- Dynamic-resolution set (must be name-resolvable from the repository at runtime): `host.xqiz.it`,
  `platformUI.xqiz.it`, `proxy_manager.xqiz.it` (optional), plus `platformDB.xqiz.it` via
  `createDbHost`. Static closure of kernel is only auth+common+platformDB.
- The platform also *generates and compiles modules at runtime* (jsondb hosts, `_web` stubs) into
  `~/xqiz.it/platform/build` and links them via
  `LinkedRepository([DirRepository(build), <injected repo>])` — the bundle only replaces the
  *install lib dir* leg, and those runtime dirs keep working unchanged.

Steps:

1. Build XDK locally with the `bundle` changes (`./gradlew installDist` in xtclang1; use that XDK's
   `xtc`/`xec`).
2. `cd ../platform && ./gradlew installDist` (unchanged platform build).
3. `xtc bundle -L build/install/platform/lib --main kernel.xqiz.it -o build/install/platform/platform.xtc`
   → expect one blob embedding all 11 modules (kernel primary).
4. Sanity: `xtc disass` or `ModuleInfo`-level check listing embedded modules; verify all 11 names
   (incl. `proxy_manager.xqiz.it`) appear.
5. Run: `xec -L build/install/platform/platform.xtc build/install/platform/platform.xtc <pw>`
   (or `-L <blob> --main`-style invocation as supported; the blob serves as both the lib path entry
   and the main-module file). Keep `cfg.json` in its expected location relative to the working dir.
6. Verify parity with the lib-dir run: kernel boots, HTTPS endpoint on 8090 answers, platformUI
   loads, `platformCLI` connects; then a shutdown via `platformCLI ... shutdown`.
7. Negative test: remove one module from the bundle selection and confirm the bundle-time sanity
   pass (step 6 of the implementation sketch) reports it, rather than a boot-time failure.

## Risks and gotchas (from the deep repository/FileStructure analysis)

**Merge/format level**
- `FileStructure.merge` ignores `addChild()`'s `false` return (FileStructure.java:176): id
  collisions silently keep the existing child (typically a fingerprint stub) and drop the real
  module. Handled by the explicit fingerprint-removal step in the bundler recipe; a follow-up
  hardening could make `merge` return/throw on collision.
- Versioned vs. versionless `ModuleConstant`s are *different keys* (`ModuleConstant.compareDetails`
  :254-271): `X` and `X v:1.0` can coexist, `findModule(name)` then returns a nondeterministic
  winner and `getChild(String)` finds only the versionless one. First iteration: reject bundling
  when two selected modules share a name, and normalize on the versioned id that the module
  actually carries.
- `ModuleType.Embedded` is currently **unreachable** — no setter exists (`m_moduletype` private;
  parser marks `embedded` imports as Required with a "performed much later" TODO,
  TypeCompositionStatement.java:1074-1076). Bundled non-main modules must not persist as `Primary`:
  `isEmbeddedModule()`'s assert (ModuleStructure.java:381-384) fires under `-ea`, and
  `collectModuleDependencies` has a `case Primary -> throw` path. Plan: add a package-private
  `markEmbedded()` (serialization already round-trips the type, ModuleStructure.java:703).
- Conditional/sibling modules are unsupported at the container level
  (`FileStructure.isSiblingAllowed():730` returns false with a TODO) — only conditional members
  inside modules exist. No action needed; noted so nobody expects conditional bundling.
- The main module id must resolve to a present child at load (`disassemble:897`) — the main module
  can never be left as a fingerprint.
- One shared `ConstantPool` for the whole container: `writeTo` → `reregisterConstants(true)` →
  `optimize()` re-sorts it on every write (bundles are regenerate-only); `validateConstants()` is
  the only correctness guard (silent-refusal semantics in `ConstantPool.register:194`).
- Multi-version modules in one file are not separable today (`purgeVersion`/`purgeVersionsExcept`
  are TODO no-ops, ModuleStructure.java:551/568) — fine, we reject same-name inputs anyway.
- File-format version gate is exact-match (`isFileVersionSupported:668-681`): a bundle is locked to
  the precise XDK format version that wrote it. Same as any `.xtc`, but a bundle makes the blast
  radius bigger — surface the producing version in `bundle`'s output and in `disass`.

**Runtime consumption**
- The read-through-clone / sibling-link hazard and the `linkModules:496` absorb-everything
  behavior: see the spike section above — this is the one question that must be answered by
  experiment before the CLI is polished.
- Memory/size: one pool for all modules should net smaller than N separate files (shared
  constants), but verify platformUI's 2.2 MB embedded GUI resources survive the merge intact.
- Ecstasy-side reflection: `xRTFileTemplate.invokeChildren:264-289` restricts a file template's
  children to the main module's dependency closure — unrelated embedded modules are invisible to
  Ecstasy code inspecting the file. Irrelevant for platform boot (the kernel goes through the
  injected repository, and `xCoreRepository` just forwards to the Java repo — it inherits the fix
  for free), but worth a native accessor eventually.
- `ModuleInfo`/`--deduce` identifies a bundle by its main module only (tool/ModuleInfo.java:558) —
  acceptable; document.

**Out of scope / non-problems**
- Native C launchers (`javatools_launcher`) are stale anyway (they pass `xcc`/`xec` as verbs, which
  aren't in `COMMANDS`) and are excluded from the distribution — untouched by this work.
- `LinkedRepository.getAvailableVersions:84` has a latent NPE for unknown modules; don't fix
  drive-by, but don't trip it either (the `FileRepository` override should return null-consistent
  results the same way).

## Implementation findings (phases 0-3)

Everything below was learned by building it; branch `multi-module-single-binary-xtc-repository`.

### Spike results (task 0)

- The two-module hand-bundle proved the format round-trips and exposed the real failure mode
  immediately: running the bundle crashed with a `validateConstants` assert inside
  `NativeContainer.createFileStructure`. Root cause: merging a module out of a container whose
  sibling dependencies are *real modules* (not fingerprints) leaves the target pool ignorant of
  those modules, so `ConstantPool.register` **silently refuses** the clone's constants (research
  gotcha #8 verified in the wild) and the clone keeps source-pool references.
- Fix: `merge()` synthesizes `Required` fingerprints for every real-sibling module in the merged
  module's transitive `collectDependencies()` closure (only when the source file
  `hasMultipleChildren()`). This one fix is what makes *detached copies* of bundle members
  self-describing — the planned "detach helper" fell out for free.
- `merge()` also now supersedes a same-id *fingerprint* child with the real module instead of
  silently keeping the stub (`addChild()`'s ignored `false` return, research gotcha #1). Real-vs-
  real collisions still no-op; the Bundler guards duplicates upstream.
- Subtle timing bug worth remembering: the primary module id must be established *provisionally
  before* fingerprint synthesis (the `ModuleStructure` ctor auto-marks new modules as fingerprints
  only if the file already has a different primary id), then **re-established after**
  `registerConstants` — the clone's identity constant is replaced by the pool-owned one during
  registration, and serializing the pre-registration constant writes a garbage position (this
  produced corrupt, unreadable bundles until fixed).
- `linkModules` (runtime phase) needed the predicted tolerance: ids absorbed from a multi-module
  file (`listModulesTodo.addAll(fileUnlinked.moduleIds())`) that have no fingerprint child in the
  app structure are now skipped — sharing a container with a dependency does not make a module a
  dependency.
- `validateConstants()` asserts a runtime-only precondition (`NakedRefType` present), so offline
  bundling uses the split-out `validateModuleConstants()`.
- Verified end-to-end: `xec -L bundle.xtc bundle.xtc` (explicit-file path) AND
  `xec -L bundle.xtc ModB` (repository/by-name path incl. the read-through clone) both run.

### Two more "a module's file is its own file" consumers (found in platform validation)

The codebase-wide assumption that a module's `FileStructure` is a single-module file surfaced in
exactly two more places, both fixed by serving non-main bundle members as **detached copies** at
the repository boundaries (the copies are correct thanks to the merge fingerprint synthesis):

1. **`ModuleRepository.getResolvedModule`** (lib_ecstasy mgmt/ModuleRepository.x:39) returns
   `template.parent.resolve(this).mainModule` — for a bundle-attached module, `parent` is the
   bundle and `mainModule` is the *bundle's* main. Symptom: jsondb's `ModuleGenerator.findSchema`
   scanned **kernel's** classes while reporting "Schema is not found in module
   'platformDB.xqiz.it'". Fix: `xCoreRepository.getModule` (native) hands out a detached copy for
   non-main container members.
2. **Runtime-compiler fingerprint hoisting**: compile-time `linkModules` hoists transitive
   fingerprints into the compiled file (`fileTop.addChild(moduleFingerprint)`), assuming
   downstream repo modules resolve to fingerprint-shaped files. Against a raw bundle the
   downstream "fingerprint" is a real embedded module and hoisting silently degrades — the
   platform's runtime-generated `platformDB_jsondb` module compiled *without* its transitive
   fingerprints, exploding later at `invokeReplace` with `Missing module "common.xqiz.it"`. Fix:
   `FileRepository.loadModule` serves non-main members as detached copies (memoized in the cache;
   the container's main module stays attached, and staleness is judged by the main module only,
   since fresh clones always read as "modified").

The Java `-L` chain (Runner/linker) deliberately still sees the container-attached main module —
the explicit-file launch path reads the `FileStructure` directly and `BuildRepository` stores by
reference, so no copies are made where none are needed.

### Platform validation results (task 3)

- `xtc bundle -v -L build/install/platform/lib --main kernel.xqiz.it -o platform.xtc`:
  all **11 modules** bundled (selection is by *declared* name — `proxy.xtc` correctly enters as
  `proxy_manager.xqiz.it`), externals correctly limited to 16 `xtclang.org` fingerprints.
- **Size: 2,479,766 bytes vs ~4.9 MB** of separate files — the shared constant pool roughly
  halves the footprint.
- `xec -L platform.xtc platform.xtc password` boots with **full parity**: identical log sequence
  (AccountManager → HostManager → UI controller → certificate → "Started the XtcPlatform"),
  HTTPS endpoint answers identically. Dynamic by-name resolution (`host.xqiz.it`,
  `platformUI.xqiz.it`, `proxy_manager.xqiz.it`) and the runtime jsondb module
  generation-and-compile path both work against the bundle.
- Negative test: omitting a *statically imported* module (`common.xqiz.it`) from the selection
  produces the bundle-time warning naming it. **Documented limitation**: modules resolved only
  *dynamically by name* (host/platformUI/proxy_manager style) are invisible to static analysis and
  cannot be warned about — bundling "everything on the module path" (the default) is the safe
  mode for such applications.

### Test coverage

- `BundlerTest` (javatools): options parse + command-line round-trip, launcher dispatch (help,
  invalid args), multi-module container round-trip through `FileStructure` + generalized
  `FileRepository` (detached serving, memoization, version-aware lookup), single-module
  regression.
- `LauncherOptionsJsonTest`: `BundlerOptions` JSON round-trip.
- `XdkIntegrationTest`: bundler dispatch coverage.
- Full `javatools:test` and `xdk:test` suites green with all core changes.

## Can the XDK itself ship as a single `.xtc`?

> **Status (2026-08-13): DONE as an opt-in artifact.** `./gradlew xdk:bundleXdk` (JavaExec over the
> installed dist, configuration-cache compatible, incremental) produces `xdk/build/bundle/xdk.xtc`:
> **all 22 XDK modules** — ecstasy (main), mack, `_native`, and every lib — in one **4,355,750-byte
> fully self-contained file with zero remaining fingerprints** (separate files: ~4.6 MB). A new
> `--include-system` bundler flag lifts the implicit xtclang.org exclusion. Validated by compiling
> AND running the two-module spike app with `-L xdk.xtc` as the *only* system library reference
> (`java -jar javatools.jar build/run`, bypassing the launcher scripts' -L injection).
>
> **Footprint measurement (the phase-4 decision point)**: trivial-app run, lib-dir XDK 255 MB max
> RSS / 2.74 s vs bundle XDK 320 MB / 3.03 s — the absorb-all cost of materializing all 22 modules
> is **+25% RSS, +11% startup** for an app that needs three of them; the delta shrinks as apps use
> more of the XDK. **Decision: option (a) — accept absorb-all for the opt-in artifact.** Revisit
> lazier loading only if/when the single file becomes the default distribution form.
>
> Remaining follow-ups (deliberately not done): switching the generated launcher scripts to
> `-L xdk.xtc` (XdkDistribution.kt), docker image, publishing xdk.xtc as a distribution artifact,
> and making the single file the default.

**Yes — and it is now planned as a phase, not a maybe.** The original evidence:

- The runtime *already does this in memory on every startup*: `NativeContainer` merges ecstasy +
  turtle + `_native` into one `FileStructure` (NativeContainer.java:137-141) and per-app containers
  additionally merge crypto/net/web (:744-755). The JIT does the same (NativeTypeSystem.java:98-101).
  A single-file XDK is essentially persisting what the runtime builds anyway.
- Bootstrapping is a **non-problem** for a post-pass: the XDK compiles exactly as today (ecstasy and
  turtle even share one `xcc` invocation already, emitting two files); the bundle step runs on the
  finished binaries. Nothing circular.
- Native template binding survives: templates bind by name path against `mack.xtclang.org` /
  `_native.xtclang.org` fetched via `fileRoot.getChild(...)` (NativeContainer.java:147-149,724-729)
  — a bundle preserves qualified names. `NakedRefType` is a transient pool field re-derived from
  mack's `NakedRef` on load, so it round-trips.
- `prelinkSystemLibraries` (Launcher.java:753-773) and the whole `-L` machinery only need
  `getModuleNames()`/`loadModule(...)` — satisfied by the same generalized `FileRepository`.
- The format-version lock is a non-issue here: the XDK bundle ships *with* the exact XDK that wrote
  it.

XDK-specific work items (the reasons it is phase 4 and not phase 1):

1. **Distribution plumbing**: the generated launcher scripts hardwire
   `-L lib -L javatools/javatools_turtle.xtc -L javatools/javatools_bridge.xtc`
   (`XdkDistribution.kt:69-88` `REQUIRED_XTC_MODULES`/`generateModulePaths`) → becomes a single
   `-L <xdk.xtc>`; touches `XdkDistribution.kt`, `xdk/build.gradle.kts` distribution contents,
   `docker/Dockerfile`, `docker/scripts/extract-distribution.sh`.
2. **Per-app footprint policy** — the one real decision. `linkModules:496` absorbs *every* module
   of a multi-module file into an app's structure once any module is loaded from it. Today an app
   container carries the fixed subset ecstasy+turtle+crypto+net+web+native; with a full-XDK blob it
   would drag xenia, jsondb, xml, cli, etc. into every app. Options: (a) accept it (simplest;
   measure first — it may be cheap since the pool is shared), (b) ship two blobs (core-runtime
   bundle + the rest as today's lib dir), (c) implement the detach helper (fingerprint
   re-synthesis) so loads from a bundle can be surgical. Decide with data from the spike.
3. **Main-module choice**: `ecstasy.xtclang.org` as the container's primary (the deep-analysis
   recommendation; it is what every consumer fingerprints anyway).
4. Same `markEmbedded()` and version-aware `loadModule` prerequisites as the platform case — no
   extra work, they land in phases 1-2.

## Testing

- `javatools` unit tests following existing patterns:
  - `OptionsTest` — `BundlerOptions` parse/round-trip (`toCommandLine`).
  - `LauncherOptionsJsonTest` — JSON round-trip for `BundlerOptions`.
  - `LauncherVersionTest`-style dispatch test — `Launcher.launch(Launcher.CMD_BUNDLE, ...)`.
  - New `FileRepository` tests: multi-module file → `getModuleNames()`/`loadModule()`; existing
    single-module files unchanged.
  - `Bundler` round-trip test: compile N tiny modules (manualTests fixtures), bundle, re-open with
    `FileStructure`, assert `moduleIds()`, re-load each module via `FileRepository`.
- `xdk/src/test/java/org/xvm/xdk/XdkIntegrationTest.java:353-371` — add `bundle` to the verb
  integration coverage.
- End-to-end: the platform guinea-pig procedure above (manual for the first iteration).

## Implementation order

0. **Spike** (throwaway): hand-merge two tiny modules into one file, run it, and answer the
   runtime-consumption question (read-through clone vs. explicit-file path vs. absorb-on-link).
   This de-risks everything downstream and picks between fix (a) and (b) above.
1. `FileRepository` read-path generalization (incl. the version-aware `loadModule` override) +
   `ModuleStructure.markEmbedded()` + unit tests. Standalone, immediately useful.
2. `Bundler` + `BundlerOptions` + `Launcher` wiring + help/docs + unit tests.
3. Local XDK install; **platform guinea-pig run** per the validation section; record results
   (sizes, boot parity) in the PR.
4. **XDK single-file bundle**: distribution plumbing (`XdkDistribution.kt`, xdk dist contents,
   docker), footprint-policy decision from spike/measurement data, `xdk.xtc` as an additional
   (initially opt-in) distribution artifact alongside the lib dir.
5. Follow-ups (separate PRs, only if wanted): Gradle plugin `xtcBundle` task, dedicated exe,
   `--include-system` fully-self-contained app blobs, making the single-file XDK the default
   distribution form.
