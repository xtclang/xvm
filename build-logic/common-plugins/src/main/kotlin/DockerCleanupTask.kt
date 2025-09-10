import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

// Docker cleanup task for configuration cache compatibility
abstract class DockerCleanupTask @Inject constructor() : DefaultTask() {
    
    @get:Input
    @get:Optional
    abstract val keepCount: Property<Int>
    
    @get:Input
    @get:Optional 
    abstract val dryRun: Property<Boolean>
    
    @get:Input
    @get:Optional
    abstract val forced: Property<Boolean>
    
    @get:Input
    abstract val packageName: Property<String>
    
    @get:Inject
    abstract val execOperations: ExecOperations
    
    init {
        keepCount.convention(10)
        dryRun.convention(false)
        forced.convention(false)
        packageName.convention("xvm")
    }
    
    @TaskAction
    fun cleanupImages() {
        logger.lifecycle("🧹 Docker Package Cleanup")
        logger.lifecycle("=".repeat(50))
        
        val pkgName = packageName.get()
        val isDryRun = dryRun.get()
        val keep = keepCount.get()
        val isForced = forced.get()
        
        if (isDryRun) logger.lifecycle("🔍 DRY RUN MODE")
        
        val githubToken = System.getenv("GITHUB_TOKEN")
        val versions = fetchPackageVersions(pkgName, githubToken)
        if (versions.isEmpty()) {
            logger.lifecycle("❌ No versions found")
            return
        }
        
        val parsedVersions = versions.mapNotNull { parseImageVersion(it) }.sortedByDescending { it.created }
        val masterImages = parsedVersions.filter { it.isMasterImage }
        val masterToKeep = if (masterImages.isNotEmpty()) listOf(masterImages.first()) else emptyList()
        
        val allToKeep = mutableSetOf<ImageVersion>().apply {
            addAll(masterToKeep)
            addAll(parsedVersions.take(keep))
        }.sortedByDescending { it.created }
        
        val toDelete = parsedVersions.filter { it !in allToKeep }
        val masterInKeep = allToKeep.filter { it.isMasterImage }
        
        if (masterInKeep.isEmpty() && masterImages.isNotEmpty()) {
            logger.lifecycle("❌ SAFETY CHECK FAILED: Would delete all master images!")
            return
        }
        
        logger.lifecycle("📦 Package: $pkgName (${parsedVersions.size} total)")
        logger.lifecycle("✅ Keeping: ${allToKeep.size} versions (${masterInKeep.size} master)")
        logger.lifecycle("🗑️  Deleting: ${toDelete.size} versions")
        
        if (toDelete.isEmpty()) {
            logger.lifecycle("✅ No cleanup needed")
            return
        }
        
        if (isDryRun) {
            logger.lifecycle("🔍 DRY RUN COMPLETE")
            return
        }
        
        val needsConfirmation = !(System.getenv("CI") == "true") && !isForced
        if (needsConfirmation) {
            logger.lifecycle("❓ Delete ${toDelete.size} versions? (Type 'yes' to confirm)")
            val response = readlnOrNull()?.trim()?.lowercase()
            if (response != "yes") {
                logger.lifecycle("❌ Cancelled")
                return
            }
        }
        
        // Execute deletions
        var deleted = 0
        
        toDelete.forEach { version ->
            val result = execGhApiDelete("orgs/xtclang/packages/container/$pkgName/versions/${version.id}", githubToken)
            if (result.isSuccessful()) {
                deleted++
                logger.lifecycle("✅ Deleted version ${version.id} (tags: ${version.tags})")
            } else {
                logger.warn("❌ Delete failed for version ${version.id}: ${result.output}")
            }
        }
        
        logger.lifecycle("🎯 Attempted deletions: $deleted/${toDelete.size} versions")
        
        // Verify deletions
        logger.lifecycle("🔍 Verifying deletions...")
        var actuallyDeleted = 0
        val maxRetries = 3
        
        for (attempt in 1..maxRetries) {
            Thread.sleep(if (attempt > 1) 5000L else 1000L)
            
            val remainingVersions = fetchPackageVersions(pkgName, githubToken)
            val remainingIds = remainingVersions.mapNotNull { parseImageVersion(it)?.id }.toSet()
            actuallyDeleted = toDelete.count { it.id !in remainingIds }
            
            logger.lifecycle("📊 Attempt $attempt: $actuallyDeleted/${toDelete.size} deletions confirmed")
            
            if (actuallyDeleted == toDelete.size) {
                logger.lifecycle("🎉 All deletions verified! Final count: ${remainingVersions.size} versions remaining")
                break
            }
            
            if (attempt < maxRetries) {
                logger.lifecycle("⏳ Waiting 5s for API consistency...")
            }
        }
        
        if (actuallyDeleted != toDelete.size) {
            logger.warn("⚠️  Package cleanup incomplete: $actuallyDeleted/${toDelete.size} deletions confirmed after $maxRetries attempts")
            logger.warn("⚠️  This may be due to GitHub API delays, permission issues, or network problems")
            logger.warn("✅  Continuing build - package cleanup is not critical for build success")
        }
    }
    
    private fun execGhApiDelete(apiPath: String, token: String?): ProcessResult {
        val cmd = mutableListOf("gh", "api", "-X", "DELETE", apiPath)
        
        val env = mutableMapOf<String, String>()
        if (token != null) {
            env["GITHUB_TOKEN"] = token
        }
        
        return try {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                commandLine(cmd)
                environment(env)
                standardOutput = output
                isIgnoreExitValue = true
            }
            ProcessResult(0, output.toString().trim())
        } catch (e: Exception) {
            ProcessResult(-1, e.message ?: "Unknown error", e)
        }
    }
    
    // Utility functions for package management
    private fun fetchPackageVersions(packageName: String, token: String?): List<Map<String, Any?>> {
        val cmd = mutableListOf("gh", "api", "orgs/xtclang/packages/container/$packageName/versions", "--jq", ".")
        
        val env = mutableMapOf<String, String>()
        if (token != null) {
            env["GITHUB_TOKEN"] = token
        }
        
        return try {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                commandLine(cmd)
                environment(env)
                standardOutput = output
                isIgnoreExitValue = true
            }
            
            val result = output.toString().trim()
            if (result.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                groovy.json.JsonSlurper().parseText(result) as List<Map<String, Any?>>
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch package versions: ${e.message}")
            emptyList()
        }
    }
    
    private data class ImageVersion(
        val id: Int,
        val tags: List<String>,
        val lastUpdated: String,
        val downloadCount: Int,
        val size: Long
    )
    
    private val ImageVersion.isMasterImage: Boolean
        get() = tags.any { it.startsWith("latest") || it == "master" }
    
    private val ImageVersion.created: String
        get() = lastUpdated
    
    private fun parseImageVersion(versionData: Map<String, Any?>): ImageVersion? {
        return try {
            val id = (versionData["id"] as Number).toInt()
            val tags = ((versionData["metadata"] as Map<*, *>?)?.get("container") as Map<*, *>?)?.get("tags") as List<*>? 
                ?: emptyList<String>()
            val created = versionData["created_at"] as String? ?: ""
            val downloadCount = (versionData["download_count"] as Number?)?.toInt() ?: 0
            val size = (versionData["size"] as Number?)?.toLong() ?: 0L
            
            ImageVersion(
                id = id,
                tags = tags.filterIsInstance<String>(),
                lastUpdated = created,
                downloadCount = downloadCount,
                size = size
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private data class ProcessResult(
        val exitValue: Int,
        val output: String,
        val exception: Exception? = null
    ) {
        fun isSuccessful(): Boolean = exitValue == 0
    }
}