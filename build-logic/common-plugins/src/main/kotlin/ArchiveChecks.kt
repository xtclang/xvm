import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Fails, and deletes [archive], unless it reads as a whole zip or jar: every entry at its offset, with all of its
 * data. Two builds of one checkout that write or copy the same jar at once can leave one that lists its entries but
 * cannot read them. A later build would take such a file as up to date, since the fingerprint it recorded was taken
 * from the finished file, so the check deletes it and fails instead, and the next build makes it again.
 *
 * @param what what the archive is, as the failure names it
 */
fun requireWholeArchive(
    archive: File,
    what: String,
) {
    val broken =
        try {
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                    zip.getInputStream(entry).use { it.transferTo(OutputStream.nullOutputStream()) }
                }
            }
            null
        } catch (e: IOException) {
            e
        }
    if (broken != null) {
        archive.delete()
        throw GradleException(
            "$what, $archive, is not a whole archive (${broken.message}): another build was probably writing it at the " +
                "same time. It is deleted; run the build again.",
            broken,
        )
    }
}

/**
 * Fails, and deletes [archive], unless its [entry] has the same bytes as [expected], as a copy of [expected] that an
 * archive was made with must. See [requireWholeArchive] for why such a copy can differ.
 *
 * @param what what the archive is, as the failure names it
 */
fun requireArchivedCopy(
    archive: File,
    entry: String,
    expected: File,
    what: String,
) {
    val archived = ZipFile(archive).use { zip -> zip.getEntry(entry)?.let { found -> zip.getInputStream(found).use(::sha256) } }
    if (archived == null || !archived.contentEquals(expected.inputStream().use(::sha256))) {
        archive.delete()
        throw GradleException(
            "$what, $archive, does not hold $expected as $entry: another build was probably writing it at the same " +
                "time. It is deleted; run the build again.",
        )
    }
}

private fun sha256(input: InputStream): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(1 shl 16)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return digest.digest()
        digest.update(buffer, 0, read)
    }
}
