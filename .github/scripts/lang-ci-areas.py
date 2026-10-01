#!/usr/bin/env python3
"""Decide which lang CI checks a change needs, and fingerprint the inputs of each check.

CI runs three kinds of lang checks:
  core      tree-sitter grammar, XDK corpus parse and native libraries; LSP adapter and DSL tests
  intellij  the main build with the lang composite attached, and the IntelliJ Plugin Verifier
  vscode    the VS Code extension's headless tests and VSIX

Their inputs fall into four groups (a file belongs to the first group that matches it):
  intellij  the IntelliJ plugin, and the file it syncs from javatools
  vscode    the VS Code extension, and the files it bundles from the repository root
  build     everything else the lang build reads: the rest of lang/, the build-logic it includes,
            the Gradle wrapper and the root build configuration
  corpus    the XDK sources the core checks parse as test data
Version catalog entries belong to the group whose sources use them (resolved through version.ref),
so an entry only one plugin's build uses affects only that plugin.

A build change needs every check, a corpus change only the core checks, and a plugin change
only that plugin's checks. Each check's fingerprint hashes the groups it reads, so a pull request
push that leaves them unchanged (a rebase, a commit elsewhere) can skip a check that passed.

Usage: lang-ci-areas.py BASE [HEAD]
Prints GitHub step outputs (key=value lines): lang, core, intellij, vscode, reason, and
core-fingerprint, intellij-fingerprint, vscode-fingerprint. When BASE is empty or not a commit,
every check is needed.
"""

import hashlib
import json
import re
import subprocess
import sys
import tomllib

GROUPS = (
    ("intellij", (
        "lang/intellij-plugin/**",
        "javatools/src/main/java/org/xvm/tool/XtcProjectCreator.java",
    )),
    ("vscode", (
        "lang/vscode-extension/**",
        "LICENSE.md",
        "doc/logo/x.jpg",
    )),
    ("build", (
        "lang/**",
        "build-logic/settings-plugins/**",
        "build-logic/common-plugins/**",
        "gradlew",
        "gradlew.bat",
        "gradle/wrapper/**",
        "gradle/gradle-daemon-jvm.properties",
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties",
        "version.properties",
    )),
    ("corpus", (
        "lib_*/**.x",
        "manualTests/src/main/x/**.x",
    )),
)
# Where each group's sources refer to version catalog entries.
CATALOG = "gradle/libs.versions.toml"
CATALOG_USERS = {
    "intellij": ("lang/intellij-plugin",),
    "vscode": ("lang/vscode-extension",),
    "build": ("lang", ":(exclude)lang/intellij-plugin", ":(exclude)lang/vscode-extension",
              "build-logic/settings-plugins", "build-logic/common-plugins"),
}
NEEDS = {"build": ("core", "intellij", "vscode"), "corpus": ("core",), "intellij": ("intellij",), "vscode": ("vscode",)}
READS = {"core": ("build", "corpus"), "intellij": ("build", "intellij"), "vscode": ("build", "vscode")}
# Build sources only: docs under lang/ show catalog accessors in examples.
SOURCES = ("**/*.kts", "**/*.kt", "**/*.gradle", "**/*.java")
ACCESSOR = r'libs\.[A-Za-z0-9_.]+|find(Library|Version|Plugin|Bundle)\("[^"]+"\)'
FIND_SECTIONS = {"Library": "libraries", "Version": "versions", "Plugin": "plugins", "Bundle": "bundles"}


def glob_regex(pattern):
    """'**' matches across directories, '*' within one."""
    parts = re.split(r"(\*\*|\*)", pattern)
    return re.compile("".join({"**": ".*", "*": "[^/]*"}.get(p, re.escape(p)) for p in parts) + r"\Z")


MATCHERS = [(group, [glob_regex(p) for p in patterns]) for group, patterns in GROUPS]


def group_of(path):
    return next((group for group, regexes in MATCHERS if any(r.match(path) for r in regexes)), None)


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def is_commit(rev):
    return bool(rev) and subprocess.run(["git", "cat-file", "-e", f"{rev}^{{commit}}"], capture_output=True).returncode == 0


def catalog_references(head, paths):
    """(section, alias parts) for each catalog accessor in the build sources under paths."""
    pathspecs = [path if path.startswith(":(exclude)") else f":(glob){path}/{source}"
                 for path in paths for source in (("",) if path.startswith(":(exclude)") else SOURCES)]
    found = subprocess.run(["git", "grep", "-ohE", ACCESSOR, head, "--", *pathspecs],
                           capture_output=True, text=True).stdout
    for token in set(found.split()):
        if match := re.fullmatch(r'find(Library|Version|Plugin|Bundle)\("([^"]+)"\)', token):
            yield FIND_SECTIONS[match[1]], re.split(r"[-_.]", match[2])
        else:
            parts = token.split(".")[1:]
            if parts and parts[0] in ("versions", "plugins", "bundles"):
                yield parts[0], parts[1:]
            else:
                yield "libraries", parts


def catalog_entries(rev, references):
    """The catalog entries the references resolve to at rev, as sorted 'section.alias = value' lines."""
    try:
        catalog = tomllib.loads(git("show", f"{rev}:{CATALOG}"))
    except subprocess.CalledProcessError:
        return []
    entries = {}

    def add(section, alias):
        if f"{section}.{alias}" in entries:
            return
        value = catalog[section][alias]
        entries[f"{section}.{alias}"] = value
        if section == "bundles":
            for library in value:
                add("libraries", library)
        elif isinstance(value, dict) and isinstance(value.get("version"), dict) and "ref" in value["version"]:
            add("versions", value["version"]["ref"])

    for section, parts in references:
        # An accessor names an alias, possibly followed by a member such as `.get`: take the longest
        # prefix that is an alias. Gradle maps '-', '_' and '.' in aliases alike to '.'.
        aliases = {tuple(re.split(r"[-_.]", alias)): alias for alias in catalog.get(section, {})}
        alias = next((aliases[tuple(parts[:n])] for n in range(len(parts), 0, -1) if tuple(parts[:n]) in aliases), None)
        if alias is not None:
            add(section, alias)
    return [f"{key} = {json.dumps(value, sort_keys=True)}" for key, value in sorted(entries.items())]


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else ""
    head = sys.argv[2] if len(sys.argv) > 2 else "HEAD"
    references = {group: list(catalog_references(head, paths)) for group, paths in CATALOG_USERS.items()}

    if is_commit(base):
        changed = {}
        for path in git("diff", "--name-only", "--no-renames", base, head).splitlines():
            if group := group_of(path):
                changed[group] = changed.get(group, 0) + 1
        for group, group_references in references.items():
            if catalog_entries(base, group_references) != catalog_entries(head, group_references):
                changed[group] = changed.get(group, 0) + 1
        groups = sorted(changed)
        reason = ", ".join(f"{group} ({count})" for group, count in sorted(changed.items())) or "nothing lang reads changed"
        reason = f"changed lang input groups: {reason}" if changed else reason
    else:
        groups = [group for group, _ in GROUPS]
        reason = f"comparison base '{base}' unavailable: treating every lang input as changed"
    needed = {check for group in groups for check in NEEDS[group]}

    inputs = {group: [] for group, _ in GROUPS}
    for line in git("ls-tree", "-r", "--full-tree", head).splitlines():
        meta, path = line.split("\t", 1)
        if group := group_of(path):
            inputs[group].append(f"{meta.split()[2]} {path}")
    for group, group_references in references.items():
        inputs[group] += catalog_entries(head, group_references)

    print(f"lang={'true' if needed else 'false'}")
    for check in READS:
        print(f"{check}={'true' if check in needed else 'false'}")
    print(f"reason={reason}")
    for check, groups_read in READS.items():
        text = "\n".join(line for group in groups_read for line in sorted(inputs[group]))
        print(f"{check}-fingerprint={hashlib.sha256(text.encode()).hexdigest()[:16]}")


if __name__ == "__main__":
    main()
