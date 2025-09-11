import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.MavenPomLicense
import org.gradle.api.publish.maven.MavenPomDeveloper
import org.gradle.api.tasks.bundling.Zip

/**
 * Binary plugin that handles Maven publications in a configuration cache compatible way.
 * This plugin is completely isolated from script contexts to avoid any serialization issues.
 */
class MavenPublicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply required plugins
        project.pluginManager.apply("maven-publish")
        
        // Configure publishing extension using binary plugin approach
        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        
        // Extract all values outside lambda to avoid project reference capture
        val projectGroupId = project.group.toString()
        val projectArtifactId = project.name  
        val projectVersion = project.version.toString()
        
        // Create XDK-specific publication if this is the XDK project
        if (project.name == "xdk") {
            publishing.publications.register("xdkArchive", MavenPublication::class.java) {
                groupId = projectGroupId
                artifactId = projectArtifactId
                version = projectVersion
                
                // Configure POM
                pom {
                    name.set("xdk")
                    description.set("XTC Language Software Development Kit (XDK) Distribution Archive")
                    url.set("https://xtclang.org")
                    
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
                
                // Add artifact from distZip task
                val distZipTask = project.tasks.getByName("distZip") as Zip
                artifact(distZipTask)
            }
        }
        
        // Configure all maven publications with common settings
        publishing.publications.withType(MavenPublication::class.java).configureEach {
            // Configure POM with common settings for all projects
            pom {
                // Set URL if not already set
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
}