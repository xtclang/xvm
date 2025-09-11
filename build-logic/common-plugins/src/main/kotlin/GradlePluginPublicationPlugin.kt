import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

/**
 * Binary plugin specifically for Gradle Plugin projects to handle configuration cache compatibility
 * for gradle plugin publications created by the gradle-plugin-publish plugin.
 */
class GradlePluginPublicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply required plugins
        project.pluginManager.apply("maven-publish")
        
        // Configure publishing extension using binary plugin approach
        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        
        // Extract all values outside any lambda to avoid project reference capture
        val projectGroupId = project.group.toString()
        val projectArtifactId = project.name  
        val projectVersion = project.version.toString()
        
        // Configure publications created by gradle-plugin-publish plugin
        project.afterEvaluate {
            publishing.publications.withType(MavenPublication::class.java).configureEach {
                // Ensure all publications have the extracted values instead of project references
                if (groupId == project.group.toString()) {
                    groupId = projectGroupId
                }
                if (artifactId == project.name) {
                    artifactId = projectArtifactId
                }
                if (version == project.version.toString()) {
                    version = projectVersion
                }
                
                // Configure POM with static values
                pom {
                    if (url.orNull?.isEmpty() != false) {
                        url.set("https://xtclang.org")
                    }
                    
                    licenses {
                        license {
                            name.set("The XDK License")
                            url.set("https://github.com/xtclang/xvm/tree/master/license")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("xtclang-workflows")
                            name.set("XTC Team")
                            email.set("noreply@xtclang.org")
                        }
                    }
                }
            }
        }
        
        // Create publishLocal task that was removed when we stopped applying the publish plugin
        project.afterEvaluate {
            val publishAllPublicationsToMavenLocalRepository = project.tasks.named("publishAllPublicationsToMavenLocalRepository")
            
            project.tasks.register("publishLocal") {
                group = PUBLISH_TASK_GROUP
                description = "Task that publishes project publications to local repositories (e.g. mavenLocal)."
                dependsOn(publishAllPublicationsToMavenLocalRepository)
            }
        }
    }
}