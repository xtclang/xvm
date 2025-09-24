import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

// Simple JSON parsing utilities for GitHub API responses
fun parseJsonArray(json: String): List<Map<String, Any?>> {
    val trimmed = json.trim()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
        throw IllegalArgumentException("Expected JSON array")
    }

    val content = trimmed.substring(1, trimmed.length - 1).trim()
    if (content.isEmpty()) return emptyList()

    val objects = mutableListOf<Map<String, Any?>>()
    var braceCount = 0
    var start = 0
    var inString = false
    var escaped = false

    for (i in content.indices) {
        val char = content[i]
        when {
            escaped -> escaped = false
            char == '\\' && inString -> escaped = true
            char == '"' -> inString = !inString
            !inString && char == '{' -> braceCount++
            !inString && char == '}' -> {
                braceCount--
                if (braceCount == 0) {
                    val objJson = content.substring(start, i + 1).trim()
                    if (objJson.isNotEmpty()) {
                        objects.add(parseJsonObject(objJson))
                    }
                    start = i + 1
                    while (start < content.length && content[start] in " ,\n\r\t") start++
                }
            }
        }
    }

    return objects
}

fun parseJsonObject(json: String): Map<String, Any?> {
    val trimmed = json.trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        throw IllegalArgumentException("Expected JSON object")
    }

    val content = trimmed.substring(1, trimmed.length - 1).trim()
    if (content.isEmpty()) return emptyMap()

    val result = mutableMapOf<String, Any?>()
    val keyValueRegex = """"([^"]+)"\s*:\s*("([^"]*)"|(\d+)|true|false|null|\[.*?\]|\{.*?\})""".toRegex()

    keyValueRegex.findAll(content).forEach { match ->
        val key = match.groupValues[1]
        val valueStr = match.groupValues[2]
        val value = when {
            valueStr.startsWith("\"") -> match.groupValues[3] // String value
            valueStr.matches("""\d+""".toRegex()) -> valueStr.toInt() // Integer
            valueStr == "true" -> true
            valueStr == "false" -> false
            valueStr == "null" -> null
            valueStr.startsWith("[") -> parseJsonArray(valueStr) // Array
            valueStr.startsWith("{") -> parseJsonObject(valueStr) // Object
            else -> valueStr
        }
        result[key] = value
    }

    return result
}

fun generateJsonString(data: Map<String, Any?>): String {
    fun valueToJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"$value\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        is List<*> -> "[${value.joinToString(",") { valueToJson(it) }}]"
        is Map<*, *> -> "{${value.entries.joinToString(",") { "\"${it.key}\":${valueToJson(it.value)}" }}}"
        else -> "\"$value\""
    }

    return "{${data.entries.joinToString(",") { "\"${it.key}\":${valueToJson(it.value)}" }}}"
}

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

        val packages = parseJsonArray(result.output)

        val packageMap = mutableMapOf<String, Map<Pair<String, Int>, List<String>>>()
        val requestedNames = packageNames.get().toSet()

        packages.forEach { node ->
            val packageName = node["name"] as? String
                ?: throw GradleException("Package name not found")

            if (requestedNames.isNotEmpty() && packageName !in requestedNames) {
                logger.info("[github] Skipping package: '$packageName'")
                return@forEach
            }

            val packageVersionCount = node["version_count"] as? Int ?: 0
            if (packageVersionCount <= 0) {
                logger.warn("[github] Package '$packageName' has no versions")
                return@forEach
            }

            val versions = gh("api", "https://api.github.com/orgs/$org/packages/maven/$packageName/versions")
            if (!versions.isSuccessful()) {
                logger.warn("[github] Failed to fetch versions for package '$packageName'")
                return@forEach
            }

            val versionArray = parseJsonArray(versions.output)

            val timeMap = mutableMapOf<Pair<String, Int>, MutableList<String>>()
            versionArray.forEach { version ->
                val versionName = version["name"] as? String
                    ?: throw GradleException("Version name not found")
                val versionId = version["id"] as? Int
                    ?: throw GradleException("Version id not found")
                val versionUpdatedAt = version["updated_at"] as? String
                    ?: throw GradleException("Version updated time not found")

                val timestamps = timeMap.getOrPut(versionName to versionId) { mutableListOf() }
                timestamps.add(versionUpdatedAt)
            }

            packageMap[packageName] = timeMap
        }

        // Write results to output file if specified
        outputFile.orNull?.asFile?.let { file ->
            file.parentFile.mkdirs()
            val jsonData = packageMap.mapValues { entry ->
                entry.value.mapKeys { "${it.key.first}:${it.key.second}" }.mapValues { it.value }
            }
            val json = generateJsonString(jsonData)
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

        val packages = parseJsonArray(resolveResult.output)

        var changes = 0

        for (packageName in selectedNames) {
            val packageExists = packages.any { (it["name"] as? String) == packageName }
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

                val versionArray = parseJsonArray(versions.output)

                versionArray.forEach { version ->
                    val versionName = version["name"] as? String
                    val versionId = version["id"] as? Int

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

// ResolveGitInfoTask and ShowGitInfoTask removed - replaced by Palantir gradle-git-version plugin
