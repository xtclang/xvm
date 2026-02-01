package org.xvm.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.xvm.lsp.server.XtcLanguageServer
import java.io.DataInputStream

/**
 * Verifies that compiled bytecode targets the correct JDK version.
 *
 * The LSP server runs OUT-OF-PROCESS with its own JRE (Java 24), NOT inside IntelliJ.
 * This allows using jtreesitter 0.26+ which requires the FFM API (Java 22+).
 *
 * See doc/plans/PLAN_OUT_OF_PROCESS_LSP.md for architecture details.
 *
 * Class file major versions:
 * - JDK 24 = 68
 * - JDK 23 = 67
 * - JDK 22 = 66
 * - JDK 21 = 65
 */
@DisplayName("Bytecode Version Compatibility")
class BytecodeVersionTest {
    companion object {
        /**
         * Expected class file major version (JDK 24).
         * The build is configured to target org.xtclang.kotlin.jdk from version.properties.
         */
        private const val EXPECTED_VERSION = 68

        /**
         * Maximum allowed class file major version for the LSP server.
         * Must match the JRE version used for out-of-process execution.
         */
        private const val MAX_ALLOWED_VERSION = 68
    }

    @Test
    @DisplayName("XtcLanguageServer bytecode should target JDK 24")
    fun serverBytecodeTargetsJdk24() {
        val majorVersion = getClassFileMajorVersion(XtcLanguageServer::class.java)

        assertThat(majorVersion)
            .describedAs("Class file major version for XtcLanguageServer")
            .isEqualTo(EXPECTED_VERSION)
    }

    @Test
    @DisplayName("All LSP server classes must target JDK 24 for out-of-process execution")
    fun allClassesMustTargetOutOfProcessJdk() {
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
