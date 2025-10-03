import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task to validate all publishing credentials without actually publishing.
 * Validates GitHub, Maven Central, Gradle Plugin Portal, and signing credentials.
 * Provides detailed error messages with setup instructions when credentials are missing.
 */
abstract class ValidateCredentialsTask : DefaultTask() {
    @get:Input
    abstract val enableGithub: Property<Boolean>

    @get:Input
    abstract val enablePluginPortal: Property<Boolean>

    @get:Input
    abstract val enableMavenCentral: Property<Boolean>

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
        logger.lifecycle("")
        logger.lifecycle("🔐 Publishing Credentials Validation Report")
        logger.lifecycle("=".repeat(60))
        logger.lifecycle("")

        var hasErrors = false
        val errors = mutableListOf<String>()

        // 1. Validate GitHub Packages credentials
        logger.lifecycle("📦 GitHub Packages")
        val githubEnabled = enableGithub.get()
        if (githubEnabled) {
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
        } else {
            logger.lifecycle("   Status: ⏭️  Disabled (use -Porg.xtclang.publish.github=true to enable)")
        }

        // 2. Validate Maven Central credentials
        logger.lifecycle("")
        logger.lifecycle("🏛️  Maven Central")
        val mavenCentralEnabled = enableMavenCentral.get()
        if (mavenCentralEnabled) {
            val mcUsername = mavenCentralUsername.getOrElse("")
            val mcPassword = mavenCentralPassword.getOrElse("")

            logger.lifecycle("   Username: ${if (mcUsername.isNotEmpty()) "✅ Available" else "❌ Missing"}")
            logger.lifecycle("   Password: ${if (mcPassword.isNotEmpty()) "✅ Available" else "❌ Missing"}")

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
        } else {
            logger.lifecycle("   Status: ⏭️  Disabled (use -Porg.xtclang.publish.mavenCentral=true to enable)")
        }

        // 3. Validate Gradle Plugin Portal credentials
        logger.lifecycle("")
        logger.lifecycle("🔌 Gradle Plugin Portal")
        val portalEnabled = enablePluginPortal.get()
        if (portalEnabled) {
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
        } else {
            logger.lifecycle("   Status: ⏭️  Disabled (use -Porg.xtclang.publish.gradlePluginPortal=true to enable)")
        }

        // 4. Maven Local (always available, no credentials needed)
        logger.lifecycle("")
        logger.lifecycle("💾 Maven Local")
        logger.lifecycle("   Status: ✅ Always available (no credentials needed)")
        logger.lifecycle("   Path:   ~/.m2/repository")

        // 5. Validate Signing credentials (at the end, after publishing locations)
        logger.lifecycle("")
        logger.lifecycle("✍️  Artifact Signing")

        val keyId = signingKeyId.getOrElse("")
        val password = signingPassword.getOrElse("")
        val keyRingFile = signingSecretKeyRingFile.getOrElse("")
        val key = signingKey.getOrElse("")
        val inMemoryKey = signingInMemoryKey.getOrElse("")

        logger.lifecycle("   Key ID:           ${if (keyId.isNotEmpty()) "✅ Available" else "⚠️  Not set"}")
        logger.lifecycle("   Password:         ${if (password.isNotEmpty()) "✅ Available" else "⚠️  Not set"}")
        logger.lifecycle("   Key Ring File:    ${if (keyRingFile.isNotEmpty()) "✅ Available ($keyRingFile)" else "⚠️  Not set"}")
        logger.lifecycle("   In-Memory Key:    ${if (key.isNotEmpty()) "✅ Available (signing.key)" else if (inMemoryKey.isNotEmpty()) "✅ Available (signingInMemoryKey)" else "⚠️  Not set"}")

        val hasKeyRing = keyRingFile.isNotEmpty()
        val hasInMemoryKey = key.isNotEmpty() || inMemoryKey.isNotEmpty()
        val hasKeySource = hasKeyRing || hasInMemoryKey
        // Password can be empty for passwordless keys (common with in-memory keys in CI)
        // We just need keyId and a key source (keyRingFile or in-memory key)
        val signingConfigured = keyId.isNotEmpty() && hasKeySource

        // Determine which repositories require signing
        val repositoriesRequiringSigning = mutableListOf<String>()
        if (mavenCentralEnabled) repositoriesRequiringSigning.add("Maven Central")
        if (githubEnabled) repositoriesRequiringSigning.add("GitHub Packages (recommended)")

        if (!signingConfigured) {
            // Determine what's missing
            val missingParts = mutableListOf<String>()
            if (keyId.isEmpty()) missingParts.add("keyId")
            if (!hasKeySource) missingParts.add("key source (keyRingFile or in-memory key)")
            // Note: password is optional for passwordless keys

            logger.lifecycle("   ⚠️  Signing not fully configured - missing: ${missingParts.joinToString(", ")}")
            if (password.isEmpty() && hasKeySource) {
                logger.lifecycle("   ℹ️  Password not set (OK if using passwordless key)")
            }
            if (repositoriesRequiringSigning.isNotEmpty()) {
                logger.lifecycle("   Recommended for: ${repositoriesRequiringSigning.joinToString(", ")}")
            }

            // Only error if Maven Central is enabled (strict requirement)
            if (mavenCentralEnabled) {
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
            } else if (githubEnabled) {
                logger.lifecycle("   ℹ️  Signing recommended for GitHub Packages but not strictly required")
            } else {
                logger.lifecycle("   ℹ️  Signing not required for currently enabled repositories")
            }
        } else {
            logger.lifecycle("   ✅ Signing fully configured")
            if (repositoriesRequiringSigning.isNotEmpty()) {
                logger.lifecycle("   Will sign artifacts for enabled publishers: ${repositoriesRequiringSigning.joinToString(", ")}")
            }
        }

        // Final summary
        logger.lifecycle("")
        logger.lifecycle("=" .repeat(60))

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
        logger.lifecycle("✅ All enabled publishing targets have valid credentials")
        logger.lifecycle("")
        logger.lifecycle("Ready to publish to:")
        if (githubEnabled) logger.lifecycle("  • GitHub Packages")
        if (mavenCentralEnabled) logger.lifecycle("  • Maven Central (with signing)")
        if (portalEnabled) logger.lifecycle("  • Gradle Plugin Portal")
        logger.lifecycle("  • Maven Local")
    }
}
