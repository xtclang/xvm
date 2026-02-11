package org.xtclang.idea.lsp.jre

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

@DisplayName("JreProvisioner")
class JreProvisionerTest {
    @TempDir
    lateinit var cacheDir: Path

    private val version = JreProvisioner.TARGET_VERSION

    private fun provisioner() = JreProvisioner(cacheDir = cacheDir, version = version)

    /** The JRE directory path that JreProvisioner uses internally. */
    private val jreDir: Path get() = cacheDir.resolve("temurin-$version-jre")

    /** The failure marker path that JreProvisioner uses internally. */
    private val failureMarker: Path get() = cacheDir.resolve(".provision-failed-$version")

    /** The metadata file path that JreProvisioner uses internally. */
    private val metadataFile: Path get() = cacheDir.resolve("temurin-$version-jre.json")

    @Nested
    @DisplayName("findCachedJava()")
    inner class FindCachedJava {
        @Test
        @DisplayName("finds java in standard layout (bin/java)")
        fun standardLayout() {
            val binDir = jreDir.resolve("bin")
            Files.createDirectories(binDir)
            val java = binDir.resolve("java")
            Files.createFile(java)
            Files.setPosixFilePermissions(java, setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ))

            val result = provisioner().findCachedJava()

            assertThat(result).isEqualTo(java)
        }

        @Test
        @DisplayName("finds java in nested structure (jdk-25+9/bin/java)")
        fun nestedStructure() {
            val binDir = jreDir.resolve("jdk-25+9/bin")
            Files.createDirectories(binDir)
            val java = binDir.resolve("java")
            Files.createFile(java)
            Files.setPosixFilePermissions(java, setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ))

            val result = provisioner().findCachedJava()

            assertThat(result).isEqualTo(java)
        }

        @Test
        @DisplayName("finds java in macOS Contents/Home layout")
        fun macOsContentsHome() {
            val binDir = jreDir.resolve("Contents/Home/bin")
            Files.createDirectories(binDir)
            val java = binDir.resolve("java")
            Files.createFile(java)
            Files.setPosixFilePermissions(java, setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ))

            val result = provisioner().findCachedJava()

            assertThat(result).isEqualTo(java)
        }

        @Test
        @DisplayName("returns null when cache directory does not exist")
        fun emptyCache() {
            val result = provisioner().findCachedJava()

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("returns null when java is not executable")
        fun notExecutable() {
            val binDir = jreDir.resolve("bin")
            Files.createDirectories(binDir)
            val java = binDir.resolve("java")
            Files.createFile(java)
            // Ensure not executable
            Files.setPosixFilePermissions(java, setOf(PosixFilePermission.OWNER_READ))

            val result = provisioner().findCachedJava()

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("flattenSingleSubdirectory()")
    inner class FlattenSingleSubdirectory {
        @Test
        @DisplayName("flattens single nested directory")
        fun singleNestedDir() {
            Files.createDirectories(jreDir)
            val nestedDir = jreDir.resolve("jdk-25+9")
            Files.createDirectories(nestedDir.resolve("bin"))
            Files.createDirectories(nestedDir.resolve("lib"))
            Files.createDirectories(nestedDir.resolve("conf"))
            Files.createFile(nestedDir.resolve("bin/java"))
            Files.createFile(nestedDir.resolve("lib/modules"))

            provisioner().flattenSingleSubdirectory()

            assertThat(jreDir.resolve("bin")).isDirectory()
            assertThat(jreDir.resolve("lib")).isDirectory()
            assertThat(jreDir.resolve("conf")).isDirectory()
            assertThat(jreDir.resolve("bin/java")).exists()
            assertThat(nestedDir).doesNotExist()
        }

        @Test
        @DisplayName("does not flatten when multiple entries exist")
        fun multipleEntries() {
            Files.createDirectories(jreDir.resolve("bin"))
            Files.createDirectories(jreDir.resolve("lib"))
            Files.createFile(jreDir.resolve("bin/java"))

            provisioner().flattenSingleSubdirectory()

            // Should remain unchanged
            assertThat(jreDir.resolve("bin")).isDirectory()
            assertThat(jreDir.resolve("lib")).isDirectory()
        }

        @Test
        @DisplayName("does nothing for empty directory")
        fun emptyDir() {
            Files.createDirectories(jreDir)

            provisioner().flattenSingleSubdirectory()

            assertThat(jreDir).isEmptyDirectory()
        }
    }

    @Nested
    @DisplayName("Failure markers")
    inner class FailureMarkers {
        @Test
        @DisplayName("hasFailedBefore returns false when no marker exists")
        fun noMarker() {
            assertThat(provisioner().hasFailedBefore()).isFalse()
        }

        @Test
        @DisplayName("hasFailedBefore returns true when marker exists")
        fun markerExists() {
            Files.createDirectories(cacheDir)
            Files.createFile(failureMarker)

            assertThat(provisioner().hasFailedBefore()).isTrue()
        }

        @Test
        @DisplayName("clearFailure removes marker, metadata, and cache")
        fun clearFailure() {
            // Set up failure marker, metadata, and cached JRE
            Files.createDirectories(cacheDir)
            Files.createFile(failureMarker)
            Files.writeString(metadataFile, "{}")
            Files.createDirectories(jreDir.resolve("bin"))
            Files.createFile(jreDir.resolve("bin/java"))

            provisioner().clearFailure()

            assertThat(failureMarker).doesNotExist()
            assertThat(metadataFile).doesNotExist()
            assertThat(jreDir).doesNotExist()
        }
    }
}
