package org.xvm.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.xvm.lsp.server.XtcLanguageServer
import java.io.DataInputStream

/**
 * Verifies that compiled bytecode is compatible with IntelliJ IDEA's JDK requirements.
 *
 * IntelliJ 2025.1 runs on JDK 21, so the LSP server bytecode must target JDK 21 or lower.
 * This ensures the IntelliJ plugin can load and use the LSP server classes in-process.
 *
 * Class file major versions:
 * - JDK 21 = 65
 * - JDK 17 = 61
 * - JDK 11 = 55
 * - JDK 8  = 52
 */
@DisplayName("Bytecode Version Compatibility")
class BytecodeVersionTest {
    companion object {
        /**
         * Maximum allowed class file major version for IntelliJ compatibility.
         * JDK 21 = major version 65
         */
        private const val MAX_ALLOWED_VERSION = 65

        /**
         * Expected class file major version (JDK 21).
         * The build is configured to target JDK 21 for IntelliJ 2025.1 compatibility.
         */
        private const val EXPECTED_VERSION = 65
    }

    @Test
    @DisplayName("XtcLanguageServer bytecode should target JDK 21")
    fun serverBytecodeTargetsJdk21() {
        val majorVersion = getClassFileMajorVersion(XtcLanguageServer::class.java)

        assertThat(majorVersion)
            .describedAs("Class file major version for XtcLanguageServer")
            .isEqualTo(EXPECTED_VERSION)
    }

    @Test
    @DisplayName("All LSP server classes must be loadable by JDK 21")
    fun allClassesMustBeCompatibleWithJdk21() {
        // Sample key classes from each package
        val classesToCheck =
            listOf(
                XtcLanguageServer::class.java,
                org.xvm.lsp.adapter.XtcCompilerAdapter::class.java,
                org.xvm.lsp.model.Location::class.java,
                org.xvm.lsp.model.Diagnostic::class.java,
            )

        for (clazz in classesToCheck) {
            val majorVersion = getClassFileMajorVersion(clazz)
            assertThat(majorVersion)
                .describedAs("Class file major version for ${clazz.simpleName}")
                .isLessThanOrEqualTo(MAX_ALLOWED_VERSION)
        }
    }

    /**
     * Reads the major version from a class file.
     *
     * Class file format:
     * - Bytes 0-3: Magic number (0xCAFEBABE)
     * - Bytes 4-5: Minor version
     * - Bytes 6-7: Major version
     */
    private fun getClassFileMajorVersion(clazz: Class<*>): Int {
        val classFileName = "/${clazz.name.replace('.', '/')}.class"
        val classStream =
            clazz.getResourceAsStream(classFileName)
                ?: throw IllegalStateException("Cannot find class file: $classFileName")

        return DataInputStream(classStream).use { dis ->
            val magic = dis.readInt()
            require(magic == 0xCAFEBABE.toInt()) {
                "Invalid class file magic number: ${magic.toString(16)}"
            }
            dis.readUnsignedShort() // minor version (ignored)
            dis.readUnsignedShort() // major version
        }
    }
}
