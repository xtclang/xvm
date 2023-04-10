
import org.gradle.api.Project

fun Project.zgetBooleanProperty(name: String, defaultValue: Boolean = false): Boolean {
    return if (project.hasProperty(name)) project.property(name).toString().toBoolean() else defaultValue
}

fun geg2() {
    prinln("GEGGA")
}
