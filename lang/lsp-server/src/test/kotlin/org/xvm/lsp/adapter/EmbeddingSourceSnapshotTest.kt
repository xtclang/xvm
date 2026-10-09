package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.tool.ModuleInfo
import org.xvm.tool.ResourceDir
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** Direct host tests: no IDE, adapter, URI service or compiler-owned state in the input snapshot. */
class EmbeddingSourceSnapshotTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `unsaved roots and members compile without creating files`() {
        val root = directory.resolve("Project.x").toFile().canonicalFile
        val member = directory.resolve("Project/pkg/Added.x").toFile().canonicalFile
        val errors = ErrorList()
        val result = compile(snapshot(root, mapOf(root to "module Project {}", member to "class Added {}")), errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(result.module().getChild("pkg").getChild("Added")).isNotNull()
        assertThat(result.parsed()).isNotNull()
        assertThat(root.exists()).isFalse()
        assertThat(member.exists()).isFalse()
    }

    @Test
    fun `a fresh snapshot controls membership instead of cached or disk contents`() {
        val root = directory.resolve("Project.x").toFile().canonicalFile
        val member = directory.resolve("Project/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("this disk text must not be parsed")
        member.writeText("class Child { void broken( }")
        val errors = ErrorList()
        val first = compile(snapshot(root, mapOf(root to "module Project {}", member to "class Child {}")), errors)
        assertThat(first.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(first.module().getChild("Child")).isNotNull()
        val second = compile(snapshot(root, mapOf(root to "module Project {}")), ErrorList())
        assertThat(second.succeeded()).isTrue()
        assertThat(second.module().getChild("Child")).isNull()
        assertThat(first.module().getChild("Child")).isNotNull()
        assertThat(member.readText()).contains("broken")
    }

    @Test
    fun `member diagnostics retain their source identity and correction is independent`() {
        val root = directory.resolve("Project.x").toFile().canonicalFile
        val member = directory.resolve("Project/Child.x").toFile().canonicalFile
        val errors = ErrorList()
        val failed = compile(snapshot(root, mapOf(root to "module Project {}", member to "class Child { void broken( }")), errors)
        assertThat(failed.succeeded()).isFalse()
        assertThat(errors.errors).isNotEmpty()
        assertThat(errors.errors.map { (it.site() as ErrorListener.Site.In).source().fileName }).containsOnly(member.path)
        val fixed = compile(snapshot(root, mapOf(root to "module Project {}", member to "class Child {}")), ErrorList())
        assertThat(fixed.succeeded()).isTrue()
    }

    @Test
    fun `resource-only packages survive source snapshot assembly`() {
        val root = directory.resolve("Project.x").toFile().canonicalFile
        val resources = directory.resolve("Project/spare").toFile().canonicalFile
        resources.mkdirs()
        File(resources, "data.txt").writeText("resource content")
        val errors = ErrorList()
        val sources = snapshot(root, mapOf(root to "module Project {}"), setOf(resources), ResourceDir(root.parentFile))
        val result = compile(sources, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val spare = sources.getSourceTree(ErrorList()) as ModuleInfo.DirNode
        assertThat(
            spare
                .packageNodes()
                .single()
                .resourceDir()
                .getByName("data.txt"),
        ).isNotNull()
    }

    @Test
    fun `cancellation during source loading produces no parser fatal`() {
        val root = directory.resolve("Project.x").toFile().canonicalFile
        val member = directory.resolve("Project/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Project {}")
        member.writeText("class Child {}")
        val cancelled = AtomicBoolean()
        val sources =
            object : ModuleInfo(root, false) {
                override fun readSource(file: File): CharArray =
                    super.readSource(file).also {
                        if (file == member) cancelled.set(true)
                    }
            }
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(sources, null, ErrorListener.cancellable(errors, cancelled::get))
        assertThat(cancelled.get()).isTrue()
        assertThat(result.succeeded()).isFalse()
        assertThat(result.parsed()).isNull()
        assertThat(errors.errors).isEmpty()
    }

    private fun compile(
        sources: ModuleInfo,
        errors: ErrorList,
    ): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        return EmbeddingSupport.instance().compileModule(sources, null, errors)
    }

    private fun snapshot(
        root: File,
        text: Map<File, String>,
        extraDirectories: Set<File> = emptySet(),
        resources: ResourceDir = ResourceDir(emptyList()),
    ): ModuleInfo {
        val boundary = File(root.parentFile, root.nameWithoutExtension)
        val entries = linkedMapOf<File, LinkedHashMap<File, ModuleInfo.SourceEntry>>()

        fun addParents(file: File) {
            var current = file
            while (current != boundary) {
                entries.getOrPut(current.parentFile) { linkedMapOf() }[current] = ModuleInfo.SourceEntry(current, true)
                current = current.parentFile
            }
        }
        text.keys.filter { it != root }.forEach { file ->
            entries.getOrPut(file.parentFile) { linkedMapOf() }[file] = ModuleInfo.SourceEntry(file, false)
            addParents(file.parentFile)
        }
        extraDirectories.forEach(::addParents)
        val snapshot = text.toMap()
        return object : ModuleInfo(root, "Project") {
            override fun isSourceTree(): Boolean = true

            override fun getResourceDir(): ResourceDir = resources

            override fun sourceEntries(directory: File): List<SourceEntry> = entries[directory]?.values?.toList().orEmpty()

            override fun readSource(file: File): CharArray =
                snapshot[file]?.toCharArray() ?: throw IOException("Missing snapshot source: $file")
        }
    }
}
