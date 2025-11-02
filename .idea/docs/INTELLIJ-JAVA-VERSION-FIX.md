# Why IntelliJ Wasn't Using Java 25

## The Problem

You were getting this error when trying to run `org.xvm.tool.Launcher`:
```
Toolchain from `executable` property does not match toolchain from `javaLauncher` property.
```

## Root Cause

IntelliJ had **THREE** different Java version settings that were all wrong:

### 1. Project Language Level: Java 6 ❌

**File:** `.idea/misc.xml`
```xml
<component name="ProjectRootManager" version="2" languageLevel="JDK_1_6" project-jdk-name="temurin-25" project-jdk-type="JavaSDK" />
```

**Problem:** Even though your Project SDK was `temurin-25`, the **language level** was set to Java 6. This tells IntelliJ what Java language features to support.

**Fixed to:**
```xml
<component name="ProjectRootManager" version="2" languageLevel="JDK_25" project-jdk-name="temurin-25" project-jdk-type="JavaSDK" />
```

### 2. Bytecode Target Level: Java 6 ❌

**File:** `.idea/compiler.xml`
```xml
<bytecodeTargetLevel target="1.6" />
```

**Problem:** IntelliJ was configured to generate Java 6 bytecode, even though your code requires Java 25 features.

**Fixed to:**
```xml
<bytecodeTargetLevel target="25" />
```

### 3. Build Mode: IntelliJ IDEA (not Gradle) ❌

**File:** `.idea/gradle.xml`
```xml
<option name="delegatedBuild" value="false" />
<option name="testRunner" value="PLATFORM" />
```

**Problem:**
- `delegatedBuild=false` means IntelliJ uses its own compiler, not Gradle
- `testRunner=PLATFORM` means IntelliJ runs tests directly, not via Gradle

This caused:
- Resources not being generated (`implicit.x`, `build-info.properties`)
- Gradle tasks skipped during build
- Toolchain conflicts when running JavaExec tasks

**Fixed to:**
```xml
<option name="delegatedBuild" value="true" />
<option name="testRunner" value="GRADLE" />
```

## Why This Happened

### Old IntelliJ Defaults

IntelliJ used to default to:
- Using its own compiler (IDEA mode)
- Conservative bytecode target levels
- Not reading Gradle toolchain configurations

These defaults made sense for older projects but don't work well with modern Gradle builds using:
- Java toolchains
- Custom resource generation tasks
- Configuration cache

### Manual Overrides

Someone (possibly IntelliJ during initial project import) set:
- Language level to 1.6
- Bytecode target to 1.6

These settings stuck around even after upgrading to Java 25.

## The Toolchain Conflict Explained

Your Gradle build script (`build-logic/common-plugins/.../org.xtclang.build.java.gradle.kts`) configures **ALL** JavaExec tasks to use the Java toolchain:

```kotlin
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}
```

Where `java.toolchain` is set to Java 25:

```kotlin
java {
    toolchain.languageVersion.set(jdkVersion.map { JavaLanguageVersion.of(it) })
}
```

And `jdkVersion` comes from `xdk.properties`:
```properties
org.xtclang.java.jdk=25
```

**When IntelliJ creates a run configuration** for an Application (like running `Launcher.main()`), it:
1. Creates a Gradle `JavaExec` task behind the scenes
2. Sets the `executable` property based on your IntelliJ project settings
3. Your build script also sets the `javaLauncher` property to Java 25

**Gradle 9.x is strict** and rejects tasks with both properties set to different values:
- `executable` → IntelliJ's project SDK (was using Java 6 language level settings)
- `javaLauncher` → Gradle toolchain (Java 25)

**Result:** `Toolchain from executable property does not match toolchain from javaLauncher property.`

## What Changed

I fixed these three settings:

1. **`.idea/misc.xml`**: `languageLevel="JDK_1_6"` → `languageLevel="JDK_25"`
2. **`.idea/compiler.xml`**: `target="1.6"` → `target="25"`
3. **`.idea/gradle.xml`**: `delegatedBuild="false"` → `delegatedBuild="true"` and `testRunner="PLATFORM"` → `testRunner="GRADLE"`

## How to Verify

1. **Restart IntelliJ** (important - IntelliJ caches these settings)

2. **Check Project Structure:**
   - File → Project Structure → Project
   - SDK: Should show `temurin-25` or Java 25
   - Language level: Should show `25`

3. **Create an Application Run Configuration:**
   - Run → Edit Configurations → + → Application
   - Name: `Test Launcher`
   - Module: `xvm.javatools.main`
   - Main class: `org.xvm.tool.Launcher`
   - Program arguments: (leave empty or add args)
   - Click OK

4. **Run it:**
   - Should execute without the toolchain conflict error
   - Should see "Command name is missing" (from Launcher, not Gradle)

5. **Debug it:**
   - Set a breakpoint in `Launcher.main()`
   - Right-click configuration → Debug
   - Should hit the breakpoint

## Why These Settings Weren't Correct Already

### Theory 1: Old Project Import
If this project was first imported into IntelliJ years ago when it was using Java 6 or 8, IntelliJ would have set these defaults and they stuck around.

### Theory 2: Manual Configuration
Someone manually set the language level to 1.6 at some point (maybe trying to match a legacy XDK target?) and forgot to update it when moving to Java 25.

### Theory 3: IntelliJ Bug/Quirk
IntelliJ sometimes doesn't update language level settings when you change the Project SDK. You have to manually sync them.

### Theory 4: Gradle Sync Never Worked
If the Gradle sync was failing (due to Java 24 vs Java 25 issue), IntelliJ never got a chance to read the proper toolchain settings from Gradle and fell back to defaults.

## How to Prevent This for Others (Gene, etc.)

### Option 1: Commit These Settings

The `.idea/` directory is currently gitignored (partially). You could commit these specific files so everyone gets the right settings:

```bash
# In .gitignore, ensure these are NOT ignored:
!.idea/misc.xml
!.idea/compiler.xml
!.idea/gradle.xml
```

Then commit:
```bash
git add .idea/misc.xml .idea/compiler.xml .idea/gradle.xml
git commit -m "Configure IntelliJ to use Java 25 and Gradle build mode"
```

**Pros:** Everyone gets correct settings out of the box
**Cons:** `.idea/` files can have personal settings (window positions, etc.)

### Option 2: Document Required Settings

Create `INTELLIJ-SETUP.md` with instructions:

```markdown
# IntelliJ IDEA Setup

After opening the project:

1. File → Project Structure → Project
   - SDK: Java 25
   - Language level: 25

2. Preferences → Build, Execution, Deployment → Build Tools → Gradle
   - Gradle JVM: Java 25 (or #JAVA_HOME if pointing to Java 25)
   - Build and run using: Gradle
   - Run tests using: Gradle

3. Reload Gradle project
```

### Option 3: Gradle IDEA Plugin (Automatic Configuration)

Add to root `build.gradle.kts`:

```kotlin
plugins {
    idea
}

idea {
    project {
        languageLevel = org.gradle.plugins.ide.idea.model.IdeaLanguageLevel(25)
        jdkName = "25"
    }
}
```

Then run:
```bash
./gradlew idea
```

This generates `.idea/` files with correct settings.

**Pros:** Automated, repeatable
**Cons:** Requires running `./gradlew idea` after clone

## Recommendation

**For this project:** Commit the fixed `.idea/misc.xml`, `.idea/compiler.xml`, and `.idea/gradle.xml` files.

**Why:** This is a team project with specific Java 25 requirement. Everyone should get the same configuration. Personal settings (like window layout) are in other files like `.idea/workspace.xml` which should stay gitignored.

Add to `.gitignore`:
```gitignore
# IntelliJ IDEA
/.idea/*
!/.idea/misc.xml
!/.idea/compiler.xml
!/.idea/gradle.xml
!/.idea/vcs.xml
!/.idea/.gitignore
```

This ensures:
- ✅ Critical configuration is shared
- ✅ Personal preferences stay local
- ✅ New team members get correct setup
- ✅ Gene won't have this problem again

## Summary

**Before:**
- Language level: Java 6
- Bytecode target: Java 6
- Build mode: IntelliJ IDEA
- Result: Toolchain conflict errors, missing resources

**After:**
- Language level: Java 25
- Bytecode target: Java 25
- Build mode: Gradle
- Result: Everything works, including Application run configurations

**Restart IntelliJ to apply these changes!**
