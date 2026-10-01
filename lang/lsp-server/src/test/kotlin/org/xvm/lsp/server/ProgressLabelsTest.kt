package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.util.ProgressLabels

class ProgressLabelsTest {
    @Test
    fun `source labels retain useful paths and select the most specific workspace root`() {
        assertThat(ProgressLabels.source("file:///code/platform/src/Consumer.x", listOf("file:///code/", "file:///code/platform/")))
            .isEqualTo("src/Consumer.x")
        assertThat(ProgressLabels.source("file:///code/My%20App/Library.x"))
            .isEqualTo("My App/Library.x")
        assertThat(ProgressLabels.source("untitled:Scratch.x"))
            .isEqualTo("untitled:Scratch.x")
        assertThat(ProgressLabels.source("file:///code/Consumer%0A.x"))
            .isEqualTo("code/Consumer .x")
        assertThat(ProgressLabels.source("invalid path with spaces"))
            .isEqualTo("invalid path with spaces")
    }
}
