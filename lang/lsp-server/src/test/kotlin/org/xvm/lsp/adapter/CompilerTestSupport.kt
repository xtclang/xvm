package org.xvm.lsp.adapter

import org.xvm.api.EmbeddingSupport
import org.xvm.api.InterpreterConnector
import org.xvm.asm.ConstantPool
import org.xvm.asm.FileStructure
import org.xvm.asm.LinkedRepository
import org.xvm.asm.ModuleStructure
import org.xvm.compiler.InstantRepository
import org.xvm.lsp.adapter.xdk.XdkLibraries
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Tests use the same bundled module artifacts as the production server. */
internal object CompilerTestSupport {
    fun configure() {
        XdkLibraries.configure()
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
