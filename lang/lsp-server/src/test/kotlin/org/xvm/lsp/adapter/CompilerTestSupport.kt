package org.xvm.lsp.adapter

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.DirRepository
import org.xvm.asm.LinkedRepository
import java.io.File

/** Configure the embedding API with the module artifacts supplied by Gradle. */
internal object CompilerTestSupport {
    private val configured by lazy {
        val modulePath =
            checkNotNull(System.getProperty("xtc.test.modulePath")) {
                "Run compiler tests through Gradle so their XDK module dependencies are built"
            }
        val repositories =
            modulePath.split(File.pathSeparator).map { path ->
                val directory = File(path)
                check(directory.isDirectory) { "Missing compiler test module directory: $directory" }
                DirRepository(directory, true)
            }
        val repository = LinkedRepository(*repositories.toTypedArray())
        for (module in listOf("ecstasy.xtclang.org", "mack.xtclang.org", "_native.xtclang.org")) {
            checkNotNull(repository.loadModule(module)) { "Missing compiler test module: $module" }
        }
        EmbeddingSupport.instance().configure(repository, null)
    }

    fun configure() {
        configured
    }
}
