# XDK Version Selector for IntelliJ Plugin

> **Created**: 2026-04-03
> **Status**: Planning
> **Scope**: IntelliJ plugin -- XDK SDK type, auto-detection, Project Structure UI, integration
> **Depends on**: `idea-specific.md` Section 7 (XDK Resolution & Module Path)

## 1. Background and Motivation

### How Java JDK Selection Works in IntelliJ

IntelliJ's Java JDK management is the gold standard we want to emulate. The key
components are:

- **`SdkType`** -- Abstract class that defines a type of SDK. Java has `JavaSdk`
  (extends `SdkType`). Each SDK type provides validation, version detection, icon,
  auto-detection of installed instances, and an additional data configurable panel.

- **`ProjectJdkTable`** -- Application-level service (global, not per-project) that
  stores all registered SDK instances. Users manage this via **Settings > Project
  Structure > Platform Settings > SDKs**. Each entry has a name, home path, version,
  and type-specific additional data (e.g., classpath, sourcepath, javadoc URLs).

- **`ProjectRootManager`** -- Project-level service that stores which SDK from the
  `ProjectJdkTable` is assigned to a project. Set via **Project Structure > Project
  Settings > Project > SDK**.

- **`ProjectJdkOrderEntry`** -- Module-level dependency on an SDK, allowing modules
  within a project to use different SDK versions.

The flow: user registers JDK installations globally, then each project picks one.
IntelliJ auto-detects JDKs in standard locations (`/usr/lib/jvm/`, SDKMAN paths,
Homebrew paths, etc.) and offers to add them.

### How Other Language Plugins Handle SDKs

- **Go** (`GoSdkType extends SdkType`): auto-detects in `~/go`, `/usr/local/go`,
  `GOROOT`. Validates by checking for `go` binary and `src/runtime` directory.
  Version read from `go version` output.

- **Python** (`PythonSdkType extends SdkType`): scans PATH, `~/.pyenv`, conda
  envs, `/usr/bin/python*`. Supports virtualenvs as separate SDK entries.
  Version from `python --version`.

- **Rust** (`RsSdkType`): detects `~/.rustup/toolchains/`, `~/.cargo/bin`.
  Version from `rustc --version`. Supports multiple toolchains.

The pattern is consistent: `SdkType` subclass + auto-detection + Project Structure
integration + per-project SDK selection.

### Why XDK Needs This

Currently the XTC IntelliJ plugin has **no XDK awareness**. Examining the codebase:

- **Run configurations** (`XtcRunConfiguration.kt`): The Gradle mode delegates to
  `./gradlew runXtc`. The CLI mode hard-codes `exePath = "xtc"` relying on PATH.
  No knowledge of XDK location, version, or javatools.jar.

- **LSP server** (`XtcLspConnectionProvider.kt`): Finds `xtc-lsp-server.jar` bundled
  in the plugin's `bin/` directory. No connection to XDK installation.

- **DAP server** (`XtcDebugAdapterFactory.kt`): Same -- finds `xtc-dap-server.jar`
  from the plugin bundle.

- **Project wizard** (`XtcNewProjectWizardStep.kt`): Uses `XtcProjectCreator` with
  a version from the plugin's own version. No XDK selection.

- **`plugin.xml`**: States "Requires XDK_HOME environment variable" but nothing
  actually reads `XDK_HOME`.

Without XDK awareness, the plugin cannot:
1. Run XTC programs directly (without Gradle)
2. Provide the LSP server with the module path for cross-module navigation
3. Validate that the project's XDK version matches the installed XDK
4. Auto-compile `.x` files
5. Show which XDK version the project uses

## 2. Current XDK State in the Project

### XDK Distribution Layout

After `./gradlew :xdk:installDist`, the XDK is at `xdk/build/install/xdk/`:

```
xdk/
  bin/                    # Launcher scripts
    xtc                   # Unified launcher (Unix)
    xtc.bat               # Unified launcher (Windows)
    xcc                   # Compiler launcher (Unix)
    xcc.bat               # Compiler launcher (Windows)
    xec                   # Runner launcher (Unix)
    xec.bat               # Runner launcher (Windows)
  javatools/              # JVM runtime
    javatools.jar          # Main runtime (~6MB, entry point: org.xvm.tool.Launcher)
    javatools-jitbridge.jar # JIT bridge
    javatools_bridge.xtc   # Native bridge module
    javatools_turtle.xtc   # Turtle module
  lib/                    # Standard library modules (.xtc files)
    ecstasy.xtc            # Core library (~1.9MB)
    collections.xtc
    json.xtc
    web.xtc
    ... (20 modules total)
  doc/
  examples/
  README.md
```

### How the Gradle Plugin Resolves XDK

The `org.xtclang.xtc-plugin` (`XtcPlugin.java` / `XtcProjectDelegate.java`)
supports two dependency modes:

**1. XDK as direct dependency (included build / composite build):**
```kotlin
dependencies {
    xdk(libs.xdk)  // Resolves to org.xtclang:xdk
}
```
This uses Gradle's included build substitution. Within the XVM repo, the `xdk`
project is included, so Gradle substitutes the dependency with the locally built
XDK. The plugin extracts `.xtc` modules and `javatools.jar` from the XDK contents
via the `xdkContents` configuration.

**2. XDK as distribution archive (external projects):**
```kotlin
dependencies {
    xdkDistribution("org.xtclang:xdk:0.4.4")
}
```
The plugin's `XtcExtractXdkTask` extracts the ZIP artifact, pulling out `.xtc`
modules and `javatools.jar` into `build/xdk/`.

**Version source of truth**: For the XDK itself (composite build), `gradle/libs.versions.toml`
does not declare an explicit XDK version because the XDK is built from source.

For **external consumer projects** (the common case for plugin users), the canonical pattern
is shown in the [xtc-app-template](https://github.com/xtclang/xtc-app-template):

```toml
# gradle/libs.versions.toml
[versions]
xtc = "0.4.4-SNAPSHOT"

[plugins]
xtc = { id = "org.xtclang.xtc-plugin", version.ref = "xtc" }

[libraries]
xdk = { module = "org.xtclang:xdk", version.ref = "xtc" }
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.xtc)
}
dependencies {
    xdkDistribution(libs.xdk)
}
```

**This is the primary detection path for the XDK selector**: after IntelliJ syncs the
Gradle project (which it does automatically on import/open), the **Gradle Tooling API**
resolves all dependencies — including `org.xtclang:xdk`. We do NOT parse TOML files
or build scripts ourselves. Instead, we query the resolved Gradle model for the XDK
artifact coordinates and cache location, the same way IntelliJ discovers JDK versions
from `java.toolchain` declarations. The Gradle plugin downloads the XDK artifact from
Maven Central (or mavenLocal) and extracts it to `build/xdk/`. The IntelliJ plugin
detects this extracted XDK and registers it as the project SDK.

Whether the user declares the dependency via a version catalog, a hardcoded string
(`xdkDistribution("org.xtclang:xdk:0.4.4")`), or any other Gradle mechanism — the
Tooling API gives us the resolved version and artifact path regardless.

### How Version is Embedded in the XDK

`BuildInfo.java` reads from `build-info.properties` (generated at build time):
- `xdk.version` -- e.g., "0.4.4-SNAPSHOT"
- `xvm.version.major` / `xvm.version.minor` -- file format versions
- `git.commit` / `git.status` -- build provenance

The `javatools.jar` MANIFEST also contains version information. The `xtc --version`
command reads from the `ecstasy.xtc` module's version string, falling back to
`BuildInfo.getXdkVersion()`.

### XDK Publishing

The XDK is published to Maven Central / GitHub Maven as a ZIP artifact:
- Group: `org.xtclang`
- Artifact: `xdk`
- Packaging: `zip`
- Version: e.g., `0.4.4`

## 3. Architecture Overview

### Component Diagram

```
+------------------------------------------------------------------+
|  IntelliJ Platform                                                |
|                                                                   |
|  ProjectJdkTable (application-level)                              |
|  +------+------+------+                                           |
|  |XDK   |XDK   |XDK   | ... registered XDK installations         |
|  |0.4.3 |0.4.4 |0.5.0 |                                          |
|  +------+------+------+                                           |
|         |                                                         |
|  ProjectRootManager (per-project)                                 |
|  +-------------------+                                            |
|  | project SDK: -----+-----> XDK 0.4.4                            |
|  +-------------------+                                            |
|         |                                                         |
|  +------+-------+  +----------+  +--------+  +-----------+       |
|  |XdkSdkType    |  |XdkLocator|  |RunConfig| |LSP Client |       |
|  |  validate     |  |Service   |  |  uses   | | provides  |       |
|  |  detect       |  | resolves |  | XDK for | | module    |       |
|  |  version      |  | XDK path |  | java    | | path to   |       |
|  |  display      |  | priority |  | -jar    | | server    |       |
|  +--------------+  +----------+  +--------+  +-----------+       |
+------------------------------------------------------------------+
```

### Resolution Priority Order

When the plugin needs the XDK path, it resolves in this order.
**The project's declared version always wins** — just like IntelliJ discovers
JDK versions from Gradle's `java.toolchain`, we discover the XDK version from
the resolved Gradle dependency model after sync.

1. **Gradle Tooling API** (auto-detected after Gradle sync, highest priority)
   - After IntelliJ syncs the Gradle project, query the resolved dependency graph
     for `org.xtclang:xdk` artifacts — gives us version + cache path for free
   - Check `build/xdk/` for the extracted XDK from `xdkDistribution`
   - Check Gradle's dependency cache (`~/.gradle/caches/`) for resolved artifacts
   - For XDK-from-source projects: check `xdk/build/install/xdk/`
   - **No file parsing** — works regardless of how the user declares the dependency
     (version catalog, hardcoded string, variable, etc.)

2. **IDE-configured SDK** (Project Structure > Project > SDK)
   - Manual override by the user
   - Stored in `.idea/misc.xml` as `<project-jdk-name>`

3. **`XDK_HOME` environment variable**
   - Traditional approach, common in shell-based workflows

4. **System PATH detection**
   - Run `which xtc` and resolve the parent XDK directory
   - Works when XDK is installed system-wide

5. **Maven local repository**
   - Scan `~/.m2/repository/org/xtclang/xdk/` for installed versions
   - Auto-extract ZIP to provide an XDK home

6. **Auto-download** (future)
   - Download from Maven Central on demand
   - Similar to Gradle JDK auto-provisioning

## 4. `XdkSdkType` Implementation

### 4a. Core Class

```kotlin
package org.xtclang.idea.sdk

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import org.jdom.Element
import org.xtclang.idea.XtcIconProvider
import java.io.File
import java.nio.file.Path
import javax.swing.Icon

/**
 * SDK type for XDK installations. Registered via the `com.intellij.sdkType`
 * extension point, this enables XDK entries in Project Structure > SDKs
 * and per-project SDK selection.
 *
 * Validation: An XDK home is valid if it contains:
 *   - javatools/javatools.jar (the runtime)
 *   - lib/ecstasy.xtc (the core library module)
 *
 * Version detection: Reads the MANIFEST from javatools.jar or runs
 * `java -jar javatools.jar --version` to extract the XDK version string.
 */
class XdkSdkType : SdkType("XDK") {

    override fun getPresentableName(): String = "XDK"

    override fun getIcon(): Icon =
        XtcIconProvider.XTC_ICON ?: super.getIcon()

    // ---- Validation ----

    override fun isValidSdkHome(path: String): Boolean {
        val home = File(path)
        val hasJavatools = File(home, JAVATOOLS_JAR_RELATIVE).isFile
        val hasEcstasy = File(home, ECSTASY_MODULE_RELATIVE).isFile
        return hasJavatools && hasEcstasy
    }

    // ---- Version ----

    override fun getVersionString(sdkHome: String): String? {
        return readVersionFromManifest(sdkHome)
            ?: readVersionFromLauncher(sdkHome)
    }

    // ---- Display ----

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String {
        val version = getVersionString(sdkHome) ?: "unknown"
        return "XDK $version"
    }

    override fun suggestHomePath(): String? =
        suggestHomePaths().firstOrNull()

    override fun suggestHomePaths(): Collection<String> {
        val paths = mutableListOf<String>()

        // 1. XDK_HOME environment variable
        System.getenv("XDK_HOME")?.let { paths.add(it) }

        // 2. Common installation locations
        val userHome = System.getProperty("user.home")
        listOf(
            "$userHome/xdk",
            "$userHome/.xdk",
            "$userHome/.local/share/xdk",
            "/opt/xdk",
            "/usr/local/xdk",
        ).filter { File(it).isDirectory }
            .forEach { paths.add(it) }

        // 3. PATH-based detection: find 'xtc' binary and resolve parent
        resolveFromPath()?.let { paths.add(it) }

        // 4. Maven local repository
        findMavenLocalXdks(userHome).forEach { paths.add(it) }

        // 5. Gradle build output (for XDK developers)
        // Look for xdk/build/install/xdk/ relative to common project locations
        listOf(
            "$userHome/src/xvm/xdk/build/install/xdk",
            "$userHome/src/xtclang/xdk/build/install/xdk",
        ).filter { isValidSdkHome(it) }
            .forEach { paths.add(it) }

        return paths.filter { isValidSdkHome(it) }
    }

    // ---- Additional Data ----

    override fun createAdditionalDataConfigurable(
        sdkModel: SdkModel,
        sdkModificator: SdkModificator,
    ): AdditionalDataConfigurable? = XdkAdditionalDataConfigurable()

    override fun saveAdditionalData(
        additionalData: SdkAdditionalData,
        additional: Element,
    ) {
        if (additionalData is XdkAdditionalData) {
            additionalData.save(additional)
        }
    }

    override fun loadAdditionalData(additional: Element): SdkAdditionalData =
        XdkAdditionalData.load(additional)

    // ---- Setup ----

    override fun setupSdkPaths(sdk: Sdk, sdkModel: SdkModel): Boolean {
        val modificator = sdk.sdkModificator
        val homePath = sdk.homePath ?: return false

        // Set version
        modificator.versionString = getVersionString(homePath)

        // TODO: Add lib/ as "classes" root so IntelliJ indexes .xtc modules
        // TODO: Add examples/ and doc/ as documentation roots

        modificator.commitChanges()
        return true
    }

    // ---- Private helpers ----

    private fun readVersionFromManifest(sdkHome: String): String? {
        val jar = File(sdkHome, JAVATOOLS_JAR_RELATIVE)
        if (!jar.isFile) return null
        return try {
            java.util.jar.JarFile(jar).use { jf ->
                jf.manifest?.mainAttributes?.getValue("Implementation-Version")
            }
        } catch (e: Exception) {
            logger.warn("Failed to read version from $jar: ${e.message}")
            null
        }
    }

    private fun readVersionFromLauncher(sdkHome: String): String? {
        // Fallback: invoke xtc --version and parse output
        val xtcBin = File(sdkHome, "bin/xtc")
        if (!xtcBin.isFile) return null
        return try {
            val process = ProcessBuilder(xtcBin.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            // Parse "XTC/XVM <version> ..." format
            VERSION_PATTERN.find(output)?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.warn("Failed to read version from xtc binary: ${e.message}")
            null
        }
    }

    companion object {
        private val logger = logger<XdkSdkType>()

        /** Relative path from XDK home to javatools.jar */
        const val JAVATOOLS_JAR_RELATIVE = "javatools/javatools.jar"

        /** Relative path from XDK home to the core library module */
        const val ECSTASY_MODULE_RELATIVE = "lib/ecstasy.xtc"

        /** Pattern to extract version from xtc --version output */
        private val VERSION_PATTERN = Regex("""(\d+\.\d+\.\d+(?:-\w+)?)""")

        /**
         * Resolve XDK home from the system PATH by finding the 'xtc' binary.
         * Returns the XDK home directory (parent of bin/) or null.
         */
        fun resolveFromPath(): String? {
            return try {
                val process = ProcessBuilder("which", "xtc")
                    .redirectErrorStream(true)
                    .start()
                val path = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                if (process.exitValue() == 0 && path.isNotEmpty()) {
                    // xtc is at <xdk>/bin/xtc, so parent.parent is XDK home
                    Path.of(path).parent?.parent?.toString()
                } else null
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Find XDK installations in Maven local repository.
         * Scans ~/.m2/repository/org/xtclang/xdk/ for version directories
         * that contain an extracted XDK.
         */
        fun findMavenLocalXdks(userHome: String): List<String> {
            val m2Dir = File("$userHome/.m2/repository/org/xtclang/xdk")
            if (!m2Dir.isDirectory) return emptyList()
            return m2Dir.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { versionDir ->
                    // Look for extracted XDK or ZIP that could be extracted
                    val extractedDir = File(versionDir, "xdk")
                    if (extractedDir.isDirectory &&
                        File(extractedDir, JAVATOOLS_JAR_RELATIVE).isFile
                    ) {
                        extractedDir.absolutePath
                    } else null
                }
                ?: emptyList()
        }
    }
}
```

### 4b. Additional Data

```kotlin
package org.xtclang.idea.sdk

import com.intellij.openapi.projectRoots.SdkAdditionalData
import org.jdom.Element

/**
 * Additional data stored per XDK SDK entry. Tracks:
 * - Whether this XDK was auto-detected or manually configured
 * - The Maven coordinates if resolved from a repository
 * - The Gradle project path if resolved from a local build
 */
class XdkAdditionalData(
    var source: XdkSource = XdkSource.MANUAL,
    var mavenCoordinates: String? = null,
    var gradleProjectPath: String? = null,
) : SdkAdditionalData {

    enum class XdkSource {
        /** User manually browsed to XDK directory */
        MANUAL,
        /** Auto-detected from XDK_HOME environment variable */
        ENVIRONMENT,
        /** Auto-detected from system PATH */
        PATH,
        /** Resolved from Maven local repository */
        MAVEN_LOCAL,
        /** Downloaded from Maven Central */
        MAVEN_CENTRAL,
        /** Resolved from Gradle build output */
        GRADLE_BUILD,
    }

    fun save(element: Element) {
        element.setAttribute("source", source.name)
        mavenCoordinates?.let { element.setAttribute("mavenCoordinates", it) }
        gradleProjectPath?.let { element.setAttribute("gradleProjectPath", it) }
    }

    companion object {
        fun load(element: Element): XdkAdditionalData {
            val source = element.getAttributeValue("source")
                ?.let { runCatching { XdkSource.valueOf(it) }.getOrNull() }
                ?: XdkSource.MANUAL
            return XdkAdditionalData(
                source = source,
                mavenCoordinates = element.getAttributeValue("mavenCoordinates"),
                gradleProjectPath = element.getAttributeValue("gradleProjectPath"),
            )
        }
    }
}
```

### 4c. Additional Data Configurable (Settings Panel)

```kotlin
package org.xtclang.idea.sdk

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Configuration panel shown in the SDK detail area of Project Structure > SDKs.
 * Displays XDK-specific information: source, module count, tools available.
 */
class XdkAdditionalDataConfigurable : AdditionalDataConfigurable {
    private var sdk: Sdk? = null

    override fun setSdk(sdk: Sdk) {
        this.sdk = sdk
    }

    override fun createComponent(): JComponent = panel {
        group("XDK Information") {
            row("Source:") {
                val data = sdk?.sdkAdditionalData as? XdkAdditionalData
                label(data?.source?.name ?: "Unknown")
            }
            row("Tools:") {
                label("xtc, xcc, xec")
            }
            row("Standard Library:") {
                val homePath = sdk?.homePath
                if (homePath != null) {
                    val libDir = java.io.File(homePath, "lib")
                    val moduleCount = libDir.listFiles()
                        ?.count { it.extension == "xtc" } ?: 0
                    label("$moduleCount modules")
                } else {
                    label("N/A")
                }
            }
        }
    }

    override fun isModified(): Boolean = false

    @Throws(ConfigurationException::class)
    override fun apply() {
        // No editable fields yet
    }

    override fun reset() {
        // No editable fields yet
    }
}
```

### 4d. plugin.xml Registration

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- XDK SDK Type - enables XDK entries in Project Structure > SDKs -->
    <sdkType implementation="org.xtclang.idea.sdk.XdkSdkType"/>

    <!-- Validation banner when no XDK is configured for the project -->
    <projectSdkSetupValidator
        implementation="org.xtclang.idea.sdk.XdkSetupValidator"/>
</extensions>
```

## 5. XDK Locator Service

A project-level service that provides the resolved XDK path to all consumers
(run configurations, LSP client, DAP factory). Caches the result and re-resolves
when project settings change.

```kotlin
package org.xtclang.idea.sdk

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File
import java.nio.file.Path

/**
 * Project service that resolves the XDK installation path using the priority
 * chain defined in the architecture overview.
 *
 * All plugin components that need the XDK path should use this service rather
 * than resolving it independently.
 */
@Service(Service.Level.PROJECT)
class XdkLocatorService(private val project: Project) {
    private val logger = logger<XdkLocatorService>()

    data class XdkLocation(
        val homePath: Path,
        val version: String?,
        val source: XdkAdditionalData.XdkSource,
    ) {
        val javatoolsJar: Path get() = homePath.resolve("javatools/javatools.jar")
        val libDir: Path get() = homePath.resolve("lib")
        val binDir: Path get() = homePath.resolve("bin")
        val xtcBinary: Path get() = binDir.resolve("xtc")
    }

    /**
     * Resolve the XDK installation for this project.
     * Returns null if no XDK can be found.
     *
     * Resolution order:
     * 1. Project SDK (set in Project Structure)
     * 2. Gradle build output (for XDK developer projects)
     * 3. XDK_HOME environment variable
     * 4. System PATH
     * 5. Maven local repository (newest version)
     */
    fun resolve(): XdkLocation? {
        return resolveFromProjectSdk()
            ?: resolveFromGradleBuild()
            ?: resolveFromEnvironment()
            ?: resolveFromPath()
            ?: resolveFromMavenLocal()
    }

    /** Convenience: get the javatools.jar path or null */
    fun resolveJavatoolsJar(): Path? = resolve()?.javatoolsJar

    /** Convenience: get the lib/ directory for module path */
    fun resolveLibDir(): Path? = resolve()?.libDir

    // ---- Resolution strategies ----

    private fun resolveFromProjectSdk(): XdkLocation? {
        val sdk = ProjectRootManager.getInstance(project).projectSdk
        if (sdk?.sdkType !is XdkSdkType) return null
        val homePath = sdk.homePath ?: return null
        if (!XdkSdkType().isValidSdkHome(homePath)) return null
        logger.info("XDK resolved from project SDK: $homePath")
        return XdkLocation(
            homePath = Path.of(homePath),
            version = sdk.versionString,
            source = XdkAdditionalData.XdkSource.MANUAL,
        )
    }

    private fun resolveFromGradleBuild(): XdkLocation? {
        val basePath = project.basePath ?: return null
        // Check if project contains xdk/build/install/xdk/
        val candidates = listOf(
            "$basePath/xdk/build/install/xdk",
            "$basePath/build/install/xdk",
        )
        for (candidate in candidates) {
            if (XdkSdkType().isValidSdkHome(candidate)) {
                logger.info("XDK resolved from Gradle build: $candidate")
                return XdkLocation(
                    homePath = Path.of(candidate),
                    version = XdkSdkType().getVersionString(candidate),
                    source = XdkAdditionalData.XdkSource.GRADLE_BUILD,
                )
            }
        }
        return null
    }

    private fun resolveFromEnvironment(): XdkLocation? {
        val xdkHome = System.getenv("XDK_HOME") ?: return null
        if (!XdkSdkType().isValidSdkHome(xdkHome)) {
            logger.warn("XDK_HOME=$xdkHome is not a valid XDK installation")
            return null
        }
        logger.info("XDK resolved from XDK_HOME: $xdkHome")
        return XdkLocation(
            homePath = Path.of(xdkHome),
            version = XdkSdkType().getVersionString(xdkHome),
            source = XdkAdditionalData.XdkSource.ENVIRONMENT,
        )
    }

    private fun resolveFromPath(): XdkLocation? {
        val xdkHome = XdkSdkType.resolveFromPath() ?: return null
        if (!XdkSdkType().isValidSdkHome(xdkHome)) return null
        logger.info("XDK resolved from PATH: $xdkHome")
        return XdkLocation(
            homePath = Path.of(xdkHome),
            version = XdkSdkType().getVersionString(xdkHome),
            source = XdkAdditionalData.XdkSource.PATH,
        )
    }

    private fun resolveFromMavenLocal(): XdkLocation? {
        val userHome = System.getProperty("user.home")
        val xdks = XdkSdkType.findMavenLocalXdks(userHome)
        if (xdks.isEmpty()) return null
        // Use the newest version (last alphabetically, assuming semver)
        val newest = xdks.maxByOrNull { it }!!
        logger.info("XDK resolved from Maven local: $newest")
        return XdkLocation(
            homePath = Path.of(newest),
            version = XdkSdkType().getVersionString(newest),
            source = XdkAdditionalData.XdkSource.MAVEN_LOCAL,
        )
    }

    companion object {
        fun getInstance(project: Project): XdkLocatorService =
            project.getService(XdkLocatorService::class.java)
    }
}
```

## 6. Setup Validator (Missing SDK Banner)

When no XDK is configured, IntelliJ shows a yellow banner at the top of editors,
similar to "Project SDK is not defined."

```kotlin
package org.xtclang.idea.sdk

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Shows a warning banner in the editor when the project has no XDK SDK configured
 * and the file is an .x file.
 */
class XdkSetupValidator : ProjectSdkSetupValidator {

    override fun isApplicableFor(project: Project, file: VirtualFile): Boolean =
        file.extension == "x"

    override fun getErrorMessage(project: Project, file: VirtualFile): String? {
        val sdk = ProjectRootManager.getInstance(project).projectSdk
        if (sdk != null && sdk.sdkType is XdkSdkType) return null

        // Check if XdkLocatorService can find an XDK even without explicit config
        val locator = XdkLocatorService.getInstance(project)
        if (locator.resolve() != null) return null

        return "No XDK configured. Go to Project Structure > Project > SDK to set an XDK."
    }
}
```

## 7. Auto-Detection Logic

### 7a. Standard Locations by Platform

| Platform | Locations |
|----------|-----------|
| macOS | `~/xdk`, `~/.xdk`, `/opt/xdk`, `/usr/local/xdk`, Homebrew (`/opt/homebrew/opt/xdk`) |
| Linux | `~/xdk`, `~/.xdk`, `~/.local/share/xdk`, `/opt/xdk`, `/usr/local/xdk`, SDKMAN (`~/.sdkman/candidates/xdk/`) |
| Windows | `%USERPROFILE%\xdk`, `%LOCALAPPDATA%\xdk`, `C:\xdk` |
| All | `$XDK_HOME`, Maven local `~/.m2/repository/org/xtclang/xdk/<version>/xdk/` |

### 7b. Detection on Project Open

When a project opens, the `XdkProjectOpenListener` checks if:
1. The project has an XDK SDK configured -- if yes, done.
2. If not, it runs auto-detection and offers to configure.

```kotlin
package org.xtclang.idea.sdk

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Runs on project open to detect XDK and suggest configuration.
 */
class XdkProjectOpenActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Only trigger for projects that contain .x files or apply the xtc plugin
        if (!isXtcProject(project)) return

        val sdk = com.intellij.openapi.roots.ProjectRootManager
            .getInstance(project).projectSdk
        if (sdk?.sdkType is XdkSdkType) return // Already configured

        val locator = XdkLocatorService.getInstance(project)
        val location = locator.resolve()

        if (location != null) {
            // Found an XDK, offer to configure it
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XTC Language Server")
                .createNotification(
                    "XDK Detected",
                    "Found XDK ${location.version ?: "unknown"} at ${location.homePath}. " +
                        "Configure it as the project SDK?",
                    NotificationType.INFORMATION,
                )
                .addAction(ConfigureXdkAction(project, location))
                .notify(project)
        }
    }

    private fun isXtcProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        // Check for build.gradle.kts with xtc plugin or any .x files
        val buildFile = java.io.File(basePath, "build.gradle.kts")
        if (buildFile.exists() && buildFile.readText().contains("xtc")) return true
        // Check for src/main/x/ directory
        return java.io.File(basePath, "src/main/x").isDirectory
    }
}
```

### 7c. Registration in plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Auto-detect XDK on project open -->
    <postStartupActivity
        implementation="org.xtclang.idea.sdk.XdkProjectOpenActivity"/>
</extensions>
```

## 8. UI Description

### 8a. Project Structure > SDKs

When the user opens **Project Structure > Platform Settings > SDKs** and clicks
the **+** button, "XDK" appears in the list alongside "JDK", "Kotlin SDK", etc.

Clicking "XDK" opens a file chooser pre-populated with `suggestHomePaths()` results.
The user selects an XDK directory, and the plugin validates it via `isValidSdkHome()`.

After adding, the SDK panel shows:

```
+-------------------------------------------+
| Name: [XDK 0.4.4                       ]  |
| XDK home path: [/opt/xdk               ]  |
|                                            |
| XDK Information                            |
| +-----------------------------------------+|
| | Source:           MANUAL                 ||
| | Tools:            xtc, xcc, xec         ||
| | Standard Library: 20 modules            ||
| +-----------------------------------------+|
+--------------------------------------------+
```

### 8b. Project Structure > Project

In **Project Structure > Project Settings > Project**, the SDK dropdown now
includes XDK entries alongside JDK entries:

```
+-------------------------------------------+
| SDK: [XDK 0.4.4                      |v]  |
|      +----------------------------+       |
|      | <No SDK>                    |       |
|      | JDK 25                     |       |
|      | XDK 0.4.4                  |  <--  |
|      | XDK 0.5.0-SNAPSHOT         |       |
|      +----------------------------+       |
+-------------------------------------------+
```

### 8c. Settings Page (Languages & Frameworks > XTC)

A project-level settings page for XTC-specific configuration:

```
+-------------------------------------------+
| Languages & Frameworks > XTC               |
|                                            |
| XDK Configuration                          |
| +------------------------------------------+
| | XDK:   [XDK 0.4.4              |v] [+]  |
| |        (from Project Structure)          |
| |                                          |
| | Auto-detect: [x] Scan on project open   |
| +------------------------------------------+
|                                            |
| Execution                                  |
| +------------------------------------------+
| | Default mode:                            |
| |   (*) Gradle (recommended for Gradle     |
| |       projects)                          |
| |   ( ) XDK Direct (uses javatools.jar)    |
| |   ( ) CLI (requires xtc on PATH)         |
| +------------------------------------------+
|                                            |
| LSP Server                                 |
| +------------------------------------------+
| | Log level:  [INFO              |v]       |
| | [x] Enable semantic tokens               |
| +------------------------------------------+
+--------------------------------------------+
```

### 8d. Run Configuration Integration

The run configuration editor gains an XDK path field when "XDK Direct" mode
is selected:

```
+-------------------------------------------+
| XTC Run Configuration                      |
|                                            |
| Module name:     [MyApp                 ]  |
| Method name:     [                      ]  |
| Module arguments:[                      ]  |
|                                            |
| Execution mode:                            |
|   (*) Gradle (recommended)                 |
|   ( ) XDK Direct                           |
|   ( ) CLI (requires xtc on PATH)           |
|                                            |
| XDK: [Use project SDK (XDK 0.4.4)  |v]    |
|      +----------------------------+        |
|      | Use project SDK (XDK 0.4.4) |       |
|      | XDK 0.4.4                   |       |
|      | XDK 0.5.0-SNAPSHOT          |       |
|      | Custom path...              |       |
|      +----------------------------+        |
|                                            |
| [ ] Quiet mode (-q)                        |
+--------------------------------------------+
```

## 9. LSP Server and Run Configuration Integration

### 9a. LSP Server -- Module Path via workspace/configuration

The `XtcLanguageClient` already handles `workspace/configuration` requests.
Extend it to serve the XDK module path:

```kotlin
// In XtcLanguageClient.kt

private fun resolveConfigSection(item: ConfigurationItem): Any? =
    when (item.section) {
        FORMATTING_SECTION -> readFormattingSettings()
        MODULE_PATH_SECTION -> readModulePath()       // NEW
        XDK_PATH_SECTION -> readXdkPath()             // NEW
        else -> null
    }

private fun readModulePath(): List<String> {
    val locator = XdkLocatorService.getInstance(project)
    val location = locator.resolve() ?: return emptyList()
    val paths = mutableListOf<String>()

    // XDK standard library
    paths.add(location.libDir.toString())

    // XDK javatools modules (bridge, turtle)
    val javatoolsDir = location.homePath.resolve("javatools")
    if (javatoolsDir.toFile().isDirectory) {
        paths.add(javatoolsDir.toString())
    }

    // Project build output (if available)
    project.basePath?.let { base ->
        listOf(
            "$base/build/xtc/main",
            "$base/build/xtc",
        ).filter { java.io.File(it).isDirectory }
            .forEach { paths.add(it) }
    }

    return paths
}

private fun readXdkPath(): String? {
    val locator = XdkLocatorService.getInstance(project)
    return locator.resolve()?.homePath?.toString()
}

companion object {
    const val FORMATTING_SECTION = "xtc.formatting"
    const val MODULE_PATH_SECTION = "xtc.modulePath"   // NEW
    const val XDK_PATH_SECTION = "xtc.xdkPath"         // NEW
}
```

### 9b. Run Configuration -- XDK Direct Mode

Extend `XtcRunConfiguration` with a third execution mode that uses
`javatools.jar` directly:

```kotlin
// New method in XtcRunConfiguration.kt

private fun createXdkDirectCommandLine(): GeneralCommandLine {
    val locator = XdkLocatorService.getInstance(project)
    val xdk = locator.resolve()
        ?: throw ExecutionException("No XDK configured. Set XDK in Project Structure > SDK.")

    // Find java binary (prefer project JDK, fall back to system)
    val javaPath = findJavaBinary()

    return GeneralCommandLine().apply {
        exePath = javaPath
        addParameter("-jar")
        addParameter(xdk.javatoolsJar.toString())
        addParameter("run")
        // Add module path: XDK lib + project build output
        addParameter("-L")
        addParameter(xdk.libDir.toString())
        project.basePath?.let { base ->
            val buildOutput = java.io.File("$base/build/xtc/main")
            if (buildOutput.isDirectory) {
                addParameter("-L")
                addParameter(buildOutput.absolutePath)
            }
        }
        // Module and method
        moduleName.takeIf { it.isNotBlank() }?.let { addParameter(it) }
        methodName.takeIf { it.isNotBlank() }?.let {
            addParameter("-M")
            addParameter(it)
        }
        moduleArguments.split(",")
            .filter { it.isNotBlank() }
            .forEach(::addParameter)
        workDirectory = project.basePath?.let { Path(it).toFile() }
        logger.info("Running XDK Direct: $commandLineString")
    }
}
```

### 9c. DAP Server -- XDK Awareness

The `XtcDebugAdapterDescriptor` should pass the XDK path in DAP launch parameters
so the debug adapter can resolve modules:

```kotlin
override fun getDapParameters(): Map<String, Any> {
    val params = mutableMapOf<String, Any>(
        "type" to "xtc",
        "request" to "launch",
    )
    environment.project.basePath?.let { params["cwd"] = it }

    // Pass XDK path to DAP server
    val locator = XdkLocatorService.getInstance(environment.project)
    locator.resolve()?.let { xdk ->
        params["xdkPath"] = xdk.homePath.toString()
        params["modulePath"] = listOf(
            xdk.libDir.toString(),
            xdk.homePath.resolve("javatools").toString(),
        )
    }

    return params
}
```

## 10. Integration with the Gradle Plugin's XDK Version

### 10a. Reading XDK Version from Gradle

When the project is a Gradle project with the `org.xtclang.xtc-plugin`, we can
extract the XDK version by reading the Gradle model. IntelliJ's Gradle integration
provides access to this.

The two key patterns for XDK dependency declaration are:

```kotlin
// Pattern 1: Direct XDK dependency (most common for external projects)
dependencies {
    xdk(libs.xdk)                              // Via version catalog
    xdk("org.xtclang:xdk:0.4.4")             // Direct coordinates
    xdkDistribution("org.xtclang:xdk:0.4.4") // Distribution ZIP
}

// Pattern 2: Included build (XDK developers)
// No explicit version -- resolved via composite build substitution
```

Use IntelliJ's `ExternalSystemProjectTracker` / Gradle Tooling API to read the
resolved dependency graph after Gradle sync and extract the XDK artifact version
and cache path. This is the same approach IntelliJ uses for JDK discovery from
`java.toolchain` — no build file parsing, works with any dependency declaration style.

### 10b. Syncing Gradle XDK with IDE SDK

When the project's Gradle configuration specifies XDK 0.4.4, but the IDE has
XDK 0.5.0 selected, the plugin should warn:

```
Warning: Project Gradle build uses XDK 0.4.4, but project SDK is XDK 0.5.0.
[Use Gradle version] [Keep current]
```

This is similar to how IntelliJ warns when the Gradle JDK differs from the project
JDK.

## 11. File Structure

All new files go under `lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/sdk/`:

```
lang/intellij-plugin/src/main/kotlin/org/xtclang/idea/
  sdk/
    XdkSdkType.kt                  # SdkType implementation
    XdkAdditionalData.kt           # SDK additional data (source tracking)
    XdkAdditionalDataConfigurable.kt  # Settings panel in SDK detail view
    XdkLocatorService.kt           # Project service for XDK resolution
    XdkSetupValidator.kt           # "No XDK configured" banner
    XdkProjectOpenActivity.kt      # Auto-detect on project open
    ConfigureXdkAction.kt          # Notification action to configure XDK
  settings/
    XtcSettingsConfigurable.kt     # Languages & Frameworks > XTC page
    XtcSettingsState.kt            # Persistent state for XTC settings
```

Files to modify:

| File | Change |
|------|--------|
| `plugin.xml` | Register `sdkType`, `projectSdkSetupValidator`, `postStartupActivity`, `projectConfigurable`, `projectService` |
| `run/XtcRunConfiguration.kt` | Add `ExecutionMode` enum, `createXdkDirectCommandLine()`, settings editor radio buttons |
| `lsp/XtcLanguageClient.kt` | Add `xtc.modulePath` and `xtc.xdkPath` configuration sections |
| `dap/XtcDebugAdapterFactory.kt` | Pass XDK path in DAP parameters |
| `project/XtcNewProjectWizardStep.kt` | Use resolved XDK version instead of plugin version |

## 12. Phased Implementation Plan

### Phase 1: Foundation (Effort: 3-4 days)

**Goal**: XDK appears as an SDK type in Project Structure; basic auto-detection works.

| Task | File | Effort |
|------|------|--------|
| Create `XdkSdkType` with validation and version detection | `sdk/XdkSdkType.kt` | 4h |
| Create `XdkAdditionalData` and configurable | `sdk/XdkAdditionalData.kt`, `sdk/XdkAdditionalDataConfigurable.kt` | 2h |
| Register `sdkType` in `plugin.xml` | `plugin.xml` | 15min |
| Implement `suggestHomePaths()` for macOS/Linux/Windows | `sdk/XdkSdkType.kt` | 2h |
| Create `XdkLocatorService` with resolution chain | `sdk/XdkLocatorService.kt` | 4h |
| Register project service in `plugin.xml` | `plugin.xml` | 15min |
| Manual testing: add XDK in Project Structure, verify validation | -- | 2h |
| Unit tests for `isValidSdkHome()` and `getVersionString()` | `test/.../sdk/XdkSdkTypeTest.kt` | 2h |

**Deliverable**: Users can manually add and select XDK installations in Project
Structure. Auto-detection suggests paths when adding.

### Phase 2: Auto-Detection and Notifications (Effort: 2-3 days)

**Goal**: Plugin auto-detects XDK and prompts configuration on project open.

| Task | File | Effort |
|------|------|--------|
| Create `XdkSetupValidator` (editor banner) | `sdk/XdkSetupValidator.kt` | 1h |
| Create `XdkProjectOpenActivity` | `sdk/XdkProjectOpenActivity.kt` | 3h |
| Create `ConfigureXdkAction` (notification action) | `sdk/ConfigureXdkAction.kt` | 2h |
| Auto-register detected XDK in `ProjectJdkTable` | `sdk/XdkProjectOpenActivity.kt` | 2h |
| Register extensions in `plugin.xml` | `plugin.xml` | 15min |
| Test: open XTC project without XDK, verify banner and notification | -- | 2h |

**Deliverable**: Opening an XTC project without XDK shows a helpful banner and
auto-detects available installations.

### Phase 3: Run Configuration Integration (Effort: 3-4 days)

**Goal**: Run configurations can use XDK directly without Gradle.

| Task | File | Effort |
|------|------|--------|
| Add `ExecutionMode` enum (GRADLE, XDK_DIRECT, CLI) | `run/XtcRunConfiguration.kt` | 1h |
| Implement `createXdkDirectCommandLine()` | `run/XtcRunConfiguration.kt` | 4h |
| Update settings editor with radio group and XDK selector | `run/XtcRunConfiguration.kt` | 3h |
| Update `XtcRunConfigurationProducer` to auto-select mode | `run/XtcRunConfigurationProducer.kt` | 2h |
| Serialize/deserialize new fields | `run/XtcRunConfiguration.kt` | 1h |
| Test: run XTC module in all three modes | -- | 3h |

**Deliverable**: Users can run XTC programs directly via `javatools.jar` without
Gradle, with the XDK resolved from the project SDK.

### Phase 4: LSP/DAP Integration (Effort: 2-3 days)

**Goal**: LSP server receives XDK module path; DAP gets XDK context.

| Task | File | Effort |
|------|------|--------|
| Add `xtc.modulePath` to `XtcLanguageClient` | `lsp/XtcLanguageClient.kt` | 2h |
| Add `xtc.xdkPath` to `XtcLanguageClient` | `lsp/XtcLanguageClient.kt` | 1h |
| LSP server: request and cache module path at init | (lsp-server project) | 4h |
| Pass XDK path in DAP launch parameters | `dap/XtcDebugAdapterFactory.kt` | 1h |
| Test: verify LSP server receives correct paths | -- | 2h |

**Deliverable**: LSP server knows the XDK module path for cross-module navigation.

### Phase 5: Settings Page and Gradle Sync (Effort: 3-4 days)

**Goal**: Full settings UI; Gradle XDK version syncs with IDE SDK.

| Task | File | Effort |
|------|------|--------|
| Create `XtcSettingsConfigurable` | `settings/XtcSettingsConfigurable.kt` | 4h |
| Create `XtcSettingsState` (persistent state) | `settings/XtcSettingsState.kt` | 2h |
| Read XDK version from Gradle model | `sdk/XdkLocatorService.kt` | 4h |
| Gradle/IDE version mismatch warning | `sdk/XdkProjectOpenActivity.kt` | 2h |
| Register configurable in `plugin.xml` | `plugin.xml` | 15min |
| Test: settings page, Gradle sync warning | -- | 2h |

**Deliverable**: Full Languages & Frameworks > XTC settings page; version mismatch
detection between Gradle and IDE SDK.

### Phase 6: Maven Auto-Download (Effort: 3-4 days, future)

**Goal**: Plugin can download XDK from Maven Central on demand.

| Task | File | Effort |
|------|------|--------|
| Maven Central version listing | `sdk/XdkMavenResolver.kt` | 4h |
| Download and extract XDK ZIP | `sdk/XdkMavenResolver.kt` | 4h |
| Progress indicator during download | `sdk/XdkMavenResolver.kt` | 2h |
| Cache management (cleanup old versions) | `sdk/XdkMavenResolver.kt` | 2h |
| Integration with `XdkSdkType.suggestHomePaths()` | `sdk/XdkSdkType.kt` | 1h |
| Test: download and configure XDK from Maven Central | -- | 2h |

**Deliverable**: Plugin can download any published XDK version from Maven Central,
similar to Gradle's JDK auto-provisioning.

## 13. Total Effort Estimate

| Phase | Days | Priority |
|-------|------|----------|
| Phase 1: Foundation | 3-4 | **Critical** |
| Phase 2: Auto-Detection | 2-3 | **High** |
| Phase 3: Run Config | 3-4 | **High** |
| Phase 4: LSP/DAP | 2-3 | **High** |
| Phase 5: Settings + Gradle | 3-4 | **Medium** |
| Phase 6: Maven Download | 3-4 | **Low** (future) |
| **Total** | **16-22** | |

Phases 1-4 are the core functionality (~12 days). Phases 5-6 are polish.

## 14. Key Design Decisions

### Decision 1: SDK Type vs. Project Service Alone

**Chosen: SDK Type (`SdkType`)**. Rationale: integrates with IntelliJ's existing
Project Structure UI, supports multiple XDK versions side by side, persists across
IDE restarts, and follows the pattern established by Java, Go, Python, and Rust
plugins. A project service alone would require a custom settings page with no
integration into the standard SDK management flow.

### Decision 2: javatools.jar Detection vs. xtc Binary Detection

**Chosen: Both, with javatools.jar as primary**. The `javatools.jar` is the actual
runtime -- run configurations use it directly via `java -jar`. The `xtc` binary
is a shell script wrapper. We validate both exist, but the JAR is the critical
artifact.

### Decision 3: Per-Project vs. Global XDK

**Chosen: Both**. XDK installations are registered globally in `ProjectJdkTable`
(like JDK). Each project selects one from the global pool. This matches IntelliJ's
standard model and supports switching XDK versions per project.

### Decision 4: Gradle XDK Resolution

**Chosen: Text parsing first, Gradle model later**. Phase 1 uses simple regex
parsing of `build.gradle.kts` to extract the XDK version. Phase 5 upgrades to
using IntelliJ's Gradle integration API (`ExternalSystemProjectTracker`) for
accurate dependency resolution. Text parsing covers 80% of cases with minimal
complexity.

### Decision 5: Module Path Delivery to LSP

**Chosen: `workspace/configuration` protocol**. The LSP server already requests
`xtc.formatting` via this mechanism. Adding `xtc.modulePath` and `xtc.xdkPath`
sections follows the established pattern and works cross-editor (VS Code can
serve the same sections from `settings.json`).

## 15. Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| `SdkType` API changes across IntelliJ versions | Pin to 2026.1 API, use `@Suppress("UnstableApiUsage")` where needed, test with `sinceBuild`/`untilBuild` |
| XDK ZIP extraction for Maven artifacts is slow | Cache extracted XDKs in `~/.xtclang/sdks/<version>/`, check before re-extracting |
| Multiple XDK layouts (installDist vs. ZIP vs. manual) | `isValidSdkHome()` checks only for the two essential files (`javatools.jar` + `ecstasy.xtc`), works with any layout |
| Gradle model API complexity | Start with text parsing (Phase 1); upgrade to Gradle API in Phase 5 only if text parsing proves insufficient |
| `xtc --version` is slow to invoke | Prefer JAR manifest reading; cache version strings in `XdkAdditionalData` |

## 16. References

- IntelliJ SDK Docs: [SDK](https://plugins.jetbrains.com/docs/intellij/sdk.html)
- IntelliJ SDK Docs: [SdkType](https://plugins.jetbrains.com/docs/intellij/sdk.html#sdk-type)
- Go plugin source: `GoSdkType.kt` in [intellij-go](https://github.com/nicholasgasior/intellij-go)
- Python plugin: `PythonSdkType.java` in intellij-community
- Rust plugin: `RsSdkType.kt` in intellij-rust
- Current XTC plugin: `lang/intellij-plugin/` in this repository
- XDK distribution: `xdk/build.gradle.kts` -- `distributions { main { contents { ... } } }`
- Gradle XTC plugin: `plugin/src/main/java/org/xtclang/plugin/` -- `XtcProjectDelegate.java`
- Existing plan: `lang/doc/plans/idea-specific.md` Section 7
