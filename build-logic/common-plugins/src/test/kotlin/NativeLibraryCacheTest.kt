import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NativeLibraryCacheTest {
    @TempDir
    lateinit var root: File

    private fun entry() = File(root, "hash/linux-x64")
    private fun grammar(entry: File) = File(entry, "libtree-sitter-xtc.so")
    private fun runtime(entry: File) = File(entry, "libtree-sitter.so")

    @Test
    fun successfulPairIsReusedWithoutCallingBuilder() {
        val entry = NativeLibraryCache.getOrBuild(entry(), "so") { grammar, runtime ->
            grammar.writeText("grammar")
            runtime.writeText("runtime")
        }
        assertEquals(entry, NativeLibraryCache.getOrBuild(entry, "so") { _, _ -> error("Unexpected rebuild") })
        assertEquals("grammar", grammar(entry).readText())
        assertEquals("runtime", runtime(entry).readText())
        assertEquals(listOf(entry.name), entry.parentFile.list()!!.toList())
    }

    @Test
    fun failedBuildDoesNotPublishOrLeaveStagingFiles() {
        val failure = assertThrows(IOException::class.java) {
            NativeLibraryCache.getOrBuild(entry(), "so") { grammar, _ ->
                grammar.writeText("partial")
                throw IOException("compiler failed")
            }
        }
        assertEquals("compiler failed", failure.message)
        assertFalse(entry().exists())
        assertEquals(emptyList<String>(), entry().parentFile.list()!!.toList())
    }

    @Test
    fun missingRuntimeIsNeverPublished() {
        assertThrows(IOException::class.java) {
            NativeLibraryCache.getOrBuild(entry(), "so") { grammar, _ -> grammar.writeText("grammar") }
        }
        assertFalse(entry().exists())
    }

    @Test
    fun emptyLibraryIsNeverPublished() {
        assertThrows(IOException::class.java) {
            NativeLibraryCache.getOrBuild(entry(), "so") { grammar, runtime ->
                grammar.writeText("grammar")
                runtime.writeText("")
            }
        }
        assertFalse(entry().exists())
    }

    @Test
    fun incompleteExistingEntryIsReportedWithoutOverwritingIt() {
        entry().mkdirs()
        grammar(entry()).writeText("existing")
        assertThrows(IOException::class.java) {
            NativeLibraryCache.getOrBuild(entry(), "so") { _, _ -> error("Must not overwrite an existing entry") }
        }
        assertEquals("existing", grammar(entry()).readText())
    }

    @Test
    fun concurrentBuildersPublishOneWholePair() {
        val ready = CountDownLatch(2)
        val finish = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val builds = listOf("first", "second").map { name ->
                executor.submit<File> {
                    NativeLibraryCache.getOrBuild(entry(), "so") { grammar, runtime ->
                        grammar.writeText(name)
                        ready.countDown()
                        check(finish.await(10, SECONDS)) { "Timed out waiting for publication check" }
                        runtime.writeText(name)
                    }
                }
            }
            try {
                assertTrue(ready.await(10, SECONDS))
                assertFalse(entry().exists(), "Partial builds must not be visible as cache entries")
            } finally {
                finish.countDown()
            }
            builds.forEach { assertEquals(entry(), it.get(10, SECONDS)) }
        }
        assertTrue(grammar(entry()).readText() in listOf("first", "second"))
        assertEquals(grammar(entry()).readText(), runtime(entry()).readText())
        assertEquals(listOf(entry().name), entry().parentFile.list()!!.toList())
    }
}
