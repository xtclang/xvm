data class SemanticVersion(val artifactGroup: String, val artifactId: String, val artifactVersion: String) {

    override fun toString(): String {
        return "$artifactGroup:$artifactId:$artifactVersion"
    }

    fun isSnapshot(): Boolean {
        return artifactVersion.endsWith("-SNAPSHOT")
    }
}
