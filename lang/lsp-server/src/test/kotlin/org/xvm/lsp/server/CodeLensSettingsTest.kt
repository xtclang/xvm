package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Protects the enabled reference-lens default and strict boolean configuration. Missing preferences remain
 * valid while malformed overrides fail instead of silently changing behavior.
 */
class CodeLensSettingsTest {
    @Test
    fun `references default on and accept only an explicit boolean override`() {
        listOf(null, emptyMap<String, Any>(), mapOf("references" to true)).forEach {
            assertThat(CodeLensSettings.references(it)).isTrue()
        }
        assertThat(CodeLensSettings.references(mapOf("references" to false))).isFalse()
        listOf(false, "false", mapOf("references" to "false"), mapOf("references" to null)).forEach {
            assertThatThrownBy { CodeLensSettings.references(it) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
