import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.gradle.plugins.signing.SigningExtension

/**
 * XDK Publishing Convention
 *
 * Provides centralized credential management and common publishing configuration
 * for publishing to various repositories. Consolidates common logic shared between
 * plugin and xdk projects.
 *
 * Registers the `xdkPublishingCredentials` extension for unified access to publishing
 * credentials from properties and environment variables.
 *
 * Projects can customize the POM name and description by calling:
 * ```
 * xdkPublishing {
 *     pomName.set("Custom Name")
 *     pomDescription.set("Custom Description")
 * }
 * ```
 *
 * Automatically creates a `validateCredentials` task for validating publishing
 * credentials without actually publishing.
 */

plugins {
    id("org.xtclang.build.xdk.properties")
    id("com.vanniktech.maven.publish")
}

val xdkPublishingCredentials = extensions.create<XdkPublishingCredentials>(
    "xdkPublishingCredentials",
    xdkProperties
)

/**
 * Extension for customizing publishing POM metadata.
 */
abstract class XdkPublishingExtension {
    abstract val pomName: Property<String>
    abstract val pomDescription: Property<String>
}

val xdkPublishing = extensions.create<XdkPublishingExtension>("xdkPublishing")

// Get coordinates from project (automatically set by properties plugin)
val publicationGroupId = project.group.toString()
val publicationArtifactId = project.name
val publicationVersion = project.version.toString()

// Configure Vanniktech Maven Publish with common settings
extensions.configure<MavenPublishBaseExtension> {
    signAllPublications()
    coordinates(publicationGroupId, publicationArtifactId, publicationVersion)

    // Only configure Maven Central if credentials are available (optional for non-publishing builds)
    val hasMavenCreds = xdkPublishingCredentials.mavenCentralUsername.orNull?.isNotEmpty() == true &&
                       xdkPublishingCredentials.mavenCentralPassword.orNull?.isNotEmpty() == true
    if (hasMavenCreds) {
        publishToMavenCentral(automaticRelease = false)
    } else {
        logger.info("[publishing] Maven Central not configured - missing credentials (skipped at configuration time)")
    }

    // Common POM configuration
    pom {
        // Allow projects to customize name and description
        name.convention(xdkPublishing.pomName)
        description.convention(xdkPublishing.pomDescription)
        url.set("https://xtclang.org")

        licenses {
            license {
                name.set("The XDK License")
                url.set("https://github.com/xtclang/xvm/tree/master/license")
            }
        }

        developers {
            developer {
                id.set("xtclang")
                name.set("XTC Team")
                email.set("noreply@xtclang.org")
            }
        }
    }
}

// Always register GitHub Packages repository (will be skipped at task level if disabled)
extensions.configure<PublishingExtension> {
    repositories {
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/xtclang/xvm")
            // Credentials are configuration cache inputs and evaluated at configuration time
            credentials {
                username = xdkPublishingCredentials.githubUsername.get().ifEmpty { "xtclang" }
                password = xdkPublishingCredentials.githubPassword.get()
            }
        }
    }
}

// Configure signing plugin if available (applied by Vanniktech plugin)
plugins.withType<SigningPlugin> {
    extensions.configure<SigningExtension> {
        val creds = xdkPublishingCredentials

        // Try to get the in-memory key - prefer signing.key over legacy signingInMemoryKey
        val inMemoryKey = creds.signingKey.orElse(creds.signingInMemoryKey).orElse("")
        val keyId = creds.signingKeyId.orElse("")
        val password = creds.signingPassword.orElse("")  // Can be empty for keys without passwords

        // Check if we have minimum required credentials for in-memory signing (key and keyId)
        val hasInMemoryKey = inMemoryKey.map { it.isNotEmpty() }.get()
        val hasKeyId = keyId.map { it.isNotEmpty() }.get()

        // Configure in-memory signing if we have both key and keyId
        if (hasInMemoryKey && hasKeyId) {
            useInMemoryPgpKeys(keyId.get(), inMemoryKey.get(), password.get())
            logger.info("[publishing] Signing configured with in-memory GPG key (keyId: ${keyId.get()}, password: ${if (password.get().isEmpty()) "none" else "set"})")
            return@configure
        }

        // Configure keyring file signing if available
        val keyRingFile = creds.signingSecretKeyRingFile.get()
        if (hasKeyId && keyRingFile.isNotEmpty()) {
            logger.info("[publishing] Signing configured with keyring file: $keyRingFile (keyId: ${keyId.get()})")
            return@configure
        }

        // No signing configured
        logger.info("[publishing] Signing not configured - missing credentials (hasKey=${hasInMemoryKey}, hasKeyId=${hasKeyId})")
    }
}

// Determine if this is a snapshot or release version
val isSnapshot = publicationVersion.contains("SNAPSHOT", ignoreCase = true)
val allowRelease = xdkPublishingCredentials.allowRelease.get()

// Log publishing configuration
logger.lifecycle("[publishing] Version: $publicationVersion ${if (isSnapshot) "(SNAPSHOT)" else "(RELEASE)"}")
if (isSnapshot) {
    logger.lifecycle("[publishing] SNAPSHOT: Publishing to GitHub Packages + Maven Central Snapshots")
    logger.lifecycle("[publishing] SNAPSHOT: Gradle Plugin Portal will be skipped (does not accept snapshots)")
} else {
    if (allowRelease) {
        logger.lifecycle("[publishing] RELEASE: Publishing enabled (allowRelease=true)")
        logger.lifecycle("[publishing] RELEASE: Will publish to GitHub Packages + Maven Central Staging + Gradle Plugin Portal")
    } else {
        logger.lifecycle("[publishing] RELEASE: Publishing BLOCKED (allowRelease=false)")
        logger.lifecycle("[publishing] RELEASE: Use -Porg.xtclang.allowRelease=true to enable")
    }
}

// Configure publishing tasks to skip gracefully when credentials are missing or releases not allowed
tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        // For releases, check allowRelease flag
        if (!isSnapshot && !allowRelease) {
            logger.lifecycle("[publishing] ⏭️  Skipping $name - release publishing requires -Porg.xtclang.allowRelease=true")
            return@onlyIf false
        }

        // Check for required credentials based on repository
        val repoName = repository?.name ?: "unknown"
        when (repoName.lowercase()) {
            "github" -> {
                val hasGithubCreds = xdkPublishingCredentials.githubPassword.get().isNotEmpty()
                if (!hasGithubCreds) {
                    logger.lifecycle("[publishing] ⏭️  Skipping $name - GitHub credentials not configured")
                    logger.lifecycle("[publishing]     Set githubPassword or GITHUB_TOKEN to enable GitHub Packages publishing")
                    return@onlyIf false
                }
            }
            "sonatype" -> {
                val hasMavenCreds = xdkPublishingCredentials.mavenCentralUsername.get().isNotEmpty() &&
                                   xdkPublishingCredentials.mavenCentralPassword.get().isNotEmpty()
                if (!hasMavenCreds) {
                    logger.lifecycle("[publishing] ⏭️  Skipping $name - Maven Central credentials not configured")
                    logger.lifecycle("[publishing]     Set mavenCentralUsername and mavenCentralPassword to enable Maven Central publishing")
                    return@onlyIf false
                }
            }
        }

        true
    }
}

// Register validateCredentials task for credential validation
val validateCredentials by tasks.registering(ValidateCredentialsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Validate all publishing credentials (GitHub, Maven Central, Plugin Portal, Signing) without publishing"
    // Set project info for output (configuration-cache safe)
    projectName.set(project.name)
    projectVersion.set(project.version.toString())
    allowRelease.set(xdkPublishingCredentials.allowRelease)
    githubUsername.set(xdkPublishingCredentials.githubUsername)
    githubPassword.set(xdkPublishingCredentials.githubPassword)
    gradlePublishKey.set(xdkPublishingCredentials.gradlePublishKey)
    gradlePublishSecret.set(xdkPublishingCredentials.gradlePublishSecret)
    mavenCentralUsername.set(xdkPublishingCredentials.mavenCentralUsername)
    mavenCentralPassword.set(xdkPublishingCredentials.mavenCentralPassword)
    signingKeyId.set(xdkPublishingCredentials.signingKeyId)
    signingPassword.set(xdkPublishingCredentials.signingPassword)
    signingSecretKeyRingFile.set(xdkPublishingCredentials.signingSecretKeyRingFile)
    signingKey.set(xdkPublishingCredentials.signingKey)
    signingInMemoryKey.set(xdkPublishingCredentials.signingInMemoryKey)
}
