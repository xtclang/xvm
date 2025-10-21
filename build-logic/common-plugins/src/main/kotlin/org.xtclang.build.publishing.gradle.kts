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

// Bridge signing credentials from XdkPublishingCredentials to dotted project properties
// This allows both local (signing.keyId from gradle.properties) and CI (signing_keyId from ORG_GRADLE_PROJECT_)
// credentials to be visible to the Gradle signing plugin via standard signing.* property names.
// Note: Only set if not already defined (existing gradle.properties take precedence)
// IMPORTANT: For configuration cache compatibility, we must:
// 1. Get actual values from Providers (.orNull) instead of setting Provider objects
// 2. Only set non-empty values to avoid config cache serialization issues
// 3. Prefer in-memory keys over keyring files
if (!project.hasProperty("signing.keyId")) {
    val keyId = xdkPublishingCredentials.signingKeyId.orNull?.takeIf { it.isNotEmpty() }
    if (keyId != null) {
        project.extensions.extraProperties.set("signing.keyId", keyId)
    }
}
if (!project.hasProperty("signing.password")) {
    val password = xdkPublishingCredentials.signingPassword.orNull?.takeIf { it.isNotEmpty() }
    if (password != null) {
        project.extensions.extraProperties.set("signing.password", password)
    }
}
// Prefer in-memory key (signing.key) over keyring file for configuration cache compatibility
// The signing plugin evaluates keyring file paths during config cache serialization which breaks CI
if (!project.hasProperty("signing.key")) {
    val inMemoryKey = xdkPublishingCredentials.signingKey.orNull?.takeIf { it.isNotEmpty() }
    if (inMemoryKey != null) {
        project.extensions.extraProperties.set("signing.key", inMemoryKey)
    }
}
// Only set keyring file if in-memory key is not available AND the file path is non-empty
// This ensures we never try to use keyring files in CI environments
if (!project.hasProperty("signing.key") && !project.hasProperty("signing.secretKeyRingFile")) {
    val keyRingFile = xdkPublishingCredentials.signingSecretKeyRingFile.orNull?.takeIf { it.isNotEmpty() }
    if (keyRingFile != null) {
        project.extensions.extraProperties.set("signing.secretKeyRingFile", keyRingFile)
    }
}

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

    // Always configure Maven Central - credentials will be checked at task execution time
    // Configuration cache requires no eager evaluation of credentials
    publishToMavenCentral(automaticRelease = false)

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
            // Use implicit credentials for configuration cache compatibility
            // Gradle automatically looks for properties: githubUsername and githubPassword
            // CI should set: ORG_GRADLE_PROJECT_githubUsername and ORG_GRADLE_PROJECT_githubPassword
            // Or use the secret gradle properties file
            credentials(PasswordCredentials::class)
        }
    }
}

// Configure signing plugin if available (applied by Vanniktech plugin)
// The signing plugin automatically reads signing.* properties
// Our XdkPublishingCredentials supports both dotted (signing.keyId) and underscored (signing_keyId)
// versions, ensuring compatibility with local gradle.properties and CI env vars
plugins.withType<SigningPlugin> {
    extensions.configure<SigningExtension> {
        // Let the signing plugin handle credentials automatically via Gradle properties
        // The Vanniktech plugin will call useInMemoryPgpKeys if signing.key is available
        // or use keyring file if signing.secretKeyRingFile is available

        isRequired = false  // Don't fail build if signing is not configured
    }
}

/**
 * Logs the publishing configuration based on version type (snapshot or release).
 * Configuration-time logging with no provider evaluation for configuration cache compatibility.
 */
fun logPublishingConfiguration(logger: Logger, version: String, isSnapshot: Boolean) {
    logger.info("[publishing] Version: $version ${if (isSnapshot) "(SNAPSHOT)" else "(RELEASE)"}")
    if (isSnapshot) {
        logger.info("[publishing] SNAPSHOT: Publishing to GitHub Packages + Maven Central Snapshots")
        return
    }
    logger.warn("[publishing] Publication, if triggered, will run in release mode.")
}

// Determine if this is a snapshot or release version
// Keep allowRelease as a Provider for configuration cache compatibility
val isSnapshot = publicationVersion.contains("SNAPSHOT", ignoreCase = true)
val allowReleaseProvider = xdkPublishingCredentials.allowRelease

// Log publishing configuration
logPublishingConfiguration(logger, publicationVersion, isSnapshot)

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

// Make all publish tasks depend on validateCredentials to fail fast before publishing
tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(validateCredentials)
}
