package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList

/** A required consumer of the existing Java API; no installed XDK or later listener API is needed. */
class CompilerConsumerTest {
    @Test
    fun `compiled variants support a real embedding compilation`() {
        CompilerTestSupport.configure()
        val errors = ErrorList(100)
        val module = EmbeddingSupport.instance().compile("module Consumer { Int answer() = 42; }", null, errors)

        assertThat(errors.errors).isEmpty()
        assertThat(module).isNotNull()
        assertThat(module.name).isEqualTo("Consumer")
    }
}
