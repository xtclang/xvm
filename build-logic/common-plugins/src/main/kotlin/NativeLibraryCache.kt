import java.io.File
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

/** Publishes a complete pair with one rename; concurrent builders never write into a shared entry. */
object NativeLibraryCache {
    fun getOrBuild(directory: File, extension: String, build: (File, File) -> Unit): File {
        if (directory.exists()) {
            requireComplete(directory, extension)
            return directory
        }

        val parent = directory.toPath().toAbsolutePath().parent
        Files.createDirectories(parent)
        val staging = Files.createTempDirectory(parent, ".native-staging-").toFile()
        try {
            build(File(staging, "libtree-sitter-xtc.$extension"), File(staging, "libtree-sitter.$extension"))
            requireComplete(staging, extension)
            try {
                Files.move(staging.toPath(), directory.toPath(), ATOMIC_MOVE)
            } catch (failure: FileSystemException) {
                // Another builder may have published a complete, immutable entry first.
                // A missing/partial destination must not hide an actual publication error.
                if (!isComplete(directory, extension)) throw failure
            }
            return directory
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun requireComplete(directory: File, extension: String) {
        if (!isComplete(directory, extension)) {
            throw IOException("Incomplete native library cache entry: $directory")
        }
    }

    private fun isComplete(directory: File, extension: String): Boolean =
        listOf("libtree-sitter-xtc.$extension", "libtree-sitter.$extension")
            .all { name -> File(directory, name).let { it.isFile && it.length() > 0 } }
}
