package org.xtclang.idea.run

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import org.xtclang.idea.lsp.EcstasyConfigurable

/**
 * Settings | Build, Execution, Deployment | Ecstasy, beside the IDE's other build tools: a link to the page that
 * chooses the build mode, which stands with the plugin's other pages under Languages & Frameworks | Ecstasy.
 */
class XtcBuildPointerConfigurable : BoundConfigurable("Ecstasy") {
    override fun createPanel(): DialogPanel =
        panel {
            row { text("Whether Build, Rebuild and Run use the embedded compiler or Gradle is chosen on") }
            row { link("Languages & Frameworks | Ecstasy | Build and Run") { EcstasyConfigurable.open(it, "xtc.build") } }
        }
}
