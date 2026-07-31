# LLM Configuration (Codex, Claude, etc.)
## ABSOLUTE RULE: No Unsupervised Destructive Git Operations

**NEVER push commits, create remote branches, delete branches, or perform any destructive git operation without explicit manual confirmation from the user.** This includes `git push`, `git push -u`, `git branch -D`, `git push --delete`, and any `gh` command that modifies remote state (e.g., `gh pr create`). Local branch creation and local commits are allowed when requested, but nothing leaves the local machine without the user saying so.

## MOST IMPORTANT RULE: Gradle Task Execution

### NEVER Run Clean with Other Tasks

**FORBIDDEN - Do not do the gradle "clean" task with any other task:**
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

**REQUIRED - "clean" must run one at a time:**
```bash
./gradlew clean
./gradlew build publishLocal
```

### Why This Rule Exists
The XVM project uses a custom aggregator plugin that prevents running `clean` with other lifecycle tasks to avoid:
- Race conditions in composite builds
- Build conflicts between subprojects when cleaning
- Task ordering issues with clean

Most other task combinations work fine - the restriction only applies to `clean`.

# Java Code Style Rules
1. Do not use star imports like "import pkg.*"; instead use explicit imports until the number of imports in the package goes over 25
2. Try not to use fully qualified Java package names in the Java code, but use `import` instead for those classes.
3. For new or actively edited Java code, avoid `var` declarations unless the explicit type is very complex and long. Leave existing `var` usage alone unless that specific code is being rewritten for another reason.
4. Add succinct comments when code intent is not obvious; avoid comments that simply restate the code.
5. When adding or changing a comment (Javadoc and otherwise), paragraph separation should be a blank line. In Javadoc, the blank line should contain the HTML `<p/>` to indicate a paragraph separator.
6. When adding or changing short comments (up to 3 or 4 lines) inside methods (this does not apply to the Javadoc), do not force capitalization of the first word of a sentence, use semicolons instead of periods to separate phrases, and don't add a  period at the end.
7. Each file that is modified should end with a line terminator so that there is a blank line at the end of the source file, line feeds are allowed but all carriage returns must be removed, and all trailing tabs and spaces must be removed from each line.
8. Never remove existing comments unless a code change has invalidated the comment; only add your own as specified above.
9. Every time in our Java sources that you see a switch case that contains code, it should end with either a `break`, `return`, `throw`, `continue`, `yield`, or a comment that says `// fall through`. Everything else should be treated as an error.
10. Line width is 100 characters, although code is permitted to exceed this guideline when the readability is improved by doing so. Line continuations are indented 8 spaces from the start of the **initial** line from which the continuation is continuing.

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


## Lang Composite Build Properties

The `lang/` directory is a **Gradle composite build** that is disabled by default. Two `-P` properties are required to enable it when running any `./gradlew :lang:*` task from the project root:

```bash
./gradlew :lang:<task> -PincludeBuildLang=true -PincludeBuildAttachLang=true
```

### What the properties do
- **`-PincludeBuildLang=true`**: Includes the `lang/` directory as a composite build, making `:lang:*` tasks visible to the root project
- **`-PincludeBuildAttachLang=true`**: Wires `lang/` lifecycle tasks (build, test, etc.) to the root build's lifecycle, so `./gradlew build` from the root also builds lang

### Why they exist
Both properties default to `false` in the root `gradle.properties` so that:
1. CI and other developers don't need to build `lang/` unless they're working on it
2. The main XDK build stays fast for contributors who aren't touching language tooling
3. The composite build inclusion is opt-in to avoid unexpected build interactions

### When to use them
- **Always** when running any `./gradlew :lang:*` task from the project root
- Both properties are always needed together -- using only one will fail
- If you forget them, the build will fail with "project ':lang' not found" or similar

### Alternative: local gradle.properties override
Instead of passing `-P` flags every time, developers can set them in their local `gradle.properties`:
```properties
includeBuildLang=true
includeBuildAttachLang=true
```

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
