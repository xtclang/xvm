import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Extracts the regular files and directories used by the native tool distributions. */
object SafeTarExtractor {
    fun extract(archive: TarArchiveInputStream, outputDir: File) {
        val root = outputDir.toPath().toAbsolutePath().normalize()
        Files.createDirectories(root)
        val canonicalRoot = root.toRealPath()
        var entry = archive.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name.startsWith('/') || '\\' in name || ':' in name) {
                throw IOException("Unsafe tar entry '$name': expected a relative archive path")
            }
            val target = root.resolve(name).normalize()
            val output = target.toFile()
            if (!target.startsWith(root) || !output.canonicalFile.toPath().startsWith(canonicalRoot)) {
                throw IOException("Unsafe tar entry '$name': destination escapes the extraction directory")
            }
            val directory = entry.linkFlag == TarConstants.LF_DIR
            val regular = entry.linkFlag == TarConstants.LF_NORMAL || entry.linkFlag == TarConstants.LF_OLDNORM
            if ((!directory && !regular) || !archive.canReadEntryData(entry)) {
                throw IOException("Unsafe tar entry '$name': only regular files and directories are supported")
            }
            if (directory) {
                Files.createDirectories(target)
            } else {
                Files.createDirectories(target.parent)
                output.outputStream().buffered().use { archive.copyTo(it) }
                if (entry.mode and 0b001_000_000 != 0) {
                    output.setExecutable(true)
                }
            }
            entry = archive.nextEntry
        }
    }
}
