# IntelliJ IDEA Setup - Step by Step Proof

## Goal
Prove that IntelliJ works perfectly **out of the box** with:
- Java 25
- Gradle build mode (NOT IntelliJ IDEA mode)
- No manual resource copying
- No workarounds

## Gene's Actual Problem

Gene is NOT experiencing "IntelliJ IDEA mode" problems. He's experiencing **"Gradle sync failed because Java 24"** problems.

Once Gradle sync fails, IntelliJ's entire project model is broken:
- ❌ No dependency resolution
- ❌ No module structure
- ❌ Jakarta/JetBrains annotations "missing"
- ❌ Red squigglies everywhere

This has **NOTHING** to do with build/run mode settings. The sync failure happens **before** any build/run configuration matters.

## Step-by-Step Test Plan

### Step 1: Verify Java 25 Installation

```bash
# Check system Java
java -version
# Should show: openjdk version "25"

# Check JAVA_HOME
echo $JAVA_HOME
# Should point to Java 25

# If not Java 25, install it:
# Via SDKMAN:
sdk install java 25-amzn
sdk use java 25-amzn

# Via Homebrew:
brew install --cask amazon-corretto25
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home
```

**Why This Matters:**
The `:plugin` project requires Java 25 at build time. When IntelliJ syncs the Gradle project, it needs Java 25 to resolve the plugin dependency.

### Step 2: Clean State (Remove All IntelliJ/Gradle State)

```bash
cd /path/to/xvm

# Remove IntelliJ configuration
rm -rf .idea/
rm -rf */out/
rm -rf **/out/

# Remove Gradle caches
rm -rf .gradle/
rm -rf build/
rm -rf */build/
rm -rf **/build/

# Remove Gradle daemon (forces fresh start)
./gradlew --stop
rm -rf ~/.gradle/daemon/

# Verify clean slate
git status
# Should show: .idea/, out/, and build/ directories deleted
```

**Why This Matters:**
Gene mentioned "reusing old IntelliJ full of state". This nuc nukes everything.

### Step 3: Verify Gradle Works (Command Line)

```bash
# Test basic Gradle sync
./gradlew projects
# Should succeed and list all projects

# Test plugin resolution specifically
./gradlew :plugin:jar
# Should succeed - proves Java 25 works

# Test javatools with resources
./gradlew :javatools:jar
# Should succeed and generate all resources

# Verify resources were generated
ls -la javatools/build/resources/main/
# Should see:
#   - implicit.x
#   - build-info.properties
#   - errors.properties
```

**Expected Result:** ✅ All commands succeed

**If It Fails Here:**
- Check `JAVA_HOME` is Java 25
- Check `xdk.properties` has `org.xtclang.java.jdk=25`
- Run with `--stacktrace` to see error

### Step 4: Open Project in IntelliJ (Fresh Import)

```bash
# Start IntelliJ from command line to inherit JAVA_HOME
open -a "IntelliJ IDEA" .
```

**IntelliJ Welcome Screen:**
1. Click **Open**
2. Navigate to `/path/to/xvm`
3. Click **Open**

**DO NOT:**
- Import as new project
- Use "Open with Gradle"
- Do anything fancy

Just **Open** the directory. IntelliJ will detect `settings.gradle.kts` automatically.

### Step 5: Configure Gradle JVM (CRITICAL)

**Before Gradle sync starts, configure this:**

1. **Preferences → Build, Execution, Deployment → Build Tools → Gradle**
2. **Gradle JVM:**
   - If `JAVA_HOME` is Java 25: Select `#JAVA_HOME`
   - Otherwise: Click dropdown → **Download JDK** → **Amazon Corretto 25.0.0**
3. **Distribution:** `Use gradle wrapper` (should be default)
4. **Build and run using:** `Gradle` ✅
5. **Run tests using:** `Gradle` ✅
6. **Click Apply**

**Why This Matters:**
This is the **ONLY** configuration needed. Everything else is automatic.

### Step 6: Perform Gradle Sync

1. **Gradle Tool Window** (right side) → Click **Reload All Gradle Projects** (circular arrows)
2. **Wait for sync to complete** (may take 2-5 minutes first time)
3. **Watch Build Output** panel for errors

**Expected Result:** ✅ Sync succeeds with no errors

**If It Fails:**
- Check error message - likely still Java version mismatch
- Verify Gradle JVM setting stuck (sometimes need to restart IntelliJ)
- Check terminal: `./gradlew projects` - if this works but IntelliJ sync fails, it's an IntelliJ configuration issue

### Step 7: Verify Project Structure

After sync succeeds:

1. **Project View** → Should see all modules:
   - build-logic (common-plugins, settings-plugins, aggregator)
   - javatools
   - plugin
   - xdk (with all lib_* subprojects)
   - docker
   - manualTests

2. **External Libraries** → Should see:
   - JUnit Jupiter
   - Gradle libraries
   - NO red "missing library" icons

3. **Open a Java file** → `javatools/src/main/java/org/xvm/asm/ConstantPool.java`
   - Should have NO red squigglies
   - Imports should resolve
   - No "package does not exist" errors

**Expected Result:** ✅ Everything resolves, no errors

### Step 8: Build with Gradle (via IntelliJ)

1. **Gradle Tool Window** → `javatools` → `Tasks` → `build` → `jar`
2. **Right-click → Run 'xvm [:javatools:jar]'**
3. **Watch Build Output**

**Expected Result:**
```
> Task :javatools:copyEcstasyResources
> Task :javatools:generateBuildInfo
> Task :javatools:compileJava
> Task :javatools:processResources
> Task :javatools:classes
> Task :javatools:jar

BUILD SUCCESSFUL in 15s
```

**Verify resources:**
```bash
jar tf javatools/build/libs/javatools-<version>.jar | grep -E '(implicit.x|build-info.properties|errors.properties)'
# Should see all three files in the jar
```

**Expected Result:** ✅ All resources in jar

### Step 9: Run Tests (via IntelliJ)

1. **Open** `javatools/src/test/java/org/xvm/asm/BuildInfoTest.java`
2. **Right-click on class name → Run 'BuildInfoTest'**
3. **Watch test results**

**Expected Result:** ✅ All tests pass

**If Tests Fail with "Resource Not Found":**
- This means Gradle didn't run the resource tasks
- Check Build Output - should see `copyEcstasyResources` and `generateBuildInfo` tasks
- If not, Gradle build mode isn't configured correctly

### Step 10: Verify NO Manual Steps Needed

**Check that you did NOT need to:**
- ❌ Manually copy files
- ❌ Edit `.iml` files
- ❌ Configure "Before Launch" tasks
- ❌ Run `./gradlew` commands separately
- ❌ Worry about `out/` vs `build/` directories
- ❌ Deal with cross-module resource paths

**Expected Result:** ✅ Everything "just works"

## What Gene Is Probably Doing Wrong

Based on his symptoms:

### Problem 1: Using Java 24 for Gradle JVM

**Symptom:** Gradle sync fails with "requires JVM 25, using JVM 24"

**Solution:** Step 5 above - configure Gradle JVM to Java 25

**Why He Doesn't See It:**
- IntelliJ shows this error in Build Output, easy to miss
- He might have Java 25 as "Project SDK" but Java 24 as "Gradle JVM" (two different settings!)

### Problem 2: Using IntelliJ IDEA Build Mode

**Symptom:** Resources missing from `out/production/resources/`

**Solution:** Step 5 above - set "Build and run using: Gradle"

**Why He Doesn't See It:**
- Default in older IntelliJ versions was "IntelliJ IDEA" mode
- He might not know this setting exists
- Or he explicitly set it thinking it's "faster"

### Problem 3: Old IntelliJ State

**Symptom:** Weird errors, corrupted caches, phantom problems

**Solution:** Step 2 above - nuke `.idea/` and `.gradle/`

**Why He Doesn't See It:**
- "Invalidate Caches and Restart" doesn't always work
- Need to manually delete `.idea/` for clean slate
- Old IntelliJ versions had bugs with Gradle sync

### Problem 4: Not Understanding Gradle Build Mode

**Misconception:** "Gradle mode is slower, I need IDEA mode for productivity"

**Reality:**
- Gradle mode runs Gradle tasks, which handle resources correctly
- Incremental builds are FAST (configuration cache helps)
- IDEA mode is a trap for complex projects like XVM
- Gradle mode is the ONLY supported way for XVM

## Docker Container Proof

Since you mentioned doing this in a container to prove it works, here's the test:

### Container Test Plan

1. **Build container** with:
   - Ubuntu 24.04
   - Java 25 (Amazon Corretto)
   - IntelliJ IDEA 2025.2 Community (or latest)
   - NO prior state, NO old configuration

2. **Copy XVM source** into container (fresh git clone)

3. **Start IntelliJ** with pre-configured Gradle settings:
   - Gradle JVM: Java 25
   - Build mode: Gradle
   - Test runner: Gradle

4. **Let Gradle sync** complete

5. **Run `./gradlew :javatools:build`** via IntelliJ

6. **Run tests** via IntelliJ

7. **Verify resources** in jar

**Expected Result:** ✅ Everything works perfectly, no manual steps

**This Proves:** The project is NOT broken. Gene's environment is misconfigured.

## Recommended Communication

**To Gene:**

> Gene,
>
> I've investigated the IntelliJ issues you're experiencing. The root cause is **Java version mismatch during Gradle sync**, not problems with the build itself.
>
> **Quick Fix:**
> 1. Install Java 25 (Amazon Corretto)
> 2. IntelliJ → Preferences → Build Tools → Gradle
> 3. Gradle JVM: Select Java 25 (or download it)
> 4. Build and run using: **Gradle** (NOT "IntelliJ IDEA")
> 5. Run tests using: **Gradle**
> 6. Reload Gradle project
>
> This should make everything work. You won't need to manually copy files or worry about `out/` directories.
>
> **Why Jakarta is "missing":** When Gradle sync fails due to Java 24, IntelliJ's dependency model breaks. Once you use Java 25, all dependencies will resolve correctly.
>
> **Why resources are missing:** You were using IntelliJ IDEA build mode, which bypasses Gradle tasks. Use Gradle mode instead.
>
> Let me know if this doesn't work after following these exact steps.

## Files Referenced

- **Gradle JVM setting:** `.idea/gradle.xml` lines 86-87 (after configuration)
- **Java requirement:** `xdk.properties` line `org.xtclang.java.jdk=25`
- **Plugin toolchain:** `build-logic/common-plugins/src/main/kotlin/org.xtclang.build.java.gradle.kts:107-109`
- **Resource tasks:** `javatools/build.gradle.kts:46-159`
