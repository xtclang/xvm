# Claude Configuration

## MOST IMPORTANT RULE: Gradle Task Execution

### Composite Build Usage
This is a composite build where you CAN run gradlew with many tasks across multiple subprojects, but tasks may interfere with each other.

**CRITICAL CLEAN RULE**: If you need to clean, you MUST run `./gradlew clean` as a standalone command first, with NO other tasks. Never combine clean with other tasks.

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

When working with Gradle build files, always follow [Gradle Best Practices](https://docs.gradle.org/9.0.0/userguide/best_practices_general.html):

- **Configuration Cache Compatibility**: Use injected services (`ExecOperations`, `FileSystemOperations`) instead of project-level methods (`project.exec`, `project.javaexec`)
- **Task Dependencies**: Declare explicit task dependencies using `dependsOn`, `mustRunAfter`, or input/output relationships
- **Lazy Configuration**: Use Provider APIs and avoid eager evaluation during configuration
- **Incremental Builds**: Properly declare inputs and outputs for custom tasks
- **Build Performance**: Minimize configuration time work and prefer build cache compatible patterns

When refactoring build scripts, proactively suggest migrations to follow these best practices, especially for configuration cache compatibility and proper task modeling.

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.

# CRITICAL GRADLE RULE - CONFIGURATION CACHE COMPATIBILITY
**NEVER WRITE GRADLE CODE THAT IS INCOMPATIBLE WITH THE CONFIGURATION CACHE**
- Every time you write Gradle code, you MUST test the build to verify configuration cache compatibility
- Do NOT capture script object references (like `logger`, `project`) in task actions
- Use injected services (`@Inject` with `ExecOperations`, `Logger`, etc.) for configuration cache compatibility
- Use Worker API or convention plugins for complex task logic
- Always test with `./gradlew <task> --info` after making changes
