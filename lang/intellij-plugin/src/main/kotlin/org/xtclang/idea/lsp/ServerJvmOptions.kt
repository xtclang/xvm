package org.xtclang.idea.lsp

/** A bounded set of tuning flags; process entry points, agents and protocol properties stay owned by the launcher. */
internal object ServerJvmOptions {
    private val memory = Regex("(-Xms|-Xmx|-Xss|-XX:MaxMetaspaceSize=|-XX:ReservedCodeCacheSize=)([1-9][0-9]*)([kKmMgG])")
    private val collector = Regex("-XX:\\+Use(G1|Serial|Parallel|Z)GC")

    fun validate(options: List<String>): List<String> {
        val keys =
            options.map { option ->
                val size = memory.matchEntire(option)
                when {
                    size != null -> {
                        size.groupValues[1]
                    }

                    collector.matches(option) -> {
                        "collector"
                    }

                    Regex("-XX:ActiveProcessorCount=[1-9][0-9]*").matches(option) -> {
                        require(option.substringAfter('=').toIntOrNull() in 1..1024) { "ActiveProcessorCount must be between 1 and 1024" }
                        "processors"
                    }

                    else -> {
                        throw IllegalArgumentException(
                            "Unsupported JVM option: $option. Use heap/stack sizes, MaxMetaspaceSize, ReservedCodeCacheSize, ActiveProcessorCount or a supported collector.",
                        )
                    }
                }
            }
        require(keys.distinct().size == keys.size) { "Duplicate or conflicting JVM options" }

        fun bytes(prefix: String): Long? =
            options.firstOrNull { it.startsWith(prefix) }?.let {
                val match = requireNotNull(memory.matchEntire(it))
                val multiplier =
                    when (match.groupValues[3].lowercase()) {
                        "k" -> 1024L
                        "m" -> 1024L * 1024
                        else -> 1024L * 1024 * 1024
                    }
                val count = match.groupValues[2].toLongOrNull()
                require(count != null && count <= Long.MAX_VALUE / multiplier) { "JVM memory size is too large" }
                count * multiplier
            }
        options.filter { memory.matches(it) }.forEach { bytes(requireNotNull(memory.matchEntire(it)).groupValues[1]) }
        val initial = bytes("-Xms")
        val maximum = bytes("-Xmx")
        require(initial == null || maximum == null || initial <= maximum) { "Initial heap must not exceed maximum heap" }
        return options.toList()
    }
}
