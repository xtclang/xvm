# XTC Bundles: Multi-Module `.xtc` Files

This document explains what an XTC *bundle* is, why it exists, how the `xtc bundle` command
builds one, and how the compiler, linker, and runtime consume one. For command-line usage, see
the [XTC CLI Reference](xtc-cli.md#xtc-bundle---module-bundling); this document covers the
concepts and the internal mechanics.

## Why bundles exist

An Ecstasy application is compiled into one `.xtc` file per module, and deployed as a directory
of such files that the runtime resolves against (`xec -L lib/ lib/app.xtc`). That is flexible for
development, but awkward for distribution: a deployment is a *set* of files that must stay
together, be versioned together, and be pointed at together.

The `.xtc` binary format never actually required one-module-per-file: a `FileStructure` — the
on-disk container — has always been defined as holding *one or more* modules, and the runtime
already fuses multiple modules into a single in-memory `FileStructure` on every startup (the
native container merges `ecstasy`, `mack`, and `_native` this way). A bundle simply *persists*
that multi-module container to disk:

- **One file is the deployment.** `xec -L app.xtc app.xtc` replaces a lib directory; the file is
  a complete module repository that every tool (runner, compiler, linker, reflection) can resolve
  modules from by name.
- **Smaller than the sum of its parts.** All bundled modules share one constant pool. The
  xqiz.it platform's 11 modules shrink from ~4.9 MB of separate files to a 2.48 MB bundle.
- **The XDK itself can be one file.** `./gradlew xdk:bundleXdk` produces `xdk.xtc`: all 22 XDK
  modules, fully self-contained, usable as the entire system library path (see below).

## The container model

A multi-module `FileStructure` contains exactly one **main module** (`getModuleId()`), any number
of **embedded modules** (`ModuleType.Embedded` — real, complete modules that are not the main
one), and any number of **fingerprints** (`ModuleType.Optional/Desired/Required` — name-and-version
stubs for *external* dependencies, resolved from a repository at link time).

A bundle is exactly that shape: the main module (e.g. `kernel.xqiz.it`), its co-bundled siblings
as embedded modules, and fingerprints for everything left external (typically the XDK modules).
A fully self-contained bundle — such as the single-file XDK — has no fingerprints at all.

## What `xtc bundle` does

```
xtc bundle [-L path]... [-o output] [--main module] [--include-system] [module_name_or_file ...]
```

The pipeline:

1. **Selection.** With no trailing arguments, every *non-system* module found on the module path
   is selected. Trailing arguments select explicitly, by qualified module name or `.xtc` file
   path. Selection always keys on the **declared module name**, never the file name — e.g. the
   platform's `proxy.xtc` declares (and bundles as) `proxy_manager.xqiz.it`.
2. **Main module determination.** `--main <name>` picks explicitly; otherwise the main module is
   inferred as the only selected module that no *other* selected module imports. Zero or multiple
   candidates is an error naming them (circular imports also require `--main`).
3. **Merge.** Starting from a container anchored on the main module, every other selected module
   is merged in. `FileStructure.merge` supersedes a same-id *fingerprint* with the real module
   (historically it silently kept the stub), and synthesizes `Required` fingerprints for any
   dependency of a merged module that remains external — so the bundle is always self-describing.
4. **Embedded marking.** Every non-main real module is marked `ModuleType.Embedded`
   (`ModuleStructure.markEmbedded()`); `Primary` is reserved for the main module of a file.
5. **Sanity reporting.** Remaining fingerprints (the bundle's external, run-time-resolved
   dependencies) are listed. A **warning** names any module that is present on the module path,
   statically imported by a bundled module, but *not* selected — a likely mistake. Note the
   limitation: modules loaded *dynamically by name* (e.g. `repository.getModule("host.xqiz.it")`
   in application code) are invisible to static analysis and cannot be warned about; the default
   "bundle everything on the module path" mode is the safe choice for such applications.
6. **Write.** The shared constant pool is re-registered and optimized, then the container is
   written to `-o` (default `<main-simple-name>.bundle.xtc`). Bundles are regenerate-only — there
   is no incremental append.

### `--include-system`: what it is and why it exists

By default, implicit selection **excludes system modules** (qualified names ending in
`xtclang.org`), and they stay behind as fingerprints. This is deliberate:

- An application bundle should contain *the application*. The XDK modules are the runtime's
  standard library — they ship with, and are resolved from, the XDK installation, exactly as
  with a lib directory. Embedding them into every app bundle would bloat each artifact, freeze a
  copy of the standard library inside it, and duplicate what the machine already has.
- An `.xtc` file is version-locked to the exact binary format that wrote it, so an app bundle
  carrying its own XDK copy has no compatibility advantage over one that resolves the installed
  XDK at run time.

`--include-system` lifts the exclusion for the one case where embedding the standard library *is*
the point: building a **fully self-contained** artifact, i.e. the single-file XDK itself (where
the "application" *is* the standard library), or a truly standalone blob that must run with no
XDK lib directory present. Without the flag, `xtc bundle` run via the `xtc` launcher script would
also happily glom the entire XDK into every app bundle, because the script appends the XDK's own
lib directories to the module path — the default exclusion is what keeps app bundles lean.

## How bundles are consumed

No consumer-side configuration is needed; a bundle works anywhere a lib directory or a
single-module `.xtc` works. The mechanics:

- **`FileRepository` is multi-module aware.** A file on the `-L` path exposes *every* real module
  it contains: `getModuleNames()` lists them, `loadModule(name)` serves them, and a version-aware
  `loadModule(name, version, exact)` override matches versions in place (the interface default
  would fail for non-main modules). Fingerprints are never exposed as loadable modules.
- **Non-main modules are served as detached copies** (memoized per name). Several consumers
  reasonably assume "a module's `FileStructure` is its own single-module file": Ecstasy's
  `getResolvedModule` resolves via `template.parent.resolve(repo).mainModule`, and the runtime
  compiler hoists transitive dependency fingerprints out of downstream files. Handing out a
  detached copy — its own small `FileStructure` with the module plus fingerprints for its
  dependencies — makes a bundle member indistinguishable from a module loaded from its own file.
  The merge fingerprint synthesis (step 3 above) is what makes these copies complete.
- **Link-time tolerance.** When the runtime linker absorbs the module list of a multi-module
  file, members that the running application never imports are skipped rather than treated as
  link failures — sharing a container with a dependency does not make a module a dependency.
- **Launch forms.** Both work:
  `xec -L app.xtc app.xtc` (explicit file: the container is read directly and its main module
  runs) and `xec -L app.xtc some.module.name` (repository resolution by name).

## Examples

Bundle an application's install directory (main module inferred, XDK stays external):

```bash
xtc bundle -L build/install/myapp/lib
xec -L myapp.bundle.xtc myapp.bundle.xtc
```

Bundle the xqiz.it platform — 11 modules, one 2.48 MB file, boots identically to the lib dir:

```bash
xtc bundle -L build/install/platform/lib --main kernel.xqiz.it -o platform.xtc
xec -L platform.xtc platform.xtc <password>
```

Build the single-file XDK and use it as the entire system library path:

```bash
./gradlew xdk:bundleXdk        # -> xdk/build/bundle/xdk.xtc (22 modules, self-contained)
java -jar javatools.jar build -L xdk.xtc MyApp.x
java -jar javatools.jar run   -L xdk.xtc MyApp.xtc
```

The fully bundled extreme — an entire platform deployment as two files and a jar, no lib
directories anywhere:

```bash
java -jar javatools.jar run -L xdk.xtc -L platform.xtc platform.xtc <password>
```

## Design notes

Decisions worth remembering, and why they went the way they did:

- **`FileRepository` was generalized, not subclassed.** The "single file, single module"
  restriction lived only in `FileRepository`'s implementation — the on-disk format is already a
  container. A `MultiFileRepository` subclass would have overridden every method while reusing
  none of the single-module-shaped private state, and `Launcher.configureLibraryRepo` maps
  *file → `FileRepository`* unconditionally, so a distinct class would also have needed
  content-sniffing dispatch. Generalizing means `xec -L bundle.xtc` works with zero changes to
  the Runner, the linker, or consumer projects. The version-aware
  `loadModule(name, version, exact)` override is mandatory: the interface default funnels into
  `ModuleStructure.extractVersion`, which asserts the module is its file's main module.
- **Non-main members are handed out as detached copies at the repository boundaries**
  (`FileRepository.loadModule`, memoized; `xCoreRepository.getModule` for Ecstasy-visible
  reflection). Several consumers assume "a module's file structure is its own single-module
  file" — Ecstasy's `getResolvedModule` takes `template.parent.resolve(repo).mainModule`, and
  the runtime compiler hoists transitive dependency fingerprints out of downstream files;
  against a raw container both silently misbehave. The detached copy makes a bundle member
  indistinguishable from a module loaded from its own file, and the merge fingerprint synthesis
  is what makes those copies complete. The `-L` chain's *main* module deliberately stays
  container-attached — the explicit-file launch path reads the `FileStructure` directly and no
  copy is needed.
- **`merge` supersedes fingerprints instead of silently keeping them.** Historically,
  `FileStructure.merge` ignored `addChild()`'s `false` return on an id collision, so merging a
  real module over its own fingerprint silently kept the stub — the "merge order is critical"
  trap. Fingerprint-vs-real is a genuine key collision (`f_moduleById` is keyed by
  `ModuleConstant`, and a versionless fingerprint id equals the versionless real id), so the
  real module now replaces the fingerprint. One subtlety lives in the code: the primary module
  id is established *provisionally* before fingerprint synthesis (the `ModuleStructure`
  constructor auto-marks new modules as fingerprints only when the file already has a different
  primary id) and re-established *after* constant registration, because registration replaces
  the clone's identity constant with the pool-owned one.
- **`ModuleType.Embedded` was unreachable before bundles.** No setter existed; merged modules
  persisted as `Primary`, which trips `isEmbeddedModule()`'s assert under `-ea` (the shipped
  launcher scripts enable assertions). `markEmbedded()` closes that gap; the type serializes as
  a plain ordinal byte, so it round-trips.
- **Runtime link tolerance.** The runtime linker absorbs every module id of a multi-module file
  once any member is loaded from it; absorbed ids with no fingerprint in the app structure are
  skipped rather than failed — nothing imports them.
- **Constraints inherited from the format** (unchanged by this work): conditional *sibling*
  modules are unsupported at the container level; multi-version modules in one file are not
  separable (`purgeVersion`/`purgeVersionsExcept` are no-ops), hence the same-name rejection;
  `validateConstants()` asserts a runtime-only precondition (`NakedRefType`), so offline
  bundling validates with `validateModuleConstants()`.

## Implementation map

| Piece | Where |
|---|---|
| `bundle` verb, selection, main-module inference, warnings | `javatools/.../org/xvm/tool/Bundler.java` |
| Options, builder, JSON round-trip | `LauncherOptions.BundlerOptions` in `javatools/.../org/xvm/tool/LauncherOptions.java` |
| Verb registration, help, dispatch | `javatools/.../org/xvm/tool/Launcher.java` (`CMD_BUNDLE`) |
| Merge collision upgrade + sibling-dep fingerprint synthesis | `FileStructure.merge` in `javatools/.../org/xvm/asm/FileStructure.java` |
| Runtime link tolerance | `FileStructure.linkModules` (runtime branch) |
| Multi-module repository read path, detached serving | `javatools/.../org/xvm/asm/FileRepository.java` |
| Embedded marking | `ModuleStructure.markEmbedded()` |
| Ecstasy-visible detached serving | `javatools/.../org/xvm/runtime/template/_native/mgmt/xCoreRepository.java` |
| Single-file XDK build task | `bundleXdk` in `xdk/build.gradle.kts` (opt-in, incremental, configuration-cache compatible) |
| Tests | `javatools/.../org/xvm/tool/BundlerTest.java`, `LauncherOptionsJsonTest`, `XdkIntegrationTest` |

## Validation record

- **xqiz.it platform** (11 modules, incl. a 2.2 MB embedded web UI): bundles to a single
  2.48 MB `platform.xtc` (separate files ~4.9 MB) and boots with full parity — identical log
  sequence, identical HTTPS behavior, dynamic by-name module resolution
  (`host`/`platformUI`/`proxy_manager`), and the runtime jsondb module generation-and-compile
  path all working from the single file.
- **Single-file XDK**: `xdk:bundleXdk` → all 22 XDK modules (ecstasy as main, mack, `_native`,
  every lib) in one fully self-contained 4.36 MB `xdk.xtc`, zero remaining fingerprints.
  Compiling and running applications works with `-L xdk.xtc` as the only system reference.
- **The two-bundle extreme**: the platform boots and serves from
  `java -jar javatools.jar run -L xdk.xtc -L platform.xtc platform.xtc <pw>` — a complete
  deployment as two bundle files and a jar, zero lib directories.

## Costs and limitations

- **Absorb-all footprint.** Loading a module out of a bundle materializes the container. For a
  trivial app running against the single-file XDK instead of the lib directory, measured cost is
  about +25% max RSS and +11% startup; the delta shrinks as an application actually uses more of
  the bundled modules. This is why the single-file XDK is an opt-in artifact rather than the
  default distribution.
- **Dynamic dependencies cannot be verified at bundle time** (see step 5 above).
- **One version per module name.** Bundling two modules with the same qualified name is rejected.
- **Format version lock.** Like any `.xtc`, a bundle is readable only by the exact binary format
  version that wrote it — with a larger blast radius, since one file carries many modules.
- **Regenerate-only.** Adding or updating a member means re-running `xtc bundle`.

## Future work

Deliberately not done yet, in rough order of likely value:

- Switch the generated `xtc`/`xcc`/`xec` launcher scripts to `-L xdk.xtc`
  (`XdkDistribution.kt`), update the docker image, and publish `xdk.xtc` as a distribution
  artifact — the steps toward making the single file the XDK's default form. Gate the default
  switch on footprint measurements for realistic applications (see costs above).
- A Gradle plugin `xtcBundle` task, so consumer projects can bundle as part of their build
  instead of shelling out to `xtc bundle`.
- Fully self-contained application blobs (`--include-system` for apps), and a dedicated
  launcher executable if bundling becomes a first-class distribution flow.
- Lazier bundle loading, if the absorb-all footprint ever matters for the default XDK form.
