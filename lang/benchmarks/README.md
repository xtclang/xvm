# Gradle Build Profile: `lang:runIntellijPlugin`

## Run Details

- **Branch**: `lagergren/lsp-extend4` (commit `e691fa6c3`)
- **Command**: `./gradlew lang:runIntellijPlugin --profile --info --scan`
- **Gradle**: 9.2.1 (daemon, aarch64)
- **JDK**: Amazon Corretto 25 (aarch64)

Two consecutive runs of the same task from the same commit, to compare
cold-cache vs hot-cache performance.

| | Cold Cache Run | Hot Cache Run |
|---|---|---|
| **Date** | 2026-02-12 09:02:53 CET | 2026-02-12 09:38:20 CET |
| **Configuration cache** | MISS (calculated fresh) | HIT (reused) |
| **Build scan** | [chnowxdko2dxa](https://gradle.com/s/chnowxdko2dxa) | [g6hbgdwpxdstu](https://gradle.com/s/g6hbgdwpxdstu) |
| **Tasks** | 203 actionable: 61 executed, 142 up-to-date | 203 actionable: 35 executed, 168 up-to-date |

## Files in This Directory

| File | Description |
|---|---|
| `gradle-profile-output.log` | Full `--info` console output, cold cache (4,331 lines) |
| `gradle-profile-output-hot.log` | Full `--info` console output, hot cache (3,594 lines) |
| `profile/profile-2026-02-12-09-02-53.html` | Gradle profile HTML — cold cache |
| `profile-hot/profile-2026-02-12-09-38-20.html` | Gradle profile HTML — hot cache |
| `configuration-cache-report.html` | Configuration cache inputs report (cold run) |

## Side-by-Side Summary

| Phase | Cold Cache | Hot Cache | Speedup |
|---|---|---|---|
| **Total Build Time** | **2m 40s** | **1m 32s** | **1.7x** |
| Startup | 1.0s | 0.9s | 1.1x |
| Settings + buildSrc | 0.01s | 0.004s | — |
| Loading Projects | 4.3s | 0.6s | **7.2x** |
| Configuring Projects | 1.9s | 0.3s | **6.3x** |
| Dependency Resolution | 1.1s | 0.1s | **11x** |
| Artifact Transforms | 0s | 0s | — |
| **Task Execution** | **3m 51s** | **1m 26s** | **2.7x** |

The totals include `runIde` wall-clock time (IDE was open: ~1m53s cold, ~1m26s hot).
Actual build work before IDE launch: ~2m cold, ~0.3s hot.

## Cold Cache: Task Execution Breakdown (Top 20)

| Task | Duration | Result |
|---|---|---|
| `:intellij-plugin:runIde` | 1m 53.27s | (IDE open time) |
| `:lib-json:compileXtc` | 9.90s | executed |
| `:lib-ecstasy:compileXtc` | 8.53s | executed |
| `:lib-collections:compileXtc` | 8.50s | executed |
| `:lib-xunit:compileXtc` | 7.93s | executed |
| `:lib-oodb:compileXtc` | 7.90s | executed |
| `:lib-crypto:compileXtc` | 7.87s | executed |
| `:lib-aggregate:compileXtc` | 7.53s | executed |
| `:lib-cli:compileXtc` | 7.47s | executed |
| `:lib-xenia:compileXtc` | 6.87s | executed |
| `:javatools-bridge:compileXtc` | 6.64s | executed |
| `:lib-jsondb:compileXtc` | 5.87s | executed |
| `:lib-webauth:compileXtc` | 5.34s | executed |
| `:lib-webcli:compileXtc` | 4.46s | executed |
| `:lib-xml:compileXtc` | 4.29s | executed |
| `:lib-sec:compileXtc` | 3.61s | executed |
| `:lib-web:compileXtc` | 3.44s | executed |
| `:lib-xunit-engine:compileXtc` | 3.39s | executed |
| `:lib-net:compileXtc` | 2.81s | executed |
| `:lib-xunit-db:compileXtc` | 2.55s | executed |

Total XTC compilation time (sum of all `compileXtc`): ~109s across 20 modules.

## Hot Cache: Task Execution Breakdown

| Task | Duration | Result |
|---|---|---|
| `:intellij-plugin:runIde` | 1m 26.10s | (IDE open time) |
| `:startScripts` | 0.048s | executed |
| `:generateBuildInfo` | 0.032s | UP-TO-DATE |
| `:intellij-plugin:patchPluginXml` | 0.021s | UP-TO-DATE |
| `:tree-sitter:extractZig` | 0.015s | UP-TO-DATE |
| `:generateExternalPluginSpecBuilders` | 0.014s | UP-TO-DATE |
| `:compileKotlin` | 0.011s | UP-TO-DATE |
| `:compilePluginsBlocks` | 0.011s | UP-TO-DATE |
| `:publishMavenPublicationToMavenLocal` | 0.011s | executed |
| `:publishPluginMavenPublicationToMavenLocal` | 0.009s | executed |
| `:tree-sitter:downloadZig` | 0.009s | executed |
| `:lsp-server:compileKotlin` | 0.005s | UP-TO-DATE |
| `:lib-jsondb:compileXtc` | 0.004s | UP-TO-DATE |

**Zero `compileXtc` tasks executed.** All 20 XTC modules were UP-TO-DATE (109s saved).

## Cold Cache: Configuration Phase

| Project | Duration |
|---|---|
| `:intellij-plugin` | 0.886s |
| `:debug-adapter` | 0.426s |
| `:javatools-bridge` | 0.253s |
| `:` (root) | 0.146s |
| All others | <0.04s each |
| **Total** | **1.94s** |

In the hot cache run, configuration was skipped entirely (cache HIT: 0.3s total).

## Cold Cache: Dependency Resolution

| Configuration | Duration |
|---|---|
| `:intellij-plugin:intellijPlatformJavaCompiler` | 0.779s (72% of total) |
| `:lsp-server:runtimeClasspath` | 0.149s |
| All others | <0.04s each |
| **Total** | **1.09s** |

In the hot cache run, dependency resolution was near-zero (0.14s total).

## Observations

### Why All XTC Modules Recompiled (Cold Run)

Every `compileXtc` task ran because `javatools.jar` had changed since the last build.
The `generateBuildInfo` task embeds the git commit hash into `build-info.properties`,
which causes `javatools:processResources` to be not up-to-date, which causes
`javatools:jar` to rebuild. Since every XTC compilation depends on `javatools.jar`
(the compiler), a commit change causes a full XTC recompilation cascade.

This is the correct behavior — `generateBuildInfo` only invalidates when the commit
hash changes (a new commit), not when rebuilding with unstaged file changes. The
`gitCommit` input property uses the Palantir git-version plugin to resolve the
current HEAD, and `gitStatus` tracks the dirty state.

### Why Hot Cache Was So Fast

The hot run had no source or commit changes, so:
1. **Configuration cache HIT** — skipped project loading (4.3s → 0.6s) and configuration (1.9s → 0.3s)
2. **All XTC modules UP-TO-DATE** — `javatools.jar` hadn't changed, so 0/20 modules needed recompilation (109s → 0s)
3. **All Java/Kotlin compilation UP-TO-DATE** — no source changes
4. **IntelliJ sandbox UP-TO-DATE** — `prepareSandbox`, `composedJar`, etc. all cached
5. **Only `publishToMavenLocal` tasks re-executed** — these always run (no UP-TO-DATE check on publish)

### Key Takeaways

- **Build-over-build (no changes)**: Sub-second build work + IDE launch time
- **Build after commit change**: ~109s XTC recompilation is the dominant cost
- **Configuration cache**: Saves ~6s per build (loading + configuring + dependency resolution)
- **XTC compilation parallelism**: 20 modules across 10 workers, but dependency ordering serializes critical paths (ecstasy must finish before aggregate, collections, etc.)