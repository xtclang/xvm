package org.xvm.lsp.server

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("LspJsonOptions")
class LspJsonOptionsTest {
    @Nested
    @DisplayName("isObject")
    inner class IsObjectTests {
        @Test
        fun `should reject null`() {
            assertThat(LspJsonOptions.isObject(null)).isFalse()
        }

        @Test
        fun `should reject scalar`() {
            assertThat(LspJsonOptions.isObject("hello")).isFalse()
            assertThat(LspJsonOptions.isObject(42)).isFalse()
            assertThat(LspJsonOptions.isObject(listOf("a"))).isFalse()
        }

        @Test
        fun `should accept Map`() {
            assertThat(LspJsonOptions.isObject(mapOf("a" to 1))).isTrue()
            assertThat(LspJsonOptions.isObject(emptyMap<String, Any>())).isTrue()
        }

        @Test
        fun `should accept JsonObject`() {
            assertThat(LspJsonOptions.isObject(JsonObject())).isTrue()
        }

        @Test
        fun `should accept JsonElement holding an object`() {
            val elem: com.google.gson.JsonElement = JsonObject()
            assertThat(LspJsonOptions.isObject(elem)).isTrue()
        }

        @Test
        fun `should reject JsonElement holding non-object`() {
            assertThat(LspJsonOptions.isObject(JsonPrimitive("foo"))).isFalse()
            assertThat(LspJsonOptions.isObject(JsonArray())).isFalse()
            assertThat(LspJsonOptions.isObject(JsonNull.INSTANCE)).isFalse()
        }
    }

    @Nested
    @DisplayName("stringList")
    inner class StringListTests {
        @Test
        fun `should read List of strings from Map`() {
            val raw = mapOf("paths" to listOf("/a", "/b"))
            assertThat(LspJsonOptions.stringList(raw, "paths")).containsExactly("/a", "/b")
        }

        @Test
        fun `should read JsonArray of strings from JsonObject`() {
            val arr =
                JsonArray().apply {
                    add(JsonPrimitive("/a"))
                    add(JsonPrimitive("/b"))
                }
            val obj = JsonObject().apply { add("paths", arr) }
            assertThat(LspJsonOptions.stringList(obj, "paths")).containsExactly("/a", "/b")
        }

        @Test
        fun `should drop blank and null entries from List`() {
            val raw = mapOf("paths" to listOf("/a", "  ", null, "/b"))
            assertThat(LspJsonOptions.stringList(raw, "paths")).containsExactly("/a", "/b")
        }

        @Test
        fun `should drop JsonNull and blank entries from JsonArray`() {
            val arr =
                JsonArray().apply {
                    add(JsonPrimitive("/a"))
                    add(JsonNull.INSTANCE)
                    add(JsonPrimitive("  "))
                    add(JsonPrimitive("/b"))
                }
            val obj = JsonObject().apply { add("paths", arr) }
            assertThat(LspJsonOptions.stringList(obj, "paths")).containsExactly("/a", "/b")
        }

        @Test
        fun `should return empty for missing key`() {
            assertThat(LspJsonOptions.stringList(mapOf("other" to listOf("x")), "paths")).isEmpty()
        }

        @Test
        fun `should return empty for non-array value`() {
            assertThat(LspJsonOptions.stringList(mapOf("paths" to "not-a-list"), "paths")).isEmpty()
        }

        @Test
        fun `should return empty for null raw`() {
            assertThat(LspJsonOptions.stringList(null, "paths")).isEmpty()
        }

        @Test
        fun `should coerce non-string list entries via toString`() {
            val raw = mapOf("vals" to listOf(42, 3.14, true))
            assertThat(LspJsonOptions.stringList(raw, "vals")).containsExactly("42", "3.14", "true")
        }
    }

    @Nested
    @DisplayName("int")
    inner class IntTests {
        @Test
        fun `should read Int from Map`() {
            assertThat(LspJsonOptions.int(mapOf("n" to 7), "n")).isEqualTo(7)
        }

        @Test
        fun `should coerce Long Number to Int`() {
            assertThat(LspJsonOptions.int(mapOf("n" to 7L), "n")).isEqualTo(7)
        }

        @Test
        fun `should coerce numeric String to Int`() {
            assertThat(LspJsonOptions.int(mapOf("n" to "12"), "n")).isEqualTo(12)
        }

        @Test
        fun `should reject non-numeric String`() {
            assertThat(LspJsonOptions.int(mapOf("n" to "abc"), "n")).isNull()
        }

        @Test
        fun `should read JsonPrimitive number from JsonObject`() {
            val obj = JsonObject().apply { add("n", JsonPrimitive(42)) }
            assertThat(LspJsonOptions.int(obj, "n")).isEqualTo(42)
        }

        @Test
        fun `should read numeric JsonPrimitive string from JsonObject`() {
            val obj = JsonObject().apply { add("n", JsonPrimitive("99")) }
            assertThat(LspJsonOptions.int(obj, "n")).isEqualTo(99)
        }

        @Test
        fun `should return null for JsonNull value`() {
            val obj = JsonObject().apply { add("n", JsonNull.INSTANCE) }
            assertThat(LspJsonOptions.int(obj, "n")).isNull()
        }

        @Test
        fun `should return null for missing key`() {
            assertThat(LspJsonOptions.int(mapOf("other" to 1), "n")).isNull()
        }

        @Test
        fun `should return null for null raw`() {
            assertThat(LspJsonOptions.int(null, "n")).isNull()
        }
    }

    @Nested
    @DisplayName("boolean")
    inner class BooleanTests {
        @Test
        fun `should read Boolean from Map`() {
            assertThat(LspJsonOptions.boolean(mapOf("b" to true), "b")).isTrue()
            assertThat(LspJsonOptions.boolean(mapOf("b" to false), "b")).isFalse()
        }

        @Test
        fun `should coerce true and false strings`() {
            assertThat(LspJsonOptions.boolean(mapOf("b" to "true"), "b")).isTrue()
            assertThat(LspJsonOptions.boolean(mapOf("b" to "false"), "b")).isFalse()
        }

        @Test
        fun `should reject other strings (strict)`() {
            assertThat(LspJsonOptions.boolean(mapOf("b" to "yes"), "b")).isNull()
            assertThat(LspJsonOptions.boolean(mapOf("b" to "1"), "b")).isNull()
            assertThat(LspJsonOptions.boolean(mapOf("b" to "TRUE"), "b")).isNull()
        }

        @Test
        fun `should read JsonPrimitive boolean from JsonObject`() {
            val obj = JsonObject().apply { add("b", JsonPrimitive(true)) }
            assertThat(LspJsonOptions.boolean(obj, "b")).isTrue()
        }

        @Test
        fun `should read boolean JsonPrimitive string from JsonObject`() {
            val obj = JsonObject().apply { add("b", JsonPrimitive("false")) }
            assertThat(LspJsonOptions.boolean(obj, "b")).isFalse()
        }

        @Test
        fun `should return null for JsonNull value`() {
            val obj = JsonObject().apply { add("b", JsonNull.INSTANCE) }
            assertThat(LspJsonOptions.boolean(obj, "b")).isNull()
        }

        @Test
        fun `should return null for missing key`() {
            assertThat(LspJsonOptions.boolean(mapOf("other" to true), "b")).isNull()
        }

        @Test
        fun `should return null for null raw`() {
            assertThat(LspJsonOptions.boolean(null, "b")).isNull()
        }
    }
}
