package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.OptionTag

/** How the language server's JVM uses memory, chosen on the Ecstasy › Server Runtime and Logs page. */
enum class MemoryPolicy(
    private val label: String,
) {
    DEFAULT("Default: the maximum heap below, or the JVM's own, a quarter of this machine's memory"),
    ADAPTIVE("Adaptive: starts small, grows as the work needs and gives memory back, using at most 3/4 of this machine's"),
    ;

    override fun toString() = label
}

/** Application-owned and excluded from Settings Sync and project files. */
@Service(Service.Level.APP)
@State(name = "EcstasyServerRuntime", storages = [Storage(value = "ecstasy-server-runtime.xml", roamingType = RoamingType.DISABLED)])
internal class ServerRuntimeSettings : SerializablePersistentStateComponent<ServerRuntimeSettings.Options>(Options()) {
    /**
     * Persisted JVM, log-retention and library settings used when launching the language server. [compileLibraries]: an
     * opened XDK source is compiled with its whole library, for navigation in it. [replaceLibraries]: a workspace module
     * of a bundled library's name replaces that library, as the XVM repository's replace them all.
     */
    data class Options(
        // Explicit bindings preserve immutable fields in IntelliJ's XML serializer.
        @field:OptionTag @JvmField val vmOptions: String = "",
        @field:OptionTag @JvmField val logHistoryDays: Int = 7,
        @field:OptionTag @JvmField val logMaxFileMb: Int = 10,
        @field:OptionTag @JvmField val logTotalSizeMb: Int = 50,
        @field:OptionTag @JvmField val logRetainedSessions: Int = 5,
        @field:OptionTag @JvmField val compileLibraries: Boolean = true,
        @field:OptionTag @JvmField val replaceLibraries: Boolean = true,
        @field:OptionTag @JvmField val memoryPolicy: MemoryPolicy = MemoryPolicy.DEFAULT,
        /** The maximum heap of [MemoryPolicy.DEFAULT], as `4G`; blank for the JVM's own. */
        @field:OptionTag @JvmField val maxHeap: String = "",
    ) {
        fun arguments(): List<String> =
            ServerJvmOptions.validate(
                vmOptions
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList(),
            )

        /** The memory policy's flags, unless [arguments] choose a collector or a maximum heap. */
        fun memoryArguments(): List<String> = ServerJvmOptions.memory(memoryPolicy, maxHeap, arguments())
    }

    fun launchArguments(): List<String> =
        state.let {
            it.memoryArguments() + it.arguments() + it.logArguments() +
                it.libraryArguments()
        }

    fun install(
        expected: Options,
        replacement: Options,
    ) {
        replacement.arguments()
        replacement.memoryArguments()
        replacement.logArguments()
        updateState {
            require(it == expected) { "Runtime settings changed while this page was open. Reset before applying." }
            replacement
        }
    }

    companion object {
        fun getInstance(): ServerRuntimeSettings = ApplicationManager.getApplication().getService(ServerRuntimeSettings::class.java)
    }
}

/**
 * Whether the server compiles an opened XDK source's whole library, and whether the workspace's own modules replace
 * bundled libraries of their names, as the server's `xtc.lsp.compileLibraries` and `xtc.lsp.replaceLibraries` take them.
 */
internal fun ServerRuntimeSettings.Options.libraryArguments(): List<String> =
    listOf("-Dxtc.lsp.compileLibraries=$compileLibraries", "-Dxtc.lsp.replaceLibraries=$replaceLibraries")

internal fun ServerRuntimeSettings.Options.logArguments(): List<String> {
    require(logHistoryDays in 1..90 && logMaxFileMb in 1..100 && logTotalSizeMb in logMaxFileMb..1000 && logRetainedSessions in 1..20) {
        "Log retention: days 1–90, file MB 1–100, archive MB at least file MB and at most 1000, retired sessions 1–20"
    }
    return listOf(
        "-Dxtc.logs.historyDays=$logHistoryDays",
        "-Dxtc.logs.maxFileMb=$logMaxFileMb",
        "-Dxtc.logs.totalSizeMb=$logTotalSizeMb",
        "-Dxtc.logs.retainedSessions=$logRetainedSessions",
    )
}
