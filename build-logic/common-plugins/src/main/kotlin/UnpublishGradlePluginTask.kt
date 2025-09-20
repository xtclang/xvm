import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Task to unpublish/delete a specific version from Gradle Plugin Portal.
 * Provides guidance on self-service deletion vs support requests.
 */
abstract class UnpublishGradlePluginTask : DefaultTask() {
    @get:Input
    abstract val pluginId: Property<String>

    @get:Input
    abstract val unpublishVersion: Property<String>

    @get:Input
    abstract val hasCredentials: Property<Boolean>

    @TaskAction
    fun unpublishVersion() {
        val version = unpublishVersion.get()
        val pluginIdValue = pluginId.get()

        if (version.isEmpty()) {
            throw GradleException("""
                |No version specified for unpublishing!
                |Usage: ./gradlew unpublishGradlePlugin -PunpublishGradlePlugin=0.4.4
            """.trimMargin())
        }

        logger.lifecycle("Attempting to unpublish plugin '$pluginIdValue' version '$version'...")

        // Reuse existing Plugin Portal parsing logic
        val htmlResponse = fetchPluginPage(pluginIdValue)
        val versions = parsePluginPortalHtml(htmlResponse)
        val targetVersion = versions.find { it.version == version }

        if (targetVersion == null) {
            logger.lifecycle("❌ Version '$version' not found on Gradle Plugin Portal")
            logger.lifecycle("   Available versions: ${versions.joinToString(", ") { it.version }}")
            logger.lifecycle("   Portal URL: https://plugins.gradle.org/plugin/$pluginIdValue")
            return
        }

        logger.lifecycle("✅ Found version '$version' (${targetVersion.status})")

        // For simplicity, assume versions newer than current latest (0.4.5) are recent
        val isRecent = compareVersions(version, "0.4.5") > 0

        if (isRecent) {
            logger.lifecycle("🎯 Version appears to be recent - may be within 7-day self-service deletion window")
            provideSelfServiceGuidance(pluginIdValue, version)
        } else {
            logger.lifecycle("⏰ Version appears to be older - likely requires support request")
            provideSupportGuidance(pluginIdValue, version)
        }

        // Show available credentials status
        logger.lifecycle(if (hasCredentials.get()) "✅ Plugin Portal credentials are available" else "❌ Plugin Portal credentials not configured")
    }

    private fun fetchPluginPage(pluginId: String): String {
        val url = "https://plugins.gradle.org/plugin/${java.net.URLEncoder.encode(pluginId, "UTF-8")}"
        val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("Accept", "text/html")
        connection.setRequestProperty("User-Agent", "XTC-Gradle-Build/1.0")

        return if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            throw GradleException("Failed to fetch plugin page: HTTP ${connection.responseCode}")
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            when {
                p1 > p2 -> return 1
                p1 < p2 -> return -1
            }
        }
        return 0
    }

    private fun provideSelfServiceGuidance(pluginId: String, version: String) {
        logger.lifecycle("")
        logger.lifecycle("🔗 Manual deletion instructions:")
        logger.lifecycle("   1. Go to: https://plugins.gradle.org/plugin/$pluginId")
        logger.lifecycle("   2. Find version '$version' in the version list")
        logger.lifecycle("   3. Click the 'Delete' button next to that version")
        logger.lifecycle("   4. Confirm the deletion when prompted")
        logger.lifecycle("")
        logger.lifecycle("⏰ Remember: Self-service deletion is only available for 7 days after publication")
    }

    private fun provideSupportGuidance(pluginId: String, version: String) {
        logger.lifecycle("")
        logger.lifecycle("📧 Support request required for older versions:")
        logger.lifecycle("   1. Contact Gradle support through the Plugin Portal")
        logger.lifecycle("   2. Request deletion of plugin '$pluginId' version '$version'")
        logger.lifecycle("   3. Provide justification for the deletion request")
        logger.lifecycle("   4. Wait for manual review by Gradle staff")
        logger.lifecycle("")
        logger.lifecycle("🌐 Plugin Portal: https://plugins.gradle.org/plugin/$pluginId")
    }

    private fun parsePluginPortalHtml(htmlResponse: String): List<PluginVersion> {
        val versions = mutableListOf<PluginVersion>()
        try {
            // Parse HTML to extract version info
            // Looking for pattern like: <h3>Version 0.4.5 (latest)</h3>
            val versionPattern = Regex("<h3>Version\\s+([^\\s]+)\\s*(?:\\(([^)]+)\\))?\\s*</h3>")
            val versionMatches = versionPattern.findAll(htmlResponse).toList()

            for (match in versionMatches) {
                val version = match.groupValues[1]
                val status = if (match.groupValues.size > 2 && match.groupValues[2].isNotBlank()) {
                    match.groupValues[2]
                } else {
                    "published"
                }

                versions.add(PluginVersion(version, status))
            }

            // Also look for version links like: <a href="/plugin/org.xtclang.xtc-plugin/0.4.4">0.4.4</a>
            val linkPattern = Regex("<a href=\"/plugin/[^/]+/([^\"]+)\">\\1</a>")
            val linkMatches = linkPattern.findAll(htmlResponse).toList()

            for (match in linkMatches) {
                val version = match.groupValues[1]
                // Only add if not already found (avoid duplicates)
                if (versions.none { it.version == version }) {
                    versions.add(PluginVersion(version, "published"))
                }
            }

            // If no version found in h3, try alternative patterns
            if (versions.isEmpty()) {
                // Try plugin-id-version pattern
                val altPattern = Regex("id=\"plugin-id-version\"[^>]*>([^<]+)<")
                val altMatch = altPattern.find(htmlResponse)
                if (altMatch != null) {
                    versions.add(PluginVersion(altMatch.groupValues[1], "found"))
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse Plugin Portal HTML response: ${e.message}")
        }

        return versions.sortedByDescending { it.version }
    }

    data class PluginVersion(val version: String, val status: String)
}