package org.xvm.lsp.adapter

import org.xvm.api.EmbeddingSupport
import org.xvm.api.InterpreterConnector
import org.xvm.asm.ConstantPool
import org.xvm.asm.DirRepository
import org.xvm.asm.FileStructure
import org.xvm.asm.LinkedRepository
import org.xvm.asm.ModuleStructure
import org.xvm.compiler.BuildRepository
import org.xvm.compiler.InstantRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Uses only the compiled-module variants supplied by Gradle, including on a clean checkout. */
internal object CompilerTestSupport {
    private val repository by lazy {
        val paths = requireNotNull(System.getProperty("xtc.test.modules")) { "Gradle must supply compiler test modules" }
        val repositories =
            paths.split(File.pathSeparatorChar).map { path ->
                val directory = File(path)
                check(directory.isDirectory) { "Missing compiled-module directory: $directory" }
                DirRepository(directory, true)
            }
        LinkedRepository(true, BuildRepository(), *repositories.toTypedArray()).also { repository ->
            for (name in listOf("ecstasy.xtclang.org", "mack.xtclang.org", "_native.xtclang.org")) {
                checkNotNull(repository.loadModule(name)) { "Missing required compiler module: $name" }
            }
        }
    }

    fun configure() {
        EmbeddingSupport.instance().configure(repository, null)
    }

    /** Executes the serialized module's no-argument run method through the interpreter. */
    fun execute(module: ModuleStructure): Int {
        val bytes = ByteArrayOutputStream().also { module.fileStructure.writeTo(it) }.toByteArray()
        val restored = FileStructure(ByteArrayInputStream(bytes)).module
        val repository = LinkedRepository(InstantRepository(restored), EmbeddingSupport.instance().configuredRepository)
        val connector = InterpreterConnector(repository)
        connector.loadModule(restored.name)
        connector.start(null)
        ConstantPool.withPool(connector.constantPool).use {
            connector.invoke0(connector.findMethods("run").single())
        }
        return connector.join()
    }
}
