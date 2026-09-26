import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Content identity shared by explicit cache population and on-demand native builds.
 * Paths and modification times do not affect the key; all compiler and source contents do.
 */
class NativeLibraryInputs(
    private val cliVersion: String,
    private val zigVersion: String,
    private val compiler: File,
    private val compilerLibraryDirectory: File,
    private val grammar: File,
    private val scanner: File,
    private val parserDirectory: File,
    private val runtimeDirectory: File,
    private val compilerFlags: List<String> = NativeLibraryCommands.compilerFlags,
) {
    private val contents by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.text("native-library-cache-v2")
        digest.text(cliVersion)
        digest.text(zigVersion)
        digest.text(compilerFlags.size.toString())
        compilerFlags.forEach { digest.text(it) }
        digest.file("compiler", compiler)
        digest.directory("compiler-libraries", compilerLibraryDirectory)
        digest.file("grammar", grammar)
        digest.file("scanner", scanner)
        digest.directory("parser", parserDirectory)
        digest.directory("runtime", runtimeDirectory)
        digest.digest()
    }

    fun fingerprint(platform: String, target: String, extension: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(contents)
        digest.text(platform)
        digest.text(target)
        digest.text(extension)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun MessageDigest.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        update(bytes)
    }

    private fun MessageDigest.file(label: String, file: File) {
        require(file.isFile) { "Missing native build input: $file" }
        text(label)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        update(digest.digest())
    }

    private fun MessageDigest.directory(label: String, directory: File) {
        require(directory.isDirectory) { "Missing native build input directory: $directory" }
        text(label)
        val root = directory.toPath()
        Files.walk(root).use { paths ->
            val files = paths.filter { Files.isRegularFile(it) }.sorted().toList()
            text(files.size.toString())
            files.forEach { file(root.relativize(it).joinToString("/"), it.toFile()) }
        }
    }
}

/** Shared arguments keep the cache key and every native compiler invocation in agreement. */
object NativeLibraryCommands {
    val compilerFlags: List<String> = listOf("cc", "-shared", "-fPIC")

    fun grammar(target: String, parserDirectory: File, parser: File, scanner: File, output: File): List<String> =
        compilerFlags + listOf(
            "-target", target, "-I", parserDirectory.absolutePath,
            parser.absolutePath, scanner.absolutePath, "-o", output.absolutePath,
        )

    fun runtime(target: String, source: File, output: File): List<String> =
        compilerFlags + listOf(
            "-target", target, "-I", File(source, "include").absolutePath,
            "-I", File(source, "src").absolutePath, File(source, "src/lib.c").absolutePath,
            "-o", output.absolutePath,
        )
}
