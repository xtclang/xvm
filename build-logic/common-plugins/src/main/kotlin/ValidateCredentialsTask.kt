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
    abstract val gitHubUsername: Property<String>

    @get:Input
    abstract val gitHubPassword: Property<String>

    @get:Input
    abstract val enableGitHub: Property<Boolean>

    @get:Input
    abstract val enablePluginPortal: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val gradlePublishKey: Property<String>

    @get:Input
    @get:Optional
    abstract val gradlePublishSecret: Property<String>

    @TaskAction
    fun validate() {
        // Validate GitHub credentials (required only when GitHub publishing is enabled)
        if (enableGitHub.get()) {
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
                |  Username: ${if (username.isNotEmpty()) "✅ Available ($username)" else "❌ Missing"}
                |  Password/Token: ${if (password.isNotEmpty()) "✅ Available" else "❌ Missing"}
                """.trimMargin())
            }

            logger.lifecycle("✅ GitHub credentials validated successfully")
            logger.lifecycle("   Username: $username")
            logger.lifecycle("   Token: Available (${password.take(8)}...)")
        } else {
            logger.lifecycle("ℹ️  GitHub publishing is disabled - skipping credential validation")
        }

        // Validate Plugin Portal credentials (only if enabled)
        if (enablePluginPortal.get()) {
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
                    |  API Key: ${if (portalKey.isNotEmpty()) "✅ Available (${portalKey.take(8)}...)" else "❌ Missing"}
                    |  Secret: ${if (portalSecret.isNotEmpty()) "✅ Available" else "❌ Missing"}
                """.trimMargin())
            }

            logger.lifecycle("✅ Plugin Portal credentials validated successfully")
            logger.lifecycle("   API Key: Available (${portalKey.take(8)}...)")
            logger.lifecycle("   Secret: Available")
        }
    }
}