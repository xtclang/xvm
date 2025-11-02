# IntelliJ IDEA Configuration Documentation

This directory contains documentation for IntelliJ IDEA setup and configuration for the XVM project.

## Quick Start

If you're experiencing IntelliJ issues (wrong Java version, missing resources, toolchain conflicts):

1. **Ensure Java 25 is installed** and `JAVA_HOME` points to it
2. **Run:** `./gradlew idea` to regenerate IntelliJ configuration
3. **Restart IntelliJ**
4. **Reload Gradle Project** (Gradle tool window → circular arrows)

## Files in This Directory

### Setup Guides
- **[INTELLIJ-SETUP-STEPS.md](INTELLIJ-SETUP-STEPS.md)** - Step-by-step setup instructions with verification
- **[INTELLIJ-JAVA-VERSION-FIX.md](INTELLIJ-JAVA-VERSION-FIX.md)** - Explanation of Java 6 default issue and fix
- **[INTELLIJ-ISSUES-ANALYSIS.md](INTELLIJ-ISSUES-ANALYSIS.md)** - Deep dive into all IntelliJ/Gradle issues

### Test Script
- **[test-intellij-setup.sh](../test-intellij-setup.sh)** - Automated verification that setup is correct

## Common Issues

### "Toolchain from executable property does not match javaLauncher"
**Fix:** Ensure all IntelliJ Java settings point to Java 25:
- File → Project Structure → Project → SDK: Java 25
- Preferences → Gradle → Gradle JVM: Java 25

### "implicit.x not found" or "build-info.properties missing"
**Fix:** Enable Gradle build mode:
- Preferences → Gradle → Build and run using: **Gradle** (not IntelliJ IDEA)

### "Package org.jetbrains.annotations does not exist"
**Fix:** This happens when Gradle sync fails. Check Build Output for sync errors. Usually a Java version issue.

## Project Configuration

The XVM project uses:
- **Java 25** (required - set in `xdk.properties`)
- **Gradle 9.1.0+** (via wrapper)
- **Gradle build mode** (delegatedBuild=true in `.idea/gradle.xml`)
- **Custom resource generation** (implicit.x, build-info.properties)
- **Composite builds** (build-logic, plugin, xdk, docker, etc.)

## Why These Settings Are Committed

The `.idea/` directory is mostly gitignored, but these files are committed:
- `misc.xml` - Java 25 language level
- `compiler.xml` - Java 25 bytecode target
- `gradle.xml` - Gradle build mode configuration
- `vcs.xml` - Git VCS settings

**Reason:** Without these, IntelliJ defaults to Java 6 when Gradle sync fails, causing massive confusion for new developers.

## Docker Testing (Optional)

If you want to test IntelliJ in a clean environment:

```bash
cd docker/idea
./run-intellij-rdp.sh
```

Connect with Microsoft Remote Desktop to `localhost:3389` (user: developer, pass: developer)

See `docker/idea/INTELLIJ-DOCKER.md` for details.

## Automated Testing

Run the verification script to check your setup:

```bash
./.idea/test-intellij-setup.sh
```

This checks:
- Java 25 is installed
- Gradle wrapper works
- Resources are generated correctly
- IntelliJ configuration is correct

## Contributing

If you make changes to IntelliJ configuration:

1. Test with `./gradlew idea` to regenerate files
2. Verify IntelliJ can import the project cleanly
3. Run `./.idea/test-intellij-setup.sh` to verify
4. Document changes in this directory

## Questions?

If IntelliJ still doesn't work after following these guides, check:
- Build output for Gradle sync errors
- Java version: `java -version` and `echo $JAVA_HOME`
- Gradle JVM setting in IntelliJ preferences
- Ask in team chat or file an issue
