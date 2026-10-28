package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.api.InterpreterConnector
import org.xvm.asm.ConstantPool
import org.xvm.asm.DirRepository
import org.xvm.asm.ErrorList
import org.xvm.asm.FileRepository
import org.xvm.asm.FileStructure
import org.xvm.asm.LinkedRepository
import org.xvm.asm.ModuleRepository
import org.xvm.asm.ModuleStructure
import org.xvm.compiler.InstantRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

/** Exercises repository reporting as a host and through the native Ecstasy runtime. */
@Timeout(60)
class RepositoryReportingTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `candidate reports remain separate from compilation outcomes`(singleFile: Boolean) {
        CompilerTestSupport.configure()
        val file = directory.resolve("Old.xtc").toFile()
        unsupported(file, "Old")
        val reports = mutableMapOf<File, IOException>()
        val repository: ModuleRepository =
            if (singleFile) FileRepository(file, true, reports::put) else DirRepository(directory.toFile(), true, reports::put)
        val support = EmbeddingSupport.instance()
        val validErrors = ErrorList(100)
        assertThat(support.compile("module Good {}", repository, validErrors)).isNotNull()
        assertThat(validErrors.errors).isEmpty()
        assertThat(reports[file]?.message).contains("Unsupported .xtc version")

        val missingErrors = ErrorList(100)
        assertThat(support.compile("module Test { package lib import Missing; }", repository, missingErrors)).isNull()
        assertThat(missingErrors.hasSeriousErrors()).isTrue()
        assertThat(missingErrors.errors).noneMatch { it.message.contains(file.path) }
    }

    @Test
    fun `Ecstasy repositories preserve platform fallback and retry with optional reporting`() {
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

                service Reports {
                    Int count = 0;
                    void record(File file, Exception cause) {
                        assert file.name == "Old.xtc" || file.name == "Good.xtc";
                        assert cause.cause != Null;
                        ++count;
                    }
                }

                Int run() {
                    @Inject Directory rootDir;
                    Directory first = rootDir.dirFor("${first.toString().removePrefix("/")}");
                    Directory second = rootDir.dirFor("${second.toString().removePrefix("/")}");
                    Reports reports = new Reports();
                    DirRepository primary = new DirRepository(first, (file, cause) -> reports.record(file, cause));
                    ModuleRepository fallback = new DirRepository(second);
                    ModuleRepository linked = new LinkedRepository([primary, fallback].toArray(Constant, True));
                    assert ModuleTemplate good := linked.getModule("Good.example.org");
                    assert good.qualifiedName == "Good.example.org";
                    assert reports.count >= 2;
                    assert !(linked.getModule("Missing"));

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
