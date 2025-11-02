# IntelliJ IDEA Build Issues - Root Cause Analysis

## Summary

Gene and others are experiencing catastrophic IntelliJ IDEA failures after syncing with master. The issues stem from a fundamental mismatch between how Gradle and IntelliJ IDEA handle cross-module resources and Java toolchain requirements.

## Core Problems Identified

### 1. Missing Resources in IntelliJ Build Mode ⚠️ CRITICAL

**Problem:** When building with IntelliJ IDEA (not Gradle), three critical resource files are missing:
- `implicit.x` (from lib_ecstasy)
- `build-info.properties` (generated at build time)
- `errors.properties` (exists but may not be copied correctly)

**Root Cause:**
IntelliJ's "Make" operation **bypasses Gradle tasks entirely**. The javatools project relies on custom Gradle tasks:

```kotlin
// javatools/build.gradle.kts:46-50
/**
 * INTELLIJ FIX: Copy resources at build time instead of referencing them directly.
 *
 * Instead of adding lib_ecstasy resources as a source directory (which confuses IntelliJ),
 * we copy them to our build directory and include that.
 */
val copyEcstasyResources by tasks.registering(Copy::class) {
    from(xdkEcstasyResourcesConsumer)  // Cross-module dependency
    into(layout.buildDirectory.dir("generated/resources/main"))
}

val generateBuildInfo by tasks.registering(GenerateBuildInfo::class) {
    // Generates build-info.properties dynamically
}
```

**Why It Fails:**
- IntelliJ compiles to `out/production/classes/` and `out/production/resources/`
- Gradle builds to `build/classes/` and `build/resources/`
- Custom Gradle tasks that copy/generate resources never run in IDEA mode
- Missing `implicit.x` causes `ConstantPool` static initialization to fail silently
- Missing `build-info.properties` causes version info to be unavailable

**File Locations:**
- Source: `/lib_ecstasy/src/main/resources/implicit.x`
- Gradle copies to: `/javatools/build/generated/resources/main/implicit.x`
- IntelliJ expects at: `/javatools/out/production/resources/implicit.x`
- **Gap:** IntelliJ never runs `copyEcstasyResources` task

### 2. Java Version Mismatch - Plugin Requires JDK 25 ⚠️ CRITICAL

**Error Message:**
```
Could not resolve project :plugin.
Required by: buildscript of project ':xdk'
> Dependency requires at least JVM runtime version 25. This build uses a Java 24 JVM.
```

**Root Cause:**
The `plugin` project is configured to require Java 25 via the toolchain:

```kotlin
// build-logic/common-plugins/.../org.xtclang.build.java.gradle.kts:107-109
java {
    toolchain.languageVersion.set(jdkVersion.map { JavaLanguageVersion.of(it) })
}
```

Where `jdkVersion` comes from `xdk.properties`:
```
org.xtclang.java.jdk=25
```

**The Problem:**
- When IntelliJ syncs the Gradle project, it uses its **configured Gradle JVM**
- If that JVM is Java 24 (or IntelliJ's bundled JDK), the sync fails
- The `:plugin` project is compiled with Java 25 toolchain
- When `:xdk` tries to use `:plugin` in its buildscript classpath, Gradle checks runtime compatibility
- Java 24 < Java 25 → **BOOM**

**Why This Recently Broke:**
Likely a recent commit bumped the JDK requirement to 25, but Gene's IntelliJ is still configured to use Java 24 for Gradle operations.

### 3. Jakarta Library Missing

**Error:** `package org.jetbrains.annotations does not exist`

**Likely Cause:**
This is a dependency resolution issue during IntelliJ sync. The `org.jetbrains.annotations` package comes from:
```groovy
// Common test dependency
testImplementation("org.jetbrains:annotations:...")
```

When the Gradle sync fails (due to Java version mismatch), IntelliJ's dependency model becomes corrupted, causing:
- Missing library references
- Unresolved imports
- Red squigglies everywhere

###4. Broken Symlink (Non-Issue)

**Reported:** `./gradle` directory contains a broken link to `~/.gradle/gradle.properties`

**Analysis:** This is **NOT** an actual problem:
```bash
$ ls -la gradle/
drwxr-xr-x   4 marcus  staff   128 Nov  2 11:50 .
├── libs.versions.toml  ✅
└── wrapper/            ✅
```

The `gradle/` directory is for:
- Version catalogs (`libs.versions.toml`)
- Gradle wrapper configuration

There's NO symlink to `~/.gradle/gradle.properties` in the actual directory structure. This might be a misunderstanding or IntelliJ showing confusing metadata.

## Why Gene's Workaround Works

Gene's workaround:
1. Hard reset to older commit
2. Invalidate IDEA caches
3. Ignore `org.jetbrains.annotations` error during rebuild
4. Build with Gradle in terminal: `./gradlew build`
5. **Manually copy** resources from `build/resources/main/` to `out/production/classes/`

**Why It Works:**
- Step 4 runs Gradle, which executes `copyEcstasyResources` and `generateBuildInfo` tasks
- Step 5 manually syncs Gradle's output to IntelliJ's expected location
- This bypasses IntelliJ's broken resource handling

**Why It's Terrible:**
- Manual file copying after every build
- Can't sync with master (Java version issue persists)
- Fragile and error-prone workflow

## Recommended Solutions

### Solution 1: Configure IntelliJ's Gradle JVM (IMMEDIATE FIX)

**For Gene and others having sync failures:**

1. **Open IntelliJ IDEA**
2. **Preferences → Build, Execution, Deployment → Build Tools → Gradle**
3. **Gradle JVM:** Change from "Project SDK" or "#JAVA_HOME" to:
   - `/Users/[username]/.sdkman/candidates/java/25-amzn` (if using SDKMAN)
   - Or select **Download JDK → Amazon Corretto 25**
4. **Click Apply**
5. **Gradle tool window → Reload All Gradle Projects** (circular arrows)

This ensures IntelliJ uses Java 25 for Gradle sync operations, satisfying the `:plugin` toolchain requirement.

### Solution 2: Use Gradle for Building (RECOMMENDED)

**Stop using IntelliJ IDEA build mode entirely:**

1. **Preferences → Build, Execution, Deployment → Build Tools → Gradle**
2. **Build and run using:** → `Gradle` (NOT `IntelliJ IDEA`)
3. **Run tests using:** → `Gradle` (NOT `IntelliJ IDEA`)

**Trade-offs:**
- ✅ All resources copied correctly (Gradle tasks run)
- ✅ Build works identically to command line
- ✅ No manual file copying needed
- ✅ Configuration cache benefits
- ❌ Slightly slower initial builds (incremental is fine)
- ❌ No "instant" compilation feedback

### Solution 3: Fix IntelliJ Resource Handling (PROPER FIX)

**Make IntelliJ aware of generated resources:**

The javatools `build.gradle.kts` already tries to handle this (lines 46-50), but IntelliJ may not be picking it up correctly.

**Option A: Use IntelliJ Resource Directories**

Add IntelliJ-specific configuration to `.idea/modules/javatools.iml`:

```xml
<component name="NewModuleRootManager">
  <content url="file://$MODULE_DIR$/../../javatools">
    <sourceFolder url="file://$MODULE_DIR$/../../javatools/build/generated/resources/main" type="java-resource" generated="true" />
  </content>
</component>
```

**Problem:** `.iml` files are auto-generated and overwritten on Gradle sync.

**Option B: Gradle IDEA Plugin Configuration**

Add to `javatools/build.gradle.kts`:

```kotlin
plugins {
    idea
}

idea {
    module {
        // Mark generated resources directory
        generatedSourceDirs.add(file("build/generated/resources/main"))
        // Ensure IntelliJ runs these tasks before compile
        val copyEcstasyResources by tasks.existing
        val generateBuildInfo by tasks.existing
        project.tasks.named("ideaModule").configure {
            dependsOn(copyEcstasyResources, generateBuildInfo)
        }
    }
}
```

**Problem:** This only helps if IntelliJ delegates resource generation to Gradle, which it doesn't in IDEA mode.

**Option C: Pre-Build Tasks in IntelliJ**

Configure IntelliJ to run Gradle tasks before every build:

1. **Run → Edit Configurations → Edit Configuration Templates → Application**
2. **Before launch:** Click `+` → **Run Gradle Task**
3. Add tasks: `:javatools:copyEcstasyResources` and `:javatools:generateBuildInfo`
4. Apply to all run configurations

**Problem:** Tedious to configure, fragile, not team-wide.

### Solution 4: Commit Generated Resources (ANTI-PATTERN)

**DON'T DO THIS**, but it would "work":

```bash
cd javatools
./gradlew copyEcstasyResources generateBuildInfo
git add src/main/resources/implicit.x
git add src/main/resources/build-info.properties
git commit -m "Check in generated resources for IntelliJ"
```

**Why This Is Terrible:**
- Generated files in version control
- Merge conflicts on every build
- Stale build info
- Violates build reproducibility

## What Changed Recently?

Based on the symptoms, likely one of these recent changes:

1. **JDK requirement bumped to 25** in `xdk.properties` or `build-logic`
2. **Dependency updates** that pulled in newer Jakarta/JetBrains annotations requiring Java 25
3. **Plugin project changes** that increased its JVM target to 25
4. **Gradle version update** with stricter toolchain enforcement

Check recent commits to `xdk.properties` or `gradle/libs.versions.toml`:

```bash
git log --oneline -20 --all -- xdk.properties gradle/libs.versions.toml
```

## Verification Steps

### For Gene (or anyone with the issue):

1. **Check your JAVA_HOME:**
   ```bash
   echo $JAVA_HOME
   java -version
   ```
   Must be Java 25.

2. **Check IntelliJ's Gradle JVM:**
   - Preferences → Build, Execution, Deployment → Build Tools → Gradle
   - Gradle JVM: Should show Java 25

3. **Check project-level SDK:**
   - File → Project Structure → Project
   - SDK: Should be Java 25

4. **Clean everything:**
   ```bash
   ./gradlew clean
   rm -rf .gradle/ .idea/ out/
   ```

5. **Re-import project:**
   - File → Close Project
   - Open → Select `/path/to/xvm`
   - Let it reimport

6. **Verify Gradle sync succeeds:**
   - Gradle tool window → Reload All Gradle Projects
   - Should complete without Java version errors

7. **Build with Gradle:**
   ```bash
   ./gradlew javatools:build
   ```

8. **Verify resources exist:**
   ```bash
   ls -la javatools/build/resources/main/
   # Should see: implicit.x, build-info.properties, errors.properties
   ```

9. **If using IntelliJ mode, manually sync once:**
   ```bash
   cp javatools/build/resources/main/*.{x,properties} javatools/out/production/resources/
   ```

## Long-Term Recommendation

**Team Decision Needed:**

**Option A:** Everyone uses Gradle build mode
- Pro: Consistent, reliable, no workarounds
- Con: Slightly slower, less "IDE-native"

**Option B:** Everyone uses IntelliJ mode with manual sync script
- Pro: Faster incremental compilation
- Con: Requires custom sync script after builds

**Option C:** Fix IntelliJ's resource handling via Gradle IDEA plugin
- Pro: Best of both worlds
- Con: Requires investigation and testing

**My Recommendation:** **Option A** (Gradle build mode). The XVM project uses:
- Composite builds
- Custom Gradle plugins
- Complex resource generation
- Configuration cache

These advanced features don't play nicely with IntelliJ's internal build system. Using Gradle everywhere ensures consistency.

## Immediate Action Items

1. **Document required Java version** in `README.md`:
   ```markdown
   ## Requirements
   - **Java 25** (Amazon Corretto recommended)
   - Gradle 9.1.0+ (via wrapper)
   ```

2. **Add version check** to root `settings.gradle.kts`:
   ```kotlin
   val requiredJavaVersion = 25
   val currentJavaVersion = JavaVersion.current().majorVersion.toInt()
   if (currentJavaVersion < requiredJavaVersion) {
       throw GradleException("This build requires Java $requiredJavaVersion or later. Current: $currentJavaVersion")
   }
   ```

3. **Update `CLAUDE.md`** with IntelliJ configuration instructions:
   ```markdown
   ## IntelliJ IDEA Configuration

   ### Required JDK
   - Install Java 25 (Amazon Corretto)
   - Set JAVA_HOME to Java 25

   ### Gradle Settings
   - Preferences → Build, Execution, Deployment → Build Tools → Gradle
   - Gradle JVM: Select Java 25 (or #JAVA_HOME if set to Java 25)
   - Build and run using: Gradle (recommended)
   - Run tests using: Gradle (recommended)
   ```

4. **Create helper script** `scripts/sync-idea-resources.sh`:
   ```bash
   #!/bin/bash
   # Sync Gradle-built resources to IntelliJ output directory
   # Only needed if using IntelliJ build mode

   ./gradlew javatools:copyEcstasyResources javatools:generateBuildInfo

   mkdir -p javatools/out/production/resources
   cp javatools/build/resources/main/* javatools/out/production/resources/

   echo "✅ Resources synced to IntelliJ output directory"
   ```

## Files Referenced

- `/javatools/build.gradle.kts` - Resource generation tasks
- `/build-logic/common-plugins/src/main/kotlin/org.xtclang.build.java.gradle.kts` - Java toolchain config
- `/plugin/build.gradle.kts` - Plugin build (requires Java 25)
- `/xdk.properties` - JDK version setting
- `/javatools/src/main/java/org/xvm/asm/ConstantPool.java:3709` - Loads `implicit.x`
- `/javatools/src/main/java/org/xvm/asm/BuildInfo.java` - Loads `build-info.properties`
- `/.idea/gradle.xml:86-87` - IntelliJ Gradle configuration
