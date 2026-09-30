package org.xtclang.idea.lsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

/** Invalid external JSON must not replace a live connection's last valid preferences. */
@Service(Service.Level.PROJECT)
internal class LanguageServicePreferences(private val project: Project) {
    private val current = AtomicReference(LanguageServiceConfiguration())

    fun read(): LanguageServiceConfiguration = runCatching {
        LanguageServiceSettings.effective(project)
    }
        .onSuccess(current::set)
        .getOrElse {
            logger<LanguageServicePreferences>()
                .warn("Invalid Ecstasy service settings; retaining previous values", it)
            current.get()
        }
}
