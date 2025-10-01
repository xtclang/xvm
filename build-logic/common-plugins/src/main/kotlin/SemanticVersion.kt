data class SemanticVersion(val artifactGroup: String, val artifactId: String, val artifactVersion: String) {

    companion object {
        fun fromJson(json: String): SemanticVersion {
            // Parse simple JSON manually: {"artifactGroup":"group","artifactId":"id","artifactVersion":"version"}
            val trimmed = json.trim().removeSurrounding("{", "}")
            val parts = mutableMapOf<String, String>()

            // Simple JSON parsing for the expected format
            val keyValueRegex = """"([^"]+)"\s*:\s*"([^"]*)"""".toRegex()
            keyValueRegex.findAll(trimmed).forEach { match ->
                parts[match.groupValues[1]] = match.groupValues[2]
            }

            return SemanticVersion(
                artifactGroup = parts["artifactGroup"] ?: throw IllegalArgumentException("Missing artifactGroup in JSON"),
                artifactId = parts["artifactId"] ?: throw IllegalArgumentException("Missing artifactId in JSON"),
                artifactVersion = parts["artifactVersion"] ?: throw IllegalArgumentException("Missing artifactVersion in JSON")
            )
        }

        fun toJson(semanticVersion: SemanticVersion): String {
            return """{"artifactGroup":"${semanticVersion.artifactGroup}","artifactId":"${semanticVersion.artifactId}","artifactVersion":"${semanticVersion.artifactVersion}"}"""
        }
    }

    override fun toString(): String {
        return "$artifactGroup:$artifactId:$artifactVersion"
    }

    /**
     * Increase the microVersion with one, and potentially add or remove a SNAPSHOT suffix from
     * this semantic version.
     *
     * @return New SemanticVersion, as SemanticVersion objects are immutable.
     */
    fun bump(toSnapshot: Boolean = true): SemanticVersion {
        return run {
            val lastDot = artifactVersion.lastIndexOf('.')
            if (lastDot == -1) {
                throw IllegalArgumentException("Illegal version format: '$artifactVersion'")
            }
            val majorMinorVersion = artifactVersion.take(lastDot)
            val microVersionFull = artifactVersion.substring(lastDot + 1)
            val microVersion = microVersionFull.trim { !it.isDigit() }
            val nextMicroVersion = microVersion.toInt() + 1
            if (isSnapshot()) {
                throw IllegalArgumentException("Bumping semantic version that is already a snapshot. Is this intentional?")
            }
            val next = SemanticVersion(
                artifactGroup,
                artifactId,
                buildString {
                    append(majorMinorVersion)
                    append('.')
                    append(nextMicroVersion)
                    if (toSnapshot) {
                        append("-SNAPSHOT")
                    }
                })
            next
        }
    }

    fun isSnapshot(): Boolean {
        return artifactVersion.endsWith("-SNAPSHOT")
    }
}
