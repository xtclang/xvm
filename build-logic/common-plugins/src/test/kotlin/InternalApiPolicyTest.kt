import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Protects the verifier gate against broad exceptions, dependency drift and incomplete reports.
 * Uses report-format fixtures so new platform internals cannot silently inherit LSP4IJ exceptions.
 */
class InternalApiPolicyTest {
    @TempDir lateinit var reports: Path

    private val api = "Internal method com.redhat.devtools.lsp4ij.LanguageServiceAccessor.getStartedServers() : java.util.Set"
    private val policy get() = listOf("# Exact reviewed dependency", "lsp4ij=0.21.0", api)

    @Test
    fun `accepts repeated uses of an exact signature across IDE reports`() {
        report("IDE-A", listOf(usage(api), usage(api)))
        report("IDE-B", listOf(usage(api)))
        assertEquals(3, InternalApiPolicy.verify(reports, policy, "0.21.0"))
    }

    @Test
    fun `rejects new LSP4IJ members and IntelliJ internal APIs`() {
        val unknown = "Internal method com.redhat.devtools.lsp4ij.LanguageServiceAccessor.newMethod() : void"
        val platform = "Internal method com.intellij.ide.plugins.PluginManager.findEnabledPlugin() : void"
        report("IDE", listOf(usage(api), usage(unknown), usage(platform)))
        val error = assertThrows(IllegalStateException::class.java) { InternalApiPolicy.verify(reports, policy, "0.21.0") }
        assertTrue(error.message!!.contains(unknown))
        assertTrue(error.message!!.contains(platform))
    }

    @Test
    fun `a changed member signature requires review`() {
        report("IDE", listOf(usage(api.replace("java.util.Set", "java.util.List"))))
        assertThrows(IllegalStateException::class.java) { InternalApiPolicy.verify(reports, policy, "0.21.0") }
    }

    @Test
    fun `debug parameter names do not change the API signature`() {
        val member = "Internal method com.redhat.devtools.lsp4ij.LanguageServiceAccessor.hasAny"
        val types = "(com.intellij.psi.PsiFile, java.util.function.Predicate) : boolean"
        report("IDE-A", listOf(usage("$member(com.intellij.psi.PsiFile arg0, java.util.function.Predicate arg1) : boolean")))
        report("IDE-B", listOf(usage("$member(com.intellij.psi.PsiFile file, java.util.function.Predicate filter) : boolean")))
        assertEquals(2, InternalApiPolicy.verify(reports, listOf("lsp4ij=0.21.0", member + types), "0.21.0"))
    }

    @Test
    fun `parameter normalization preserves types order and arity`() {
        val member = "Internal method com.redhat.devtools.lsp4ij.LanguageServiceAccessor.hasAny"
        val policy = listOf("lsp4ij=0.21.0", "$member(com.intellij.psi.PsiFile, java.util.function.Predicate) : boolean")
        for (parameters in listOf(
            "com.intellij.psi.PsiElement file, java.util.function.Predicate filter",
            "java.util.function.Predicate filter, com.intellij.psi.PsiFile file",
            "com.intellij.psi.PsiFile file",
        )) {
            report("IDE", listOf(usage("$member($parameters) : boolean")))
            assertThrows(IllegalStateException::class.java) { InternalApiPolicy.verify(reports, policy, "0.21.0") }
        }
    }

    @Test
    fun `rejects both configured and verifier-resolved dependency upgrades`() {
        report("IDE", listOf(usage(api)), dependency = "0.22.0")
        assertThrows(IllegalArgumentException::class.java) { InternalApiPolicy.verify(reports, policy, "0.22.0") }
        assertThrows(IllegalStateException::class.java) { InternalApiPolicy.verify(reports, policy, "0.21.0") }
    }

    @Test
    fun `missing or truncated reports cannot pass`() {
        assertThrows(IllegalStateException::class.java) { InternalApiPolicy.verify(reports, policy, "0.21.0") }
        val directory = report("IDE", listOf(usage(api)), count = 2)
        assertThrows(IllegalStateException::class.java) { InternalApiPolicy.verify(reports, policy, "0.21.0") }
        Files.delete(directory.resolve("internal-api-usages.txt"))
        assertThrows(IllegalStateException::class.java) { InternalApiPolicy.verify(reports, policy, "0.21.0") }
    }

    @Test
    fun `a clean verdict does not require an empty usages file`() {
        val directory = report("IDE", emptyList())
        Files.delete(directory.resolve("internal-api-usages.txt"))
        assertEquals(0, InternalApiPolicy.verify(reports, policy, "0.21.0"))
    }

    @Test
    fun `unrecognized report text fails rather than being skipped`() {
        report("IDE", listOf("Unrecognized internal API finding"))
        assertThrows(IllegalStateException::class.java) { InternalApiPolicy.verify(reports, policy, "0.21.0") }
    }

    @Test
    fun `exception file cannot include platform APIs or package wildcards`() {
        for (entry in listOf("Internal class com.intellij.InternalClass", "Internal class com.redhat.devtools.lsp4ij.*")) {
            assertThrows(IllegalArgumentException::class.java) {
                InternalApiPolicy.verify(reports, listOf("lsp4ij=0.21.0", entry), "0.21.0")
            }
        }
    }

    private fun usage(symbol: String): String = "$symbol is invoked in org.xtclang.idea.lsp.Adapter.run() : void. This method is marked internal."

    private fun report(ide: String, usages: List<String>, count: Int = usages.size, dependency: String = "0.21.0"): Path {
        val directory = Files.createDirectories(reports.resolve("$ide/plugins/org.xtclang.idea/0.4.4-SNAPSHOT"))
        Files.writeString(directory.resolve("verification-verdict.txt"), if (count == 0) "Compatible." else "Compatible. $count usages of internal API")
        Files.writeString(directory.resolve("dependencies.txt"), "+--- com.redhat.devtools.lsp4ij:$dependency\n")
        Files.writeString(directory.resolve("internal-api-usages.txt"), usages.joinToString("\n"))
        return directory
    }
}
