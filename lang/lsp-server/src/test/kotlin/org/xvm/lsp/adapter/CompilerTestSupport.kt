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
import java.io.File

/** Tests use the same bundled module artifacts as the production server. */
internal object CompilerTestSupport {
    fun configure() {
        XdkLibraries.configure()
    }

    /**
     * A Gradle model's `xdk` entry for a build whose XDK, extracted into [libraries], is [version] with the module files
     * of SHA-256 [modules] and a javatools of [javatools]: by default, the bundled XDK, as the XTC plugin reads it from a
     * distribution that the server's own build made.
     */
    fun xdk(
        libraries: File,
        version: String? = XdkLibraries.version,
        modules: Collection<String> = XdkLibraries.revisions,
        javatools: String? = XdkLibraries.javatools,
    ): Map<String, Any?> =
        mapOf("version" to version, "libraries" to libraries.toURI().toString(), "modules" to modules.sorted(), "javatools" to javatools)

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
