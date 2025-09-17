import XdkDistribution.Companion.DISTRIBUTION_TASK_GROUP
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.versioning)
    alias(libs.plugins.xdk.build.aggregator)
}

/**
 * Installation and distribution tasks that aggregate publishable/distributable included
 * build projects. The aggregator proper should be as small as possible, and only contains
 * LifeCycle dependencies, aggregated through the various included builds. This creates as
 * few bootstrapping problems as possible, since by the time we get to the configuration phase
 * of the root build.gradle.kts, we have installed convention plugins, resolved version catalogs
 * and similar things.
 */
val installDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution in the xdk/build/distributions and xdk/build/install directories."
    dependsOn(xdk.task(":$name"))
}

val installWithNativeLaunchersDist by tasks.registering {
    group = DISTRIBUTION_TASK_GROUP
    description = "Install the XDK distribution with native launchers in the xdk/build/install directory."
    dependsOn(xdk.task(":$name"))
}

/**
 * Register aggregated publication tasks to the top level project, to ensure we can publish both
 * the XDK and the XTC plugin (and other future artifacts) with './gradlew publish' or
 * './gradlew publishToMavenLocal'.  Snapshot builds should only be allowed to be published
 * in local repositories.
 *
 * Publishing tasks can be racy, but it seems that Gradle serializes tasks that have a common
 * output directory, which should be the case here. If not, we will have to put back the
 * parallel check/task failure condition.
 *
 * Publish remote - one way to do it is to only allow snapshot publications in GitHub, otherwise
 * we need to do it manually. "publishRemoteRelease", in which case we will also feed into
 * jreleaser.
 */
val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to GitHub Packages."
    dependsOn(validateGitHubCredentials)
    // Publish all vanniktech publications to GitHub Packages for both projects
    dependsOn(
        plugin.task(":publishAllPublicationsToGitHubRepository"),
        xdk.task(":publishMavenPublicationToGitHubRepository")
    )
}

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Publish XDK and plugin artifacts to local Maven repository."
    // Publish to local Maven repository for both projects
    dependsOn(
        plugin.task(":publishToMavenLocal"),
        xdk.task(":publishToMavenLocal")
    )
}

/**
 * Publish both local (mavenLocal) and remote (GitHub, and potentially mavenCentral, gradlePluginPortal)
 * packages for the current code.
 */
val publish by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates publish tasks for builds with publications."
    dependsOn(publishLocal, publishRemote)
}

private val xdk = gradle.includedBuild("xdk")
private val plugin = gradle.includedBuild("plugin")
private val includedBuildsWithPublications = listOf(xdk, plugin)
private val publishTaskPrefixes = listOf("list", "delete")
private val publishTaskSuffixesRemote = listOf("RemotePublications")
private val publishTaskSuffixesLocal = listOf("LocalPublications")


/**
 * Docker tasks - forwarded to docker subproject
 * TODO: Skip this and resolve the dist some other way.
 */

private val dockerSubproject = gradle.includedBuild("docker")
private val dockerTaskNames = listOf(
    "dockerBuildAmd64", "dockerBuildArm64", "dockerBuild",
    "dockerBuildMultiPlatform", "dockerPushMultiPlatform", 
    "dockerPushAmd64", "dockerPushArm64", "dockerPushAll",
    "dockerBuildAndPush", "dockerBuildAndPushMultiPlatform",
    "dockerCreateManifest", "dockerBuildPushAndManifest"
)

// Forward all docker tasks to the docker subproject
dockerTaskNames.forEach { taskName ->
    tasks.register(taskName) {
        group = "docker"
        description = "Forward to docker subproject task: $taskName"
        dependsOn(dockerSubproject.task(":$taskName"))
        
        // Ensure XDK is built first for tasks that need it
        if (taskName.contains("Build") || taskName.contains("Push")) {
            dependsOn(installDist)
        }
    }
}

// Configuration cache compatible task for listing remote GitHub publications
abstract class ListRemotePublicationsTask : DefaultTask() {
    @get:Input
    abstract val gitHubToken: Property<String>
    
    @TaskAction
    fun listPublications() {
        val token = gitHubToken.get()
        if (token.isEmpty()) {
            logger.lifecycle("No GitHub token available - cannot list remote publications")
            return
        }
        
        logger.lifecycle("Fetching GitHub packages for xtclang/xvm...")
        
        // Try multiple API endpoints and package types
        var foundPackages = false
        
        val apiEndpoints = listOf(
            "https://api.github.com/orgs/xtclang/packages?package_type=maven",
            "https://api.github.com/repos/xtclang/xvm/packages?package_type=maven",
            "https://api.github.com/orgs/xtclang/packages",  // All package types
            "https://api.github.com/repos/xtclang/xvm/packages"  // All package types
        )
        
        for (apiUrl in apiEndpoints) {
            try {
                logger.debug("Trying API: $apiUrl")
                val connection = java.net.URI(apiUrl).toURL().openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val packageNames = parsePackageNames(response)
                    
                    if (packageNames.isNotEmpty()) {
                        foundPackages = true
                        logger.lifecycle("Found ${packageNames.size} packages using ${apiUrl.substringAfter("api.github.com/")}:")
                        
                        // Now get versions for each package
                        packageNames.forEach { packageName ->
                            getPackageVersions(packageName, token, apiUrl.contains("/orgs/"))
                        }
                        break  // Stop trying other endpoints once we find packages
                    }
                } else {
                    logger.debug("API $apiUrl returned: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                logger.debug("Error with API $apiUrl: ${e.message}")
            }
        }
        
        if (!foundPackages) {
            logger.lifecycle("No packages found using GitHub API.")
            logger.lifecycle("This could mean:")
            logger.lifecycle("  - Packages haven't been published yet")
            logger.lifecycle("  - Token lacks required permissions")  
            logger.lifecycle("  - Packages are private/internal")
            
            // Try the actual Maven package names from GitHub packages page (excluding docker packages)
            val knownPackages = listOf(
                "org.xtclang.xdk",
                "org.xtclang.xtc-plugin",
                "org.xtclang.xtc-plugin.org.xtclang.xtc-plugin.gradle.plugin"
            )
            
            logger.lifecycle("Trying direct package lookups with known names...")
            knownPackages.forEach { packageName ->
                getPackageVersions(packageName, token, true)  // Use org-level API
            }
        }
        
        logger.lifecycle("")
        logger.lifecycle("View all packages: https://github.com/xtclang/xvm/packages")
    }
    
    private fun parsePackageNames(jsonResponse: String): List<String> {
        val packageNames = mutableListOf<String>()
        
        try {
            val lines = jsonResponse.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("\"name\":")) {
                    val name = trimmed.substringAfter("\"name\": \"").substringBefore("\",").substringBefore("\"")
                    if (name.isNotEmpty()) {
                        packageNames.add(name)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse package names: ${e.message}")
        }
        
        return packageNames
    }
    
    private fun getPackageVersions(packageName: String, token: String, useOrgAPI: Boolean = true) {
        try {
            val baseUrl = if (useOrgAPI) {
                "https://api.github.com/orgs/xtclang/packages"
            } else {
                "https://api.github.com/repos/xtclang/xvm/packages"
            }
            // URL encode the package name in case it has dots or special characters
            val encodedPackageName = java.net.URLEncoder.encode(packageName, "UTF-8")
            val url = "$baseUrl/maven/$encodedPackageName/versions?per_page=100&page=1"
            val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            
            when (connection.responseCode) {
                200 -> {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    logger.debug("API response for $packageName: $response")
                    val versions = parseGitHubPackageVersions(response)
                    
                    if (versions.isNotEmpty()) {
                        logger.lifecycle("  $packageName")
                        versions.take(5).forEach { version ->
                            logger.lifecycle("    ${version.name} (GitHub API updated: ${version.updatedAt})")
                        }
                        if (versions.size > 5) {
                            logger.lifecycle("    ... and ${versions.size - 5} more versions")
                        }

                        // For snapshots, also check Maven metadata for actual latest timestamp
                        if (versions.any { it.name.contains("SNAPSHOT") }) {
                            val latestSnapshotInfo = getLatestSnapshotTimestamp(packageName, token)
                            if (latestSnapshotInfo != null) {
                                logger.lifecycle("    Latest snapshot artifacts: ${latestSnapshotInfo}")
                            }
                        }
                    } else {
                        logger.lifecycle("  $packageName - Found package but response parsing failed or empty")
                        logger.lifecycle("    API URL: $url")
                        // Always show the response to debug what GitHub is actually returning
                        if (response.length < 2000) {
                            logger.lifecycle("    Response: $response")
                        } else {
                            logger.lifecycle("    Response length: ${response.length} chars (truncated)")
                            logger.lifecycle("    First 500 chars: ${response.take(500)}")
                        }
                    }
                }
                404 -> {
                    // Don't log 404s for fallback attempts to reduce noise
                }
                else -> {
                    logger.lifecycle("  $packageName - Error ${connection.responseCode}: ${connection.responseMessage}")
                }
            }
        } catch (e: Exception) {
            logger.debug("Error fetching versions for $packageName: ${e.message}")
        }
    }
    
    private fun parseGitHubPackageVersions(jsonResponse: String): List<PackageVersion> {
        // Simple JSON parsing without external dependencies
        val versions = mutableListOf<PackageVersion>()
        
        try {
            // Extract all name and updated_at pairs from the JSON response
            val nameMatches = Regex("\"name\":\\s*\"([^\"]+)\"").findAll(jsonResponse).toList()
            val updatedAtMatches = Regex("\"updated_at\":\\s*\"([^\"]+)\"").findAll(jsonResponse).toList()
            
            // Pair them up (assuming they appear in the same order)
            val minSize = minOf(nameMatches.size, updatedAtMatches.size)
            for (i in 0 until minSize) {
                val name = nameMatches[i].groupValues[1]
                val updatedAt = updatedAtMatches[i].groupValues[1]
                versions.add(PackageVersion(name, updatedAt))
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse GitHub API response: ${e.message}")
        }
        
        return versions.sortedByDescending { it.updatedAt }
    }
    
    private fun parsePackageVersionObject(objectJson: String): PackageVersion? {
        try {
            var name = ""
            var updatedAt = ""
            
            // Extract name and updated_at fields
            val nameMatch = Regex("\"name\":\\s*\"([^\"]+)\"").find(objectJson)
            val updatedAtMatch = Regex("\"updated_at\":\\s*\"([^\"]+)\"").find(objectJson)
            
            name = nameMatch?.groupValues?.get(1) ?: ""
            updatedAt = updatedAtMatch?.groupValues?.get(1) ?: ""
            
            return if (name.isNotEmpty() && updatedAt.isNotEmpty()) {
                PackageVersion(name, updatedAt)
            } else null
        } catch (e: Exception) {
            logger.debug("Failed to parse package version object: ${e.message}")
            return null
        }
    }
    
    private fun getLatestSnapshotTimestamp(packageName: String, token: String): String? {
        try {
            // Try to access the Maven metadata to get actual snapshot timestamps
            val metadataUrl = "https://maven.pkg.github.com/xtclang/xvm/${packageName.replace('.', '/')}/maven-metadata.xml"
            val connection = java.net.URI(metadataUrl).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $token")

            logger.debug("Maven metadata request for $packageName: $metadataUrl -> ${connection.responseCode}")

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                logger.debug("Maven metadata response for $packageName: ${response.take(500)}")

                // Parse XML to find latest snapshot timestamp
                val lastUpdatedRegex = """<lastUpdated>(\d{14})</lastUpdated>""".toRegex()
                val timestampRegex = """<timestamp>(\d{8}\.\d{6})</timestamp>""".toRegex()
                val buildNumberRegex = """<buildNumber>(\d+)</buildNumber>""".toRegex()

                val lastUpdated = lastUpdatedRegex.find(response)?.groupValues?.get(1)
                val timestamp = timestampRegex.find(response)?.groupValues?.get(1)
                val buildNumber = buildNumberRegex.find(response)?.groupValues?.get(1)

                if (lastUpdated != null) {
                    // Convert lastUpdated format: 20250917122716 -> 2025-09-17 12:27:16
                    val year = lastUpdated.substring(0, 4)
                    val month = lastUpdated.substring(4, 6)
                    val day = lastUpdated.substring(6, 8)
                    val hour = lastUpdated.substring(8, 10)
                    val minute = lastUpdated.substring(10, 12)
                    val second = lastUpdated.substring(12, 14)
                    return "$year-$month-$day $hour:$minute:$second"
                } else if (timestamp != null && buildNumber != null) {
                    // Convert timestamp format: 20250917.122638 -> 2025-09-17 12:26:38
                    val year = timestamp.substring(0, 4)
                    val month = timestamp.substring(4, 6)
                    val day = timestamp.substring(6, 8)
                    val time = timestamp.substring(9).replace(".", ":")
                    return "$year-$month-$day $time (build $buildNumber)"
                } else {
                    logger.debug("Could not find lastUpdated, timestamp, or buildNumber in Maven metadata for $packageName")
                }
            }
        } catch (e: Exception) {
            logger.debug("Could not fetch Maven metadata for $packageName: ${e.message}")
        }
        return null
    }

    data class PackageVersion(val name: String, val updatedAt: String)
}

// list|deleteLocalPublicatiopns/remotePublications.
publishTaskPrefixes.forEach { prefix ->
    publishTaskSuffixesLocal.forEach { suffix ->
        val taskName = "$prefix$suffix"
        tasks.register(taskName) {
            group = PUBLISH_TASK_GROUP
            description = "Task that aggregates '$taskName' tasks for builds with publications."
            includedBuildsWithPublications.forEach { it ->
                dependsOn(it.task(":$taskName"))
            }
        }
    }
}

abstract class ValidateGitHubCredentialsTask : DefaultTask() {
    @get:Input
    abstract val gitHubUsername: Property<String>

    @get:Input
    abstract val gitHubPassword: Property<String>

    @TaskAction
    fun validate() {
        val username = gitHubUsername.get()
        val password = gitHubPassword.get()

        if (password.isEmpty()) {
            throw GradleException("""
                |GitHub credentials not available for publishing!
                |
                |Please provide credentials using one of these methods:
                |
                |1. Local development - Set properties in ~/.config/xtc/gradle.properties:
                |   GitHubUsername=your-username
                |   GitHubPassword=your-personal-access-token
                |
                |2. CI/GitHub Actions - Environment variables (automatically set):
                |   GITHUB_ACTOR=actor-name
                |   GITHUB_TOKEN=github-token
                |
                |3. Command line properties:
                |   ./gradlew publishRemote -PGitHubUsername=your-username -PGitHubPassword=your-token
                |
                |Current status:
                |  Username: ${if (username.isNotEmpty()) "✅ Available ($username)" else "❌ Missing"}
                |  Password/Token: ${if (password.isNotEmpty()) "✅ Available" else "❌ Missing"}
            """.trimMargin())
        }

        logger.lifecycle("✅ GitHub credentials validated successfully")
        logger.lifecycle("   Username: $username")
        logger.lifecycle("   Token: Available (${password.take(8)}...)")
    }
}

// Validate GitHub credentials are available for publishing
val validateGitHubCredentials by tasks.registering(ValidateGitHubCredentialsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Validate that GitHub credentials are available for publishing"

    gitHubUsername.set(
        project.findProperty("GitHubUsername")?.toString()
            ?: providers.environmentVariable("GITHUB_ACTOR").getOrNull()
            ?: "xtclang-workflows"
    )
    gitHubPassword.set(
        project.findProperty("GitHubPassword")?.toString()
            ?: providers.environmentVariable("GITHUB_TOKEN").getOrNull()
            ?: ""
    )
}

// Special handling for remote publication listing - use GitHub API integration instead of delegation
val listRemotePublications by tasks.registering(ListRemotePublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "List remote GitHub publications using GitHub API integration"
    dependsOn(validateGitHubCredentials)
    val gitHubPassword = project.findProperty("GitHubPassword")?.toString()
        ?: providers.environmentVariable("GITHUB_TOKEN").getOrNull()
    if (!gitHubPassword.isNullOrEmpty()) {
        gitHubToken.set(gitHubPassword)
    } else {
        logger.lifecycle("GitHub token not available - remote publication listing disabled")
    }
}

// Handle deleteRemotePublications with delegation
val deleteRemotePublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that aggregates 'deleteRemotePublications' tasks for builds with publications."
    includedBuildsWithPublications.forEach { it ->
        dependsOn(it.task(":deleteRemotePublications"))
    }
}
