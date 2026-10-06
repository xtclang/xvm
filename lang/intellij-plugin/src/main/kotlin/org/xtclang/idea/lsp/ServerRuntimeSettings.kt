package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Application-owned and excluded from Settings Sync and project files. */
@Service(Service.Level.APP)
@State(name = "EcstasyServerRuntime", storages = [Storage(value = "ecstasy-server-runtime.xml", roamingType = RoamingType.DISABLED)])
internal class ServerRuntimeSettings : SerializablePersistentStateComponent<ServerRuntimeSettings.Options>(Options()) {
    data class Options(
        @JvmField val vmOptions: String = "",
        @JvmField val logHistoryDays: Int = 7,
        @JvmField val logMaxFileMb: Int = 10,
        @JvmField val logTotalSizeMb: Int = 50,
        @JvmField val logRetainedSessions: Int = 5,
    ) {
        fun arguments(): List<String> =
            ServerJvmOptions.validate(
                vmOptions
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList(),
            )
    }

    fun launchArguments(): List<String> = state.let { it.arguments() + it.logArguments() }

    fun install(
        expected: Options,
        replacement: Options,
    ) {
        replacement.arguments()
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
