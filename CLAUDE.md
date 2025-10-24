# Claude Configuration

## MOST IMPORTANT RULE: Gradle Task Execution

### NEVER Run Multiple Tasks in One Command

**FORBIDDEN - The aggregator plugin will reject these:**
```bash
./gradlew clean build                     # ❌ WILL FAIL
./gradlew build publishLocal              # ❌ WILL FAIL
./gradlew clean build publishLocal        # ❌ WILL FAIL
./gradlew build publishLocal -PallowMultipleTasks=true  # ❌ NEVER USE THIS FLAG UNLESS YOU KNOW THIS IS TESTED AND WORKS FOR THE JOBS INVOLVED
```

**REQUIRED - Run each task individually:**
```bash
./gradlew clean
./gradlew build
./gradlew publishLocal
```

### Why This Rule Exists
The XVM project uses a custom aggregator plugin that enforces single-task execution to prevent:
- Race conditions in composite builds
- Build conflicts between subprojects
- Task ordering issues
- Configuration cache problems

The `-PallowMultipleTasks=true` override exists but is reserved for tested workflows and when you know what you are doing. You DO NOT know what you are doing.

# Code Style Rules (UNBREAKABLE)
1. ALWAYS add a newline at the end of every file
2. NEVER use star imports (import foo.*) - always use explicit imports
3. NEVER use fully qualified Java package names in the Java code. Always import, so that i.e `org.gradle.api.model.ObjectFactory` is just `ObjectFactory`

### Safe Approach Options:

**Option 1: Target specific subprojects (SAFEST)**
- `./gradlew javatools:jar` (single project task)
- `./gradlew xdk:installDist` (single project task) 
- `./gradlew javatools:clean` (single project task)

**Option 2: Composite tasks (USE WITH CAUTION)**
- `./gradlew build` - Usually works but may have interference
- `./gradlew installDist` - Usually works but may have interference
- **NEVER**: `./gradlew clean build` or `./gradlew clean <anything>`

**Clean Workflow:**
1. `./gradlew clean` (standalone, nothing else)
2. Wait for completion
3. Then run your desired tasks: `./gradlew build` or `./gradlew installDist`

This prevents task interference and allows for reliable builds.

## Gradle Best Practices

When working with Gradle build files, always follow [Gradle Best Practices](https://docs.gradle.org/9.1.0/userguide/best_practices_general.html):

- **Configuration Cache Compatibility**: Use injected services (`ExecOperations`, `FileSystemOperations`) instead of project-level methods (`project.exec`, `project.javaexec`)
- **Task Dependencies**: Declare explicit task dependencies using `dependsOn`, `mustRunAfter`, or input/output relationships
- **Lazy Configuration**: Use Provider APIs and avoid eager evaluation during configuration
- **Incremental Builds**: Properly declare inputs and outputs for custom tasks
- **Build Performance**: Minimize configuration time work and prefer build cache compatible patterns

When refactoring build scripts, proactively suggest migrations to follow these best practices, especially for configuration cache compatibility and proper task modeling.

## CRITICAL KOTLIN DSL SYNTAX REQUIREMENTS

**NEVER use old untyped Gradle syntax in build.gradle.kts files. ALWAYS use typed operations:**

❌ **FORBIDDEN - Never do this:**
```kotlin
tasks.register("taskName") {
    dependsOn("otherTask")  // String-based dependency
}
```

✅ **REQUIRED - Always do this:**
```kotlin
val taskName by tasks.registering {
    dependsOn(tasks.named("otherTask"))  // Typed dependency
}
```

or even better:

```kotlin
val otherTask by tasks.existing<SomeTaskType>()

val taskName by tasks.registering {
    dependsOn(otherTask)  // Typed dependency
}
```

**Rules:**
- Always use `val taskName by tasks.registering` instead of `tasks.register("taskName")`
- Always use typed task references with proper Provider API
- This ensures proper build cache support, configuration cache compatibility, and IDE support
- NEVER run without the configuration cache enabled. Everything MUST work with the configuration cache.


# important-instruction-reminders
- Do what has been asked; nothing more, nothing less.
- NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.

# CRITICAL GRADLE RULE - CONFIGURATION CACHE COMPATIBILITY
**NEVER WRITE GRADLE CODE THAT IS INCOMPATIBLE WITH THE CONFIGURATION CACHE**
- Every time you write Gradle code, you MUST test the build to verify configuration cache compatibility
- Do NOT capture script object references (like `logger`, `project`) in task actions
- Use injected services (`@Inject` with `ExecOperations`, `Logger`, etc.) for configuration cache compatibility
- Use Worker API or convention plugins for complex task logic
- Always test with `./gradlew <task> --info` after making changes
