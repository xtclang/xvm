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
