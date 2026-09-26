import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

class NativeLibraryInputsTest {
    @TempDir
    lateinit var root: File

    private val files = listOf(
        "zig/zig", "zig/lib/std.zig", "grammar.js", "src/scanner.c", "src/parser.c",
        "src/tree_sitter/parser.h", "runtime/src/lib.c", "runtime/include/tree_sitter/api.h",
    )

    private fun fixture(directory: File) {
        files.forEach { path ->
            File(directory, path).apply {
                parentFile.mkdirs()
                writeText("before")
            }
        }
    }

    private fun inputs(
        directory: File = root,
        cli: String = "0.26.9",
        zig: String = "0.15.2",
        flags: List<String> = NativeLibraryCommands.compilerFlags,
    ) = NativeLibraryInputs(
        cli, zig, File(directory, "zig/zig"), File(directory, "zig/lib"),
        File(directory, "grammar.js"), File(directory, "src/scanner.c"),
        File(directory, "src"), File(directory, "runtime"), flags,
    )

    private fun key(inputs: NativeLibraryInputs = inputs()) =
        inputs.fingerprint("linux-x64", "x86_64-linux-gnu", "so")

    @TestFactory
    fun contentChangesInvalidateCache() = files.mapIndexed { index, path ->
        dynamicTest(path) {
            val directory = File(root, index.toString())
            fixture(directory)
            val before = key(inputs(directory))
            val file = File(directory, path)
            val timestamp = Files.getLastModifiedTime(file.toPath())
            file.writeText("after!") // Same size and modification time.
            Files.setLastModifiedTime(file.toPath(), timestamp)
            assertNotEquals(before, key(inputs(directory)))
        }
    }

    @Test
    fun toolVersionsTargetsExtensionsAndFlagsInvalidateCache() {
        fixture(root)
        val before = key()
        assertNotEquals(before, key(inputs(cli = "0.26.8")))
        assertNotEquals(before, key(inputs(zig = "0.15.1")))
        assertNotEquals(before, key(inputs(flags = NativeLibraryCommands.compilerFlags + "-O2")))
        assertNotEquals(before, inputs().fingerprint("other-platform", "x86_64-linux-gnu", "so"))
        assertNotEquals(before, inputs().fingerprint("linux-x64", "x86_64-linux-musl", "so"))
        assertNotEquals(before, inputs().fingerprint("linux-x64", "x86_64-linux-gnu", "dll"))
    }

    @Test
    fun locationAndTimestampsDoNotAffectCacheIdentity() {
        fixture(root)
        val elsewhere = File(root, "elsewhere")
        fixture(elsewhere)
        files.forEach { Files.setLastModifiedTime(File(elsewhere, it).toPath(), FileTime.fromMillis(1)) }
        assertEquals(key(), key(inputs(elsewhere)))
    }

    @Test
    fun renamingAnIncludeChangesIdentity() {
        fixture(root)
        val before = key()
        Files.move(File(root, "src/tree_sitter/parser.h").toPath(), File(root, "src/tree_sitter/other.h").toPath())
        assertNotEquals(before, key())
    }

    @Test
    fun fieldsHaveUnambiguousBoundaries() {
        fixture(root)
        assertNotEquals(key(inputs(cli = "ab", zig = "c")), key(inputs(cli = "a", zig = "bc")))
    }

    @Test
    fun missingInputsFailInsteadOfReusingCache() {
        fixture(root)
        Files.delete(File(root, "zig/zig").toPath())
        assertThrows(IllegalArgumentException::class.java) { key() }
    }
}
