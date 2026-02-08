# Claude Configuration

## ABSOLUTE RULE: No Unsupervised Git Operations

**NEVER push commits, create remote branches, delete branches, or perform any destructive git operation without explicit manual confirmation from the user.** This includes `git push`, `git push -u`, `git branch -D`, `git push --delete`, and any `gh` command that modifies remote state (e.g., `gh pr create`). Local branch creation and local commits are allowed when requested, but nothing leaves the local machine without the user saying so.

## MOST IMPORTANT RULE: Gradle Task Execution

### NEVER Run Clean with Other Tasks

**FORBIDDEN - The aggregator plugin will reject these:**
```bash
./gradlew clean build                     # ❌ WILL FAIL
./gradlew clean publishLocal              # ❌ WILL FAIL
./gradlew clean build publishLocal        # ❌ WILL FAIL
```

**ALLOWED - Multiple tasks (excluding clean):**
```bash
./gradlew build publishLocal              # ✅ OK - most combinations work
./gradlew test jar                        # ✅ OK
```

**REQUIRED - Clean must run alone:**
```bash
./gradlew clean
./gradlew build
./gradlew publishLocal
```

### Why This Rule Exists
The XVM project uses a custom aggregator plugin that prevents running `clean` with other lifecycle tasks to avoid:
- Race conditions in composite builds
- Build conflicts between subprojects when cleaning
- Task ordering issues with clean

Most other task combinations work fine - the restriction only applies to `clean`.

# Code Style Rules (UNBREAKABLE)
1. ALWAYS add a newline at the end of every file
2. NEVER use star imports (import foo.*) - always use explicit imports
3. NEVER use fully qualified Java package names in the Java code. Always import, so that i.e `org.gradle.api.model.ObjectFactory` is just `ObjectFactory`
4. ALWAYS use `var` declarations when the type is clear from the right-hand side (e.g., `var x = new ArrayList<String>()` not `List<String> x = new ArrayList<>()`)

### Task Execution Patterns:

**Single project tasks:**
- `./gradlew javatools:jar`
- `./gradlew xdk:installDist`
- `./gradlew javatools:clean`

**Multiple lifecycle tasks (works for most tasks):**
- `./gradlew build installDist` - ✅ Works
- `./gradlew test jar` - ✅ Works
- `./gradlew assemble publishLocal` - ✅ Works

**Clean workflow (clean must run alone):**
1. `./gradlew clean` (standalone, nothing else)
2. Wait for completion
3. Then run your desired tasks: `./gradlew build` or `./gradlew build installDist`

**Remember:** Never combine `clean` with other lifecycle tasks.

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
- NEVER add "Co-Authored-By" lines to commit messages or pull request descriptions.

# CRITICAL GRADLE RULE - CONFIGURATION CACHE COMPATIBILITY
**NEVER WRITE GRADLE CODE THAT IS INCOMPATIBLE WITH THE CONFIGURATION CACHE**
- Every time you write Gradle code, you MUST test the build to verify configuration cache compatibility
- Do NOT capture script object references (like `logger`, `project`) in task actions
- Use injected services (`@Inject` with `ExecOperations`, `Logger`, etc.) for configuration cache compatibility
- Use Worker API or convention plugins for complex task logic
- Always test with `./gradlew <task> --info` after making changes
