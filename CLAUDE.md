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
