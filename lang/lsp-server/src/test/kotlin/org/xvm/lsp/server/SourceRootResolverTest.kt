package org.xvm.lsp.server

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@DisplayName("SourceRootResolver")
class SourceRootResolverTest {
    @TempDir
    lateinit var tmp: Path

    /** Create a real directory under [tmp] so the existence-check passes. */
    private fun realDir(name: String): String =
        tmp
            .resolve(name)
            .also { it.toFile().mkdirs() }
            .toString()

    private fun pathList(vararg paths: String): String = paths.joinToString(File.pathSeparator)

    @Nested
    @DisplayName("init options")
    inner class InitOptionsTests {
        @Test
        @DisplayName("should accept Map<String, List<String>> with xtcSourceRoots")
        fun shouldAcceptMap() {
            val a = realDir("a")
            val b = realDir("b")
            val opts = mapOf("xtcSourceRoots" to listOf(a, b))

            val roots = SourceRootResolver.resolve(opts, systemProperty = null, envVar = null)

            assertThat(roots).containsExactly(a, b)
        }

        @Test
        @DisplayName("should accept Gson JsonObject")
        fun shouldAcceptJsonObject() {
            val a = realDir("a")
            val arr = JsonArray().apply { add(JsonPrimitive(a)) }
            val obj = JsonObject().apply { add("xtcSourceRoots", arr) }

            val roots = SourceRootResolver.resolve(obj, systemProperty = null, envVar = null)

            assertThat(roots).containsExactly(a)
        }

        @Test
        @DisplayName("should ignore non-array value at xtcSourceRoots")
        fun shouldIgnoreNonArrayValue() {
            val opts = mapOf("xtcSourceRoots" to "not-a-list")
            assertThat(SourceRootResolver.resolve(opts, systemProperty = null, envVar = null)).isEmpty()
        }

        @Test
        @DisplayName("should return empty for null init options")
        fun shouldReturnEmptyForNull() {
            assertThat(SourceRootResolver.resolve(null, systemProperty = null, envVar = null)).isEmpty()
        }

        @Test
        @DisplayName("should return empty when key absent")
        fun shouldReturnEmptyWhenKeyAbsent() {
            val opts = mapOf("otherKey" to listOf("/some/path"))
            assertThat(SourceRootResolver.resolve(opts, systemProperty = null, envVar = null)).isEmpty()
        }
    }

    @Nested
    @DisplayName("system property")
    inner class SystemPropertyTests {
        @Test
        @DisplayName("should parse path-separated list")
        fun shouldParsePathList() {
            val a = realDir("a")
            val b = realDir("b")

            val roots = SourceRootResolver.resolve(null, systemProperty = pathList(a, b), envVar = null)

            assertThat(roots).containsExactly(a, b)
        }

        @Test
        @DisplayName("should ignore blank entries")
        fun shouldIgnoreBlankEntries() {
            val a = realDir("a")

            val roots = SourceRootResolver.resolve(null, systemProperty = "${File.pathSeparator}$a${File.pathSeparator}", envVar = null)

            assertThat(roots).containsExactly(a)
        }
    }

    @Nested
    @DisplayName("env var")
    inner class EnvVarTests {
        @Test
        @DisplayName("should parse path-separated list")
        fun shouldParseEnvList() {
            val a = realDir("a")
            val roots = SourceRootResolver.resolve(null, systemProperty = null, envVar = a)
            assertThat(roots).containsExactly(a)
        }
    }

    @Nested
    @DisplayName("merging and filtering")
    inner class MergingTests {
        @Test
        @DisplayName("should merge init options + sysprop + env, dedup")
        fun shouldMergeAndDedup() {
            val a = realDir("a")
            val b = realDir("b")
            val c = realDir("c")
            val opts = mapOf("xtcSourceRoots" to listOf(a, b))

            val roots = SourceRootResolver.resolve(opts, systemProperty = pathList(b, c), envVar = a)

            // a, b from init; c new from sysprop; b duplicate; a duplicate from env
            assertThat(roots).containsExactly(a, b, c)
        }

        @Test
        @DisplayName("should pass non-existent paths through (indexer is the single warning point)")
        fun shouldPassMissingPathsThrough() {
            val real = realDir("real")
            val fake = tmp.resolve("does-not-exist").toString()
            val opts = mapOf("xtcSourceRoots" to listOf(real, fake))

            val roots = SourceRootResolver.resolve(opts, systemProperty = null, envVar = null)

            assertThat(roots).containsExactly(real, fake)
        }

        @Test
        @DisplayName("should return empty when all sources are null/empty")
        fun shouldReturnEmptyForAllNull() {
            assertThat(SourceRootResolver.resolve(null, systemProperty = null, envVar = null)).isEmpty()
            assertThat(SourceRootResolver.resolve(null, systemProperty = "", envVar = "")).isEmpty()
        }
    }
}
