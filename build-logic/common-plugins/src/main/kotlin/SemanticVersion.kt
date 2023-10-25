data class SemanticVersion(val artifactGroup: String, val artifactId: String, val artifactVersion: String) {
    override fun toString(): String {
        return "$artifactGroup:$artifactId:$artifactVersion"
    }

    companion object {
        const val XDK_VERSION_CATALOG_GROUP = "xdkgroup"
    	const val XDK_VERSION_CATALOG_VERSION = "xdk"
    }
}
