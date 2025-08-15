# Centralize Java Toolchain Configuration and Enable Automatic Invalidation

## Summary

This PR modernizes the build system's Java toolchain management by centralizing configuration, implementing automatic invalidation, and enabling flexible command-line overrides. The changes ensure consistent bytecode generation and eliminate the need for manual `clean` operations when switching JDK versions.

## Key Features

### 🎯 **Centralized Toolchain Configuration**
- **Single Source of Truth**: `org.xtclang.java.jdk` property in `xdk.properties` controls JDK version for entire build
- **Consistent Bytecode**: All Java classes compiled with the same bytecode version (no mixed versions)
- **Foojay Integration**: Automatic toolchain downloading via foojay resolver

### 🔄 **Automatic Invalidation System**
- **Smart Rebuild Detection**: Changing JDK version automatically triggers recompilation of affected Java code
- **No Manual Clean Required**: Gradle detects toolchain changes via task input tracking
- **Task Input Integration**: JDK version, preview features, and compilation settings registered as JavaCompile task inputs

### ⚡ **Flexible Configuration Options**

**Priority Order (highest to lowest):**
1. **Environment Variables**: `export ORG_XTCLANG_JAVA_JDK=24`
2. **Command Line Properties**: `./gradlew build -Porg.xtclang.java.jdk=24`
3. **Java System Properties**: `./gradlew build -Dorg.xtclang.java.jdk=24`
4. **Property Files**: `org.xtclang.java.jdk=21` in `xdk.properties`

### 🚀 **Smart Preview Features**
- **Auto-Enable for JDK 24+**: Preview features automatically enabled for modern JDK versions
- **Handles Deprecated APIs**: Prevents build failures from deprecated/unsafe operations
- **Backward Compatible**: JDK 21 and below use standard compilation

## Technical Implementation

### Build System Changes

#### 1. **Automatic Invalidation** (`org.xtclang.build.java.gradle.kts`)
```kotlin
tasks.withType<JavaCompile>().configureEach {
    // Declare toolchain and XDK properties as inputs for proper invalidation
    inputs.property("jdkVersion", jdkVersion)
    inputs.property("enablePreview", enablePreview())
    // ... other compilation properties
}
```

#### 2. **Command Line Support** (`XdkProperties.kt`)
```kotlin
// Check Gradle project properties (from -P command line args)
val projectPropValue = project.findProperty(key)
if (projectPropValue != null) {
    logger.info("$prefix XdkProperties; resolved Gradle project property '$key' (from -P flag).")
    return projectPropValue.toString()
}
```

#### 3. **Smart Preview Detection** (`org.xtclang.build.java.gradle.kts`)
```kotlin
private fun enablePreview(): Boolean {
    val jdkVersion = getXdkPropertyInt("$pprefix.jdk")
    // Enable preview features automatically for Java 24+ to handle deprecated/unsafe operations
    val enablePreview = jdkVersion >= 24 || getXdkPropertyBoolean("$pprefix.enablePreview")
    return enablePreview
}
```

### JAR Distribution Updates
- **Generic Version Stripping**: Updated JAR renaming pattern to support multiple JARs in distribution
- **Future-Ready**: Prepared for `javatools-jitbridge` module integration

## Usage Examples

### Default Usage (JDK 21)
```bash
# Uses org.xtclang.java.jdk=21 from xdk.properties
./gradlew build
# Result: All Java classes compiled with bytecode version 65 (Java 21)
```

### Testing with JDK 24
```bash
# Temporary override without editing files
./gradlew build -Porg.xtclang.java.jdk=24
# Result: All Java classes compiled with bytecode version 68 (Java 24)
#         Preview features auto-enabled
```

### Environment-based Configuration
```bash
# Set for entire session
export ORG_XTCLANG_JAVA_JDK=24
./gradlew build
# Result: Uses JDK 24 until environment variable is unset
```

### Full Distribution Build
```bash
# Build complete distribution with specific JDK
./gradlew installDist -Porg.xtclang.java.jdk=24
# Result: Entire XDK distribution built with JDK 24
```

## Verification and Testing

### Bytecode Verification
```bash
# Check compiled bytecode version
find ./javatools/build -name "*.class" | head -1 | xargs javap -verbose | grep "major version"
# JDK 21: major version: 65
# JDK 24: major version: 68
```

### Automatic Invalidation Test
```bash
# 1. Build with JDK 21
./gradlew :javatools:compileJava
# 2. Switch to JDK 24 (no clean needed)
./gradlew :javatools:compileJava -Porg.xtclang.java.jdk=24
# Result: Automatic recompilation with correct bytecode version
```

## Benefits

### For Developers
- **🔧 No Manual Cleanup**: Switch JDK versions without `gradlew clean`
- **⚡ Fast Iteration**: Quick testing with different JDK versions
- **🎯 Consistent Builds**: Guaranteed bytecode consistency across modules
- **📝 Clear Documentation**: Comprehensive usage examples and comments

### For CI/CD
- **🚀 Flexible Pipelines**: Easy JDK version matrix testing
- **🔒 Reliable Builds**: Automatic invalidation prevents stale artifacts
- **📊 Environment Control**: Override via environment variables
- **⚙️ Script-Friendly**: Command-line property support

### For Build System
- **🧹 Cleaner Architecture**: Centralized toolchain management
- **🔍 Better Debugging**: Verbose property resolution logging
- **🚦 Safe Defaults**: JDK 21 as stable baseline
- **🔮 Future-Ready**: Prepared for newer JDK versions

## Backward Compatibility

- **✅ Default Behavior**: No changes to existing workflows using JDK 21
- **✅ Property Locations**: All existing property files continue to work
- **✅ Build Scripts**: No changes required to existing build automation
- **✅ IDE Integration**: Full compatibility with IntelliJ IDEA and other IDEs

## Migration Guide

### Current Users
No action required - builds continue using JDK 21 by default.

### Advanced Users
```bash
# To permanently switch to JDK 24, edit xdk.properties:
org.xtclang.java.jdk=24

# For temporary testing:
./gradlew build -Porg.xtclang.java.jdk=24
```

## Files Modified

- `build-logic/common-plugins/src/main/kotlin/org.xtclang.build.java.gradle.kts` - Automatic invalidation
- `build-logic/common-plugins/src/main/kotlin/XdkProperties.kt` - Command line support  
- `xdk.properties` - Comprehensive documentation and defaults
- `xdk/build.gradle.kts` - Generic JAR version stripping

## Future Enhancements

- Support for additional JDK versions (25, 26+)
- Per-module JDK version overrides
- Build cache optimization for different JDK versions
- Integration with `javatools_jitbridge` module