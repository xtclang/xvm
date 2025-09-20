import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.serialization.json.*
import org.gradle.api.GradleException

/**
 * Git convention plugin that provides standard git information resolution for all projects.
 * 
 * This plugin creates a single standard task `resolveGitInfo` that all projects can depend on
 * to get git branch, commit, dirty status, and other git metadata in a configuration cache
 * compatible way.
 */

abstract class ResolveGitInfoTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations
    
    @get:Input
    abstract val branchEnv: Property<String>
    
    @get:Input 
    abstract val commitEnv: Property<String>
    
    @get:Input
    abstract val version: Property<String>
    
    @get:Input
    abstract val ciFlag: Property<String>
    
    @get:InputFile
    @get:Optional
    abstract val gitHeadFile: RegularFileProperty
    
    @get:InputDirectory
    @get:Optional
    abstract val gitDirectory: DirectoryProperty
    
    @get:OutputFile
    abstract val outputFile: RegularFileProperty
    
    @TaskAction
    fun resolveGitInfo() {
        logger.info(">>> RESOLVING GIT INFORMATION")
        
        val branch = branchEnv.orNull?.takeIf { it.isNotEmpty() } ?: run {
            try {
                val output = ByteArrayOutputStream()
                execOps.exec {
                    commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
                    standardOutput = output
                }
                output.toString().trim().ifBlank { "master" }
            } catch (e: Exception) {
                logger.warn("Could not get git branch: ${e.message}")
                "master"
            }
        }
        
        val commit = commitEnv.orNull?.takeIf { it.isNotEmpty() } ?: run {
            try {
                val output = ByteArrayOutputStream()
                execOps.exec {
                    commandLine("git", "rev-parse", "HEAD")
                    standardOutput = output
                }
                output.toString().trim()
            } catch (e: Exception) {
                logger.warn("Could not get git commit: ${e.message}")
                "unknown"
            }
        }
        
        // Check if git working directory is dirty
        val isDirty = try {
            execOps.exec {
                commandLine("git", "diff", "--quiet")
            }
            false // if git diff --quiet succeeds, working directory is clean
        } catch (_: Exception) {
            true // if git diff --quiet fails, working directory is dirty
        }
        
        // Comprehensive git info for all consumers
        val gitInfo = mapOf(
            // Core git info
            "git.branch" to branch,
            "git.commit" to commit,
            "git.commit.id" to commit, // alias for compatibility
            "git.dirty" to isDirty.toString(),
            "git.status" to if (isDirty) "detached-head" else "clean",
            
            // Build info
            "git.build.version" to version.get(),
            "version" to version.get(),
            
            // Docker-specific derived info
            "docker.baseImage" to "ghcr.io/xtclang/xvm",
            "docker.isMaster" to (branch == "master").toString(),
            "docker.tagPrefix" to if (branch == "master") "latest" else branch.replace(Regex("[^a-zA-Z0-9._-]"), "_"),
            "docker.isCI" to (ciFlag.get() == "true").toString()
        )
        
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(gitInfo.map { "${it.key}=${it.value}" }.joinToString("\n"))
        }
        
        logger.info("Git info resolved: branch=$branch, commit=${commit.take(8)}, dirty=$isDirty, auto-tracking enabled")
    }
}

// Create the standard git info resolution task that all projects can use
val resolveGitInfo by tasks.registering(ResolveGitInfoTask::class) {
    group = "build"
    description = "Resolve git information (branch, commit, dirty status) in configuration cache compatible way"
    
    branchEnv.set(providers.environmentVariable("GH_BRANCH").orElse(""))
    commitEnv.set(providers.environmentVariable("GH_COMMIT").orElse(""))
    version.set(provider { project.version.toString() })
    ciFlag.set(providers.environmentVariable("CI").orElse(""))
    outputFile.set(layout.buildDirectory.file("git-info.properties"))
    
    // Monitor git repository state for proper up-to-date checking
    val gitDir = project.rootProject.layout.projectDirectory.dir(".git")
    if (gitDir.asFile.exists()) {
        gitDirectory.set(gitDir)
        val headFile = gitDir.file("HEAD")
        if (headFile.asFile.exists()) {
            gitHeadFile.set(headFile)
        }
    }
}

// Extension function to easily create git-based configurations from the resolved info
fun Project.gitInfoProvider() = resolveGitInfo.flatMap { it.outputFile }

// Extension functions for GitHub operations
fun Project.configureGitHubPackageManagement(
    organization: String = "xtclang",
    packageNames: List<String> = emptyList(),
    packageVersions: List<String> = emptyList()
) {
    tasks.named<ResolveGitHubPackagesTask>("resolveGitHubPackages") {
        this.organization.set(organization)
        this.packageNames.set(packageNames)
    }
    
    tasks.named<DeleteGitHubPackagesTask>("deleteGitHubPackages") {
        this.organization.set(organization)
        this.packageNames.set(packageNames)
        this.packageVersions.set(packageVersions)
    }
}

fun Project.loadGitInfo(): Map<String, String> {
    val gitInfoFile = resolveGitInfo.get().outputFile.get().asFile
    if (!gitInfoFile.exists()) {
        return emptyMap()
    }
    
    val props = java.util.Properties()
    gitInfoFile.inputStream().use { props.load(it) }
    return props.stringPropertyNames().associateWith { props.getProperty(it) }
}

// GitHub Protocol task extensions

abstract class GitHubPackageManagementTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations
    
    @get:Input
    abstract val organization: Property<String>
    
    @get:Input
    @get:Optional
    abstract val packageNames: ListProperty<String>
    
    @get:Input
    @get:Optional
    abstract val packageVersions: ListProperty<String>
    
    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty
    
    init {
        organization.convention("xtclang")
        packageNames.convention(emptyList())
        packageVersions.convention(emptyList())
    }
    
    protected fun gh(vararg args: String, throwOnError: Boolean = true): ProcessResult {
        return spawn(execOps, "gh", *args, throwOnError = throwOnError, logger = logger)
    }
    
    protected fun git(vararg args: String, throwOnError: Boolean = true): ProcessResult {
        return spawn(execOps, "git", *args, throwOnError = throwOnError, logger = logger)
    }
    
    private fun spawn(execOps: ExecOperations, command: String, vararg args: String, throwOnError: Boolean = true, logger: org.gradle.api.logging.Logger? = null): ProcessResult {
        val output = ByteArrayOutputStream()
        
        try {
            execOps.exec {
                commandLine(command, *args)
                standardOutput = output
                if (!throwOnError) {
                    isIgnoreExitValue = true
                }
            }
            val result = output.toString().trim()
            return ProcessResult(0 to result)
        } catch (e: Exception) {
            if (throwOnError) {
                throw e
            }
            return ProcessResult(-1 to output.toString().trim(), e)
        }
    }
}

abstract class ResolveGitHubPackagesTask : GitHubPackageManagementTask() {
    @TaskAction
    fun resolvePackages() {
        val org = organization.get()
        val result = gh("api", "https://api.github.com/orgs/$org/packages?package_type=maven")
        
        if (!result.isSuccessful()) {
            throw GradleException("Failed to fetch GitHub packages: ${result.output}")
        }
        
        val packages = Json.parseToJsonElement(result.output)
        require(packages is JsonArray) { "Expected JsonArray from GitHub API" }
        
        val packageMap = mutableMapOf<String, Map<Pair<String, Int>, List<String>>>()
        val requestedNames = packageNames.get().toSet()
        
        packages.map { it as JsonObject }.forEach { node ->
            val packageName = node["name"]!!.jsonPrimitive.content
            
            if (requestedNames.isNotEmpty() && packageName !in requestedNames) {
                logger.info("[github] Skipping package: '$packageName'")
                return@forEach
            }
            
            val packageVersionCount = node["version_count"]!!.jsonPrimitive.int
            if (packageVersionCount <= 0) {
                logger.warn("[github] Package '$packageName' has no versions")
                return@forEach
            }
            
            val versions = gh("api", "https://api.github.com/orgs/$org/packages/maven/$packageName/versions")
            if (!versions.isSuccessful()) {
                logger.warn("[github] Failed to fetch versions for package '$packageName'")
                return@forEach
            }
            
            val versionArray = Json.parseToJsonElement(versions.output)
            require(versionArray is JsonArray) { "Expected JsonArray from GitHub versions API" }
            
            val timeMap = mutableMapOf<Pair<String, Int>, MutableList<String>>()
            versionArray.map { it as JsonObject }.forEach { version ->
                val versionName = version["name"]?.jsonPrimitive?.content
                    ?: throw GradleException("Version name not found")
                val versionId = version["id"]?.jsonPrimitive?.int
                    ?: throw GradleException("Version id not found")
                val versionUpdatedAt = version["updated_at"]?.jsonPrimitive?.content
                    ?: throw GradleException("Version updated time not found")
                
                val timestamps = timeMap.getOrPut(versionName to versionId) { mutableListOf() }
                timestamps.add(versionUpdatedAt)
            }
            
            packageMap[packageName] = timeMap
        }
        
        // Write results to output file if specified
        outputFile.orNull?.asFile?.let { file ->
            file.parentFile.mkdirs()
            val json = Json.encodeToString(JsonObject.serializer(), JsonObject(packageMap.mapValues { entry ->
                JsonObject(entry.value.mapKeys { "${it.key.first}:${it.key.second}" }.mapValues {
                    JsonArray(it.value.map { JsonPrimitive(it) })
                })
            }))
            file.writeText(json)
        }
        
        logger.lifecycle("[github] Resolved ${packageMap.size} packages from organization '$org'")
    }
}

abstract class DeleteGitHubPackagesTask : GitHubPackageManagementTask() {
    @TaskAction
    fun deletePackages() {
        val org = organization.get()
        val selectedNames = packageNames.get()
        val selectedVersions = packageVersions.get()
        
        if (selectedNames.isEmpty()) {
            logger.warn("[github] No package names specified for deletion")
            return
        }
        
        // First resolve packages to get current state
        val resolveResult = gh("api", "https://api.github.com/orgs/$org/packages?package_type=maven")
        if (!resolveResult.isSuccessful()) {
            throw GradleException("Failed to fetch GitHub packages: ${resolveResult.output}")
        }
        
        val packages = Json.parseToJsonElement(resolveResult.output)
        require(packages is JsonArray) { "Expected JsonArray from GitHub API" }
        
        var changes = 0
        
        for (packageName in selectedNames) {
            val packageExists = packages.any { (it as JsonObject)["name"]?.jsonPrimitive?.content == packageName }
            if (!packageExists) {
                logger.warn("[github] Package '$packageName' not found")
                continue
            }
            
            if (selectedVersions.isEmpty()) {
                // Delete entire package
                logger.lifecycle("[github] Deleting entire package: '$packageName'")
                val deleteResult = gh(
                    "api", "--method", "DELETE",
                    "https://api.github.com/orgs/$org/packages/maven/$packageName",
                    throwOnError = false
                )
                if (!deleteResult.isSuccessful()) {
                    logger.error("[github] Failed to delete package '$packageName': ${deleteResult.output}")
                } else {
                    changes++
                }
            } else {
                // Delete specific versions
                val versions = gh("api", "https://api.github.com/orgs/$org/packages/maven/$packageName/versions")
                if (!versions.isSuccessful()) {
                    logger.warn("[github] Failed to fetch versions for package '$packageName'")
                    continue
                }
                
                val versionArray = Json.parseToJsonElement(versions.output)
                require(versionArray is JsonArray) { "Expected JsonArray from GitHub versions API" }
                
                versionArray.map { it as JsonObject }.forEach { version ->
                    val versionName = version["name"]?.jsonPrimitive?.content
                    val versionId = version["id"]?.jsonPrimitive?.int
                    
                    if (versionName != null && versionId != null && versionName in selectedVersions) {
                        logger.lifecycle("[github] Deleting package $packageName version $versionName (id: $versionId)")
                        val deleteResult = gh(
                            "api", "--method", "DELETE",
                            "https://api.github.com/orgs/$org/packages/maven/$packageName/versions/$versionId",
                            throwOnError = false
                        )
                        if (!deleteResult.isSuccessful()) {
                            logger.error("[github] Failed to delete package version: ${deleteResult.output}")
                        } else {
                            changes++
                        }
                    }
                }
            }
        }
        
        logger.lifecycle("[github] Deleted $changes package(s) or version(s)")
    }
}

abstract class GitTaggingTask : DefaultTask() {
    @get:Inject
    abstract val execOps: ExecOperations
    
    @get:Input
    abstract val version: Property<String>
    
    @get:Input
    abstract val snapshotOnly: Property<Boolean>
    
    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty
    
    init {
        snapshotOnly.convention(false)
    }
    
    @TaskAction
    fun ensureTags() {
        val semanticVersionString = version.get()
        val isSnapshot = semanticVersionString.contains("SNAPSHOT")
        val isRelease = !isSnapshot
        val snapshotOnlyMode = snapshotOnly.get()
        
        if (snapshotOnlyMode && isRelease) {
            logger.warn("[git] ensureTags called with snapshotOnly=true. Skipping non-snapshot version: $semanticVersionString")
            return
        }
        
        val artifactBaseVersion = semanticVersionString.removeSuffix("-SNAPSHOT")
        val tagPrefix = if (isSnapshot) "snapshot/" else "release/"
        val localTag = "${tagPrefix}v$artifactBaseVersion"
        
        // Check if tag already exists
        val tagExists = try {
            execOps.exec {
                commandLine("git", "rev-parse", "--verify", "refs/tags/$localTag")
                isIgnoreExitValue = true
            }
            true
        } catch (e: Exception) {
            false
        }
        
        if (tagExists) {
            logger.lifecycle("[git] Tag $localTag already exists")
            
            if (isSnapshot) {
                // Delete existing snapshot tag
                logger.lifecycle("[git] Deleting existing snapshot tag $localTag")
                try {
                    execOps.exec {
                        commandLine("git", "tag", "-d", localTag)
                        isIgnoreExitValue = true
                    }
                } catch (e: Exception) {
                    logger.warn("[git] Failed to delete local tag: ${e.message}")
                }
                
                try {
                    execOps.exec {
                        commandLine("git", "push", "--delete", "origin", localTag)
                        isIgnoreExitValue = true
                    }
                } catch (e: Exception) {
                    logger.warn("[git] Failed to delete remote tag: ${e.message}")
                }
            } else {
                logger.warn("[git] Release version $semanticVersionString already tagged. No publication will be made.")
                return
            }
        }
        
        // Create new tag
        logger.lifecycle("[git] Creating tag $localTag")
        execOps.exec {
            commandLine("git", "tag", localTag)
        }
        
        // Push tag to remote
        logger.lifecycle("[git] Pushing tag $localTag to origin")
        execOps.exec {
            commandLine("git", "push", "origin", localTag)
        }
        
        // Write result to output file if specified
        outputFile.orNull?.asFile?.let { file ->
            file.parentFile.mkdirs()
            file.writeText("$localTag\n")
        }
        
        logger.lifecycle("[git] Successfully created and pushed tag: $localTag")
    }
}

// Task registration
val resolveGitHubPackages by tasks.registering(ResolveGitHubPackagesTask::class) {
    group = "github"
    description = "Resolve GitHub packages for the organization"
}

val deleteGitHubPackages by tasks.registering(DeleteGitHubPackagesTask::class) {
    group = "github"
    description = "Delete GitHub packages or specific versions"
}

val ensureGitTags by tasks.registering(GitTaggingTask::class) {
    group = "git"
    description = "Ensure current commit is tagged with the current version"
    version.set(providers.provider { project.version.toString() })
}