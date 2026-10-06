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

    fun install(
        expected: Options,
        replacement: Options,
    ) {
        replacement.arguments()
        updateState {
            require(it == expected) { "Runtime settings changed while this page was open. Reset before applying." }
            replacement
        }
    }

    companion object {
        fun getInstance(): ServerRuntimeSettings = ApplicationManager.getApplication().getService(ServerRuntimeSettings::class.java)
    }
}
