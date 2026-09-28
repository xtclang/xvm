import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Centralized credential management extension that provides unified access to
 * publishing credentials from properties, environment variables, and property files.
 * Uses xdkProperties which automatically checks (in order):
 *   1. Environment variables (UPPERCASE_UNDERSCORE format)
 *   2. Gradle properties (-P or gradle.properties)
 *   3. System properties (-D)
 *   4. Property files (xdk.properties, version.properties, gradle.properties)
 */
abstract class XdkPublishingCredentials @Inject constructor(xdkProperties: ProjectXdkProperties) {
    // GitHub credentials - GITHUB_ACTOR and GITHUB_TOKEN env vars are automatically checked
    // Use zip to evaluate both providers and return first non-empty value
    val githubUsername: Provider<String> = xdkProperties.string("githubUsername", "")
        .zip(xdkProperties.string("github.actor", "")) { username, actor ->
            username.ifEmpty { actor }
        }
    val githubPassword: Provider<String> = xdkProperties.string("githubPassword", "")
        .zip(xdkProperties.string("github.token", "")) { password, token ->
            password.ifEmpty { token }
        }

    // Gradle Plugin Portal credentials
    val gradlePublishKey: Provider<String> = xdkProperties.string("gradle.publish.key", "")
    val gradlePublishSecret: Provider<String> = xdkProperties.string("gradle.publish.secret", "")

    // Maven Central credentials
    val mavenCentralUsername: Provider<String> = xdkProperties.string("mavenCentralUsername", "")
        .zip(xdkProperties.string("maven.central.username", "")) { username, fallback ->
            username.ifEmpty { fallback }
        }
    val mavenCentralPassword: Provider<String> = xdkProperties.string("mavenCentralPassword", "")
        .zip(xdkProperties.string("maven.central.password", "")) { password, fallback ->
            password.ifEmpty { fallback }
        }

    // Signing credentials
    // xdkProperties automatically checks both dotted and underscored Gradle properties
    // Local: uses signing.keyId from gradle.properties
    // CI: uses signing_keyId from ORG_GRADLE_PROJECT_signing_keyId
    val signingKeyId: Provider<String> = xdkProperties.string("signing.keyId", "")
    val signingPassword: Provider<String> = xdkProperties.string("signing.password", "")
    val signingSecretKeyRingFile: Provider<String> = xdkProperties.string("signing.secretKeyRingFile", "")

    // In-memory signing key with escaped newlines
    val signingKey: Provider<String> = xdkProperties.string("signing.key", "").map { it.replace("\\n", "\n") }

    // Legacy in-memory signing key (kept for compatibility)
    val signingInMemoryKey: Provider<String> = xdkProperties.string("signingInMemoryKey", "")

    // Publishing control
    // For SNAPSHOT versions: Always publish to Maven Central Snapshots, GitHub Packages, skip Gradle Plugin Portal
    // For release versions: Require -Porg.xtclang.allowRelease=true to publish (all targets including Gradle Plugin Portal)
    val allowRelease: Provider<Boolean> = xdkProperties.boolean("org.xtclang.allowRelease", false)
}

/**
 * Task to validate all publishing credentials without actually publishing.
 * Validates GitHub, Maven Central, Gradle Plugin Portal, and signing credentials.
 * Provides detailed error messages with setup instructions when credentials are missing.
 */
abstract class ValidateCredentialsTask : DefaultTask() {
    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val allowRelease: Property<Boolean>

    @get:Input
    abstract val githubUsername: Property<String>

    @get:Input
    abstract val githubPassword: Property<String>

    @get:Input
    @get:Optional
    abstract val gradlePublishKey: Property<String>

    @get:Input
    @get:Optional
    abstract val gradlePublishSecret: Property<String>

    @get:Input
    @get:Optional
    abstract val mavenCentralUsername: Property<String>

    @get:Input
    @get:Optional
    abstract val mavenCentralPassword: Property<String>

    @get:Input
    @get:Optional
    abstract val signingKeyId: Property<String>

    @get:Input
    @get:Optional
    abstract val signingPassword: Property<String>

    @get:Input
    @get:Optional
    abstract val signingSecretKeyRingFile: Property<String>

    @get:Input
    @get:Optional
    abstract val signingKey: Property<String>

    @get:Input
    @get:Optional
    abstract val signingInMemoryKey: Property<String>

    @TaskAction
    fun validate() {
        val project = projectName.get()
        val version = projectVersion.get()
        val isSnapshot = version.endsWith("-SNAPSHOT")
        val releaseAllowed = allowRelease.get()

        logger.lifecycle("🔐 Publishing Credentials Validation Report [$project]")
        logger.lifecycle("=".repeat(60))
        logger.lifecycle("")
        logger.lifecycle("Version: $version ${if (isSnapshot) "(SNAPSHOT)" else "(RELEASE)"}")

        if (!isSnapshot && !releaseAllowed) {
            logger.lifecycle("⚠️  Release version detected but publishing not allowed")
            logger.lifecycle("   Use -Porg.xtclang.allowRelease=true to enable release publishing")
            logger.lifecycle("")
            throw GradleException("Release publishing requires -Porg.xtclang.allowRelease=true")
        }

        logger.lifecycle("")

        var hasErrors = false
        val errors = mutableListOf<String>()

        // 1. Validate GitHub Packages credentials (always required)
        logger.lifecycle("📦 GitHub Packages (always enabled)")
        val username = githubUsername.get()
        val password = githubPassword.get()

        logger.lifecycle("   Username: ${if (username.isNotEmpty()) "✅ Available" else "❌ Missing"}")
        logger.lifecycle("   Token:    ${if (password.isNotEmpty()) "✅ Available" else "❌ Missing"}")

        if (password.isEmpty()) {
            hasErrors = true
            errors.add(
                """
                |GitHub Packages credentials missing!
                |Set in ~/.gradle/gradle.properties:
                |  githubUsername=your-username
                |  githubPassword=your-personal-access-token
                |Or use environment variables: GITHUB_ACTOR, GITHUB_TOKEN
                """.trimMargin()
            )
        }

        // 2. Validate Maven Central credentials (always required)
        logger.lifecycle("🏛️  Maven Central (always enabled)")
        val mcUsername = mavenCentralUsername.getOrElse("")
        val mcPassword = mavenCentralPassword.getOrElse("")

        logger.lifecycle("   Username: ${if (mcUsername.isNotEmpty()) "✅ Available" else "❌ Missing"}")
        logger.lifecycle("   Password: ${if (mcPassword.isNotEmpty()) "✅ Available" else "❌ Missing"}")
        if (isSnapshot) {
            logger.lifecycle("   Target:   Snapshots repository")
        } else {
            logger.lifecycle("   Target:   Staging repository (requires manual promotion)")
        }

        if (mcUsername.isEmpty() || mcPassword.isEmpty()) {
            hasErrors = true
            errors.add(
                """
                |Maven Central credentials missing!
                |Set in ~/.gradle/gradle.properties:
                |  mavenCentralUsername=your-username
                |  mavenCentralPassword=your-password
                |Get from: https://central.sonatype.com/account -> Generate User Token
                """.trimMargin()
            )
        }

        // 3. Validate Gradle Plugin Portal credentials
        logger.lifecycle("🔌 Gradle Plugin Portal")
        if (isSnapshot) {
            logger.lifecycle("   Status: ⏭️  Skipped for SNAPSHOT versions (Portal does not accept snapshots)")
        } else {
            logger.lifecycle("   Status: ✅ Enabled for release version")
            val portalKey = gradlePublishKey.getOrElse("")
            val portalSecret = gradlePublishSecret.getOrElse("")
            logger.lifecycle("   API Key:  ${if (portalKey.isNotEmpty()) "✅ Available" else "❌ Missing"}")
            logger.lifecycle("   Secret:   ${if (portalSecret.isNotEmpty()) "✅ Available" else "❌ Missing"}")
            if (portalKey.isEmpty() || portalSecret.isEmpty()) {
                hasErrors = true
                errors.add("""
                    |Gradle Plugin Portal credentials missing!
                    |Set in ~/.gradle/gradle.properties:
                    |  gradle.publish.key=your-api-key
                    |  gradle.publish.secret=your-api-secret
                    |Get from: https://plugins.gradle.org/ -> "My API Keys"
                    """.trimMargin())
            }
        }

        // 4. Maven Local (always available, no credentials needed)
        logger.lifecycle("💾 Maven Local")
        logger.lifecycle("   Status: ✅ Always available (no credentials needed)")
        logger.lifecycle("   Path:   ~/.m2/repository")

        // 5. Validate Signing credentials (at the end, after publishing locations)
        logger.lifecycle("")
        logger.lifecycle("✍️  Artifact Signing")

        val keyId = signingKeyId.getOrElse("")
        val signingPass = signingPassword.getOrElse("")
        val keyRingFile = signingSecretKeyRingFile.getOrElse("")
        val key = signingKey.getOrElse("")
        val inMemoryKey = signingInMemoryKey.getOrElse("")

        logger.lifecycle("   Key ID:           ${if (keyId.isNotEmpty()) "✅ Available" else "⚠️  Not set"}")
        logger.lifecycle("   Password:         ${if (signingPass.isNotEmpty()) "✅ Available" else "⚠️  Not set"}")
        logger.lifecycle("   Key Ring File:    ${if (keyRingFile.isNotEmpty()) "✅ Available ($keyRingFile)" else "⚠️  Not set"}")
        logger.lifecycle("   In-Memory Key:    ${if (key.isNotEmpty()) "✅ Available (signing.key)" else if (inMemoryKey.isNotEmpty()) "✅ Available (signingInMemoryKey)" else "⚠️  Not set"}")

        val hasKeyRing = keyRingFile.isNotEmpty()
        val hasInMemoryKey = key.isNotEmpty() || inMemoryKey.isNotEmpty()
        val hasKeySource = hasKeyRing || hasInMemoryKey
        val signingConfigured = keyId.isNotEmpty() && hasKeySource

        if (!signingConfigured) {
            val missingParts = mutableListOf<String>()
            if (keyId.isEmpty()) missingParts.add("keyId")
            if (!hasKeySource) missingParts.add("key source (keyRingFile or in-memory key)")

            logger.lifecycle("   ⚠️  Signing not fully configured - missing: ${missingParts.joinToString(", ")}")
            if (signingPass.isEmpty() && hasKeySource) {
                logger.lifecycle("   ℹ️  Password not set (OK if using passwordless key)")
            }

            hasErrors = true
            errors.add("""
                |❌ Signing credentials required for Maven Central but incomplete!
                |   Missing: ${missingParts.joinToString(", ")}
                |
                |Option 1 - Key Ring File (local development):
                |  signing.keyId=your-key-id (8-char short ID or full fingerprint)
                |  signing.password=your-key-password
                |  signing.secretKeyRingFile=/path/to/secring.gpg
                |
                |Option 2 - In-Memory Key (recommended for CI):
                |  Export: gpg --export-secret-keys --armor KEYID > signing-key.asc
                |  Then set in ~/.gradle/gradle.properties with escaped newlines:
                |  signing.keyId=your-key-id
                |  signing.password=your-key-password
                |  signing.key=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
                """.trimMargin())
        } else {
            logger.lifecycle("   ✅ Signing fully configured")
            logger.lifecycle("   Will sign all artifacts for Maven Central and GitHub Packages")
        }

        // Final summary
        logger.lifecycle("")
        logger.lifecycle("=".repeat(60))

        if (hasErrors) {
            logger.lifecycle("")
            logger.lifecycle("❌ Validation Failed - Missing Required Credentials")
            logger.lifecycle("")
            errors.forEach { error ->
                logger.lifecycle(error)
                logger.lifecycle("")
            }
            throw GradleException("Publishing credentials validation failed. See above for details.")
        }
        logger.lifecycle("✅ All publishing credentials validated")
        logger.lifecycle("")
        logger.lifecycle("Ready to publish to:")
        logger.lifecycle("  • GitHub Packages")
        logger.lifecycle("  • Maven Central ${if (isSnapshot) "Snapshots" else "Staging (requires manual promotion)"}")
        if (!isSnapshot) {
            logger.lifecycle("  • Gradle Plugin Portal")
        }
        logger.lifecycle("  • Maven Local")
    }
}
