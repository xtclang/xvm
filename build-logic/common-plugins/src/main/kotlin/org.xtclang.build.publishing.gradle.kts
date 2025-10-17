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
// Configuration cache compatible - signing configuration uses standard Gradle property lookup
// Signing credentials should be provided via:
//   - signing.keyId, signing.password, signing.key (Gradle properties)
//   - Or signing.keyId, signing.password, signing.secretKeyRingFile
// The Vanniktech plugin handles this automatically
plugins.withType<SigningPlugin> {
    extensions.configure<SigningExtension> {
        // Signing configuration is handled by Gradle properties
        // No explicit credential evaluation needed here for configuration cache compatibility
        isRequired = false  // Don't fail build if signing is not configured
    }
}

// Determine if this is a snapshot or release version
val isSnapshot = publicationVersion.contains("SNAPSHOT", ignoreCase = true)
// Keep allowRelease as a Provider for configuration cache compatibility
val allowReleaseProvider = xdkPublishingCredentials.allowRelease

// Log publishing configuration (configuration-time logging, no provider evaluation)
logger.lifecycle("[publishing] Version: $publicationVersion ${if (isSnapshot) "(SNAPSHOT)" else "(RELEASE)"}")
if (isSnapshot) {
    logger.lifecycle("[publishing] SNAPSHOT: Publishing to GitHub Packages + Maven Central Snapshots")
    logger.lifecycle("[publishing] SNAPSHOT: Gradle Plugin Portal will be skipped (does not accept snapshots)")
} else {
    logger.lifecycle("[publishing] RELEASE: Use -Porg.xtclang.allowRelease=true to enable release publishing")
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
