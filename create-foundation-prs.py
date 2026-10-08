#!/usr/bin/env python3
"""Publish the three reviewed foundation commits and their design comments.

Preview (offline, no changes): python3 create-foundation-prs.py --preview
Export all six review diffs:  python3 create-foundation-prs.py --export-diffs build/errs-review
Publish ready PRs:            python3 create-foundation-prs.py
Publish draft PRs:            python3 create-foundation-prs.py --draft

Requires Python 3.9+, git, and an authenticated gh with push/PR access to
xtclang/xvm. Run from this checkout, or use --repo-dir to locate it. The
prepared local branches must still point to the pinned, tested commits.

The offline diff export includes I1–I3 and the C1 → C2 → C3 stack. Each diff
compares a slice to its own parent. It does not publish or prepare C-series PRs.
Publishing pushes only the three I-series commits and creates PRs against master.
It never switches branches, rebases, force-pushes, or stages working files.
Existing PR descriptions are preserved; design comments are posted once.
After an interrupted run, rerun the same command. Do not run two copies at
once. A closed, unmerged PR or changed branch requires manual resolution.
"""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys
import tempfile
from typing import Optional


REPOSITORY = "xtclang/xvm"
BASE = "master"
TESTED_BASE = "f442aced626085bba6623d8345dda9d73c3397d4"
GITHUB = f"https://github.com/{REPOSITORY}"


@dataclass(frozen=True)
class Foundation:
    key: str
    branch: str
    commit: str
    title: str
    body: str
    design: str

    @property
    def marker(self) -> str:
        return f"<!-- xvm-foundation-{self.key.lower()}-design-{self.commit} -->"


FOUNDATIONS = (
    Foundation(
        "I1",
        "errs/i1-diagnostic-identity-20261008",
        "e649179e7b0e899b16327068a931ef0016874e04",
        "Preserve distinct diagnostic spans and named source buffers",
        """# Summary

## Why?

- Diagnostic deduplication can discard different errors when their parameters share a Java hash or their source spans start at the same offset but end differently.
- Hosts compiling several unsaved documents need distinct source names so errors in different buffers remain distinguishable.

## How?

- Include the actual end position and rendered parameters in the existing diagnostic identity.
- Add a named in-memory `Source` constructor, with regressions for distinct buffers, spans, parameter values, and repeated identical reports.

## Implications

The `Source(String, String)` overload is additive. File-backed source naming and the existing boolean `ErrorListener.log` contract remain compatible. Callers naming buffers must supply a non-null label and use distinct labels for distinct documents; naming a buffer does not read or create a file.

## Changes

- `ErrorListener.java`: correct the span endpoint and replace the 32-bit parameter hash in deduplication keys.
- `Source.java`: preserve a caller-supplied name for an in-memory buffer.
- `ErrorDeduplicationTest.java` and `NamedSourceDiagnosticsTest.java`: five focused regressions.

## Known follow-ups

The key still uses rendered parameter values, so this does not establish collision-free equality for arbitrary objects. A structural diagnostic-identity redesign would be a separate change.
""",
        """## Design rationale: preserve reports before changing the listener API

A diagnostic collector should suppress a repeated report of the same problem without silently removing a different problem. Two small defects violated that expectation: `genUID()` appended the start position twice, and represented the parameter array using only `Arrays.hashCode`. For example, Java strings `Aa` and `BB` have the same hash. At the same source location and diagnostic code, those distinct parameter values could collapse to one report.

### Identity and source ownership

The patch retains the existing string-key mechanism, substitutes the real end position, and renders the parameter array using `Arrays.toString`. Exact repeated reports continue to deduplicate. The special location-independent treatment of `VERIFY*` diagnostics is unchanged. Rendering is deliberately a limited repair: different objects can still render identically, and array rendering is not an unambiguous structural encoding. This fixes the demonstrated hash collision without claiming a general equality redesign or measured performance improvement.

Unsaved buffers create a related source-identity problem. `new Source(text)` has no useful document label; two buffers with the same error can therefore look like the same source to a shared collector. The additive `new Source(text, name)` constructor stores the supplied non-null label alongside the in-memory text. A document URI is a useful host-provided label, but `Source` does not normalize it, interpret it as a filesystem path, or assume ownership of a file. File-backed sources continue to report their file basename; named buffers report their supplied label; unnamed buffers retain `<no file>`.

### Scope and compatibility

There is no listener signature change, LSP dependency, editor integration, or runtime execution change here. This foundation can merge independently of the later listener/embedding redesign planned for 0.5.0. Review the identity repair and the new overload together because the buffer-name regression exercises the collector behavior the overload is meant to support.

### Validation performed

At the pinned commit below, all five new regression tests and both enabled `SourceTest` cases passed, with no failures or errors. Three existing file-oriented `SourceTest` cases remain explicitly disabled; they are not counted as executed acceptance. `spotlessCheck` passed.

```sh
./gradlew :javatools:test \\
  --tests org.xvm.asm.ErrorDeduplicationTest \\
  --tests org.xvm.compiler.NamedSourceDiagnosticsTest \\
  --tests org.xvm.compiler.SourceTest \\
  spotlessCheck --rerun-tasks --no-build-cache --console=plain --quiet
```

The regressions cover equal-start/different-end spans, the `Aa`/`BB` hash collision, exact duplicate suppression, named-buffer reporting, and retention of diagnostics from separately named buffers. The longer rendered key is a tradeoff; no heap or throughput claim is made for this patch.
""",
    ),
    Foundation(
        "I2",
        "errs/i2-ambient-pools-20261008",
        "6c6720fa4e1ce34d95153e3b851df25406cd62f2",
        "Use owning constant pools when no ambient pool is bound",
        """# Summary

## Why?

- Constant and method inspection can throw when called from a host, debugger, or test thread without a bound constant pool.
- Always using the owning pool would also be incorrect: compiler operations can intentionally bind a different pool while working across pools.

## How?

- Centralize the rule in `ConstantPool.currentOr`: prefer the bound pool, otherwise use the supplied owner.
- Apply that rule to the affected constant, method, property, and interpreter-connector lookups, and test both unbound access and bound-pool precedence.

## Implications

The helper is additive and does not bind a pool or change its lifetime. Existing operations with a bound pool retain their precedence; audited unbound calls use the relevant object's owner. This does not introduce general compiler or runtime thread safety.

## Changes

- `ConstantPool.java` and `Constant.java`: shared fallback rule and a protected owner-based helper.
- `ByteConstant`, `IntConstant`, `IdentityConstant`, `TypeConstant`, `MethodBody`, `MethodInfo`, `PropertyInfo`, and `InterpreterConnector`: use an owner where an ambient pool is absent.
- `ConstantPoolAmbientTest.java` and `MethodBodyAmbientPoolTest.java`: focused fallback and precedence regressions.
""",
        """## Design rationale: retain compiler context, support ordinary host calls

`ConstantPool.getCurrentPool()` reads thread-local context. Compiler and runtime scopes normally bind that context, but a debugger watch, a Java embedding host, or a focused test can legitimately inspect an existing object on an unbound thread. One observed failure was in the inspection path itself: `MethodBody.isOp()` and `toString()` could dereference a null ambient pool while trying to describe a method.

### Why the ambient pool must still win

An object's owner and the compiler's current working pool are not interchangeable. Cross-pool operations deliberately bind context so that well-known constants and type operations resolve in that context. Replacing every read with `getConstantPool()` would remove the null dereference but could return the wrong answer during compilation.

The rule is therefore `currentOr(owner)`: return the bound pool when present, otherwise the owner supplied by the caller. `Constant.poolInUse()` provides that rule for constants; methods use the pool of their identity, properties use their property identity, and the interpreter connector uses its container. The helper performs a lookup only: it never pushes context, changes the thread-local, extends a scope, or manufactures a global default. If a caller supplies a null fallback on an unbound thread, `currentOr` still returns null.

### Boundaries reviewers should preserve

This is a targeted audit of readers that have a meaningful owner. It is not a blanket replacement of every ambient-pool access. Existing `withPool` scopes and their restoration behavior remain intact. It also does not make shared compiler/container instances safe for arbitrary concurrent use; the fallback solves missing context for these calls, not ownership or synchronization throughout the compiler.

There is no dependency on I1 or the later listener/embedding API changes. The independent regression uses the current `getNormalizedType()` API on master. No compatibility wrapper for the older canonical-type method is introduced.

### Validation performed

Eleven tests executed with zero failures, errors, or skips: three `ConstantPoolAmbientTest` cases, two `MethodBodyAmbientPoolTest` cases, one `ConstantPoolDiagnosticsTest` case, and five `FloatingConstantPoolTest` cases. The same invocation built and installed all 24 XDK modules and passed `spotlessCheck`.

```sh
./gradlew :javatools:test \\
  --tests org.xvm.asm.ConstantPoolAmbientTest \\
  --tests org.xvm.asm.constants.MethodBodyAmbientPoolTest \\
  --tests org.xvm.asm.ConstantPoolDiagnosticsTest \\
  --tests org.xvm.asm.FloatingConstantPoolTest \\
  spotlessCheck :xdk:installDist \\
  --rerun-tasks --no-build-cache --console=plain --quiet
```

The focused tests distinguish absent context from an intentionally bound alternate pool. The full module build checks that normal compilation still completes. A normalized binary-output comparison was not repeated on this master base, so the acceptance claim is successful tests and compilation, not proven byte-for-byte equivalence.
""",
    ),
    Foundation(
        "I3",
        "errs/i3-compiler-consumer-tests-20261008",
        "21e8d6b2cb7eaeb8c4842df53705e44dc722af45",
        "Require a compiler consumer in language-tooling CI",
        """# Summary

## Why?

- Compiler and library changes need an executable language-tooling consumer so integration failures cannot hide behind a successful build of the producer alone.
- A green test task is insufficient if the required consumer is missing, empty, or skipped because its compiled modules were unavailable.

## How?

- Resolve compiled module variants as declared test inputs and compile a small module through the existing embedding API.
- Include compiler inputs in the shared core-check classification and fingerprint, and require a nonempty successful consumer result before recording the CI success marker.
- Detach optional IDE packaging from the default language-tooling lifecycle while retaining explicit plugin tasks and opt-in attachment.

```mermaid
flowchart LR
    C[Java compiler API] --> T[CompilerConsumerTest]
    M[Gradle compiled module variants] --> T
    T --> X[JUnit XML result]
    X --> G[Required-result gate]
    G --> S[Core checks success marker]
```

## Implications

`includeBuildAttachIntellijPlugin` and `includeBuildAttachVsCodeExtension` default to false for language-tooling lifecycle attachment. Explicit IDE tasks remain available, `clean` still covers the included projects, and root composite-inclusion defaults are unchanged. The compiler dependency is test-only; this PR does not ship a compiler-backed language server.

## Changes

- `CompilerConsumerTest.kt` and `CompilerTestSupport.kt`: an embedding smoke test with mandatory bootstrap/system modules.
- `lang/lsp-server/build.gradle.kts`: variant-based module inputs and a configuration-cache-compatible JVM argument provider.
- `lang/build.gradle.kts` and the LSP README: optional IDE lifecycle attachment and its usage.
- `.github/scripts/ci-changes.py` and `.github/workflows/commit.yml`: compiler-aware core invalidation and the required-result gate.

## Known follow-ups

This initial gate requires one smoke-test suite in the existing Ubuntu core lane. Later compiler-consumer suites must extend the required coverage; this is not an IDE acceptance suite or a new cross-platform test matrix.
""",
        """## Design rationale: make the producer/consumer boundary executable

Building `javatools` proves that the compiler itself builds; it does not prove that a language-tooling consumer can resolve its runtime modules and use its embedding API. This foundation adds a small required consumer on the existing master API, before introducing the much larger compiler adapter. It compiles `module Consumer { Int answer() = 42; }` and checks that compilation reports no errors and returns the expected module.

### Modules are Gradle inputs

The test consumes the composite build's compiled `xtc` library variants through a resolvable `compilerTestModules` configuration. Dependencies on Ecstasy and the native bridge provide the required module graph, including the bootstrap module. A `CommandLineArgumentProvider` passes their directories as `xtc.test.modules`; its file collection is declared with `@InputFiles` and relative path sensitivity. This lets Gradle model producer dependencies and invalidate test outputs when module content changes while preserving configuration-cache support. There is no distribution extraction, hard-coded developer installation path, or task action capturing a project/script object.

`CompilerTestSupport` creates a linked repository with a mutable `BuildRepository` first, followed by read-only repositories for the resolved module directories. The first repository supports caching loaded modules; the input directories remain read-only. Setup requires `ecstasy.xtclang.org`, `mack.xtclang.org` (turtle/bootstrap), and `_native.xtclang.org`. Missing directories or modules are failures, not assumptions that silently skip the test.

### Keep the core lane usable

Requiring a compiler consumer should not make every core language-tooling lifecycle run package both IDE plugins. The new attachment flags keep those packaging lifecycles opt-in, while explicit IDE tasks remain callable. This is a task-lifecycle boundary, not a claim that Gradle never configures or resolves anything associated with an included IDE project. Root inclusion switches and the existing IDE-specific CI lanes retain their roles.

### Invalidation and the success marker

The shared CI classifier now includes compiler, utility, bootstrap/native bridge, library, XDK, and plugin inputs in the core fingerprint. Relevant version-catalog references also invalidate the consumer. Shared build/workflow changes retain broad invalidation; existing documentation and IDE-specific classification remain selective. This is integrated into the current classifier rather than adding a second path-filter implementation.

The existing Ubuntu core job runs `:lang:lsp-server:test`, which discovers the consumer. Before writing its successful-core-check marker, a new gate parses that suite's exact JUnit XML and requires `tests > 0`, with zero failures, errors, or skips. Missing or malformed output also fails the step. Consequently, a missing or skipped consumer cannot establish a new successful core-check marker.

The normal Gradle and CI caching policies still apply. Valid cached test outputs may be reused; an XML check alone is not evidence of a newly launched test JVM. The local acceptance below deliberately forced actual test execution. Future required suites need corresponding coverage in the gate; the initial single-suite gate does not promise that every possible compiler interaction is tested.

### Validation performed

The following invocation ran twice at the pinned commit. Each run executed the consumer with one test, zero failures/errors/skips; the first stored the configuration cache and the second reused it. Both formatting checks passed.

```sh
./gradlew :lang:lsp-server:test \\
  --tests org.xvm.lsp.adapter.CompilerConsumerTest \\
  --rerun-tasks --no-build-cache \\
  spotlessCheck :lang:lsp-server:spotlessCheck \\
  -PincludeBuildLang=true -PincludeBuildAttachLang=true \\
  --console=plain --info
```

Eleven local synthetic-repository controls checked classification and fingerprint changes, including compiler inputs, shared build logic, the workflow, version catalog, IDE-only edits, and a documentation-only edit. Six result-gate controls accepted valid XML and rejected missing, empty, skipped, failing, and errored results. `actionlint` reported the same 80 existing findings on the base and candidate, with zero new findings; the workflow is not being described as globally lint-clean. These are local checks, not a claim that GitHub Actions has already run this PR.

This foundation uses the existing embedding/listener API and is independent of I1 and I2. The later breaking API work remains planned for 0.5.0.
""",
    ),
)


class PublicationError(RuntimeError):
    """A failed check or command; publication may be resumed after resolving it."""


def command(repo: Path, *args: str, strip: bool = True) -> str:
    """Run an argv directly; Markdown and branch names never pass through a shell."""
    try:
        result = subprocess.run(
            args, cwd=repo, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, check=True,
        )
    except FileNotFoundError as error:
        raise PublicationError(f"Required executable not found: {args[0]}") from error
    except subprocess.CalledProcessError as error:
        detail = (error.stderr or error.stdout or "").strip()
        raise PublicationError(f"{' '.join(args[:3])} failed: {detail}") from error
    return result.stdout.strip() if strip else result.stdout


@dataclass(frozen=True)
class ReviewSlice:
    key: str
    branch: str
    commit: str
    parent: str
    base: str


REVIEW_SLICES = tuple(
    ReviewSlice(item.key, item.branch, item.commit, TESTED_BASE, BASE)
    for item in FOUNDATIONS
) + (
    ReviewSlice(
        "C1", "errs/c1-listener-contract-20261008",
        "dfe64261448836a95fc09670bb6650be746c0660", TESTED_BASE, BASE,
    ),
    ReviewSlice(
        "C2", "errs/c2-explicit-listeners-20261008",
        "cb4483dabcebdd104ed22d4fc64f5d1c0a29399f",
        "dfe64261448836a95fc09670bb6650be746c0660", "errs/c1-listener-contract-20261008",
    ),
    ReviewSlice(
        "C3", "errs/c3-reporting-scopes-20261008",
        "4cfd442eb71132ac2dcd6afab80be2362f02bfb5",
        "cb4483dabcebdd104ed22d4fc64f5d1c0a29399f", "errs/c2-explicit-listeners-20261008",
    ),
)


def export_diffs(repo: Path, destination: Path) -> None:
    """Export pinned parent-to-child diffs using local git only, without a checkout."""
    exports = []
    for item in REVIEW_SLICES:
        actual = command(repo, "git", "rev-parse", f"refs/heads/{item.branch}")
        parent = command(repo, "git", "show", "-s", "--format=%P", item.commit)
        if actual != item.commit or parent != item.parent:
            raise PublicationError(f"{item.key}: local commit/base differs from the prepared slice.")
        patch = command(
            repo, "git", "diff", "--no-ext-diff", "--no-textconv", "--binary",
            "--full-index", "--src-prefix=a/", "--dst-prefix=b/", "--no-color",
            item.parent, item.commit, "--", strip=False,
        )
        if not patch:
            raise PublicationError(f"{item.key}: the prepared review diff is empty.")
        stats = command(repo, "git", "diff", "--no-ext-diff", "--shortstat", item.parent, item.commit, "--")
        metadata = {
            "slice": item.key, "branch": item.branch, "commit": item.commit,
            "review_base": item.base, "base_commit": item.parent,
            "patch": f"{item.key}.patch", "size": stats,
        }
        exports.append((metadata, patch))

    # Refuse to overwrite an earlier review export or any manually edited files.
    destination.mkdir(parents=True, exist_ok=False)
    for metadata, patch in exports:
        (destination / metadata["patch"]).write_text(patch, encoding="utf-8")
        print(f"{metadata['slice']}: {metadata['size']} — {destination / metadata['patch']}")
    (destination / "index.json").write_text(
        json.dumps([metadata for metadata, _ in exports], indent=2) + "\n", encoding="utf-8",
    )


def find_pr(repo: Path, foundation: Foundation) -> Optional[dict]:
    records = json.loads(command(
        repo, "gh", "pr", "list", "--repo", REPOSITORY, "--head", foundation.branch,
        "--state", "all", "--limit", "100", "--json",
        "number,state,headRefOid,baseRefName,isCrossRepository",
    ))
    records = [record for record in records if not record["isCrossRepository"]]
    if len(records) > 1:
        raise PublicationError(f"{foundation.key}: multiple PRs use this branch; inspect them first.")
    if not records:
        return None
    record = records[0]
    url = f"{GITHUB}/pull/{record['number']}"
    if record["baseRefName"] != BASE:
        raise PublicationError(f"{url} targets a different base; refusing to change it.")
    if record["state"] == "CLOSED":
        raise PublicationError(f"{url} is closed without merging; resolve it before rerunning.")
    if record["headRefOid"] != foundation.commit:
        raise PublicationError(f"{url} has a different head; the prepared text needs review.")
    return record


def inspect(repo: Path) -> tuple:
    """Validate every local/remote target before the first remote mutation."""
    for foundation in FOUNDATIONS:
        actual = command(repo, "git", "rev-parse", f"refs/heads/{foundation.branch}")
        parent = command(repo, "git", "show", "-s", "--format=%P", foundation.commit)
        if actual != foundation.commit or parent != TESTED_BASE:
            raise PublicationError(f"{foundation.key}: local commit/base differs from the tested slice.")

    urls = command(repo, "git", "remote", "get-url", "--push", "--all", "origin").splitlines()
    allowed = {
        f"git@github.com:{REPOSITORY}.git", f"git@github.com:{REPOSITORY}",
        f"{GITHUB}.git", GITHUB, f"ssh://git@github.com/{REPOSITORY}.git",
    }
    if len(urls) != 1 or urls[0] not in allowed:
        raise PublicationError("origin must have one push URL pointing to xtclang/xvm on GitHub.")
    # Read the same push destination that the explicit refspecs below will update.
    lines = command(
        repo, "git", "ls-remote", "--heads", urls[0],
        *(f"refs/heads/{item.branch}" for item in FOUNDATIONS),
    ).splitlines()
    remote = {ref: sha for sha, ref in (line.split() for line in lines)}
    permission = json.loads(command(repo, "gh", "api", f"repos/{REPOSITORY}"))
    if not permission.get("permissions", {}).get("push"):
        raise PublicationError("The authenticated gh account needs push access to xtclang/xvm.")

    prs = {}
    missing = []
    for foundation in FOUNDATIONS:
        record = find_pr(repo, foundation)
        prs[foundation.key] = record
        if record and record["state"] == "MERGED":
            continue
        sha = remote.get(f"refs/heads/{foundation.branch}")
        if sha is not None and sha != foundation.commit:
            raise PublicationError(f"{foundation.key}: remote branch differs; it will not be overwritten.")
        if sha is None:
            missing.append(foundation)
    return prs, missing


def design_comment(foundation: Foundation, urls: dict) -> str:
    links = " · ".join(f"[{item.key}]({urls[item.key]})" for item in FOUNDATIONS)
    return (
        f"{foundation.marker}\n\n{foundation.design.strip()}\n\n"
        f"### Review and validation boundary\n\n"
        f"Tested commit: [`{foundation.commit[:9]}`]({GITHUB}/commit/{foundation.commit}). "
        f"Independent base: [`{TESTED_BASE[:9]}`]({GITHUB}/commit/{TESTED_BASE}). "
        "Validation was performed locally on October 8, 2026, at these exact revisions. "
        "If master advances, the resulting merge still needs current CI validation.\n\n"
        f"Foundation PRs: {links}. All three target `master` independently; "
        "none requires another foundation PR to build.\n"
    )


def publish(repo: Path, draft: bool = False) -> None:
    prs, missing = inspect(repo)
    if missing:
        print(f"Pushing {len(missing)} prepared branch(es) to {REPOSITORY}.", flush=True)
        command(
            repo, "git", "push", "--atomic", "origin",
            *(f"{item.commit}:refs/heads/{item.branch}" for item in missing),
        )

    with tempfile.TemporaryDirectory(prefix="xvm-foundation-prs-") as directory:
        for foundation in FOUNDATIONS:
            if prs[foundation.key] is not None:
                continue
            body = Path(directory) / f"{foundation.key}-body.md"
            body.write_text(foundation.body.strip() + "\n", encoding="utf-8")
            print(f"Creating {foundation.key}: {foundation.title}", flush=True)
            command(
                repo, "gh", "pr", "create", "--repo", REPOSITORY, "--base", BASE,
                "--head", foundation.branch, "--title", foundation.title,
                "--body-file", str(body), *(["--draft"] if draft else []),
            )
            prs[foundation.key] = find_pr(repo, foundation)
            if prs[foundation.key] is None:
                raise PublicationError(f"{foundation.key}: created PR is not visible yet; rerun to resume.")

        urls = {key: f"{GITHUB}/pull/{record['number']}" for key, record in prs.items()}
        for foundation in FOUNDATIONS:
            record = prs[foundation.key]
            if record["state"] == "MERGED":
                print(f"{foundation.key}: already merged — {urls[foundation.key]}")
                continue
            pages = json.loads(command(
                repo, "gh", "api", "--paginate", "--slurp",
                f"repos/{REPOSITORY}/issues/{record['number']}/comments",
            ))
            if not any(
                foundation.marker in (comment.get("body") or "")
                for page in pages for comment in page
            ):
                comment = Path(directory) / f"{foundation.key}-design.md"
                comment.write_text(design_comment(foundation, urls), encoding="utf-8")
                command(
                    repo, "gh", "pr", "comment", str(record["number"]),
                    "--repo", REPOSITORY, "--body-file", str(comment),
                )
            print(f"{foundation.key}: description and design comment ready — {urls[foundation.key]}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--preview", action="store_true", help="Print I1–I3 PR text without commands or changes.")
    mode.add_argument("--export-diffs", type=Path, metavar="NEW_DIRECTORY", help="Export all six local review diffs; no network or publication.")
    mode.add_argument("--draft", action="store_true", help="Create new I1–I3 PRs as drafts; existing PR state is preserved.")
    parser.add_argument("--repo-dir", type=Path, default=Path(__file__).resolve().parent)
    args = parser.parse_args()
    if args.preview:
        urls = {item.key: f"{GITHUB}/compare/{BASE}...{item.branch}" for item in FOUNDATIONS}
        for foundation in FOUNDATIONS:
            print(f"\n{'=' * 72}\n{foundation.key}: {foundation.title}")
            print(f"Branch: {foundation.branch}\nCommit: {foundation.commit}\n")
            print(foundation.body.strip())
            print("\n--- Separate PR comment ---\n")
            print(design_comment(foundation, urls))
        return 0
    try:
        if args.export_diffs is not None:
            export_diffs(args.repo_dir.resolve(), args.export_diffs.resolve())
        else:
            publish(args.repo_dir.resolve(), args.draft)
    except (PublicationError, ValueError, KeyError, OSError) as error:
        print(f"Stopped: {error}\nResolve the reported issue, then rerun to resume. No rollback is attempted.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
