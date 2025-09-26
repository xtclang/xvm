import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Task to validate GitHub and Plugin Portal credentials are available for publishing.
 * Provides detailed error messages with setup instructions when credentials are missing.
 */
abstract class ValidateCredentialsTask : DefaultTask() {
    @get:Input
    abstract val enableGitHub: Property<Boolean>

    @get:Input
    abstract val enablePluginPortal: Property<Boolean>

    @get:Input
    abstract val enableMavenCentral: Property<Boolean>

    @get:Input
    abstract val gitHubUsername: Property<String>

    @get:Input
    abstract val gitHubPassword: Property<String>

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
    abstract val signingSecretKey: Property<String>

    @get:Input
    @get:Optional
    abstract val signingInMemoryKey: Property<String>

    @TaskAction
    fun validate() {
        // Validate GitHub credentials (required only when GitHub publishing is enabled)
        if (!enableGitHub.get()) {
            logger.info("ℹ️  GitHub publishing is disabled - skipping credential validation")
        } else {
            val username = gitHubUsername.get()
            val password = gitHubPassword.get()

            if (password.isEmpty()) {
                throw GradleException("""
                    |GitHub credentials not available for publishing!
                    |
                    |Please provide credentials using one of these methods:
                    |
                    |1. Local development - Set properties in ~/.gradle/gradle.properties:
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
                    |  Username: ${if (username.isNotEmpty()) "✅ Available" else "❌ Missing"}
                    |  Password/Token: ${if (password.isNotEmpty()) "✅ Available" else "❌ Missing"}
                    """.trimMargin())
            }

            logger.info("✅ GitHub credentials validated successfully")
            logger.info("   Username: Available")
            logger.info("   Token: Available")
        }

        // Validate Plugin Portal credentials (only if enabled)
        if (!enablePluginPortal.get()) return

        val portalKey = gradlePublishKey.getOrElse("")
        val portalSecret = gradlePublishSecret.getOrElse("")

        if (portalKey.isEmpty() || portalSecret.isEmpty()) {
            throw GradleException("""
                |Gradle Plugin Portal credentials not available for publishing!
                |
                |Please provide credentials using one of these methods:
                |
                |1. Local development - Set properties in ~/.gradle/gradle.properties:
                |   gradle.publish.key=your-api-key
                |   gradle.publish.secret=your-api-secret
                |
                |2. Environment variables:
                |   GRADLE_PUBLISH_KEY=your-api-key
                |   GRADLE_PUBLISH_SECRET=your-api-secret
                |
                |3. Command line properties:
                |   ./gradlew publishRemote -Pgradle.publish.key=your-key -Pgradle.publish.secret=your-secret
                |
                |Get API keys from: https://plugins.gradle.org/ -> "My API Keys" -> Generate API Key
                |
                |Current status:
                |  API Key: ${if (portalKey.isNotEmpty()) "✅ Available" else "❌ Missing"}
                |  Secret: ${if (portalSecret.isNotEmpty()) "✅ Available" else "❌ Missing"}
            """.trimMargin())
        }

        logger.info("✅ Plugin Portal credentials validated successfully")
        logger.info("   API Key: Available")
        logger.info("   Secret: Available")

        // Validate Maven Central credentials (only if enabled)
        if (!enableMavenCentral.get()) return

        val username = mavenCentralUsername.getOrElse("")
        val password = mavenCentralPassword.getOrElse("")
        val keyId = signingKeyId.getOrElse("")
        val signingPass = signingPassword.getOrElse("")
        val secretKey = signingSecretKey.getOrElse("")
        val inMemoryKey = signingInMemoryKey.getOrElse("")

        val missingCredentials = mutableListOf<String>()
        if (username.isEmpty()) missingCredentials.add("Maven Central username")
        if (password.isEmpty()) missingCredentials.add("Maven Central password")
        // Vanniktech plugin needs either signingInMemoryKey or traditional signing setup
        if (inMemoryKey.isEmpty() && (keyId.isEmpty() || secretKey.isEmpty())) {
            missingCredentials.add("Signing configuration (either signingInMemoryKey or keyId+secretKeyRingFile)")
        }

        if (missingCredentials.isNotEmpty()) {
            throw GradleException("""
                |Maven Central credentials not available for publishing!
                |
                |Missing: ${missingCredentials.joinToString(", ")}
                |
                |Please provide credentials using one of these methods:
                |
                |1. Local development - Set properties in ~/.gradle/gradle.properties:
                |   mavenCentralUsername=your-maven-central-username
                |   mavenCentralPassword=your-maven-central-password
                |   signingInMemoryKey=base64-encoded-gpg-key
                |
                |   Or traditional signing:
                |   signing.keyId=your-gpg-key-id
                |   signing.password=your-gpg-key-password
                |   signing.secretKeyRingFile=/path/to/secring.gpg
                |
                |2. Environment variables:
                |   MAVEN_CENTRAL_USERNAME=your-maven-central-username
                |   MAVEN_CENTRAL_PASSWORD=your-maven-central-password
                |   SIGNING_IN_MEMORY_KEY=base64-encoded-gpg-key
                |
                |   Or traditional signing:
                |   SIGNING_KEY_ID=your-gpg-key-id
                |   SIGNING_PASSWORD=your-gpg-key-password
                |   SIGNING_SECRET_KEY_RING_FILE=/path/to/secring.gpg
                |
                |Alternative environment variable names (legacy support):
                |   SONATYPE_USERNAME, SONATYPE_PASSWORD, OSSRH_USERNAME, OSSRH_PASSWORD
                |
                |3. Command line properties:
                |   ./gradlew publishRemote -PmavenCentralUsername=... -PmavenCentralPassword=...
                |
                |Get Maven Central credentials from: https://central.sonatype.org/
                |
                |Current status:
                |  Username: ${if (username.isNotEmpty()) "✅ Available" else "❌ Missing"}
                |  Password: ${if (password.isNotEmpty()) "✅ Available" else "❌ Missing"}
                |  Signing (in-memory key): ${if (inMemoryKey.isNotEmpty()) "✅ Available" else "❌ Missing"}
                |  Signing (traditional keyId): ${if (keyId.isNotEmpty()) "✅ Available" else "❌ Missing"}
                |  Signing (traditional secretKey): ${if (secretKey.isNotEmpty()) "✅ Available" else "❌ Missing"}
            """.trimMargin())
        }

        logger.info("✅ Maven Central credentials validated successfully")
        logger.info("   Username: Available")
        logger.info("   Password: Available")
        if (inMemoryKey.isNotEmpty()) {
            logger.info("   Signing: Using in-memory key (Vanniktech format)")
        } else {
            logger.info("   Signing: Using traditional keyId + secretKeyRingFile")
        }
    }
}
