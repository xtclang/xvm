# Building XTC From Source

This document covers all Gradle build details for the XVM project: tasks, distributions, clean semantics, 
debugging, versioning, and publishing.

**See also:** [Gradle Fundamentals](assumed-gradle-knowledge.md) for developers new to Gradle.

---

## Maven Artifacts and IDE Integration

**For Most Developers:** Use the XTC Gradle plugin in your IDE instead of command-line tools:

```kotlin
// In your build.gradle.kts
plugins {
    id("org.xtclang.xtc") version "0.4.4-SNAPSHOT"
}
```

**Maven Repository Access:**

```kotlin
repositories {
    // For snapshots and releases (current)
    maven {
        url = uri("https://maven.pkg.github.com/xtclang/xvm")
        credentials {
            username = System.getenv("GITHUB_USERNAME")
            password = System.getenv("GITHUB_TOKEN") // needs read:packages scope
        }
    }
    // For local development builds
    mavenLocal()
    // Maven Central (coming soon - will eliminate need for GitHub credentials)
    mavenCentral()
}
```

**Future Repository Access:** We plan to publish Maven artifacts to Maven Central (Sonatype OSSRH), which will eliminate the need for GitHub user/token configuration. This will make XDK artifacts available through standard Maven Central without authentication.

**Gradle Plugin Portal:** The XTC language plugin is published to the [Gradle Plugin Portal](https://plugins.gradle.org/), and we're moving toward continuous publication of plugin updates. This means you can use the plugin without any special repository configuration:

```kotlin
// No special repositories needed - fetched from Gradle Plugin Portal
plugins {
    id("org.xtclang.xtc") version "0.4.4-SNAPSHOT"
}
```

The plugin handles all XDK dependencies automatically - most XTC developers won't need the command-line tools.

---

## Gradle Build Tasks and XDK Setup

The XVM project uses Gradle for building and distribution management. Understanding the different build tasks and installation options is essential for development and deployment.

### Core Build Tasks

- **`./gradlew build`** - Executes the complete build lifecycle including compilation, testing, and packaging. This creates all XDK components but doesn't install them locally.

- **`./gradlew xdk:installDist`** - Installs the complete XDK distribution to `xdk/build/install/xdk/` with cross-platform shell script launchers (`xec`, `xcc`) ready to use immediately. This is the recommended installation method for development.

### Distribution Tasks

The project provides two main distribution variants:

#### Installation Tasks (creates local installations):

1. **`./gradlew xdk:installDist`** - **Recommended** default installation with cross-platform shell script launchers
    - **Output**: `xdk/build/install/xdk/`
    - **Contents**: Cross-platform script launchers (`xec`, `xcc`, `xec.bat`, `xcc.bat`)
    - **Ready to use**: Just add `bin/` to your PATH - no configuration needed

2. **`./gradlew xdk:installWithNativeLaunchersDist`** - Platform-specific native binary launchers
    - **Output**: `xdk/build/install/xdk-native-{os}_{arch}/` (e.g., `xdk-native-linux_amd64/`)
    - **Contents**: Platform-specific native binary launchers (`xec`, `xcc`)
    - **Ready to use**: Just add `bin/` to your PATH - no configuration needed


#### Archive Tasks (creates distributable archives):

1. **`./gradlew xdk:distZip`** / **`./gradlew xdk:distTar`** - **Recommended** default archives with cross-platform script launchers
    - **Output**: `xdk-{version}.zip` / `xdk-{version}.tar.gz`
    - **Contents**: Cross-platform script launchers (`xec`, `xcc`, `xec.bat`, `xcc.bat`)
    - **Ready to use**: Extract and add `bin/` to PATH

2. **`./gradlew xdk:withNativeLaunchersDistZip`** / **`./gradlew xdk:withNativeLaunchersDistTar`** - Platform-specific native binary launchers
    - **Output**: `xdk-{version}-native-{os}_{arch}.zip` / `xdk-{version}-native-{os}_{arch}.tar.gz`
    - **Contents**: Platform-specific native launchers (`xec`, `xcc`)
    - **Ready to use**: Extract and add `bin/` to PATH


#### Distribution Differences

**Default Distribution** (`installDist`, `distZip`, `distTar`):
- Cross-platform shell script launchers (`xec`, `xcc`, `xec.bat`, `xcc.bat`)
- Ready to use immediately - just add `bin/` to your PATH
- **Recommended for all users**

**Native Launcher Distribution** (`withNativeLaunchers*`):
- Platform-specific native binary launchers (`xec`, `xcc`)
- Ready to use immediately - just add `bin/` to your PATH
- **Alternative for specific platform requirements**

The archive tasks produce the same XDK installation content as their corresponding installation tasks, but package them as ZIP and tar.gz files in the `xdk/build/distributions/` directory. These archives are suitable for distribution and deployment to other systems.

**Example archive filenames** (for version `0.4.4-SNAPSHOT`):
- `xdk-0.4.4-SNAPSHOT.zip` - **Default**: Cross-platform script launchers
- `xdk-0.4.4-SNAPSHOT-native-macos_arm64.zip` - macOS ARM64 native launchers

### Quick Development Setup

For developers who want a working XDK installation on their local machine:

1. **Build and install:**
   ```bash
   ./gradlew xdk:installDist
   ```

2. **Add the XDK bin directory to your PATH:**
   ```bash
   # Default installation with script launchers (recommended):
   export PATH="/path/to/xvm/xdk/build/install/xdk/bin:$PATH"

   # Alternative: Platform-specific binary launchers (adjust {os}_{arch} as needed):
   export PATH="/path/to/xvm/xdk/build/install/xdk-native-linux_amd64/bin:$PATH"
   ```

3. **Set XDK_HOME environment variable:**
   ```bash
   # Default installation (recommended):
   export XDK_HOME="/path/to/xvm/xdk/build/install/xdk"

   # Alternative: Platform-specific binary launchers (adjust {os}_{arch} as needed):
   export XDK_HOME="/path/to/xvm/xdk/build/install/xdk-native-linux_amd64"
   ```

**Tip for Local Development:** You can create a symlink from your home directory to simplify path management:
```bash
# Recommended for development:
ln -sf "/path/to/xvm/xdk/build/install/xdk" ~/xdk-latest
export PATH="~/xdk-latest/bin:$PATH"
export XDK_HOME="~/xdk-latest"
```

This approach shouldn't be controversial since production installations are handled by package managers anyway.

**Important:** The default `installDist` task creates a complete, self-contained XDK installation with proper classpath configuration and ready-to-use launchers. There's no need to run platform-specific configuration scripts like `cfg_macos.sh` - these are legacy approaches that have been superseded by the current Gradle-based distribution system.

### Environment Configuration

Once you have an XDK installation (via any of the `install` group tasks), configure your environment:

- **XDK_HOME**: Set this to the root of your XDK installation directory (e.g., `xdk/build/install/xdk`)
- **PATH**: Add `$XDK_HOME/bin` to your PATH to access `xec` and `xcc` launchers

When `XDK_HOME` is properly set and the launchers are in your PATH, any `xec` (Ecstasy runner) or `xcc` (Ecstasy compiler) command will automatically use the correct XDK libraries and classpath.

### Understanding the Build Artifacts

After running any install task, you'll find:

- **`lib/`** - Core Ecstasy modules (`ecstasy.xtc`, `collections.xtc`, etc.)
- **`javatools/`** - Java-based toolchain (`javatools.jar`, bridge modules)
- **`bin/`** - Executable launchers (if using launcher variants)
    - `xec` - Ecstasy code runner
    - `xcc` - Ecstasy compiler

The difference between `build` and `installDist` is that `build` creates all the necessary artifacts but leaves them in their individual project build directories, while `installDist` assembles everything into a unified, deployable XDK structure ready for use.

---

## Understanding Gradle Clean vs Make Clean

**Important:** Gradle's `clean` is fundamentally different from traditional `make clean`:

- **Make clean**: Simply deletes all build outputs ("delete everything" semantic)
- **Gradle clean**: Intelligently removes only outputs that need to be rebuilt, leveraging Gradle's incremental build system and caching

### When to Use Clean

```bash
./gradlew clean
```

Gradle's incremental build system tracks input/output relationships and only rebuilds what has changed. You should **rarely need** to run `clean` because:

- Gradle automatically detects when files need to be rebuilt
- The build cache preserves intermediate outputs for faster rebuilds
- Clean invalidates all cached work, making subsequent builds slower

**Only use `clean` when:**
- You suspect build cache corruption
- You're troubleshooting unusual build behavior
- You're preparing for a completely fresh build for testing

### Composite Build Limitation

**Critical:** Due to our parallel composite build architecture, **never combine `clean` with other build tasks** in a single command:

```bash
# DON'T DO THIS - will cause build failures
./gradlew clean build

# DO THIS INSTEAD - run separately
./gradlew clean
./gradlew build
```

The parallel composite build runs subprojects concurrently, and `clean` will interfere with other tasks that are simultaneously creating files, leading to race conditions and build failures.

Should you, for any reason, need to clear the caches, and really start fresh, you can run the script

./bin/purge-all-build-state.sh

Or do the equivalent actions manually:

1) Close any open XTC projects in your IDEs, to avoid restarting them with a large state change under the hood.
   Optionally, also close your IDE processes.
2) Kill all Gradle daemons.
3) Delete the `$GRADLE_USER_HOME/cache` and `$GRADLE_USER_HOME/daemons` directories. *NOTE: this invalidates
   caches for all Gradle builds on your current system, and rebuilds a new Gradle version.*
4) Run `git clean -xfd` in your build root. Note that this may also delete any IDE configuration that resides
   in your build. You may want to preserve e.g. the `.idea` directory, and then you can do `git clean -xfd -e .idea`
   or perform a dry run `git clean -xfdn`, to see what will be deleted. Note that if you are at this level of
   purging stuff, it's likely a bad idea to hang on to your IDE state anyway.

---

## Debugging the Build

The build should be debuggable through any IDE, for example IntelliJ, using its Gradle tooling API
hook. You can run any task in the project in debug mode from within the IDE, with breakpoints in
the build scripts and/or the underlying non-XTC code, for example in Javatools, to debug the
compiler, runner or disassembler.

### Augmenting the Build Output

XTC follow Gradle best practise, and you can run the build, or any task therein, with the standard
verbosity flags. For example, to run the build with more verbose output, use:

```
./gradlew build --info --stacktrace
```

The build also supports Gradle build scans, which can be generated with:

```
./gradlew build --scan --stacktrace
```

Note that build scans are published to the Gradle online build scan repository (as configured
through the `gradle-enterprise` settings plugin.), so make sure that you aren't logging any
secrets, and avoid publishing build scans in "--debug" mode, as it may be a potential security
hazard.

You can also combine the above flags, and use all other standard Gradle flags, like `--stacktrace`,
and so on.

### Tasks

To see the list of available tasks for the XDK build, use:

```
./gradlew tasks
```

---

## Versioning and Publishing XDK Artifacts

* Use `publishLocal` to publish an XDK build to the local Maven repository only.
* Use `publish` to publish to both local Maven and remote repositories (GitHub Packages, optionally Maven Central and Gradle Plugin Portal). A GitHub token with permissions is required for remote publishing.
* For release versions (without -SNAPSHOT suffix), you must use `publish -PallowRelease=true` to prevent accidental releases.

*Note*: Some publish tasks may have race conditions due to parallel execution. If you encounter publishing errors:

```bash
./gradlew clean
./gradlew publishTask --no-parallel
```

Remember to run `clean` and the `publish` task separately due to our composite build architecture.

The group and version of the current XDK build and the XTC Plugin are currently defined in
the properties file "version.properties". Here, we define the version of the current XDK
and XTC Plugin, as well as their group. The default behavior is to only define the XDK, since
at this point, the Plugin, while decoupled, tracks and maps to the XDK version pretty much 1-1.
This can be taken apart with different semantic versioning, should we need to. Nothing is assuming
the plugin has the same version or group as the XDK. It's just convenient for time being.

The file `gradle/libs.versions.toml` contains all internal and external by-artifact version
dependencies to the XDK project. If you need to add a new plugin, library, or bundle, always define
its details in this version catalog, and nowhere else. The XDK build logic, will dynamically plug in
in values for the XDK and XTC Plugin artifacts that will be used only as references outside this file.

*TODO*: In the future we will also support tagging and publishing releases on GitHub, using JReleaser or a
similar framework.

Typically, the project version of anything that is unreleased should be "x.y.z-SNAPSHOT", and the first
action after tagging and uploading a release of the XDK, is usually changing the release version in
"VERSION" in the xvm repository root, and (if the plugin is versioned separately, optionally in "plugin/VERSION")
both by incrementing the micro version, and by adding a SNAPSHOT suffix. You  will likely find yourself
working in branches that use SNAPSHOT versions until they have made it into a release train. The CI/CD
pipeline can very likely handle this automatically.

---

## Bleeding Edge for Developers

If you would like to contribute to the Ecstasy Project, use the latest development version by building and installing locally:

```
./gradlew xdk:installDist
```

*Note*: this would be done after installing the XDK via `brew`, or through any other installation
utility, depending on your platform. This will overwrite several libraries and files in any
local installation.

For more information about the XTC DSL, please see the README.md file in the "plugin" project.

---

## Releasing and Publishing

This is mostly relevant to the XDK development team with release management privileges. A version
of the workflow for adding XTC releases is described [here](https://www.baeldung.com/maven-snapshot-release-repository).

We plan to move to an automatic release model in the very near future, utilizing JRelease
(and JPackage to generate our binary launchers). As an XTC/XDK developer, you do not have
to understand all the details of the release model. The somewhat incomplete and rather
manual release mode is current described here for completeness. It will soon be replaced
with something familiar.

### XDK Platform Releases

1) Take the current version of master and create a release branch.
2) Set the VERSION in the release branch project root to reflect the version of the release.
   Typically an ongoing development branch will be a "-SNAPSHOT" suffixed release, but not
   an official XTC release, which just has a group:name:version number
3) Build, tag and add the release using the GitHub release plugin.

### XDK Platform Publishing

We have verified credentials for artifacts with the group "org.xtclang" at the best known
community portals, and will start publishing there, as soon as we have an industrial
strength release model completed.

The current semi-manual process looks like this:

1) ./gradlew publish to build the artifacts and verify they work. This will publish the artifacts
   to a local repositories and the XTC GitHub org repository.
2) To publish the plugin to Gradle Plugin Portal: ./gradlew :plugin:publishPlugins (publish the plugin to gradlePortal)
3) To publish the XDK distro to Maven Central: (... TODO ... )

You can already refer to the XDK and the XTC Plugin as external artifacts for your favourite
XTC project, either by manually setting up a link to the XTC Org GitHub Maven Repository like this:

```
repositories {
   maven {
     url = https://maven.pkg.github.com/xtclang/xvm
     credentials {
        username = <your github user name>
        token = <a personal access token with read:package privileges on GitHub Maven Packages>
   }
}
```

or by simply publishing the XDK and XDK Plugin to your mavenLocal repository, and adding
that to the configuration of your XTC project, if it's not there already:

```
repositories {
   mavenLocal()
}
```

---

## Appendix: Gradle Fundamentals

This project follows industry-standard Gradle/Maven conventions. The build is designed to "just work" - clone and run `./gradlew build`.

**For developers new to Gradle**, we've documented the essential concepts you'll need:

**[Assumed Gradle Knowledge](assumed-gradle-knowledge.md)** - Covers the build lifecycle, source sets, artifacts, repositories, and the Gradle wrapper.

Key points:
- Always use `./gradlew` (the wrapper), never a system-installed `gradle`
- The build uses standard Maven artifact conventions (`group:name:version`)
- Gradle's `clean` is different from `make clean` - you rarely need it
