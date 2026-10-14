package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.api.InterpreterConnector
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.FileStructure
import org.xvm.asm.LinkedRepository
import org.xvm.asm.ModuleRepository
import org.xvm.asm.ModuleStructure
import org.xvm.compiler.InstantRepository
import org.xvm.runtime.template._native.io.xTerminalConsole
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

/** Exercises tolerant repository lookup and cache recovery through compiler and runtime APIs. */
@Isolated
@Timeout(60)
class RepositoryReportingTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `unavailable unrelated candidates do not change compilation outcomes`(singleFile: Boolean) {
        CompilerTestSupport.configure()
        val file = directory.resolve("Old.xtc").toFile()
        unsupported(file, "Old")
        val repositoryErrors = ErrorList()
        val repository = ModuleRepository.of(if (singleFile) file else directory.toFile(), true, repositoryErrors)
        val support = EmbeddingSupport.instance()
        val validErrors = ErrorList(100)
        assertThat(support.compile("module Good {}", repository, validErrors)).isNotNull()
        assertThat(validErrors.errors).isEmpty()
        assertThat(repositoryErrors.errors).hasSize(1)
        val report = repositoryErrors.errors.single()
        assertThat(report.code).isEqualTo("LAUNCHER-04")
        assertThat(report.message).contains(file.path, "Unsupported .xtc version")
        assertThat(report.params[1]).isInstanceOf(Exception::class.java)
        assertThat(report.site()).isEqualTo(ErrorListener.NOWHERE)
        assertThat(repositoryErrors.hasSeriousErrors()).isFalse()

        val missingErrors = ErrorList(100)
        assertThat(support.compile("module Test { package lib import Missing; }", repository, missingErrors)).isNull()
        assertThat(missingErrors.hasSeriousErrors()).isTrue()
        assertThat(missingErrors.errors).noneMatch { it.message.contains(file.path) }
    }

    @Test
    fun `Ecstasy repositories cache unchanged failures and recover after normal replacement`() {
        CompilerTestSupport.configure()
        val first = Files.createDirectory(directory.resolve("first"))
        val second = Files.createDirectory(directory.resolve("second"))
        unsupported(first.resolve("Old.xtc").toFile(), "Old")
        Files.writeString(first.resolve("Good.xtc"), "unreadable candidate")
        compile("module Good.example.org {}").fileStructure.writeTo(second.resolve("Good.xtc").toFile())
        val source =
            """
            module RepositoryLookup {
                import ecstasy.mgmt.DirRepository;
                import ecstasy.mgmt.LinkedRepository;
                import ecstasy.mgmt.ModuleRepository;
                import ecstasy.reflect.ModuleTemplate;

                Int run() {
                    @Inject Directory rootDir;
                    Directory first = rootDir.dirFor("${first.toString().removePrefix("/")}");
                    Directory second = rootDir.dirFor("${second.toString().removePrefix("/")}");
                    DirRepository primary = new DirRepository(first);
                    ModuleRepository fallback = new DirRepository(second);
                    ModuleRepository linked = new LinkedRepository([primary, fallback].toArray(Constant, True));
                    assert ModuleTemplate good := linked.getModule("Good.example.org");
                    assert good.qualifiedName == "Good.example.org";
                    for (Int i = 0; i < 3; ++i) {
                        // Missing lookups force a metadata check even within the scan interval.
                        assert !(linked.getModule("Missing"));
                        assert linked.getModule("Good.example.org");
                        assert primary.moduleNames.empty;
                    }

                    first.fileFor("Good.xtc").contents = second.fileFor("Good.xtc").contents;
                    assert primary.getModule("Good.example.org");
                    assert !(primary.getModule("ForceRefresh"));
                    assert primary.getModule("Good.example.org");
                    assert primary.moduleNames.contains("Good.example.org");

                    first.fileFor("Old.xtc").delete();
                    first.fileFor("Good.xtc").delete();
                    assert !(primary.getModule("ForceRefresh"));
                    assert primary.moduleNames.empty;
                    assert linked.getModule("Good.example.org");
                    return 42;
                }
            }
            """.trimIndent()
        val compiled = compile(source)
        val bytes = ByteArrayOutputStream().also { compiled.fileStructure.writeTo(it) }.toByteArray()
        val module = FileStructure(ByteArrayInputStream(bytes)).module
        val connector =
            InterpreterConnector(LinkedRepository(InstantRepository(module), EmbeddingSupport.instance().configuredRepository))
        connector.loadModule(module.name)
        connector.start(null)
        ConstantPool.withPool(connector.constantPool).use {
            connector.invoke0(connector.findMethods("run").single())
        }
        assertThat(connector.join()).isEqualTo(42)
        // The native console records its output. Only the first reads may report these unique files;
        // missing lookups and rebuilding after another file changes must reuse unavailable entries.
        val console = xTerminalConsole.CONSOLE_LOG
        val reports = (0 until console.size()).map { console.get(it) }.filter { it.contains(first.toString()) }
        assertThat(reports).hasSize(2)
        assertThat(reports.count { it.contains("Old.xtc") }).isEqualTo(1)
        assertThat(reports.count { it.contains("Good.xtc") }).isEqualTo(1)
    }

    private fun compile(source: String): ModuleStructure {
        val errors = ErrorList(100)
        val module = EmbeddingSupport.instance().compile(source, null, errors)
        assertThat(errors.errors).isEmpty()
        return requireNotNull(module)
    }

    private fun unsupported(
        file: File,
        name: String,
    ) {
        FileStructure(name).writeTo(file)
        val bytes = file.readBytes()
        ByteBuffer.wrap(bytes).putInt(2 * Int.SIZE_BYTES, FileStructure.getToolMinorVersion() - 1)
        file.writeBytes(bytes)
    }
}
