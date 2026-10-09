package org.xvm.lsp.adapter

import org.xvm.lsp.adapter.xdk.XdkLibraries

/** Tests use the same bundled module artifacts as the production server. */
internal object CompilerTestSupport {
    fun configure() {
        XdkLibraries.configure()
    }
}
