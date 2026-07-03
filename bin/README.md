# Developer Scripts

This directory contains small developer-oriented helpers for the xvm repository.
The main project documentation is in [../README.md](../README.md).

## Gradle Upgrades

Use [gradle-upgrade.sh](gradle-upgrade.sh) for Gradle wrapper upgrades. It
updates every tracked wrapper root that it finds, including nested wrappers in
non-xvm repositories such as `examples/CardGame`.

Common commands:

```sh
bin/gradle-upgrade.sh
bin/gradle-upgrade.sh <gradle-version>
bin/gradle-upgrade.sh <gradle-version> --repo=platform
bin/gradle-upgrade.sh <gradle-version> --all-xtclang-repos
bin/gradle-upgrade.sh --check --all-xtclang-repos
```

Behavior to know:

- Running without a version resolves the current stable Gradle version from
  `services.gradle.org`.
- Single-repo xvm upgrades refuse to edit `master` or `main`; run them from an
  upgrade branch.
- `--repo=<name>` and `--all-xtclang-repos` create `gradle-upgrade-<version>`
  branches in the selected sibling repo(s), validate the generated wrappers,
  commit the wrapper changes, and push the branch to `origin`.
- Downgrades fail. Re-running for the existing version warns unless there are
  uncommitted upgrade changes on the generated upgrade branch, in which case the
  script validates, commits, and pushes them.
- xvm-specific mirrors such as `XtcProjectCreator.DEFAULT_GRADLE_VERSION` and
  the embedded Kotlin comment are updated when present. Non-xvm repos are
  handled generically by updating their tracked wrapper roots.

## Remaining Helpers

- [artifact.sh](artifact.sh): build an XDK snapshot locally and optionally
  trigger CI artifact upload.
- [check-docker-version.sh](check-docker-version.sh): verify that the local
  Docker server meets the minimum version needed by build scripts.
- [check-github-token-privileges.sh](check-github-token-privileges.sh): print
  OAuth scopes for a GitHub token.
- [git-show-parent-branch.sh](git-show-parent-branch.sh): infer the likely
  parent branch for the current Git branch.
- [list-publications.sh](list-publications.sh): list local or remote Maven
  publications for xvm artifacts.
- [purge-all-build-state.sh](purge-all-build-state.sh): destructive cleanup for
  Gradle caches, build directories, local Maven state, and Docker build state.
- [test-configuration-cache.sh](test-configuration-cache.sh): run local Gradle
  scenarios that exercise configuration-cache behavior.
- [trigger-publish.sh](trigger-publish.sh): trigger and monitor the publish
  workflow chain for the current branch.
- [validate-dependabot-config.sh](validate-dependabot-config.sh): validate
  Dependabot team references through the GitHub CLI.

The license for these helper scripts is the Apache License, Version 2.0; see
[LICENSE](LICENSE).
